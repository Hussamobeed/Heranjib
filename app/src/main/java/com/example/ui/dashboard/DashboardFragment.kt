package com.example.ui.dashboard

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.R
import com.example.databinding.FragmentDashboardBinding
import com.example.service.SmsMonitoringService
import kotlinx.coroutines.launch

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = TransactionsAdapter()
        binding.rvRecentTransactions.adapter = adapter

        binding.btnDisableBatteryOpt.setOnClickListener {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${requireContext().packageName}")
            }
            startActivity(intent)
        }

        binding.btnCheckLoyalty.setOnClickListener {
            val phone = binding.etLoyaltyPhone.text?.toString()?.trim() ?: ""
            if (phone.isEmpty()) {
                Toast.makeText(requireContext(), "الرجاء إدخال رقم الهاتف أولاً", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val status = viewModel.checkCustomerLoyalty(phone)
                    binding.llLoyaltyResult.visibility = View.VISIBLE
                    binding.tvLoyaltyPhoneDisplay.text = "العميل: ${status.phone}"
                    binding.tvLoyaltyPurchaseCount.text = "إجمالي المشتريات الناجحة: ${status.successfulPurchases}"
                    
                    if (status.successfulPurchases == 0) {
                        binding.tvLoyaltyRemaining.text = "متبقي للحصول على الهدية التالية: 10 عمليات"
                        binding.pbLoyaltyProgress.progress = 0
                        binding.tvLoyaltyFavorite.text = "الفئة الأكثر شراءً: لا توجد مشتريات سابقة"
                    } else {
                        binding.tvLoyaltyRemaining.text = "متبقي للحصول على الهدية التالية: ${status.remainingToNextGift} عمليات"
                        binding.pbLoyaltyProgress.progress = status.progress
                        binding.tvLoyaltyFavorite.text = "الفئة الأكثر شراءً: ${status.favoriteCategory ?: "لا توجد فئات محددة"}"
                    }
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "خطأ أثناء جلب البيانات: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.recentTransactions.collect { transactions ->
                        adapter.submitList(transactions)
                    }
                }
                
                launch {
                    viewModel.settings.collect { settings ->
                        val isAutoSend = settings?.autoSend != false
                        
                        if (isAutoSend) {
                            binding.tvStatusText.text = "الخدمة تعمل"
                            binding.viewStatusIndicator.setBackgroundResource(R.drawable.circle_green)
                            SmsMonitoringService.startService(requireContext())
                            com.example.worker.ServiceRestartWorker.enqueue(requireContext())
                        } else {
                            binding.tvStatusText.text = "الخدمة متوقفة"
                            binding.viewStatusIndicator.setBackgroundResource(R.drawable.circle_red)
                        }
                        
                        binding.tvLastSync.text = settings?.lastSyncTime ?: "لم يتم المزامنة"
                    }
                }
                
                launch {
                    viewModel.pendingSyncCount.collect { count ->
                        binding.tvPendingSync.text = String.format("%,d", count)
                    }
                }
                
                launch {
                    viewModel.availableVouchersCount.collect { count ->
                        binding.tvAvailableVouchers.text = String.format("%,d", count)
                    }
                }
                
                launch {
                    viewModel.todaySalesCount.collect { count ->
                        binding.tvSalesCount.text = String.format("%,d", count)
                    }
                }
                
                launch {
                    viewModel.todayFailedCount.collect { count ->
                        binding.tvFailedCount.text = String.format("%,d", count)
                    }
                }
                
                launch {
                    viewModel.totalSentVouchers.collect { count ->
                        binding.tvTotalSent.text = String.format("%,d", count)
                    }
                }
                
                launch {
                    viewModel.todayRevenue.collect { revenue ->
                        binding.tvSalesAmount.text = String.format("%.2f", revenue)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkBatteryOptimization()
    }

    private fun checkBatteryOptimization() {
        val pm = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
        val isIgnoring = pm.isIgnoringBatteryOptimizations(requireContext().packageName)
        binding.cardBatteryWarning.visibility = if (isIgnoring) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
