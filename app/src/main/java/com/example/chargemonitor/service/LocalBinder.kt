package com.example.chargemonitor.service

import android.os.Binder

class LocalBinder(private val service: ChargeMonitorService) : Binder() {
    fun getService(): ChargeMonitorService = service
}
