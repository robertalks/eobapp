package com.eobapp.app.network

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.lang.reflect.Type
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/** A failure worth showing to the user as-is. */
class ApiException(message: String) : Exception(message)

/**
 * Client for the eob_scheduler stats API, reached through Cloudflare Access.
 *
 * Cloudflare Access sits in front of the scheduler, so every request carries a service token
 * (CF-Access-Client-Id / CF-Access-Client-Secret). With a valid token and a "Service Auth"
 * policy on the Access application, Cloudflare passes the request straight through; without one
 * it answers with a redirect to its login page, which an app can't follow. Redirects are
 * therefore NOT followed: a 302 to *.cloudflareaccess.com is turned into a clear message
 * instead of an HTML login page being fed to the JSON parser.
 *
 * Changing the target temperature additionally needs the scheduler's own control token
 * (config.yaml → control.token), sent as "Authorization: Bearer …".
 */
class EobApi(
    host: String,
    private val clientId: String,
    private val clientSecret: String,
    private val controlToken: String = "",
) {
    private val baseUrl = "https://" + normalizeHost(host)

    private val gson = Gson()

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    suspend fun status(): StatusResponse =
        request<StatusResponse>("GET", "/api/status", null, StatusResponse::class.java)

    suspend fun history(hours: Int): HistoryResponse =
        request<HistoryResponse>("GET", "/api/history?hours=$hours", null, HistoryResponse::class.java)

    suspend fun daily(days: Int): List<DailyRuntime> {
        val type = object : TypeToken<List<DailyRuntime>>() {}.type
        return request<List<DailyRuntime>>("GET", "/api/daily?days=$days", null, type)
    }

    suspend fun cycles(hours: Int, limit: Int = 100): CyclesResponse =
        request<CyclesResponse>("GET", "/api/cycles?hours=$hours&limit=$limit", null,
            CyclesResponse::class.java)

    /** Sets the low-tariff target; the scheduler rounds to its 0.5 °C step. */
    suspend fun setTarget(celsius: Double): TargetResult {
        val json = JsonObject().apply { addProperty("target", celsius) }.toString()
        return request<TargetResult>(
            "POST", "/api/target",
            json.toRequestBody(JSON),
            TargetResult::class.java
        )
    }

    /** Drops the app-set target; the scheduler goes back to its configured behaviour. */
    suspend fun clearTarget(): TargetResult =
        request<TargetResult>("DELETE", "/api/target", null, TargetResult::class.java)

    private suspend fun <T> request(method: String, path: String, body: RequestBody?, type: Type): T =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(baseUrl + path)
                .header("Accept", "application/json")
                .apply {
                    if (clientId.isNotBlank()) header("CF-Access-Client-Id", clientId)
                    if (clientSecret.isNotBlank()) header("CF-Access-Client-Secret", clientSecret)
                    if (method != "GET" && controlToken.isNotBlank()) {
                        header("Authorization", "Bearer $controlToken")
                    }
                    when (method) {
                        "POST" -> post(body ?: ByteArray(0).toRequestBody(JSON))
                        "DELETE" -> delete()
                        else -> get()
                    }
                }
                .build()

            val text = try {
                http.newCall(request).execute().use { response ->
                    val location = response.header("Location").orEmpty()
                    val contentType = response.header("Content-Type").orEmpty()
                    val isJson = contentType.contains("json")
                    val raw = response.body?.string().orEmpty()
                    when {
                        response.isRedirect && "cloudflareaccess.com" in location ->
                            throw ApiException(
                                "Cloudflare Access wants a login. Check the service token " +
                                    "and that the heater application has a Service Auth policy for it."
                            )
                        response.isRedirect ->
                            throw ApiException("Unexpected redirect to $location")
                        // The scheduler's own errors are JSON {"error": "..."}: show its words.
                        !response.isSuccessful && isJson ->
                            throw ApiException(errorMessage(raw) ?: "Server answered HTTP ${response.code}.")
                        response.code == 401 || response.code == 403 ->
                            throw ApiException(
                                "Cloudflare Access rejected the service token (HTTP ${response.code})."
                            )
                        response.code == 530 || response.code == 502 ->
                            throw ApiException(
                                "Cloudflare can't reach the scheduler (HTTP ${response.code}) — " +
                                    "is the tunnel or eob_scheduler down?"
                            )
                        response.code == 501 || (method != "GET" && response.code == 404) ->
                            throw ApiException(
                                "This eob_scheduler doesn't support changing the temperature yet — update it."
                            )
                        !response.isSuccessful ->
                            throw ApiException("Server answered HTTP ${response.code}.")
                        !isJson ->
                            throw ApiException(
                                "Got $contentType instead of JSON — is this the eob_scheduler address?"
                            )
                    }
                    raw
                }
            } catch (e: ApiException) {
                throw e
            } catch (e: UnknownHostException) {
                throw ApiException("Host not found: ${normalizeHost(baseUrl)}")
            } catch (e: SocketTimeoutException) {
                throw ApiException("The server didn't answer in time.")
            } catch (e: SSLException) {
                throw ApiException("Secure connection failed: ${e.message}")
            } catch (e: IOException) {
                throw ApiException("Can't connect: ${e.message ?: e.javaClass.simpleName}")
            }

            try {
                gson.fromJson<T>(text, type) ?: throw ApiException("Empty response.")
            } catch (e: JsonSyntaxException) {
                throw ApiException("The server's answer isn't what the app expects.")
            }
        }

    private fun errorMessage(raw: String): String? = try {
        gson.fromJson(raw, JsonObject::class.java)?.get("error")?.asString
    } catch (e: Exception) {
        null
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        /**
         * Accepts what people actually paste: "heater.example.com", "https://heater.example.com/",
         * even a full dashboard URL. Returns just host[:port].
         */
        fun normalizeHost(input: String): String =
            input.trim()
                .removePrefix("https://")
                .removePrefix("http://")
                .substringBefore('/')
                .trim()
    }
}
