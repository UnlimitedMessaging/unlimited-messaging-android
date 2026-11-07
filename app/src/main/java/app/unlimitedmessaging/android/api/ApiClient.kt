package app.unlimitedmessaging.android.api

import app.unlimitedmessaging.android.BuildConfig
import app.unlimitedmessaging.android.auth.AuthRepository
import app.unlimitedmessaging.android.auth.RefreshOutcome
import app.unlimitedmessaging.android.auth.TokenStore
import app.unlimitedmessaging.android.config.AppConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** The server answered with a status the caller did not expect. */
open class ApiException(val status: Int, message: String) : IOException(message)

/** 401 that refreshing could not fix. */
class UnauthorizedException : ApiException(401, "Not authorized")

/** 410: the device was removed from the website. The session on this phone is over. */
class DeviceRemovedException : ApiException(410, "Device removed")

/**
 * HTTP plumbing for the authenticated API: base URL, bearer token,
 * transparent refresh on 401, JSON, coroutine cancellation.
 */
class ApiClient(
    private val config: AppConfig,
    private val tokenStore: TokenStore,
    private val auth: AuthRepository,
) {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val http: OkHttpClient = baseHttpClient().newBuilder()
        .addInterceptor { chain ->
            val token = tokenStore.session?.accessToken
            val request = chain.request().newBuilder()
                .apply { if (token != null) header("Authorization", "Bearer $token") }
                .build()
            chain.proceed(request)
        }
        .authenticator(RefreshingAuthenticator())
        .build()

    /**
     * Client for long-polling: the read timeout must outlast the time the
     * server is allowed to hold the request open.
     */
    private val longPollHttp: OkHttpClient = http.newBuilder()
        .readTimeout(config.outboxWaitSeconds + 15L, TimeUnit.SECONDS)
        .build()

    suspend fun <T> get(path: String, response: KSerializer<T>, longPoll: Boolean = false): T? {
        val request = Request.Builder().url(url(path)).get().build()
        return execute(if (longPoll) longPollHttp else http, request, response)
    }

    suspend fun <B, T> post(path: String, body: B, bodySerializer: KSerializer<B>, response: KSerializer<T>?): T? {
        val request = Request.Builder()
            .url(url(path))
            .post(json.encodeToString(bodySerializer, body).toRequestBody(JSON))
            .build()
        return execute(http, request, response)
    }

    suspend fun delete(path: String) {
        execute<Unit>(http, Request.Builder().url(url(path)).delete().build(), null)
    }

    private fun url(path: String) = config.apiBaseUrl + path

    /** Runs the call, cancelling it if the coroutine is cancelled. Null for an empty body (204). */
    private suspend fun <T> execute(client: OkHttpClient, request: Request, serializer: KSerializer<T>?): T? {
        val response = client.newCall(request).await()
        response.use {
            when {
                it.code == 401 -> throw UnauthorizedException()
                it.code == 410 -> throw DeviceRemovedException()
                !it.isSuccessful -> throw ApiException(it.code, "HTTP ${it.code} on ${request.method} ${request.url.encodedPath}")
            }
            val text = it.body?.string()
            if (serializer == null || it.code == 204 || text.isNullOrEmpty()) return null
            return json.decodeFromString(serializer, text)
        }
    }

    /**
     * On 401, refreshes the access token once and replays the request.
     * Returning null lets the 401 through, which [execute] turns into
     * [UnauthorizedException].
     */
    private inner class RefreshingAuthenticator : Authenticator {
        override fun authenticate(route: Route?, response: Response): Request? {
            if (response.priorResponse != null) return null // Already retried once.
            val rejected = response.request.header("Authorization")?.removePrefix("Bearer ")
            return when (val outcome = auth.refreshBlocking(rejected)) {
                is RefreshOutcome.Refreshed -> response.request.newBuilder()
                    .header("Authorization", "Bearer ${outcome.accessToken}")
                    .build()
                RefreshOutcome.Rejected, RefreshOutcome.Unavailable -> null
            }
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        /** Shared settings for every HTTP client of the app. */
        fun baseHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "UnlimitedMessagingAndroid/${BuildConfig.VERSION_NAME}")
                        .build(),
                )
            }
            .build()
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) = continuation.resume(response)
        override fun onFailure(call: Call, e: IOException) {
            if (!continuation.isCancelled) continuation.resumeWithException(e)
        }
    })
    continuation.invokeOnCancellation { cancel() }
}
