package com.example.ui.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import com.example.data.local.entity.AppLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogsAdapter : ListAdapter<AppLog, LogsAdapter.LogViewHolder>(LogDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_log, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tv_timestamp)
        private val tvType: TextView = itemView.findViewById(R.id.tv_type)
        private val tvMessage: TextView = itemView.findViewById(R.id.tv_message)

        fun bind(log: AppLog) {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            tvTimestamp.text = sdf.format(Date(log.timestamp))
            tvType.text = log.type
            tvMessage.text = log.message
        }
    }

    class LogDiffCallback : DiffUtil.ItemCallback<AppLog>() {
        override fun areItemsTheSame(oldItem: AppLog, newItem: AppLog): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: AppLog, newItem: AppLog): Boolean {
            return oldItem == newItem
        }
    }
}
