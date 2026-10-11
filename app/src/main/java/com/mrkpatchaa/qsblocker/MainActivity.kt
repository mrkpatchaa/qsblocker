package com.mrkpatchaa.qsblocker

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvServiceStatus: TextView
    private lateinit var btnOpenSettings: Button
    private lateinit var switchLockEditing: Switch
    private lateinit var sharedPrefs: SharedPreferences

    companion object {
        const val PREFS_NAME = "QSBlockerPrefs"
        const val KEY_IS_LOCKED = "is_qs_locked"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvServiceStatus = findViewById(R.id.tvServiceStatus)
        btnOpenSettings = findViewById(R.id.btnOpenSettings)
        switchLockEditing = findViewById(R.id.switchLockEditing)
        
        sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        switchLockEditing.isChecked = sharedPrefs.getBoolean(KEY_IS_LOCKED, true)

        switchLockEditing.setOnCheckedChangeListener { _, isChecked ->
            sharedPrefs.edit().putBoolean(KEY_IS_LOCKED, isChecked).apply()
        }

        btnOpenSettings.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatusUI()
    }

    private fun updateServiceStatusUI() {
        val isEnabled = isAccessibilityServiceEnabled()
        
        if (isEnabled) {
            tvServiceStatus.text = "Service Status: ENABLED ✔️"
            tvServiceStatus.setTextColor(android.graphics.Color.parseColor("#1b5e20"))
            btnOpenSettings.isEnabled = false
            btnOpenSettings.text = "Service is Active"
            switchLockEditing.isEnabled = true
        } else {
            tvServiceStatus.text = "Service Status: DISABLED ❌"
            tvServiceStatus.setTextColor(android.graphics.Color.parseColor("#b71c1c"))
            btnOpenSettings.isEnabled = true
            btnOpenSettings.text = "Enable Accessibility Service"
            switchLockEditing.isEnabled = false
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = ComponentName(this, QSBlockerAccessibilityService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            contentResolver, 
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.split(':').any { it.equals(expectedComponentName, ignoreCase = true) }
    }
}
