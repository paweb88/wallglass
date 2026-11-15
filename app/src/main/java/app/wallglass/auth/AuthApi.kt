package app.wallglass.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.UUID

/**
 * Login problem. [permanent] = HA rejected the credentials (log out);
 * otherwise it is a network/server hiccup and the caller should retry.
 */
class AuthException(message: String, cause: Throwable? = null, val permanent: Boolean = false) : Exception(message, cause)

/**
 * Home Assistant OAuth2 (IndieAuth) flow.
 * client_id and redirect_uri share a host, so HA accepts the redirect without fetching anything;
 * the WebView intercepts the redirect before it leaves the device.
 */
class AuthApi(
    private val http: OkHttpClient,
    private val store: TokenStore,
) {
    private val refreshLock = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    fun newState(): String = UUID.randomUUID().toString()

    fun authorizeUrl(baseUrl: String, state: String): String =
        "${baseUrl.trimEnd('/')}/auth/authorize".toHttpUrl().newBuilder()
            .addQueryParameter("response_type", "code")
            .addQueryParameter("client_id", CLIENT_ID)
            .addQueryParameter("redirect_uri", REDIRECT_URI)
            .addQueryParameter("state", state)
            .build()
            .toString()

    suspend fun exchangeCode(baseUrl: String, code: String): Tokens {
        val body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("client_id", CLIENT_ID)
            .build()
        val tokens = requestTokens(baseUrl, body, previousRefresh = null)
        store.baseUrl = baseUrl.trimEnd('/')
        store.save(tokens)
        return tokens
    }

    /** Returns a valid access token, refreshing it when it is about to expire. */
    suspend fun validAccessToken(): String = refreshLock.withLock {
        val baseUrl = store.baseUrl ?: throw AuthException("Brak adresu Home Assistanta", permanent = true)
        val current = store.load() ?: throw AuthException("Panel nie jest zalogowany", permanent = true)
        if (!current.isExpiring(System.currentTimeMillis())) return@withLock current.accessToken
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", current.refreshToken)
            .add("client_id", CLIENT_ID)
            .build()
        val fresh = requestTokens(baseUrl, body, previousRefresh = current.refreshToken)
        store.save(fresh)
        fresh.accessToken
    }

    private suspend fun requestTokens(baseUrl: String, body: FormBody, previousRefresh: String?): Tokens =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url("${baseUrl.trimEnd('/')}/auth/token").post(body).build()
            try {
                http.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (response.code == 400 || response.code == 401 || response.code == 403) {
                        if (previousRefresh != null) store.clear()
                        throw AuthException("Home Assistant odrzucił logowanie (${response.code})", permanent = true)
                    }
                    if (!response.isSuccessful) throw AuthException("Błąd serwera ${response.code}")
                    val obj = json.parseToJsonElement(text).jsonObject
                    val access = obj["access_token"]?.jsonPrimitive?.content
                        ?: throw AuthException("Brak access_token w odpowiedzi")
                    val refresh = obj["refresh_token"]?.jsonPrimitive?.content ?: previousRefresh
                        ?: throw AuthException("Brak refresh_token w odpowiedzi")
                    val expiresIn = obj["expires_in"]?.jsonPrimitive?.long ?: 1800L
                    Tokens(access, refresh, System.currentTimeMillis() + expiresIn * 1000)
                }
            } catch (e: IOException) {
                throw AuthException("Brak połączenia z Home Assistantem", e)
            }
        }

    companion object {
        const val CLIENT_ID = "https://paweb88.github.io/wallglass/"
        const val REDIRECT_URI = "https://paweb88.github.io/wallglass/auth_callback"
    }
}
