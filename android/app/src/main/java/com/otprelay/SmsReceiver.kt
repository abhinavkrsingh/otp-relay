package com.otprelay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "OtpRelay"

        // Client-side keyword filter — non-OTP messages never leave the device
        private val OTP_KEYWORDS = listOf(
            "otp", "one-time", "one time", "passcode",
            "verification code", "verify", "auth code", "authentication code",
            "2fa", "two-factor", "two factor",
            "login code", "security code",
            "expires", "do not share",
            "access code", "temporary code",
            "your code", "use code", "enter code"
        )

        fun looksLikeOtp(message: String): Boolean {
            val lower = message.lowercase()
            return OTP_KEYWORDS.any { lower.contains(it) }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages[0].originatingAddress ?: "Unknown"
        // Concatenate all PDU parts (handles long SMS split across multiple PDUs)
        val body = messages.joinToString("") { it.messageBody ?: "" }

        Log.d(TAG, "SMS from: $sender | body length: ${body.length}")

        // Privacy filter: skip immediately if this doesn't look like an OTP message
        if (!looksLikeOtp(body)) {
            Log.d(TAG, "Skipped — not an OTP message")
            return
        }

        val prefs = context.getSharedPreferences("otp_relay", Context.MODE_PRIVATE)
        val apiUrl   = prefs.getString("api_url",    "").orEmpty()
        val secretKey= prefs.getString("secret_key", "").orEmpty()
        val myPhone  = prefs.getString("my_phone",   "").orEmpty()

        if (apiUrl.isEmpty() || secretKey.isEmpty() || myPhone.isEmpty()) {
            Log.w(TAG, "Config incomplete — open the app and fill in all fields")
            return
        }

        // Fire-and-forget HTTP POST on a background thread
        // (BroadcastReceiver has ~10 s; a simple HTTPS POST is well within that)
        Thread {
            try {
                val payload = JSONObject().apply {
                    put("secret",  secretKey)
                    put("message", body)
                    put("sender",  sender)
                    put("from",    myPhone)
                }.toString()

                val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    doOutput       = true
                    connectTimeout = 15_000
                    readTimeout    = 15_000
                }

                conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                Log.d(TAG, "Relay response: HTTP $code")
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Relay failed", e)
            }
        }.start()
    }
}
