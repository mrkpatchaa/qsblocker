package com.mrkpatchaa.qsblocker

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.text.InputFilter
import android.text.InputType
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvServiceStatus: TextView
    private lateinit var btnOpenSettings: Button
    private lateinit var btnChangePin: Button
    private lateinit var switchLockEditing: Switch
    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var parentPin: ParentPin

    private var activeDialog: AlertDialog? = null

    // Turning the lock ON is always allowed; turning it OFF needs the parent PIN
    private val lockSwitchListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        if (isChecked) {
            saveLocked(true)
        } else {
            setLockSwitchSilently(true)
            askForPin("Enter parent PIN to unlock") {
                setLockSwitchSilently(false)
                saveLocked(false)
            }
        }
    }

    companion object {
        const val PREFS_NAME = "QSBlockerPrefs"
        const val KEY_IS_LOCKED = "is_qs_locked"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvServiceStatus = findViewById(R.id.tvServiceStatus)
        btnOpenSettings = findViewById(R.id.btnOpenSettings)
        btnChangePin = findViewById(R.id.btnChangePin)
        switchLockEditing = findViewById(R.id.switchLockEditing)

        sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        parentPin = ParentPin(sharedPrefs)

        switchLockEditing.isChecked = sharedPrefs.getBoolean(KEY_IS_LOCKED, true)
        switchLockEditing.setOnCheckedChangeListener(lockSwitchListener)

        btnOpenSettings.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        btnChangePin.setOnClickListener {
            askForPin("Enter current PIN") { showCreatePinDialog(cancelable = true) }
        }
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatusUI()

        // Existing installs and first launch: nothing is usable until a PIN exists
        if (!parentPin.isSet && activeDialog?.isShowing != true) {
            showCreatePinDialog(cancelable = false)
        }
    }

    override fun onDestroy() {
        activeDialog?.dismiss()
        super.onDestroy()
    }

    private fun saveLocked(isLocked: Boolean) {
        sharedPrefs.edit().putBoolean(KEY_IS_LOCKED, isLocked).apply()
    }

    private fun setLockSwitchSilently(isChecked: Boolean) {
        switchLockEditing.setOnCheckedChangeListener(null)
        switchLockEditing.isChecked = isChecked
        switchLockEditing.setOnCheckedChangeListener(lockSwitchListener)
    }

    private fun askForPin(title: String, onSuccess: () -> Unit) {
        val remainingMs = parentPin.lockoutRemainingMs()
        if (remainingMs > 0) {
            val seconds = (remainingMs + 999) / 1000
            Toast.makeText(this, "Too many wrong attempts. Try again in ${seconds}s.", Toast.LENGTH_SHORT).show()
            return
        }

        val input = pinField("PIN")
        activeDialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(padded(input))
            .setPositiveButton("OK") { _, _ ->
                if (parentPin.verify(input.text.toString())) {
                    onSuccess()
                } else {
                    Toast.makeText(this, "Wrong PIN", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCreatePinDialog(cancelable: Boolean) {
        val pinInput = pinField("New PIN (${ParentPin.MIN_LENGTH}-${ParentPin.MAX_LENGTH} digits)")
        val confirmInput = pinField("Confirm PIN")
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(pinInput)
            addView(confirmInput)
        }

        val builder = AlertDialog.Builder(this)
            .setTitle("Set parent PIN")
            .setMessage("This PIN is required to turn the lock off.")
            .setView(padded(fields))
            .setCancelable(cancelable)
            .setPositiveButton("Save") { _, _ ->
                val pin = pinInput.text.toString()
                when {
                    !parentPin.isValidFormat(pin) -> {
                        Toast.makeText(this, "PIN must be ${ParentPin.MIN_LENGTH}-${ParentPin.MAX_LENGTH} digits", Toast.LENGTH_SHORT).show()
                        showCreatePinDialog(cancelable)
                    }
                    pin != confirmInput.text.toString() -> {
                        Toast.makeText(this, "PINs don't match", Toast.LENGTH_SHORT).show()
                        showCreatePinDialog(cancelable)
                    }
                    else -> {
                        parentPin.set(pin)
                        Toast.makeText(this, "PIN saved", Toast.LENGTH_SHORT).show()
                    }
                }
            }

        if (cancelable) {
            builder.setNegativeButton("Cancel", null)
        } else {
            builder.setNegativeButton("Close app") { _, _ -> finish() }
        }

        activeDialog = builder.show()
    }

    private fun pinField(hint: String) = EditText(this).apply {
        this.hint = hint
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        filters = arrayOf(InputFilter.LengthFilter(ParentPin.MAX_LENGTH))
    }

    private fun padded(view: android.view.View) = LinearLayout(this).apply {
        val padding = (20 * resources.displayMetrics.density).toInt()
        setPadding(padding, 0, padding, 0)
        addView(view, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
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
