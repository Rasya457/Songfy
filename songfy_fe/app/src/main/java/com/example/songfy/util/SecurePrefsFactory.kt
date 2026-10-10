package com.lagu.songfy.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Centralized factory for EncryptedSharedPreferences.
 * Falls back to regular SharedPreferences if encryption is unavailable
 * (e.g. emulator without hardware keystore, or key corruption after app reinstall).
 *
 * Usage:
 *   val prefs = SecurePrefsFactory.get(context, "my_prefs_name")
 */
object SecurePrefsFactory {

    private val cache = mutableMapOf<String, SharedPreferences>()

    @Synchronized
    fun get(context: Context, name: String): SharedPreferences {
        cache[name]?.let { return it }

        val prefs = try {
            val masterKey = MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context.applicationContext,
                name,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback — aman di release, tapi data tidak terenkripsi.
            // Ini terjadi pada emulator tanpa hardware keystore atau setelah
            // reinstall yang merusak master key lama.
            Log.w("SecurePrefsFactory", "EncryptedSharedPreferences unavailable for '$name', falling back to plain. Reason: ${e.message}")
            context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
        }

        cache[name] = prefs
        return prefs
    }
}
