package com.example.integration

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Phone-only credential. No Supabase service key or router credentials are stored here. */
class SabaLink(context: Context) {
    private val prefs = context.getSharedPreferences("saba_private", Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("heranjib_saba", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("heranjib_saba", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun encrypt(text: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(text.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
    private fun decrypt(text: String): String {
        val bytes = Base64.decode(text, Base64.NO_WRAP)
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0,12))) }
            .doFinal(bytes.copyOfRange(12,bytes.size)).toString(Charsets.UTF_8)
    }
    val installationId: String get() = synchronized(SabaLink::class.java) {
        prefs.getString("installation", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("installation",it).commit() }
    }
    val configured: Boolean get() = prefs.contains("credential")
    private fun credential(): JSONObject = JSONObject(decrypt(prefs.getString("credential", "")!!))
    fun pair(raw: String) {
        val bundle = JSONObject(raw)
        val endpoint = bundle.getString("endpoint").trimEnd('/')
        val url = URL(endpoint)
        require(url.protocol == "https" && url.userInfo == null && url.query == null && url.ref == null && url.path.endsWith("/functions/v1/api/heranjib/phone")) { "بيانات ربط SABA غير صالحة" }
        val result = post(endpoint + "/pair", JSONObject().put("installationId",installationId).put("code",bundle.getString("code")), "")
        val secret = JSONObject().put("endpoint",endpoint).put("token",result.getString("token"))
        check(prefs.edit().putString("credential",encrypt(secret.toString())).commit()) { "تعذر حفظ الربط" }
    }
    fun request(action: String, body: JSONObject): JSONObject {
        val c = credential()
        return post(c.getString("endpoint") + "/" + action, body.put("installationId",installationId), c.getString("token"))
    }
    private fun post(endpoint: String, body: JSONObject, token: String): JSONObject {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod="POST"; connection.doOutput=true; connection.instanceFollowRedirects=false
            connection.connectTimeout=15000; connection.readTimeout=45000
            connection.setRequestProperty("Content-Type","application/json; charset=utf-8")
            if(token.isNotBlank())connection.setRequestProperty("X-Heranjib-Token",token)
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code=connection.responseCode
            val text=(if(code in 200..299)connection.inputStream else connection.errorStream)?.bufferedReader(Charsets.UTF_8)?.use{it.readText()} ?: ""
            val result=try { JSONObject(text) } catch (_:Exception) { throw IllegalStateException("لم يؤكد خادم SABA المزامنة ($code)") }
            if(code !in 200..299 || !result.has("data"))throw IllegalStateException(result.optString("error","تعذر الاتصال بخادم SABA"))
            return result.getJSONObject("data")
        } finally { connection.disconnect() }
    }
}
