package com.eobapp.app.network

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/** A failure worth showing to the user as-is. */
class ApiException(message: String) : Exception(message)

/**
 * Read-only client for the eob_scheduler stats API, reached through Cloudflare Access.
 *
 * Cloudflare Access sits in front of the scheduler, so every request carries a service token
 * (CF-Access-Client-Id / CF-Access-Client-Secret). With a valid token and a "Service Auth"
 * policy on the Access application, Cloudflare passes the request straight through; without one
 * it answers with a redirect to its login page, which an app can't follow. Redirects are
 * therefore NOT followed: a 302 to *.cloudflareaccess.com is turned into a clear message
 * instead of an HTML login page being fed to the JSON parser.
 */
class EobApi(
    host: String,
    private val clientId: String,
    private val clientSecret: String,
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
        get<StatusResponse>("/api/status", StatusResponse::class.java)

    suspend fun history(hours: Int): HistoryResponse =
        get<HistoryResponse>("/api/history?hours=$hours", HistoryResponse::class.java)

    suspend fun daily(days: Int): List<DailyRuntime> {
        val type = object : TypeToken<List<DailyRuntime>>() {}.type
        return get<List<DailyRuntime>>("/api/daily?days=$days", type)
    }

    private suspend fun <T> get(path: String, type: java.lang.reflect.Type): T =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(baseUrl + path)
                .header("Accept", "application/json")
                .apply {
                    if (clientId.isNotBlank()) header("CF-Access-Client-Id", clientId)
                    if (clientSecret.isNotBlank()) header("CF-Access-Client-Secret", clientSecret)
                }
                .build()

            val body = try {
                http.newCall(request).execute().use { response ->
                    val location = response.header("Location").orEmpty()
                    when {
                        response.isRedirect && "cloudflareaccess.com" in location ->
                            throw ApiException(
                                "Cloudflare Access wants a login. Check the service token " +
                                    "and that the heater application has a Service Auth policy for it."
                            )
                        response.isRedirect ->
                            throw ApiException("Unexpected redirect to $location")
                        response.code == 401 || response.code == 403 ->
                            throw ApiException(
                                "Cloudflare Access rejected the service token (HTTP ${response.code})."
                            )
                        response.code == 530 || response.code == 502 ->
                            throw ApiException(
                                "Cloudflare can't reach the scheduler (HTTP ${response.code}) — " +
                                    "is the tunnel or eob_scheduler down?"
                            )
                        !response.isSuccessful ->
                            throw ApiException("Server answered HTTP ${response.code}.")
                    }
                    val contentType = response.header("Content-Type").orEmpty()
                    if (!contentType.contains("json")) {
                        throw ApiException(
                            "Got $contentType instead of JSON — is this the eob_scheduler address?"
                        )
                    }
                    response.body?.string().orEmpty()
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
                gson.fromJson<T>(body, type) ?: throw ApiException("Empty response.")
            } catch (e: JsonSyntaxException) {
                throw ApiException("The server's answer isn't what the app expects.")
            }
        }

    companion object {
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
