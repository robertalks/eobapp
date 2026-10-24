package com.eobapp.app.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.eobapp.app.network.EobApi

/**
 * Where the scheduler lives and the Cloudflare Access service token for it — encrypted and
 * local to the device.
 */
class SettingsRepository(context: Context) {

    private val prefs = runCatching { createPrefs(context) }.getOrElse {
        // The keystore entry can become unusable after a reinstall, an OS restore or a changed
        // debug signing key. An exception here would kill the app on launch before anything
        // renders, so clear the store and start fresh — worst case the token is re-entered.
        runCatching { context.deleteSharedPreferences(PREFS_NAME) }
        createPrefs(context)
    }

    private fun createPrefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /** e.g. "heater.example.com" — stored without scheme or path. */
    fun getHost(): String = prefs.getString(KEY_HOST, "") ?: ""

    fun getClientId(): String = prefs.getString(KEY_CLIENT_ID, "") ?: ""

    fun getClientSecret(): String = prefs.getString(KEY_CLIENT_SECRET, "") ?: ""

    /** eob_scheduler's control.token; only needed to change the target temperature. */
    fun getControlToken(): String = prefs.getString(KEY_CONTROL_TOKEN, "") ?: ""

    fun save(host: String, clientId: String, clientSecret: String, controlToken: String) =
        prefs.edit()
            .putString(KEY_HOST, EobApi.normalizeHost(host))
            .putString(KEY_CLIENT_ID, clientId.trim())
            .putString(KEY_CLIENT_SECRET, clientSecret.trim())
            .putString(KEY_CONTROL_TOKEN, controlToken.trim())
            .apply()

    /** The host is the only thing strictly required; the token is needed behind Access. */
    fun isConfigured(): Boolean = getHost().isNotBlank()

    companion object {
        private const val PREFS_NAME = "eobapp_secure_prefs"
        private const val KEY_HOST = "host"
        private const val KEY_CLIENT_ID = "cf_client_id"
        private const val KEY_CLIENT_SECRET = "cf_client_secret"
        private const val KEY_CONTROL_TOKEN = "control_token"
    }
}
