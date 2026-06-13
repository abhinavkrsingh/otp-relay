package com.otprelay

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var etApiUrl:    EditText
    private lateinit var etSecretKey: EditText
    private lateinit var etMyPhone:   EditText
    private lateinit var btnSave:     Button
    private lateinit var tvStatus:    TextView

    private val requestSmsPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) requestNotificationPermission()
        else updateStatus(smsGranted = false)
    }

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> startRelayService(); updateStatus(smsGranted = true) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etApiUrl    = findViewById(R.id.et_api_url)
        etSecretKey = findViewById(R.id.et_secret_key)
        etMyPhone   = findViewById(R.id.et_my_phone)
        btnSave     = findViewById(R.id.btn_save)
        tvStatus    = findViewById(R.id.tv_status)

        loadConfig()
        checkPermissions()

        btnSave.setOnClickListener {
            if (validateInputs()) {
                saveConfig()
                checkPermissions()
            }
        }
    }

    // ── Permissions ────────────────────────────────────────────────────────────

    private fun checkPermissions() {
        val smsGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (!smsGranted) {
            requestSmsPermission.launch(Manifest.permission.RECEIVE_SMS)
        } else {
            requestNotificationPermission()
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        startRelayService()
        updateStatus(smsGranted = true)
    }

    // ── Foreground service ─────────────────────────────────────────────────────

    private fun startRelayService() {
        startForegroundService(Intent(this, RelayForegroundService::class.java))
    }

    // ── Status display ─────────────────────────────────────────────────────────

    private fun updateStatus(smsGranted: Boolean) {
        val prefs = getSharedPreferences("otp_relay", Context.MODE_PRIVATE)
        val configured =
            !prefs.getString("api_url",    "").isNullOrEmpty() &&
            !prefs.getString("secret_key", "").isNullOrEmpty() &&
            !prefs.getString("my_phone",   "").isNullOrEmpty()

        when {
            !smsGranted -> {
                tvStatus.text = "⚠️ SMS permission denied — tap Save to try again"
                tvStatus.setTextColor(getColor(R.color.status_error))
            }
            !configured -> {
                tvStatus.text = "⚠️ Fill in all fields and tap Save"
                tvStatus.setTextColor(getColor(R.color.status_warning))
            }
            else -> {
                tvStatus.text = "✅ Relay is active"
                tvStatus.setTextColor(getColor(R.color.status_ok))
            }
        }
    }

    // ── Config persistence ─────────────────────────────────────────────────────

    private fun saveConfig() {
        getSharedPreferences("otp_relay", Context.MODE_PRIVATE).edit().apply {
            putString("api_url",    etApiUrl.text.toString().trim())
            putString("secret_key", etSecretKey.text.toString().trim())
            putString("my_phone",   etMyPhone.text.toString().trim())
            apply()
        }
        Toast.makeText(this, "Config saved ✓", Toast.LENGTH_SHORT).show()
    }

    private fun loadConfig() {
        val prefs = getSharedPreferences("otp_relay", Context.MODE_PRIVATE)
        etApiUrl.setText(prefs.getString("api_url",    ""))
        etSecretKey.setText(prefs.getString("secret_key", ""))
        etMyPhone.setText(prefs.getString("my_phone",   ""))
    }

    // ── Validation ─────────────────────────────────────────────────────────────

    private fun validateInputs(): Boolean {
        val url    = etApiUrl.text.toString().trim()
        val secret = etSecretKey.text.toString().trim()
        val phone  = etMyPhone.text.toString().trim()

        return when {
            url.isEmpty()              -> { etApiUrl.error    = "Required"; false }
            !url.startsWith("https://")-> { etApiUrl.error    = "Must start with https://"; false }
            secret.isEmpty()           -> { etSecretKey.error = "Required"; false }
            phone.isEmpty()            -> { etMyPhone.error   = "Required"; false }
            !phone.startsWith("+")     -> { etMyPhone.error   = "Use international format e.g. +919876543210"; false }
            else -> true
        }
    }
}
