package com.example.chargemonitor.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.chargemonitor.databinding.FragmentRealtimeBinding
import com.example.chargemonitor.service.ChargeMonitorService
import com.example.chargemonitor.ui.widget.ChargeCurveView
import com.example.chargemonitor.ui.widget.CurveMode
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 实时监测页：上下两个曲线图（电压+电流双轴、功率单轴），订阅前台服务数据流。
 */
class RealtimeFragment : Fragment() {

    private var _binding: FragmentRealtimeBinding? = null
    private val binding get() = _binding!!

    private var subscribed = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRealtimeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.curveVc.mode = CurveMode.VOLTAGE_CURRENT
        binding.curvePower.mode = CurveMode.POWER

        viewLifecycleOwner.lifecycleScope.launch {
            (requireActivity() as MainActivity).serviceFlow.collectLatest { svc ->
                if (svc != null && !subscribed) {
                    subscribed = true
                    observeService(svc)
                }
            }
        }
    }

    private fun observeService(svc: ChargeMonitorService) {
        viewLifecycleOwner.lifecycleScope.launch {
            svc.power.collectLatest { power ->
                binding.tvPower.text = String.format("%.1f", power)
                val point = ChargeCurveView.CurvePoint(
                    timestamp = System.currentTimeMillis(),
                    powerW = power,
                    voltageV = svc.voltage.value,
                    currentMa = svc.current.value
                )
                binding.curveVc.addPoint(point)
                binding.curvePower.addPoint(point)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            svc.mah.collectLatest { mah ->
                binding.tvMah.text = String.format("%.0f", mah)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            svc.voltage.collectLatest { voltage ->
                binding.tvVoltage.text = String.format("%.2f", voltage)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            svc.current.collectLatest { current ->
                binding.tvCurrent.text = String.format("%.0f", current)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            svc.temp.collectLatest { temp ->
                binding.tvTemp.text = String.format("%.1f", temp)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            svc.chargeType.collectLatest { type ->
                binding.tvChargeStatus.text = type.label
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
