package com.example.chargemonitor.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.chargemonitor.ChargeMonitorApp
import com.example.chargemonitor.R
import com.example.chargemonitor.databinding.FragmentHistoryListBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 历史记录页：展示充/用电阶段列表（充电阶段、用电阶段交错）。
 */
class HistoryListFragment : Fragment() {

    private var _binding: FragmentHistoryListBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: PhaseListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        loadData()
    }

    private fun setupRecyclerView() {
        adapter = PhaseListAdapter()
        binding.rvSessions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSessions.adapter = adapter
    }

    private fun loadData() {
        val database = (requireActivity().application as ChargeMonitorApp).database
        lifecycleScope.launch {
            database.phaseDao().getAllFlow().collectLatest { phases ->
                adapter.submitList(phases)
                binding.tvSessionCount.text = getString(R.string.phase_count, phases.size)
                binding.tvEmpty.visibility = if (phases.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
