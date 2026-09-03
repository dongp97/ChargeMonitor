package com.example.chargemonitor.battery

import com.example.chargemonitor.data.entity.Sample
import com.example.chargemonitor.data.entity.Session
import com.example.chargemonitor.data.repository.ChargeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 会话状态机
 *
 * 规则（按 PRD 决策）：
 * - 充满不拔线 → 不结束，继续采样
 * - 短暂断开 < 30 秒 → 同一次会话
 * - 断开 >= 30 秒 → 结束旧会话，创建新会话
 */
class ChargeSessionManager(
    private val repository: ChargeRepository
) {
    sealed class State {
        object Idle : State()
        data class Active(val session: Session) : State()
        object Disconnected : State()  // 短暂断开等待恢复
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var disconnectStartMs: Long = 0L
    private var currentSession: Session? = null
    private var peakPowerW: Double = 0.0
    private var lastSampleTs: Long = 0L

    /**
     * 处理一个新的采样数据
     */
    suspend fun onSample(data: SampleData) {
        val now = System.currentTimeMillis()
        val isCharging = data.chargeType != ChargeType.NONE

        when (_state.value) {
            is State.Idle -> {
                if (isCharging) {
                    // 开始新会话，并记录首个采样点
                    startNewSession(data, now)
                    accumulateSample(data, now)
                }
                // 未充电，保持 Idle
            }

            is State.Active -> {
                if (isCharging) {
                    // 继续充电，累加数据
                    accumulateSample(data, now)
                } else {
                    // 断开，进入 Disconnected 状态
                    disconnectStartMs = now
                    _state.value = State.Disconnected
                }
            }

            is State.Disconnected -> {
                if (isCharging) {
                    val elapsed = now - disconnectStartMs
                    if (elapsed < DISCONNECT_THRESHOLD_MS) {
                        // < 30秒，恢复同一次会话
                        _state.value = State.Active(currentSession!!)
                        // 断开期间不计积分：重置时间基准，避免 deltaT 虚增
                        lastSampleTs = now
                        accumulateSample(data, now)
                    } else {
                        // >= 30秒，结束旧会话，创建新会话
                        endCurrentSession()
                        startNewSession(data, now)
                        accumulateSample(data, now)
                    }
                } else {
                    // 仍未断开，检查是否超时
                    val elapsed = now - disconnectStartMs
                    if (elapsed >= DISCONNECT_THRESHOLD_MS) {
                        // 超时，结束会话
                        endCurrentSession()
                        _state.value = State.Idle
                    }
                }
            }
        }
    }

    private suspend fun startNewSession(data: SampleData, now: Long) {
        val session = Session(
            startTime = now,
            startLevel = data.level,
            endLevel = data.level,
            chargeType = data.chargeType.toDbString()
        )
        val id = repository.insertSession(session)
        currentSession = session.copy(id = id)
        peakPowerW = 0.0
        lastSampleTs = now
        _state.value = State.Active(currentSession!!)
    }

    private suspend fun accumulateSample(data: SampleData, now: Long) {
        val session = currentSession ?: return

        val deltaT = (now - lastSampleTs) / 1000.0  // 秒
        lastSampleTs = now

        // 电流积分（主口径）
        val chargeMa = maxOf(data.currentMa, 0.0)  // 只累加充电电流
        val deltaMah = chargeMa * deltaT / 3600.0

        // 功率计算
        val powerW = data.voltageV * (data.currentMa / 1000.0)
        val deltaWh = powerW * deltaT / 3600.0

        // 峰值功率
        if (powerW > peakPowerW) {
            peakPowerW = powerW
        }

        // 更新会话统计
        val durationS = ((now - session.startTime) / 1000).toInt()
        val totalMah = session.totalMah + deltaMah
        val totalWh = session.totalWh + deltaWh
        val avgPowerW = if (durationS > 0) totalWh / (durationS / 3600.0) else 0.0

        val updatedSession = session.copy(
            durationS = durationS.toLong(),
            endLevel = data.level,
            totalMah = totalMah,
            totalWh = totalWh,
            avgPowerW = avgPowerW,
            peakPowerW = peakPowerW
        )
        repository.updateSession(updatedSession)
        currentSession = updatedSession

        // 保存采样
        val sample = Sample(
            sessionId = session.id,
            ts = now,
            voltageV = data.voltageV,
            currentMa = data.currentMa,
            powerW = powerW,
            tempC = data.tempC
        )
        repository.insertSample(sample)

        // 更新状态
        _state.value = State.Active(updatedSession)
    }

    private suspend fun endCurrentSession() {
        val session = currentSession ?: return
        val now = System.currentTimeMillis()
        val finalSession = session.copy(
            endTime = now,
            durationS = ((now - session.startTime) / 1000).toInt().toLong()
        )
        repository.updateSession(finalSession)
        currentSession = null
        _state.value = State.Idle
    }

    /**
     * 清理 90 天前的采样数据
     */
    suspend fun cleanupOldSamples() {
        val threshold = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000)
        repository.deleteSamplesOlderThan(threshold)
    }

    companion object {
        private const val DISCONNECT_THRESHOLD_MS = 30_000L  // 30 秒
    }
}
