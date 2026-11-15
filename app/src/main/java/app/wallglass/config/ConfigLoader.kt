package app.wallglass.config

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/** Fetches the optional `/local/wallglass.json` from Home Assistant. */
class ConfigLoader(private val http: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun load(baseUrl: String): PanelConfig = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("${baseUrl.trimEnd('/')}/local/wallglass.json").build()
        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext PanelConfig()
                json.decodeFromString(PanelConfig.serializer(), response.body?.string().orEmpty())
            }
        } catch (e: Exception) {
            Log.w("ConfigLoader", "Brak lub błędny wallglass.json – używam wykrywania: ${e.message}")
            PanelConfig()
        }
    }
}
