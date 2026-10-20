package com.example.ui.vouchers

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.R
import com.example.data.local.entity.Voucher
import com.example.databinding.FragmentVoucherListBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VoucherListFragment : Fragment() {

    private var _binding: FragmentVoucherListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: VoucherListViewModel by viewModels()

    private val importLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                importFile(uri)
            }
        }
    }

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                exportFile(uri)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVoucherListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val categoryId = arguments?.getLong("categoryId") ?: -1L
        val categoryName = arguments?.getString("categoryName") ?: ""
        
        binding.toolbar.title = "قسائم $categoryName"
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        viewModel.loadVouchers(categoryId)

        val adapter = VouchersAdapter { voucher, anchorView ->
            showVoucherMenu(voucher, anchorView)
        }

        binding.recyclerVouchers.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerVouchers.adapter = adapter

        // Filter Spinner setup
        val filterOptions = arrayOf("الكل", "متاح", "مرسل")
        val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, filterOptions)
        binding.spinnerFilter.adapter = spinnerAdapter
        binding.spinnerFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                when (position) {
                    0 -> viewModel.filterVouchers(-1)
                    1 -> viewModel.filterVouchers(0)
                    2 -> viewModel.filterVouchers(1)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Search logic
        binding.etSearch.doAfterTextChanged {
            viewModel.searchVouchers(it.toString())
        }

        binding.fabImport.setOnClickListener {
            showAddVoucherOptionsDialog(categoryName)
        }

        binding.fabExport.setOnClickListener {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
                putExtra(Intent.EXTRA_TITLE, "vouchers_${categoryName}.txt")
            }
            exportLauncher.launch(intent)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.vouchers.collect { list ->
                    adapter.submitList(list)
                }
            }
        }
    }

    private fun showVoucherMenu(voucher: Voucher, anchorView: View) {
        val popup = PopupMenu(requireContext(), anchorView)
        val linked = com.example.integration.SabaLink(requireContext()).configured
        if (voucher.status != 1) {
            popup.menu.add(0, 1, 0, "تحديد كمرسل")
        } else if (!linked) {
            popup.menu.add(0, 2, 0, "إلغاء الإرسال (تحديد كمتاح)")
        }
        if (!linked) popup.menu.add(0, 3, 0, "حذف القسيمة")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    showMarkAsSentDialog(voucher)
                    true
                }
                2 -> {
                    viewModel.updateVoucherStatus(voucher, 0, null, null, null)
                    true
                }
                3 -> {
                    showDeleteConfirmation(voucher)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showMarkAsSentDialog(voucher: Voucher) {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }
        
        val phoneInput = EditText(requireContext()).apply {
            hint = "رقم هاتف العميل"
        }
        layout.addView(phoneInput)
        
        AlertDialog.Builder(requireContext())
            .setTitle("تحديد القسيمة كمرسلة")
            .setView(layout)
            .setPositiveButton("حفظ") { _, _ ->
                val phone = phoneInput.text.toString().trim()
                if (phone.isNotEmpty()) {
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    viewModel.updateVoucherStatus(voucher, 1, phone, date, time)
                } else {
                    Toast.makeText(requireContext(), "يجب إدخال رقم الهاتف", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showDeleteConfirmation(voucher: Voucher) {
        AlertDialog.Builder(requireContext())
            .setTitle("تأكيد الحذف")
            .setMessage("هل أنت متأكد من حذف هذه القسيمة؟")
            .setPositiveButton("حذف") { _, _ ->
                viewModel.deleteVoucher(voucher)
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showAddVoucherOptionsDialog(categoryName: String) {
        if (com.example.integration.SabaLink(requireContext()).configured) {
            Toast.makeText(requireContext(), "إضافة المخزون تتم من SABA", Toast.LENGTH_LONG).show()
            return
        }
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_voucher, null)
        val tvCategory = dialogView.findViewById<android.widget.TextView>(R.id.tv_selected_category)
        val btnSwitchCategory = dialogView.findViewById<View>(R.id.btn_switch_category)
        val btnImportTxt = dialogView.findViewById<View>(R.id.btn_import_txt)
        val btnManualAdd = dialogView.findViewById<View>(R.id.btn_manual_add)

        tvCategory.text = "الفئة المحددة: $categoryName"

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        btnSwitchCategory.setOnClickListener {
            dialog.dismiss()
            findNavController().navigateUp()
        }

        btnImportTxt.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "text/plain"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            importLauncher.launch(intent)
        }

        btnManualAdd.setOnClickListener {
            dialog.dismiss()
            showManualAddDialog()
        }

        dialog.show()
    }

    private fun showManualAddDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_manual_add, null)
        val etManualVouchers = dialogView.findViewById<EditText>(R.id.et_manual_vouchers)
        val btnCancel = dialogView.findViewById<View>(R.id.btn_cancel)
        val btnAdd = dialogView.findViewById<View>(R.id.btn_add)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnAdd.setOnClickListener {
            val text = etManualVouchers.text.toString()
            if (text.isNotBlank()) {
                dialog.dismiss()
                val lines = text.split("\n")
                viewLifecycleOwner.lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        viewModel.importVouchersFromList(lines)
                    }
                    showImportResultDialog(result)
                }
            } else {
                Toast.makeText(requireContext(), "الرجاء إدخال قسيمة واحدة على الأقل", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun showImportResultDialog(result: com.example.ui.vouchers.ImportResult) {
        val msg = """
            تمت الإضافة بنجاح
            إجمالي المدخل: ${result.total}
            تمت الإضافة: ${result.added}
            مكررة: ${result.duplicates}
            غير صالحة: ${result.invalid}
        """.trimIndent()
        AlertDialog.Builder(requireContext())
            .setMessage(msg)
            .setPositiveButton("موافق", null)
            .show()
    }

    private fun importFile(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val result = withContext(Dispatchers.IO) {
                        viewModel.importVouchers(inputStream)
                    }
                    showImportResultDialog(result)
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "خطأ أثناء الاستيراد", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun exportFile(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val vouchers = withContext(Dispatchers.IO) { viewModel.getVouchersList() }
                withContext(Dispatchers.IO) {
                    requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                        val writer = outputStream.bufferedWriter()
                        vouchers.forEach { voucher ->
                            writer.write(voucher.voucherNumber)
                            writer.newLine()
                        }
                        writer.flush()
                    }
                }
                Toast.makeText(requireContext(), "تم التصدير بنجاح", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "خطأ أثناء التصدير", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

