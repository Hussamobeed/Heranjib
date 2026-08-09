package com.example.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.databinding.FragmentSettingsBinding
import com.example.core.CsvExportManager
import com.example.data.local.AppDatabase
import kotlinx.coroutines.launch
import java.io.File

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()

    private val importBackupLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            performImport(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        var currentSettings = com.example.data.local.entity.Settings()

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.settings.collect { settings ->
                if (settings != null) {
                    currentSettings = settings
                    binding.switchGiftEnabled.isChecked = settings.giftEnabled
                    binding.etGiftThreshold.setText(settings.giftThreshold.toString())
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.categories.collect { cats ->
                val catNames = mutableListOf("الأكثر مبيعاً (تلقائي)")
                catNames.addAll(cats.map { it.name })
                val adapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, catNames)
                binding.spinnerGiftCategory.setAdapter(adapter)
                
                // Set selection based on current setting
                if (currentSettings.giftCategoryPreference == "MOST_BOUGHT") {
                    binding.spinnerGiftCategory.setText("الأكثر مبيعاً (تلقائي)", false)
                } else {
                    val idx = catNames.indexOf(currentSettings.giftCategoryPreference)
                    if (idx != -1) {
                        binding.spinnerGiftCategory.setText(catNames[idx], false)
                    }
                }
            }
        }

        binding.btnSaveGiftSettings.setOnClickListener {
            val threshold = binding.etGiftThreshold.text.toString().toIntOrNull() ?: 10
            val enabled = binding.switchGiftEnabled.isChecked
            val selectedItem = binding.spinnerGiftCategory.text.toString()
            val categoryPref = if (selectedItem.isBlank() || selectedItem == "الأكثر مبيعاً (تلقائي)") "MOST_BOUGHT" else selectedItem
            
            viewModel.updateSettings(
                currentSettings.copy(
                    giftEnabled = enabled,
                    giftThreshold = threshold,
                    giftCategoryPreference = categoryPref
                )
            )
            Toast.makeText(requireContext(), "تم حفظ الإعدادات", Toast.LENGTH_SHORT).show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.fetchStatus.collect { status ->
                Toast.makeText(requireContext(), status, Toast.LENGTH_SHORT).show()
                if (status.startsWith("تم إضافة") || status.startsWith("فشل") || status.startsWith("حدث خطأ")) {
                    binding.btnFetchUsers.isEnabled = true
                    binding.btnFetchUsers.text = "جلب المستخدمين"
                }
            }
        }

        binding.btnSyncNow.setOnClickListener {
            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build()
            val request = androidx.work.OneTimeWorkRequestBuilder<com.example.worker.SyncWorker>()
                .setConstraints(constraints)
                .build()
            androidx.work.WorkManager.getInstance(requireContext()).enqueue(request)
            Toast.makeText(requireContext(), "تم بدء المزامنة اليدوية", Toast.LENGTH_SHORT).show()
        }

        binding.btnFetchUsers.setOnClickListener {
            binding.btnFetchUsers.isEnabled = false
            binding.btnFetchUsers.text = "جاري الجلب..."
            val scriptUrl = "https://script.google.com/macros/s/AKfycbxjrCkIZRolRMJ2EKeXnZpqgcI5kIQoGyfTWYSILaF9rSk6vXOXRpy5Cp9dREOXC_Ii/exec"
            viewModel.fetchUsersFromLink(scriptUrl)
        }

        binding.btnExportCsv.setOnClickListener {
            binding.btnExportCsv.isEnabled = false
            binding.btnExportCsv.text = "جاري التصدير..."
            
            val exportManager = CsvExportManager(requireContext(), AppDatabase.getDatabase(requireContext()))
            viewLifecycleOwner.lifecycleScope.launch {
                val zipFile = exportManager.exportData()
                binding.btnExportCsv.isEnabled = true
                binding.btnExportCsv.text = "تصدير البيانات"
                
                if (zipFile != null && zipFile.exists()) {
                    val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", zipFile)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(intent, "مشاركة النسخة الاحتياطية"))
                } else {
                    Toast.makeText(requireContext(), "فشل التصدير", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnViewLogs.setOnClickListener {
            startActivity(Intent(requireContext(), LogsActivity::class.java))
        }

        binding.btnTestParser.setOnClickListener {
            startActivity(Intent(requireContext(), SmsParserTestActivity::class.java))
        }

        binding.btnImportCsv.setOnClickListener {
            importBackupLauncher.launch("application/zip")
        }
    }

    private fun performImport(uri: android.net.Uri) {
        binding.btnImportCsv.isEnabled = false
        binding.btnImportCsv.text = "جاري الاستيراد..."
        
        val exportManager = CsvExportManager(requireContext(), AppDatabase.getDatabase(requireContext()))
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val success = exportManager.importData(inputStream)
                    if (success) {
                        Toast.makeText(requireContext(), "تم استيراد البيانات بنجاح واستعادة قاعدة البيانات", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(requireContext(), "فشل استيراد البيانات. تأكد من صحة ملف النسخة الاحتياطية", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "لا يمكن فتح الملف المحدد", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "حدث خطأ أثناء الاستيراد: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.btnImportCsv.isEnabled = true
                binding.btnImportCsv.text = "استيراد البيانات"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
