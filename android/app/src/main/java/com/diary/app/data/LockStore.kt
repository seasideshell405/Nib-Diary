package com.diary.app.data

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

/** App lock persistence: enabled flag + salted SHA-256 PIN hash. */
class LockStore(context: Context) {
    private val prefs = context.getSharedPreferences("lock", Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
    }

    /**
     * Lock immediately when the app goes to the background (default on).
     * When off, the lock engages when the app returns to the foreground.
     */
    fun lockOnBackground(): Boolean = prefs.getBoolean(KEY_LOCK_ON_BACKGROUND, true)

    fun setLockOnBackground(value: Boolean) {
        prefs.edit().putBoolean(KEY_LOCK_ON_BACKGROUND, value).apply()
    }

    fun isFingerprintEnabled(): Boolean = prefs.getBoolean(KEY_FINGERPRINT, false)

    fun setFingerprintEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_FINGERPRINT, value).apply()
    }

    fun pinLength(): Int = prefs.getInt(KEY_PIN_LENGTH, 0)

    /** Stores a new PIN (4-6 digits). Old PIN is irreversibly replaced. */
    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
        prefs.edit()
            .putString(KEY_PIN_SALT, salt)
            .putString(KEY_PIN_HASH, hashPin(pin, salt))
            .putInt(KEY_PIN_LENGTH, pin.length)
            .apply()
    }

    fun verifyPin(pin: String): Boolean {
        val hash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val salt = prefs.getString(KEY_PIN_SALT, "").orEmpty()
        return hashPin(pin, salt) == hash
    }

    private fun hashPin(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest("diary::$salt::$pin".toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_LOCK_ON_BACKGROUND = "lock_on_background"
        const val KEY_FINGERPRINT = "fingerprint_enabled"
        const val KEY_PIN_SALT = "pin_salt"
        const val KEY_PIN_HASH = "pin_hash"
        const val KEY_PIN_LENGTH = "pin_length"
    }
}
