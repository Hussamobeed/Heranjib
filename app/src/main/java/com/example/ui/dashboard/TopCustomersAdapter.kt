package com.example.ui.dashboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.R

class TopCustomersAdapter(
    private val onSearchClicked: (String) -> Unit
) : RecyclerView.Adapter<TopCustomersAdapter.TopCustomerViewHolder>() {

    private var items: List<CustomerLoyaltyStatus> = emptyList()

    fun submitList(list: List<CustomerLoyaltyStatus>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TopCustomerViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_top_customer, parent, false)
        return TopCustomerViewHolder(view)
    }

    override fun onBindViewHolder(holder: TopCustomerViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    override fun getItemCount() = items.size

    inner class TopCustomerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvPhone: TextView = itemView.findViewById(R.id.tv_top_phone)
        private val tvProgress: TextView = itemView.findViewById(R.id.tv_top_progress)
        private val btnCopy: ImageButton = itemView.findViewById(R.id.btn_copy_phone)
        private val btnSearch: ImageButton = itemView.findViewById(R.id.btn_search_phone)

        fun bind(status: CustomerLoyaltyStatus) {
            tvPhone.text = status.phone
            
            if (status.giftEnabled) {
                tvProgress.text = "متبقي ${status.remainingToNextGift}"
            } else {
                tvProgress.text = "الهدايا معطلة"
            }

            btnCopy.setOnClickListener {
                val clipboard = itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("phone", status.phone)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(itemView.context, "تم نسخ الرقم", Toast.LENGTH_SHORT).show()
            }

            btnSearch.setOnClickListener {
                onSearchClicked(status.phone)
            }
        }
    }
}
