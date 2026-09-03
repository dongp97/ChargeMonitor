package com.example.chargemonitor.battery

import com.example.chargemonitor.data.entity.Phase
import com.example.chargemonitor.data.repository.ChargeRepository
import kotlin.math.abs

/**
 * 充/用电阶段管理：以充电状态切换为分节点，划分充电阶段和用电阶段，
 * 统计每个阶段的时长、平均电流、平均功率、累计 mAh（充电 mAh 或放电 mAh）。
 */
class PhaseManager(private val repository: ChargeRepository) {

    private var currentType: String? = null
    private var startTime = 0L
    private var lastSampleTs = 0L
    private var sampleCount = 0
    private var totalMah = 0.0
    private var currentSum = 0.0
    private var powerSum = 0.0

    suspend fun onSample(data: SampleData, now: Long) {
        val type = if (data.chargeType != ChargeType.NONE) Phase.TYPE_CHARGING else Phase.TYPE_DISCHARGING

        if (currentType == null) {
            startPhase(type, now)
        } else if (currentType != type) {
            endPhase(now)
            startPhase(type, now)
        }
        accumulate(data, now)
    }

    private fun startPhase(type: String, now: Long) {
        currentType = type
        startTime = now
        lastSampleTs = now
        sampleCount = 0
        totalMah = 0.0
        currentSum = 0.0
        powerSum = 0.0
    }

    private suspend fun endPhase(now: Long) {
        val type = currentType ?: return
        val durationS = (now - startTime) / 1000
        // 丢弃过短的阶段（< 30 秒），减少频繁拔插产生的噪音
        if (durationS >= MIN_PHASE_DURATION_S) {
            val phase = Phase(
                type = type,
                startTime = startTime,
                endTime = now,
                durationS = durationS,
                totalMah = totalMah,
                avgCurrentMa = if (sampleCount > 0) currentSum / sampleCount else 0.0,
                avgPowerW = if (sampleCount > 0) powerSum / sampleCount else 0.0
            )
            repository.insertPhase(phase)
        }
        currentType = null
    }

    private fun accumulate(data: SampleData, now: Long) {
        val deltaT = (now - lastSampleTs) / 1000.0
        lastSampleTs = now

        val current = data.currentMa
        val powerW = data.voltageV * (data.currentMa / 1000.0)

        // 充电阶段累计充电 mAh，用电阶段累计放电 mAh
        val mah = if (currentType == Phase.TYPE_CHARGING)
            maxOf(current, 0.0) * deltaT / 3600.0
        else
            maxOf(-current, 0.0) * deltaT / 3600.0
        totalMah += mah

        currentSum += abs(current)
        powerSum += abs(powerW)
        sampleCount++
    }

    companion object {
        private const val MIN_PHASE_DURATION_S = 30L
    }
}
