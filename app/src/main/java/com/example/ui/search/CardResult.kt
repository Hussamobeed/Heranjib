package com.example.ui.search

import org.json.JSONObject
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CardResult {
    private fun text(row: JSONObject, key: String): String? =
        if (!row.has(key) || row.isNull(key)) null else row.optString(key).takeIf { it.isNotBlank() }

    private fun bytes(value: String?): String? = runCatching {
        var n = value?.toBigIntegerOrNull()?.takeIf { it >= BigInteger.ZERO } ?: return null
        val units = listOf("بايت", "KB", "MB", "GB", "TB")
        var scale = BigInteger.ONE
        var index = 0
        while (n / scale >= BigInteger.valueOf(1024) && index < units.lastIndex) {
            scale *= BigInteger.valueOf(1024); index++
        }
        BigDecimal(n).divide(BigDecimal(scale), 2, RoundingMode.DOWN).stripTrailingZeros().toPlainString() + " " + units[index]
    }.getOrNull()

    private fun duration(value: String?): String? {
        val seconds = value?.toBigIntegerOrNull()?.takeIf { it >= BigInteger.ZERO } ?: return null
        val days = seconds / BigInteger.valueOf(86400)
        val rest = (seconds % BigInteger.valueOf(86400)).toLong()
        return (if (days > BigInteger.ZERO) "$days يوم " else "") +
            "%02d:%02d:%02d".format(java.util.Locale.ROOT, rest / 3600, rest / 60 % 60, rest % 60)
    }

    private fun date(value: String?): String? = value?.let {
        runCatching { DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(it)) }.getOrDefault(it)
    }

    fun fields(row: JSONObject): List<Pair<String, String>> {
        val fields = if (text(row, "source") == "Telegram") listOf(
            "الكرت" to text(row, "username"), "الفئة" to (text(row, "profile") ?: "غير معروفة"),
            "حد الوقت" to text(row, "timeLimit"), "الماك" to text(row, "telegramMac"),
            "تاريخ الرسالة" to date(text(row, "telegramAt")), "القناة" to text(row, "channelTitle")
        ) else listOf(
            "الكرت" to text(row, "username"), "النظام" to text(row, "kind"), "الجهاز" to text(row, "routerName"),
            "الفئة / البروفايل" to text(row, "profile"), "أول دخول" to text(row, "firstLoginDate"),
            "آخر ظهور" to text(row, "lastSeen"), "الماك" to text(row, "firstMac"), "نقطة الدخول" to (text(row, "firstAccessPointName") ?: text(row, "firstAccessPoint")),
            "الرفع" to bytes(text(row, "uploadBytes")), "التنزيل" to bytes(text(row, "downloadBytes")),
            "إجمالي البيانات" to bytes(text(row, "totalBytes")), "وقت الاستخدام" to duration(text(row, "uptimeSeconds")),
            "عدد الجلسات" to text(row, "sessionCount"), "الحالة" to text(row, "status"),
            "الماك الحالي / المقيّد" to text(row, "currentMac"), "نقطة الاتصال الحالية" to (text(row, "currentAccessPointName") ?: text(row, "currentAccessPoint")),
            "عنوان IP" to text(row, "ip"), "آخر حفظ للتقرير" to date(text(row, "cachedAt"))
        )
        return fields.mapNotNull { (label, value) -> value?.let { label to it } }
    }

    fun shareText(row: JSONObject): String = fields(row).joinToString("\n") { (label, value) -> "$label: $value" }
}
