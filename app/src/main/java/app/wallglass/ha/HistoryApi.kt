package app.wallglass.ha

import android.util.Log
import app.wallglass.auth.AuthApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Reads numeric sensor history from HA's REST API. */
class HistoryApi(
    private val http: OkHttpClient,
    private val auth: AuthApi,
    private val baseUrl: String,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Last [hours] of values per entity, averaged into [buckets] equal time slots. */
    suspend fun series(entityIds: List<String>, hours: Long = 24, buckets: Int = 48): Map<String, List<Float>> =
        withContext(Dispatchers.IO) {
            if (entityIds.isEmpty()) return@withContext emptyMap()
            val end = Instant.now()
            val start = end.minus(hours, ChronoUnit.HOURS)
            val url = "${baseUrl.trimEnd('/')}/api/history/period/$start".toHttpUrl().newBuilder()
                .addQueryParameter("filter_entity_id", entityIds.joinToString(","))
                .addQueryParameter("end_time", end.toString())
                .addQueryParameter("minimal_response", "")
                .addQueryParameter("no_attributes", "")
                .build()
            try {
                val token = auth.validAccessToken()
                val request = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptyMap()
                    val lists = json.parseToJsonElement(response.body?.string().orEmpty()).jsonArray
                    lists.mapNotNull { list ->
                        val points = list.jsonArray.mapNotNull { el ->
                            val o = el.jsonObject
                            val v = o["state"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: return@mapNotNull null
                            val t = o["last_changed"]?.jsonPrimitive?.contentOrNull
                                ?.let { runCatching { java.time.OffsetDateTime.parse(it).toInstant() }.getOrNull() } ?: start
                            t to v
                        }
                        val id = list.jsonArray.firstOrNull()?.jsonObject?.get("entity_id")?.jsonPrimitive?.contentOrNull
                        id?.let { it to bucketize(points, start, end, buckets) }
                    }.toMap()
                }
            } catch (e: Exception) {
                Log.w("HistoryApi", "Historia niedostępna: ${e.message}")
                emptyMap()
            }
        }

    companion object {
        /** Step-function average: each bucket takes the last known value at its end. */
        fun bucketize(points: List<Pair<Instant, Float>>, start: Instant, end: Instant, buckets: Int): List<Float> {
            if (points.isEmpty()) return emptyList()
            val sorted = points.sortedBy { it.first }
            val span = (end.toEpochMilli() - start.toEpochMilli()).toDouble()
            var idx = 0
            var last = sorted.first().second
            return (1..buckets).map { b ->
                val bucketEnd = start.toEpochMilli() + (span * b / buckets).toLong()
                while (idx < sorted.size && sorted[idx].first.toEpochMilli() <= bucketEnd) {
                    last = sorted[idx].second
                    idx++
                }
                last
            }
        }
    }
}
