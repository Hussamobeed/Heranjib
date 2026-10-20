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
            if (!com.example.integration.SabaLink(requireContext()).configured) {
                Toast.makeText(requireContext(), "اربط الهاتف بـ SABA أولاً", Toast.LENGTH_LONG).show()
            } else {
                com.example.worker.SyncWorker.enqueue(requireContext())
                Toast.makeText(requireContext(), "بدأ طلب مزامنة SABA. راجع السجل إذا تعذر الاتصال", Toast.LENGTH_LONG).show()
            }
        }
        binding.btnFetchUsers.text = "ربط SABA"
        binding.btnFetchUsers.setOnClickListener {
            val input = android.widget.EditText(requireContext()).apply {
                hint = "الصق بيانات الربط من صفحة Heranjib في SABA"
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                setPadding(32,24,32,24)
            }
            val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("ربط هاتف Heranjib بـ SABA")
                .setMessage("أنشئ رمز الربط في SABA ثم الصقه هنا. استلام الكروت يتم بأمر من SABA.")
                .setView(input).setNegativeButton("إلغاء",null).setPositiveButton("ربط",null).create()
            dialog.setOnShowListener {
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val button = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                    button.isEnabled = false
                    val raw = input.text.toString().trim()
                    viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                val stock = AppDatabase.getDatabase(requireContext()).appDao().getAllVouchersForBackup()
                                val duplicate = stock.groupingBy { it.voucherNumber }.eachCount().entries.firstOrNull { it.value > 1 }
                                require(duplicate == null) { "الكرت ${duplicate?.key} مكرر بين الفئات. أزل النسخة المكررة من المخزون قبل الربط" }
                                com.example.integration.SabaLink(requireContext()).pair(raw)
                            }
                            com.example.worker.SyncWorker.enqueue(requireContext())
                            Toast.makeText(requireContext(), "تم الربط وطلب المزامنة", Toast.LENGTH_LONG).show()
                            dialog.dismiss()
                        } catch (error: Exception) {
                            input.error = error.message ?: "تعذر الربط"
                            button.isEnabled = true
                        }
                    }
                }
            }
            dialog.show()
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
                        Toast.makeText(requireContext(), "تمت الاستعادة. بعد الربط تُدمج النسخة دون إعادة الكروت المباعة للبيع، وتبقى الكروت المسترجعة غير المعروفة للمراجعة", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(requireContext(), exportManager.lastImportError ?: "فشل استيراد البيانات. لم تتغير قاعدة البيانات", Toast.LENGTH_LONG).show()
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

