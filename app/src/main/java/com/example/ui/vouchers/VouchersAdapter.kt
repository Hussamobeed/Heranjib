package com.example.ui.vouchers

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import com.example.data.local.entity.Voucher

class VouchersAdapter(
    private val onOptionsClick: (Voucher, View) -> Unit
) : ListAdapter<Voucher, VouchersAdapter.VoucherViewHolder>(VoucherDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VoucherViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_voucher, parent, false)
        return VoucherViewHolder(view)
    }

    override fun onBindViewHolder(holder: VoucherViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VoucherViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvVoucherNumber: TextView = itemView.findViewById(R.id.tv_voucher_number)
        private val tvStatus: TextView = itemView.findViewById(R.id.tv_status)
        private val btnOptions: ImageButton = itemView.findViewById(R.id.btn_options)
        private val llSentInfo: LinearLayout = itemView.findViewById(R.id.ll_sent_info)
        private val tvPhone: TextView = itemView.findViewById(R.id.tv_phone)
        private val tvDateTime: TextView = itemView.findViewById(R.id.tv_date_time)

        init {
            btnOptions.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onOptionsClick(getItem(position), it)
                }
            }
        }

        fun bind(voucher: Voucher) {
            tvVoucherNumber.text = voucher.voucherNumber
            if (voucher.status == 0) {
                tvStatus.text = "متاح"
                tvStatus.setTextColor(itemView.context.getColor(android.R.color.darker_gray))
                llSentInfo.visibility = View.GONE
            } else {
                tvStatus.text = "مرسل"
                tvStatus.setTextColor(itemView.context.getColor(android.R.color.holo_green_dark))
                llSentInfo.visibility = View.VISIBLE
                tvPhone.text = "الهاتف: ${voucher.customerPhone ?: "غير محدد"}"
                tvDateTime.text = "التاريخ: ${voucher.sentDate ?: ""} ${voucher.sentTime ?: ""}"
            }
        }
    }

    class VoucherDiffCallback : DiffUtil.ItemCallback<Voucher>() {
        override fun areItemsTheSame(oldItem: Voucher, newItem: Voucher): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Voucher, newItem: Voucher): Boolean {
            return oldItem == newItem
        }
    }
}
