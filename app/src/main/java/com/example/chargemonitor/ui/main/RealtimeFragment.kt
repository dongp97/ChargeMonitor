package com.example.chargemonitor.ui.main

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.chargemonitor.R
import com.example.chargemonitor.databinding.FragmentRealtimeBinding
import com.example.chargemonitor.service.ChargeMonitorService
import com.example.chargemonitor.ui.widget.ChargeCurveView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 实时监测页：订阅前台服务的数据流，绘制曲线，支持全屏切换。
 */
class RealtimeFragment : Fragment() {

    private var _binding: FragmentRealtimeBinding? = null
    private val binding get() = _binding!!

    private var subscribed = false
    private var fullscreenDialog: Dialog? = null
    private var fullscreenCurve: ChargeCurveView? = null

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

        binding.btnToggleFullscreen.setOnClickListener { toggleFullscreen() }

        // 等待 Service 绑定完成后订阅一次数据
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
                binding.curveView.addPoint(point)
                fullscreenCurve?.addPoint(point)
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

    private fun toggleFullscreen() {
        if (fullscreenDialog?.isShowing == true) {
            fullscreenDialog?.dismiss()
            return
        }

        val curve = ChargeCurveView(requireContext())
        fullscreenCurve = curve
        curve.setData(binding.curveView.getPoints())

        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(curve)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        dialog.setOnDismissListener {
            fullscreenDialog = null
            fullscreenCurve = null
            binding.btnToggleFullscreen.text = getString(R.string.fullscreen)
        }
        fullscreenDialog = dialog
        dialog.show()
        binding.btnToggleFullscreen.text = getString(R.string.exit_fullscreen)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fullscreenDialog?.dismiss()
        fullscreenDialog = null
        fullscreenCurve = null
        _binding = null
    }
}
