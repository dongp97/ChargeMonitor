package com.awu.haorizi

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import org.json.JSONObject
import java.io.File

class MainActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var root: FrameLayout
    private var backHandled = false

    private val assetLoader: WebViewAssetLoader by lazy {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
    }

    companion object {
        private const val REQ_NOTIFY = 1001
        private const val REQ_PICK = 1002
        private const val REQ_WRITE = 1003
        private const val HOME = "https://appassets.androidplatform.net/assets/index.html"
    }

    // ------------------------------------------------------------ 生命周期

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 必须在 setContentView 之前声明「内容区自己处理系统栏」。
        // 放到后面的话，首次 insets 分发可能已经按旧模式走完了，
        // 之后不再触发，页面就会顶到状态栏上去。
        WindowCompat.setDecorFitsSystemWindows(window, false)

        root = FrameLayout(this)
        root.setBackgroundColor(Color.parseColor(ReminderStore.chromeBg(this)))
        setContentView(root, ViewGroup.LayoutParams(-1, -1))

        web = WebView(this)
        web.setBackgroundColor(Color.TRANSPARENT)
        web.overScrollMode = View.OVER_SCROLL_NEVER
        root.addView(web, FrameLayout.LayoutParams(-1, -1))

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            loadsImagesAutomatically = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            textZoom = 100
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = true
        }

        WebView.setWebContentsDebuggingEnabled(true)

        web.webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                if (url.startsWith("https://appassets.androidplatform.net/")) return false
                // 外部链接交给系统浏览器，别在 App 里跑
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    true
                } catch (_: ActivityNotFoundException) {
                    true
                }
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(m: android.webkit.ConsoleMessage): Boolean {
                android.util.Log.i(ReminderScheduler.TAG, "JS: ${m.message()} @${m.lineNumber()}")
                return true
            }
        }

        web.addJavascriptInterface(Bridge(), "AndroidBridge")

        // 状态栏 / 挖孔 / 手势条 / 键盘区域：全部由原生算好，作为 WebView 的内边距。
        // 页面本身不处理安全区，HTML 那边也不需要 env()。
        root.fitsSystemWindows = false
        web.fitsSystemWindows = false

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            applySafeArea(insets)
            insets
        }
        // ⚠️ 挂上监听器后必须主动要一次。
        // 首轮 insets 分发可能在我们挂监听器之前就过去了，此后若没有任何
        // insets 变化事件（不动键盘、不转屏），回调永远不触发 ——
        // 表现就是页面一路顶到状态栏上面，和状态栏重叠。
        ViewCompat.requestApplyInsets(root)

        applyStatusBarIcons(ReminderStore.chromeLight(this))

        web.loadUrl(HOME)
        web.post { ViewCompat.requestApplyInsets(root) }
        // 兜底：个别 ROM 首轮 insets 分发始终不来，延迟再查一次，
        // 仍然没值就直接用系统资源里的栏高。宁可略高，也不能被状态栏压住。
        web.postDelayed({
            if (safeTop < 0) {
                Log.w(ReminderScheduler.TAG, "insets 未分发，改用系统资源兜底")
                applyFallbackSafeArea()
            }
        }, 500)

        // 打开时把闹钟刷新一遍（系统可能在后台清过）
        ReminderScheduler.reschedule(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        web.evaluateJavascript("window.HR&&window.HR.refresh&&window.HR.refresh()", null)
    }

    override fun onResume() {
        super.onResume()
        // 回到前台时重新要一次安全区（从后台回来时系统栏状态可能变过）
        if (::root.isInitialized) ViewCompat.requestApplyInsets(root)
        web.evaluateJavascript("window.HR&&window.HR.onResume&&window.HR.onResume()", null)
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (backHandled) {
            web.evaluateJavascript("window.HR&&window.HR.onBack&&window.HR.onBack()", null)
        } else {
            super.onBackPressed()
        }
    }

    // ------------------------------------------------------------ 权限回调

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIFY || requestCode == REQ_WRITE) {
            notifyJs()
        }
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_PICK) return
        if (resultCode != RESULT_OK || data?.data == null) {
            toast("已取消导入")
            return
        }
        val uri = data.data!!
        val text = try {
            contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            }
        } catch (e: Exception) {
            null
        }
        if (text == null) {
            toast("读取失败，换一个文件试试")
            return
        }
        val quoted = JSONObject.quote(text)
        runOnUiThread {
            web.evaluateJavascript("window.HR&&window.HR.onBackupLoaded($quoted)", null)
        }
    }

    // ------------------------------------------------------------ 工具

    private fun toast(msg: String) {
        runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
    }

    private fun notifyJs() {
        runOnUiThread {
            web.evaluateJavascript("window.HR&&window.HR.onNativeChange&&window.HR.onNativeChange()", null)
        }
    }

    // ------------------------------------------------------------ 安全区

    private var safeTop = -1
    private var safeBottom = -1

    /** 系统栏高度兜底：个别 ROM 首轮分发给 0，这时按系统资源里的高度补上 */
    private fun systemBarHeight(name: String, defDp: Int): Int {
        val id = resources.getIdentifier(name, "dimen", "android")
        val px = if (id > 0) resources.getDimensionPixelSize(id)
        else (defDp * resources.displayMetrics.density).toInt()
        return px.coerceAtLeast(0)
    }

    /**
     * 把状态栏 / 挖孔 / 手势条 / 键盘的高度算成 WebView 的内边距。
     *
     * systemBars() 已含状态栏和导航栏，再并上 displayCutout()，
     * getInsets 取并集最大值，所以挖孔比状态栏高时也能盖住。
     */
    private fun applySafeArea(insets: WindowInsetsCompat) {
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

        var top = bars.top
        // 键盘弹起时它盖住的是导航栏那一侧，取较大值
        var bottom = maxOf(bars.bottom, ime.bottom)

        if (top <= 0) top = systemBarHeight("status_bar_height", 28)
        if (bottom <= 0) bottom = systemBarHeight("navigation_bar_height", 24)

        if (top == safeTop && bottom == safeBottom) return
        safeTop = top
        safeBottom = bottom
        web.setPadding(0, top, 0, bottom)
        Log.i(ReminderScheduler.TAG, "安全区 top=$top bottom=$bottom")
    }

    /** 首轮 insets 分发缺席时的兜底：直接按系统资源里的栏高留白 */
    private fun applyFallbackSafeArea() {
        val top = systemBarHeight("status_bar_height", 28)
        val bottom = systemBarHeight("navigation_bar_height", 24)
        safeTop = top
        safeBottom = bottom
        web.setPadding(0, top, 0, bottom)
        Log.i(ReminderScheduler.TAG, "兜底安全区 top=$top bottom=$bottom")
    }

    private fun applyStatusBarIcons(lightIcons: Boolean) {
        val c = WindowInsetsControllerCompat(window, root)
        c.isAppearanceLightStatusBars = lightIcons
        c.isAppearanceLightNavigationBars = lightIcons
    }

    private fun notifyGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
    }

    // ------------------------------------------------------------ JS 桥

    inner class Bridge {

        /** 环境信息，JS 启动时拉一次 */
        @JavascriptInterface
        fun info(): String {
            val o = JSONObject()
            o.put("version", "1.0")
            o.put("sdk", Build.VERSION.SDK_INT)
            o.put("model", Build.MODEL)
            o.put("notifyGranted", notifyGranted())
            o.put("exactAlarm", ReminderScheduler.canScheduleExact(this@MainActivity))
            // 安全区实测值，方便排查"内容顶到状态栏"这类问题
            o.put("safeTop", safeTop)
            o.put("safeBottom", safeBottom)
            return o.toString()
        }

        @JavascriptInterface
        fun toast(msg: String) {
            this@MainActivity.toast(msg)
        }

        @JavascriptInterface
        fun vibrate(ms: Int) {
            try {
                val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    getSystemService(VIBRATOR_SERVICE) as? Vibrator
                } ?: return
                if (!v.hasVibrator()) return
                v.vibrate(VibrationEffect.createOneShot(ms.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
            } catch (_: Exception) {
            }
        }

        /** 让原生层知道有没有弹层打开 —— 决定返回键行为 */
        @JavascriptInterface
        fun setBackHandled(v: Boolean) {
            backHandled = v
        }

        /** 页面底色同步给原生，状态栏和页面就不会出现断层 */
        @JavascriptInterface
        fun setChrome(bg: String, lightIcons: Boolean) {
            runOnUiThread {
                try {
                    root.setBackgroundColor(Color.parseColor(bg))
                } catch (_: Exception) {
                }
                applyStatusBarIcons(lightIcons)
                ReminderStore.saveChrome(this@MainActivity, bg, lightIcons)
            }
        }

        @JavascriptInterface
        fun requestNotify() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notifyGranted()) {
                runOnUiThread {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFY)
                }
            } else {
                toast("通知权限已开启")
            }
        }

        @JavascriptInterface
        fun testNotification() {
            if (!notifyGranted()) {
                toast("请先允许通知权限")
                requestNotify()
                return
            }
            ReminderScheduler.fireTest(this@MainActivity)
            toast("已发送一条测试通知")
        }

        @JavascriptInterface
        fun openExactAlarmSettings() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                toast("当前系统不需要单独授权")
                return
            }
            try {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        .setData(Uri.parse("package:$packageName"))
                )
            } catch (_: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:$packageName")))
                } catch (_: Exception) {
                    toast("请到系统设置里手动开启")
                }
            }
        }

        /** 把 JS 算好的提醒清单交给原生排闹钟 */
        @JavascriptInterface
        fun syncReminders(json: String) {
            ReminderStore.save(this@MainActivity, json)
            ReminderScheduler.reschedule(this@MainActivity)
        }

        /** 导出备份，返回给用户看的结果文案 */
        @JavascriptInterface
        fun exportBackup(fileName: String, content: String): String =
            doExport(fileName, content)

        @JavascriptInterface
        fun shareText(text: String) {
            runOnUiThread {
                try {
                    val i = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    startActivity(Intent.createChooser(i, "分享到"))
                } catch (_: Exception) {
                    toast("没有可用的分享目标")
                }
            }
        }

        @JavascriptInterface
        fun pickBackup() {
            runOnUiThread {
                try {
                    val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }
                    startActivityForResult(i, REQ_PICK)
                } catch (_: Exception) {
                    toast("打不开文件选择器")
                }
            }
        }
    }

    private fun doExport(name: String, content: String): String {
        return try {
            val bytes = content.toByteArray(Charsets.UTF_8)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return "保存失败：无法创建文件"
                contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: return "保存失败：无法写入文件"
                "已保存到「下载」目录：$name"
            } else {
                if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissions(
                        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQ_WRITE
                    )
                    return "需要存储权限，请授权后再导出一次"
                }
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!dir.exists()) dir.mkdirs()
                File(dir, name).writeBytes(bytes)
                "已保存到「下载」目录：$name"
            }
        } catch (e: Exception) {
            "保存失败：${e.message}"
        }
    }
}
