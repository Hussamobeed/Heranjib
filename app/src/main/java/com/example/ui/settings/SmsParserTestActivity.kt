package com.example.ui.settings

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.R
import com.example.core.parser.SmsParserEngine
import kotlinx.coroutines.launch

class SmsParserTestActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sms_parser_test)

        val etSmsText = findViewById<EditText>(R.id.et_sms_text)
        val btnParse = findViewById<Button>(R.id.btn_parse)
        val tvResult = findViewById<TextView>(R.id.tv_result)
        
        // Add pre-defined tests
        val btnTest1 = findViewById<Button>(R.id.btn_test1)
        val btnTest2 = findViewById<Button>(R.id.btn_test2)
        val btnTest3 = findViewById<Button>(R.id.btn_test3)
        val btnTest4 = findViewById<Button>(R.id.btn_test4)

        btnTest1.setOnClickListener { etSmsText.setText("Received 100 from 771217814") }
        btnTest2.setOnClickListener { etSmsText.setText("اضيف 100ر.ي تحويل مشترك من بشير-771217814") }
        btnTest3.setOnClickListener { etSmsText.setText("Invalid SMS without phone") }
        btnTest4.setOnClickListener { etSmsText.setText("Multiple numbers 771217814 and 771217815 inside SMS") }

        btnParse.setOnClickListener {
            val rawText = etSmsText.text.toString()
            lifecycleScope.launch {
                val dao = com.example.data.local.AppDatabase.getDatabase(this@SmsParserTestActivity).appDao()
                val validCategories = dao.getAllCategoriesList().mapNotNull { it.name.toIntOrNull() }
                
                // For test purposes if no categories exist, add 100 as dummy
                val testCategories = if (validCategories.isEmpty()) listOf(100) else validCategories

                val result = SmsParserEngine.parse(rawText, System.currentTimeMillis(), testCategories)
                
                val output = """
                    Phone: ${result.phoneNumber ?: "N/A"}
                    Amount: ${result.amount ?: "N/A"}
                    Valid: ${result.isValid}
                    Reason: ${result.reason}
                """.trimIndent()
                
                tvResult.text = output
                
                val logMessage = if (result.isValid) {
                    "Test Parse SUCCESS: Phone: ${result.phoneNumber}, Amount: ${result.amount}"
                } else {
                    "Test Parse FAILED: ${result.reason}"
                }
                dao.insertLog(com.example.data.local.entity.AppLog(type = "PARSER_TEST", message = logMessage + " | Raw: " + rawText))
            }
        }
    }
}
