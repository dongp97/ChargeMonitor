package com.example.chargemonitor.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.chargemonitor.ChargeMonitorApp
import com.example.chargemonitor.data.entity.Session
import com.example.chargemonitor.data.entity.Sample
import com.example.chargemonitor.databinding.FragmentSessionDetailBinding
import com.example.chargemonitor.ui.widget.ChargeCurveView
import kotlinx.coroutines.launch

class SessionDetailFragment : Fragment() {

    private var _binding: FragmentSessionDetailBinding? = null
    private val binding get() = _binding!!

    private var sessionId: Long = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSessionDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionId = arguments?.getLong(ARG_SESSION_ID) ?: 0

        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        loadSession()
        loadSamples()
    }

    private fun loadSession() {
        val database = (requireActivity().application as ChargeMonitorApp).database

        lifecycleScope.launch {
            val session = database.sessionDao().getById(sessionId)
            if (session != null) {
                bindSession(session)
            }
        }
    }

    private fun bindSession(session: Session) {
        // 格式化时长
        val hours = session.durationS / 3600
        val minutes = (session.durationS % 3600) / 60
        val seconds = session.durationS % 60
        binding.tvDuration.text = if (hours > 0) {
            "${hours}h${minutes}m${seconds}s"
        } else if (minutes > 0) {
            "${minutes}m${seconds}s"
        } else {
            "${seconds}s"
        }

        binding.tvMah.text = String.format("%.0f mAh", session.totalMah)
        binding.tvAvgPower.text = String.format("%.1f W", session.avgPowerW)
        binding.tvPeakPower.text = String.format("%.1f W", session.peakPowerW)
        binding.tvStartLevel.text = "${session.startLevel}%"
        binding.tvEndLevel.text = "${session.endLevel}%"
    }

    private fun loadSamples() {
        val database = (requireActivity().application as ChargeMonitorApp).database

        lifecycleScope.launch {
            val samples = database.sampleDao().getBySession(sessionId)
            val curvePoints = samples.map { sample ->
                ChargeCurveView.CurvePoint(
                    timestamp = sample.ts,
                    powerW = sample.powerW,
                    voltageV = sample.voltageV,
                    currentMa = sample.currentMa
                )
            }
            binding.curveView.setData(curvePoints)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_SESSION_ID = "session_id"

        fun newInstance(sessionId: Long): SessionDetailFragment {
            return SessionDetailFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_SESSION_ID, sessionId)
                }
            }
        }
    }
}
