package com.example

import com.example.ui.search.CardResult
import com.example.ui.search.CardSearchActivity
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class CardSearchTest {
    @Test fun customerFieldsKeepZeroesAndOmitPriceCommentAndSecrets() {
        val row = JSONObject("""{"username":"001234","profile":"100","price":90,"comment":"private","password":"secret","source":"MikroTik","uploadBytes":"0","downloadBytes":"1024","totalBytes":"1024","uptimeSeconds":"93784","sessionCount":0}""")
        val fields = CardResult.fields(row).toMap()
        assertEquals("001234", fields["الكرت"])
        assertEquals("0 بايت", fields["الرفع"])
        assertEquals("1 KB", fields["التنزيل"])
        assertEquals("1 يوم 02:03:04", fields["وقت الاستخدام"])
        assertEquals("0", fields["عدد الجلسات"])
        val text = CardResult.shareText(row)
        assertFalse(text.contains("سعر الفئة")); assertFalse(text.contains("التعليق"))
        assertFalse(text.contains("private")); assertFalse(text.contains("secret"))
        assertFalse(text.contains("null"))
    }
    @Test fun telegramTimestampNeverBecomesFirstLoginOrTimeUsed() {
        val row = JSONObject("""{"username":"819792441","profile":"100","timeLimit":"05:00:00","telegramMac":"2C:0E:3D:EB:58:CF","telegramAt":"2024-02-07T09:05:32Z","source":"Telegram"}""")
        val fields = CardResult.fields(row).toMap()
        assertEquals("100", fields["الفئة"]); assertEquals("05:00:00", fields["حد الوقت"])
        assertTrue(fields.containsKey("تاريخ الرسالة"))
        assertFalse(fields.containsKey("أول دخول")); assertFalse(fields.containsKey("وقت الاستخدام"))
    }
    @Test fun missingCountersDoNotAppearAsZero() {
        val row = JSONObject("""{"username":"001","uploadBytes":null,"uptimeSeconds":null,"source":"MikroTik"}""")
        val fields = CardResult.fields(row).toMap()
        assertFalse(fields.containsKey("الرفع")); assertFalse(fields.containsKey("وقت الاستخدام"))
    }
    @Test fun searchScreenOpensWithoutSmsPermissionsOrStoredDatabase() {
        val controller = Robolectric.buildActivity(CardSearchActivity::class.java).setup()
        val activity = controller.get()
        assertNotNull(activity.findViewById<android.view.View>(R.id.card_number))
        assertNotNull(activity.findViewById<android.view.View>(R.id.search))
        assertEquals(android.view.View.GONE, activity.findViewById<android.view.View>(R.id.device_field).visibility)
        controller.pause().stop().destroy()
    }
}
