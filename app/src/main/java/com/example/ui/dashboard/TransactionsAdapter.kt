package com.example.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.data.local.entity.Transaction
import com.example.databinding.ItemTransactionBinding

class TransactionsAdapter : ListAdapter<Transaction, TransactionsAdapter.ViewHolder>(TransactionDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemTransactionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(transaction: Transaction) {
            val phonePrefix = transaction.customerPhone.take(2).takeIf { it.isNotEmpty() } ?: "??"
            binding.tvIcon.text = phonePrefix
            
            // Mask phone number
            val phone = transaction.customerPhone
            val maskedPhone = if (phone.length >= 8) {
                "${phone.take(3)}****${phone.takeLast(3)}"
            } else {
                phone
            }
            binding.tvPhone.text = maskedPhone
            
            binding.tvDetails.text = "فئة ${transaction.categoryName} • ${transaction.smsStatus}"
            binding.tvTime.text = transaction.transactionTime
        }
    }
}

class TransactionDiffCallback : DiffUtil.ItemCallback<Transaction>() {
    override fun areItemsTheSame(oldItem: Transaction, newItem: Transaction): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Transaction, newItem: Transaction): Boolean {
        return oldItem == newItem
    }
}
