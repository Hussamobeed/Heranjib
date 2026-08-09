package com.example.ui.categories

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import com.example.data.local.model.CategoryWithStats

class CategoriesAdapter(
    private val onCategoryClick: (CategoryWithStats) -> Unit,
    private val onMoreClick: (CategoryWithStats, View) -> Unit
) : ListAdapter<CategoryWithStats, CategoriesAdapter.CategoryViewHolder>(CategoryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_category, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvCategoryName: TextView = itemView.findViewById(R.id.tv_category_name)
        private val tvTotalCount: TextView = itemView.findViewById(R.id.tv_total_count)
        private val tvAvailableCount: TextView = itemView.findViewById(R.id.tv_available_count)
        private val tvSentCount: TextView = itemView.findViewById(R.id.tv_sent_count)
        private val btnMore: ImageButton = itemView.findViewById(R.id.btn_more)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onCategoryClick(getItem(position))
                }
            }
            btnMore.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onMoreClick(getItem(position), it)
                }
            }
        }

        fun bind(categoryStats: CategoryWithStats) {
            tvCategoryName.text = "فئة ${categoryStats.category.name}"
            tvTotalCount.text = categoryStats.totalCount.toString()
            tvAvailableCount.text = categoryStats.availableCount.toString()
            tvSentCount.text = categoryStats.sentCount.toString()
        }
    }

    class CategoryDiffCallback : DiffUtil.ItemCallback<CategoryWithStats>() {
        override fun areItemsTheSame(oldItem: CategoryWithStats, newItem: CategoryWithStats): Boolean {
            return oldItem.category.id == newItem.category.id
        }

        override fun areContentsTheSame(oldItem: CategoryWithStats, newItem: CategoryWithStats): Boolean {
            return oldItem == newItem
        }
    }
}
