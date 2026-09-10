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
import androidx.core.view.updatePadding
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

        // 状态栏 / 导航栏 / 键盘区域用 App 自己的底色填充，页面被安全区顶下来。
        // IME 必须算进来，否则弹出键盘会盖住编辑表单。
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                    or WindowInsetsCompat.Type.displayCutout()
                    or WindowInsetsCompat.Type.ime()
            )
            web.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
        applyStatusBarIcons(ReminderStore.chromeLight(this))

        web.loadUrl(HOME)

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
