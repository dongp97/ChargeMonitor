package com.example.chargemonitor.ui.main

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.chargemonitor.R
import com.example.chargemonitor.databinding.ActivityMainBinding
import com.example.chargemonitor.service.ChargeMonitorService
import com.example.chargemonitor.service.LocalBinder
import com.example.chargemonitor.ui.history.HistoryListFragment
import com.example.chargemonitor.ui.settings.SettingsFragment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 主容器：底部导航（实时 / 历史 / 设置）+ 前台服务绑定。
 * 对齐 PRD：直接启动前台服务，不做通知权限请求引导。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isBound = false

    private val _service = MutableStateFlow<ChargeMonitorService?>(null)
    val serviceFlow: StateFlow<ChargeMonitorService?> = _service.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? LocalBinder
            _service.value = localBinder?.getService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            _service.value = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBottomNav()
        if (savedInstanceState == null) {
            binding.bottomNav.selectedItemId = R.id.nav_realtime
        }

        startServiceAndBind()
    }

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_realtime -> switchTo(RealtimeFragment())
                R.id.nav_history -> switchTo(HistoryListFragment())
                R.id.nav_settings -> switchTo(SettingsFragment())
                else -> return@setOnItemSelectedListener false
            }
            true
        }
    }

    private fun switchTo(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_container, fragment)
            .commit()
    }

    private fun startServiceAndBind() {
        ChargeMonitorService.start(this)
        bindService(
            Intent(this, ChargeMonitorService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
        isBound = true
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}
