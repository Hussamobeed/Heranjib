package com.example.ui.categories

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.R
import com.example.databinding.FragmentCategoriesBinding
import kotlinx.coroutines.launch

class CategoriesFragment : Fragment() {

    private var _binding: FragmentCategoriesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CategoriesViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCategoriesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = CategoriesAdapter(
            onCategoryClick = { categoryStats ->
                val bundle = Bundle().apply {
                    putLong("categoryId", categoryStats.category.id)
                    putString("categoryName", categoryStats.category.name)
                }
                findNavController().navigate(R.id.action_nav_categories_to_voucherListFragment, bundle)
            },
            onMoreClick = { categoryStats, anchorView ->
                showCategoryMenu(categoryStats, anchorView)
            }
        )

        binding.recyclerCategories.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerCategories.adapter = adapter

        binding.fabAddCategory.setOnClickListener {
            showAddCategoryDialog()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.categoriesWithStats.collect { list ->
                    adapter.submitList(list)
                }
            }
        }
    }

    private fun showAddCategoryDialog() {
        val input = EditText(requireContext())
        input.hint = "اسم الفئة (مثال: 100)"
        
        AlertDialog.Builder(requireContext())
            .setTitle("إضافة فئة جديدة")
            .setView(input)
            .setPositiveButton("إضافة") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    viewModel.addCategory(name)
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showCategoryMenu(categoryStats: com.example.data.local.model.CategoryWithStats, view: View) {
        val popup = PopupMenu(requireContext(), view)
        popup.menu.add(0, 1, 0, "تعديل الاسم")
        popup.menu.add(0, 2, 0, "حذف الفئة")
        
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    showEditCategoryDialog(categoryStats.category)
                    true
                }
                2 -> {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val deleted = viewModel.deleteCategoryIfEmpty(categoryStats.category)
                        if (!deleted) {
                            Toast.makeText(requireContext(), "لا يمكن حذف الفئة لأنها تحتوي على قسائم", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(requireContext(), "تم حذف الفئة بنجاح", Toast.LENGTH_SHORT).show()
                        }
                    }
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showEditCategoryDialog(category: com.example.data.local.entity.VoucherCategory) {
        val input = EditText(requireContext())
        input.setText(category.name)
        
        AlertDialog.Builder(requireContext())
            .setTitle("تعديل اسم الفئة")
            .setView(input)
            .setPositiveButton("حفظ") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    viewModel.updateCategory(category.copy(name = name))
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
