package com.example.core

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

enum class LockMode(val key: String) {
    NONE("none"),
    PIN("pin"),
    PATTERN("pattern");

    companion object {
        fun fromKey(key: String?): LockMode =
            entries.firstOrNull { it.key == key } ?: NONE
    }
}

class VaultLockManager(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun getLockMode(uid: String): LockMode {
        val raw = prefs.getString("${uid}_mode", LockMode.NONE.key)
        val mode = LockMode.fromKey(raw)
        if (mode != LockMode.NONE && prefs.getString("${uid}_hash", null).isNullOrEmpty()) {
            return LockMode.NONE
        }
        return mode
    }

    fun isLockEnabled(uid: String): Boolean = getLockMode(uid) != LockMode.NONE

    fun isAutoLockOnBackground(uid: String): Boolean =
        prefs.getBoolean("${uid}_auto_lock", true)

    fun setAutoLockOnBackground(uid: String, enabled: Boolean) {
        prefs.edit().putBoolean("${uid}_auto_lock", enabled).apply()
    }

    fun isDarkThemePreferred(uid: String): Boolean =
        prefs.getBoolean("${uid}_dark_theme", true)

    fun setDarkThemePreferred(uid: String, dark: Boolean) {
        prefs.edit().putBoolean("${uid}_dark_theme", dark).apply()
    }

    fun setPin(uid: String, pin: String) {
        require(pin.length in 4..6 && pin.all { it.isDigit() }) {
            "Il PIN deve avere tra 4 e 6 cifre."
        }
        saveSecret(uid, LockMode.PIN, pin)
    }

    fun setPattern(uid: String, nodes: List<Int>) {
        require(nodes.size >= 4 && nodes.distinct().size == nodes.size) {
            "La sequenza deve collegare almeno 4 punti."
        }
        saveSecret(uid, LockMode.PATTERN, nodes.joinToString("-"))
    }

    fun verifyPin(uid: String, pin: String): Boolean {
        if (getLockMode(uid) != LockMode.PIN) return false
        return verifySecret(uid, pin)
    }

    fun verifyPattern(uid: String, nodes: List<Int>): Boolean {
        if (getLockMode(uid) != LockMode.PATTERN) return false
        return verifySecret(uid, nodes.joinToString("-"))
    }

    fun disableLock(uid: String) {
        prefs.edit()
            .putString("${uid}_mode", LockMode.NONE.key)
            .remove("${uid}_hash")
            .remove("${uid}_salt")
            .apply()
    }

    private fun saveSecret(uid: String, mode: LockMode, rawSecret: String) {
        val saltBytes = ByteArray(16)
        SecureRandom().nextBytes(saltBytes)
        val salt = Base64.encodeToString(saltBytes, Base64.NO_WRAP)
        val hash = sha256("$salt:$rawSecret")
        prefs.edit()
            .putString("${uid}_mode", mode.key)
            .putString("${uid}_salt", salt)
            .putString("${uid}_hash", hash)
            .apply()
    }

    private fun verifySecret(uid: String, rawSecret: String): Boolean {
        val salt = prefs.getString("${uid}_salt", null) ?: return false
        val expectedHash = prefs.getString("${uid}_hash", null) ?: return false
        val actualHash = sha256("$salt:$rawSecret")
        return MessageDigest.isEqual(
            expectedHash.toByteArray(Charsets.UTF_8),
            actualHash.toByteArray(Charsets.UTF_8)
        )
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val PREFS_NAME = "vespro_vault_security"
    }
}
