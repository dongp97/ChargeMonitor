package com.example.chargemonitor.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.chargemonitor.ChargeMonitorApp
import com.example.chargemonitor.R
import com.example.chargemonitor.battery.BatteryReader
import com.example.chargemonitor.battery.ChargeSessionManager
import com.example.chargemonitor.battery.ChargeType
import com.example.chargemonitor.data.repository.ChargeRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class ChargeMonitorService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var batteryReader: BatteryReader
    private lateinit var sessionManager: ChargeSessionManager
    private lateinit var repository: ChargeRepository

    private var sampleCount = 0L

    private val _power = MutableStateFlow(0.0)
    val power: StateFlow<Double> = _power.asStateFlow()

    private val _mah = MutableStateFlow(0.0)
    val mah: StateFlow<Double> = _mah.asStateFlow()

    private val _voltage = MutableStateFlow(0.0)
    val voltage: StateFlow<Double> = _voltage.asStateFlow()

    private val _current = MutableStateFlow(0.0)
    val current: StateFlow<Double> = _current.asStateFlow()

    private val _temp = MutableStateFlow(0.0)
    val temp: StateFlow<Double> = _temp.asStateFlow()

    private val _level = MutableStateFlow(0)
    val level: StateFlow<Int> = _level.asStateFlow()

    private val _chargeType = MutableStateFlow(ChargeType.NONE)
    val chargeType: StateFlow<ChargeType> = _chargeType.asStateFlow()

    private val _isCharging = MutableStateFlow(false)
    val isCharging: StateFlow<Boolean> = _isCharging.asStateFlow()

    private val _sessionState = MutableStateFlow<ChargeSessionManager.State>(ChargeSessionManager.State.Idle)
    val sessionState: StateFlow<ChargeSessionManager.State> = _sessionState.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        batteryReader = BatteryReader(this)
        val app = application as ChargeMonitorApp
        repository = ChargeRepository(app.database.sessionDao(), app.database.sampleDao())
        sessionManager = ChargeSessionManager(repository)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground()
        startMonitoring()
        return START_STICKY
    }

    private fun startForeground() {
        val notification = createNotification(0.0, 0.0, "未充电")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                ChargeMonitorApp.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(ChargeMonitorApp.NOTIFICATION_ID, notification)
        }
    }

    private fun startMonitoring() {
        scope.launch {
            while (isActive) {
                try {
                    val data = batteryReader.readSampleData()

                    // 更新 UI 状态流
                    val powerW = data.voltageV * (data.currentMa / 1000.0)
                    _power.value = powerW
                    _voltage.value = data.voltageV
                    _current.value = data.currentMa
                    _temp.value = data.tempC
                    _level.value = data.level
                    _chargeType.value = data.chargeType
                    _isCharging.value = data.chargeType != ChargeType.NONE

                    // 处理会话
                    sessionManager.onSample(data)
                    _sessionState.value = sessionManager.state.value

                    // 从当前会话获取累计电量
                    val activeSession = sessionManager.state.value
                    if (activeSession is ChargeSessionManager.State.Active) {
                        _mah.value = activeSession.session.totalMah
                    } else {
                        _mah.value = 0.0
                    }

                    // 更新通知
                    updateNotification(powerW, _mah.value, data.chargeType.label)

                    // 每 100 次采样（约 200 秒）清理一次 90 天前的数据
                    sampleCount++
                    if (sampleCount % 100 == 0L) {
                        sessionManager.cleanupOldSamples()
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(SAMPLE_INTERVAL_MS)
            }
        }
    }

    private fun createNotification(power: Double, mah: Double, type: String): android.app.Notification {
        val intent = Intent(this, Class.forName("com.example.chargemonitor.ui.main.MainActivity")).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, ChargeMonitorApp.CHANNEL_ID)
            .setContentTitle("$type • ${String.format("%.1f", power)}W")
            .setContentText("已充入 ${String.format("%.0f", mah)}mAh")
            .setSmallIcon(R.drawable.ic_charging)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(power: Double, mah: Double, type: String) {
        val notification = createNotification(power, mah, type)
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(ChargeMonitorApp.NOTIFICATION_ID, notification)
    }

    private val binder = LocalBinder(this)

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val SAMPLE_INTERVAL_MS = 2000L

        fun start(context: Context) {
            val intent = Intent(context, ChargeMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ChargeMonitorService::class.java)
            context.stopService(intent)
        }
    }
}
