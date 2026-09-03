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
import com.example.chargemonitor.data.entity.Session
import com.example.chargemonitor.databinding.FragmentHistoryListBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HistoryListFragment : Fragment() {

    private var _binding: FragmentHistoryListBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: SessionListAdapter

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
        adapter = SessionListAdapter { session ->
            // 点击跳转到详情
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host_container, SessionDetailFragment.newInstance(session.id))
                .addToBackStack(null)
                .commit()
        }
        binding.rvSessions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSessions.adapter = adapter
    }

    private fun loadData() {
        val database = (requireActivity().application as ChargeMonitorApp).database

        lifecycleScope.launch {
            database.sessionDao().getAllFlow().collectLatest { sessions ->
                adapter.submitList(sessions)
                binding.tvSessionCount.text = "共 ${sessions.size} 次充电"
                binding.tvEmpty.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
