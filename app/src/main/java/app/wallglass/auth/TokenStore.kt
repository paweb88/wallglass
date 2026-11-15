package app.wallglass.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Tokens issued by Home Assistant for this panel. */
data class Tokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long,
) {
    fun isExpiring(nowMillis: Long, marginMillis: Long = 60_000): Boolean =
        nowMillis + marginMillis >= expiresAtMillis
}

/** Encrypted storage for the server address and OAuth tokens. */
class TokenStore(context: Context) {

    private val prefs: SharedPreferences = try {
        open(context)
    } catch (e: Exception) {
        // Keystore reset or corrupted file: start clean instead of crash-looping as the launcher
        android.util.Log.e("TokenStore", "Szyfrowany magazyn uszkodzony – zakładam od nowa", e)
        context.deleteSharedPreferences(FILE_NAME)
        runCatching { java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS) }
        open(context)
    }

    private fun open(context: Context): SharedPreferences {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var baseUrl: String?
        get() = prefs.getString(KEY_URL, null)
        set(value) {
            prefs.edit().putString(KEY_URL, value).commit()
        }

    fun load(): Tokens? {
        val access = prefs.getString(KEY_ACCESS, null) ?: return null
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return null
        return Tokens(access, refresh, prefs.getLong(KEY_EXPIRES, 0L))
    }

    fun save(tokens: Tokens) {
        prefs.edit()
            .putString(KEY_ACCESS, tokens.accessToken)
            .putString(KEY_REFRESH, tokens.refreshToken)
            .putLong(KEY_EXPIRES, tokens.expiresAtMillis)
            .commit()
    }

    fun clear() {
        prefs.edit().remove(KEY_ACCESS).remove(KEY_REFRESH).remove(KEY_EXPIRES).apply()
    }

    private companion object {
        const val FILE_NAME = "wallglass_secure"
        const val KEY_URL = "base_url"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_EXPIRES = "expires_at"
    }
}
