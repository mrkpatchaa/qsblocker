package com.mrkpatchaa.qsblocker

import android.content.SharedPreferences
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Parent PIN guarding the lock switch. Only a salted hash is stored, and
 * repeated wrong guesses trigger a short lockout to slow down brute forcing.
 */
class ParentPin(private val prefs: SharedPreferences) {

    companion object {
        const val MIN_LENGTH = 4
        const val MAX_LENGTH = 8
        private const val MAX_ATTEMPTS = 5
        private const val LOCKOUT_MS = 30_000L

        private const val KEY_HASH = "pin_hash"
        private const val KEY_SALT = "pin_salt"
        private const val KEY_FAILED_ATTEMPTS = "pin_failed_attempts"
        private const val KEY_LOCKOUT_UNTIL = "pin_lockout_until"
    }

    val isSet: Boolean
        get() = prefs.contains(KEY_HASH)

    fun isValidFormat(pin: String): Boolean =
        pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it.isDigit() }

    fun set(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_SALT, salt.toHex())
            .putString(KEY_HASH, hash(pin, salt).toHex())
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0L)
            .apply()
    }

    /** Milliseconds left before another attempt is allowed, or 0. */
    fun lockoutRemainingMs(): Long =
        (prefs.getLong(KEY_LOCKOUT_UNTIL, 0L) - System.currentTimeMillis()).coerceAtLeast(0L)

    /** Returns true if [pin] matches. Callers must check [lockoutRemainingMs] first. */
    fun verify(pin: String): Boolean {
        val salt = prefs.getString(KEY_SALT, null)?.fromHex() ?: return false
        val expected = prefs.getString(KEY_HASH, null)?.fromHex() ?: return false

        if (MessageDigest.isEqual(hash(pin, salt), expected)) {
            prefs.edit().putInt(KEY_FAILED_ATTEMPTS, 0).apply()
            return true
        }

        val failed = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        val editor = prefs.edit()
        if (failed >= MAX_ATTEMPTS) {
            editor.putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKOUT_UNTIL, System.currentTimeMillis() + LOCKOUT_MS)
        } else {
            editor.putInt(KEY_FAILED_ATTEMPTS, failed)
        }
        editor.apply()
        return false
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").run {
            update(salt)
            digest(pin.toByteArray(Charsets.UTF_8))
        }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.fromHex(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
