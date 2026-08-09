package com.example.ui.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import com.example.data.local.AppDatabase
import kotlinx.coroutines.launch

class LogsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logs)

        val recyclerView = findViewById<RecyclerView>(R.id.recycler_logs)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val adapter = LogsAdapter()
        recyclerView.adapter = adapter

        val dao = AppDatabase.getDatabase(this).appDao()

        lifecycleScope.launch {
            dao.getAllLogs().collect { logs ->
                adapter.submitList(logs)
            }
        }
    }
}
