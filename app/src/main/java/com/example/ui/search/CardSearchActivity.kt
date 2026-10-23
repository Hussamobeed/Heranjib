package com.example.ui.search

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.R
import com.example.databinding.ActivityCardSearchBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import org.json.JSONObject

class CardSearchActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCardSearchBinding
    private lateinit var model: CardSearchViewModel
    private var devices = emptyList<CardDevice>()
    private var rendered: JSONObject? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCardSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        binding.toolbar.setNavigationOnClickListener { finish() }
        model = ViewModelProvider(this)[CardSearchViewModel::class.java]
        fun search() {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(binding.cardNumber.windowToken, 0)
            val index = binding.device.selectedItemPosition
            val id = if (devices.size == 1) devices.first().id else devices.getOrNull(index - 1)?.id.orEmpty()
            model.search(binding.cardNumber.text.toString(), id)
        }
        binding.search.setOnClickListener { search() }
        binding.cardNumber.setOnEditorActionListener { _, action, _ ->
            if (action == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) { search(); true } else false
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { state ->
                    binding.search.isEnabled = !state.loading
                    binding.cardNumber.isEnabled = !state.loading
                    binding.device.isEnabled = !state.loading
                    binding.progress.visibility = if (state.loading) View.VISIBLE else View.GONE
                    binding.status.text = state.error ?: if (state.loading) "جارٍ البحث في الميكروتك ثم التقارير والتلجرام…" else ""
                    if (devices != state.devices) {
                        devices = state.devices
                        binding.device.adapter = ArrayAdapter(this@CardSearchActivity, android.R.layout.simple_spinner_dropdown_item, listOf("البيانات المحفوظة / الجهاز الوحيد") + devices.map { it.name })
                    }
                    binding.deviceField.visibility = if (devices.size > 1) View.VISIBLE else View.GONE
                    if (rendered !== state.result) {
                        rendered = state.result
                        binding.results.removeAllViews()
                        binding.sources.text = ""
                        state.result?.let { render(it) }
                    }
                }
            }
        }
    }

    private fun render(result: JSONObject) {
        val sources = mapOf("mikrotik" to "الميكروتك", "supabase" to "التقارير المالية", "telegram" to "التلجرام")
        val states = mapOf("found" to "تم العثور عليه", "not_found" to "لم يُعثر عليه", "unavailable" to "تعذر الفحص", "not_configured" to "غير مربوط")
        val stages = result.optJSONArray("stages")
        binding.sources.text = (0 until (stages?.length() ?: 0)).map { stages!!.getJSONObject(it) }.joinToString("\n") {
            (sources[it.optString("source")] ?: it.optString("source")) + " — " + (states[it.optString("status")] ?: it.optString("status"))
        }
        val rows = result.optJSONArray("rows")
        if (rows == null || rows.length() == 0) {
            binding.status.text = "لم يُعثر على الكرت في المصادر التي أمكن فحصها."
            return
        }
        binding.status.text = "نتيجة البحث عن الكرت " + result.optString("username")
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val card = MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
                radius = dp(14).toFloat(); cardElevation = 0f; strokeWidth = dp(1)
            }
            val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12)) }
            for ((label, value) in CardResult.fields(row)) {
                content.addView(TextView(this).apply { text = label; textSize = 12f; setTextColor(0xff607582.toInt()) })
                content.addView(TextView(this).apply { text = value; textSize = 17f; setTextIsSelectable(true); textDirection = View.TEXT_DIRECTION_FIRST_STRONG; setPadding(0, dp(2), 0, dp(12)) })
            }
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            fun button(label: String, action: () -> Unit) = MaterialButton(this).apply {
                text = label; minHeight = dp(48)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) }
                setOnClickListener { action() }
            }
            actions.addView(button("نسخ") {
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("بيانات الكرت", CardResult.shareText(row)))
                Toast.makeText(this, "تم نسخ النتيجة.", Toast.LENGTH_SHORT).show()
            })
            actions.addView(button("مشاركة") {
                try {
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, CardResult.shareText(row)) }, "مشاركة بيانات الكرت"))
                } catch (_: android.content.ActivityNotFoundException) { Toast.makeText(this, "استخدم زر النسخ لإرسال النتيجة.", Toast.LENGTH_SHORT).show() }
            })
            content.addView(actions); card.addView(content); binding.results.addView(card)
        }
    }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
