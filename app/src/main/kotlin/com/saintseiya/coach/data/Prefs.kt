package com.saintseiya.coach.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class Prefs(ctx: Context) {
    private val sp: SharedPreferences = ctx.getSharedPreferences("coach", Context.MODE_PRIVATE)
    private val secure: SharedPreferences? = try {
        val mk = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(ctx, "coach_secure", mk,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    } catch (e: Exception) { null }

    var activeAccountId: Long
        get() = sp.getLong("account", 0L)
        set(v) { sp.edit().putLong("account", v).apply() }
    var intervalMs: Long
        get() = sp.getLong("interval", 1500L)
        set(v) { sp.edit().putLong("interval", v).apply() }
    /** Distância de Hamming (0..64) mínima para considerar que a tela mudou. */
    var changeThreshold: Int
        get() = sp.getInt("threshold", 6)
        set(v) { sp.edit().putInt("threshold", v).apply() }
    var lastError: String?
        get() = sp.getString("lastError", null)
        set(v) { sp.edit().putString("lastError", v).apply() }
    var lastUpdateCheck: Long
        get() = sp.getLong("lastUpdateCheck", 0L)
        set(v) { sp.edit().putLong("lastUpdateCheck", v).apply() }
    var githubToken: String?
        get() = secure?.getString("ghToken", null)
        set(v) { secure?.edit()?.apply { if (v.isNullOrBlank()) remove("ghToken") else putString("ghToken", v.trim()) }?.apply() }
    val secureAvailable get() = secure != null
}
