package com.deskpilot

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecurePrefs(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "deskpilot_secure",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun savePassword(profileId: String, password: String) {
        prefs.edit().putString("password:$profileId", password).apply()
    }

    fun getPassword(profileId: String): String =
        prefs.getString("password:$profileId", "") ?: ""

    fun deletePassword(profileId: String) {
        prefs.edit().remove("password:$profileId").apply()
    }
}
