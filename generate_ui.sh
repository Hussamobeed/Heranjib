#!/bin/bash

NAMES=("Dashboard" "Categories" "VoucherList" "Transactions" "Reports" "Settings")
PACKAGES=("dashboard" "categories" "vouchers" "transactions" "reports" "settings")
TITLES=("لوحة القيادة" "فئات القسائم" "قائمة القسائم" "المعاملات" "التقارير" "الإعدادات")

for i in "${!NAMES[@]}"; do
    NAME="${NAMES[$i]}"
    LOWER_NAME=$(echo "$NAME" | sed 's/\(.\)\([A-Z]\)/\1_\2/g' | tr '[:upper:]' '[:lower:]')
    PKG="${PACKAGES[$i]}"
    TITLE="${TITLES[$i]}"
    
    # Layout XML
    cat << XML > app/src/main/res/layout/fragment_${LOWER_NAME}.xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">
    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:textSize="24sp"
        android:text="${TITLE}" />
</FrameLayout>
XML

    # ViewModel
    cat << VM > app/src/main/java/com/example/ui/${PKG}/${NAME}ViewModel.kt
package com.example.ui.${PKG}

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ${NAME}ViewModel @Inject constructor() : ViewModel() {
}
VM

    # Fragment
    cat << FRAG > app/src/main/java/com/example/ui/${PKG}/${NAME}Fragment.kt
package com.example.ui.${PKG}

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.databinding.Fragment${NAME}Binding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ${NAME}Fragment : Fragment() {

    private var _binding: Fragment${NAME}Binding? = null
    private val binding get() = _binding!!
    private val viewModel: ${NAME}ViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = Fragment${NAME}Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
FRAG

done
