package com.vocabflow.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializer
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ApiRequest(
    val method: Method,
    val path: String,
    val query: List<Pair<String, String>> = emptyList(),
    /** Encoded once by the caller, so a retried commit sends the same bytes. */
    val body: ByteArray? = null,
    val contentType: String = "application/json",
    val requiresAuth: Boolean = true,
    /** Extra attempts after a retryable failure. Only for requests that are safe to repeat. */
    val retries: Int = 0,
    /** Overrides the client's timeout, for requests the backend answers slowly. */
    val timeoutMillis: Long? = null,
) {
    enum class Method(val value: String) { GET("GET"), POST("POST"), PUT("PUT"), DELETE("DELETE") }

    // ByteArray has identity equality; compare contents so requests behave as values.
    override fun equals(other: Any?): Boolean =
        other is ApiRequest && method == other.method && path == other.path && query == other.query &&
            body.contentEqualsNullable(other.body) && contentType == other.contentType &&
            requiresAuth == other.requiresAuth && retries == other.retries && timeoutMillis == other.timeoutMillis

    override fun hashCode(): Int = listOf(method, path, query, body?.contentHashCode(), contentType, requiresAuth, retries)
        .hashCode()

    private fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean =
        if (this == null || other == null) this === other else contentEquals(other)
}

/**
 * HTTP transport (iOS `APIClient`): bearer auth, one refresh-and-retry on 401, backoff on retryable failures.
 * Concurrent 401s share a single refresh, because refresh tokens are single use.
 */
class ApiClient(
    baseUrl: String,
    private val tokens: TokenStoring,
    private val http: OkHttpClient = OkHttpClient(),
    private val now: () -> Long = System::currentTimeMillis,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    private val baseUrl: HttpUrl = baseUrl.toHttpUrl()
    private val refreshLock = Mutex()
    private var refreshJob: Deferred<AuthTokens>? = null

    // The refresh outlives the request that started it, so a cancelled screen can't strand the others.
    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var onSessionEnded: (suspend () -> Unit)? = null

    /** Called when the refresh token is rejected, so the app can return to sign-in. */
    fun setSessionEndedHandler(handler: suspend () -> Unit) {
        onSessionEnded = handler
    }

    val hasStoredSession: Boolean get() = tokens.load() != null

    fun store(session: AuthSession) {
        tokens.save(authTokens(session))
    }

    suspend fun signOut() {
        refreshLock.withLock {
            refreshJob?.cancel()
            refreshJob = null
        }
        tokens.clear()
    }

    suspend inline fun <reified T> send(request: ApiRequest): T = send(request, serializer())

    suspend fun <T> send(request: ApiRequest, deserializer: DeserializationStrategy<T>): T {
        val data = sendRaw(request)
        return try {
            ApiJson.decodeFromString(deserializer, data.decodeToString())
        } catch (e: IllegalArgumentException) { // SerializationException is one
            throw ApiError.InvalidResponse
        }
    }

    suspend fun sendRaw(request: ApiRequest): ByteArray {
        var attempt = 0
        while (true) {
            try {
                return authorized(request)
            } catch (error: ApiError) {
                if (!error.isRetryable || attempt >= request.retries) throw error
                attempt += 1
                pause(500L shl attempt)
            }
        }
    }

    // Auth

    private suspend fun authorized(request: ApiRequest): ByteArray {
        if (!request.requiresAuth) return execute(request, accessToken = null)
        val token = currentAccessToken()
        return try {
            execute(request, token)
        } catch (error: ApiError.Server) {
            if (error.status != 401) throw error
            execute(request, refreshed(replacing = token))
        }
    }

    private suspend fun currentAccessToken(): String {
        val current = tokens.load() ?: throw unauthorized()
        if (current.expiresAtMillis - now() > EXPIRY_MARGIN_MILLIS) return current.accessToken
        return refreshed(replacing = current.accessToken)
    }

    private suspend fun refreshed(replacing: String): String {
        val job = refreshLock.withLock {
            refreshJob ?: run {
                val current = tokens.load() ?: throw unauthorized()
                // Another request already rotated the pair while this one was in flight.
                if (current.accessToken != replacing) return current.accessToken
                refreshScope.async { rotate(current.refreshToken) }.also { refreshJob = it }
            }
        }
        try {
            return job.await().accessToken
        } finally {
            refreshLock.withLock { if (refreshJob === job) refreshJob = null }
        }
    }

    private suspend fun rotate(refreshToken: String): AuthTokens {
        val request = ApiRequest(
            method = ApiRequest.Method.POST,
            path = "auth/refresh",
            body = ApiJson.encodeToString(RefreshRequest.serializer(), RefreshRequest(refreshToken)).toByteArray(),
            requiresAuth = false,
        )
        try {
            val data = execute(request, accessToken = null)
            val session = try {
                ApiJson.decodeFromString(AuthSession.serializer(), data.decodeToString())
            } catch (e: IllegalArgumentException) {
                throw ApiError.InvalidResponse
            }
            val fresh = authTokens(session)
            currentCoroutineContext().ensureActive()
            tokens.save(fresh)
            return fresh
        } catch (error: ApiError.Server) {
            if (error.status == 401) {
                tokens.clear()
                onSessionEnded?.invoke()
            }
            throw error
        }
    }

    private fun authTokens(session: AuthSession) = AuthTokens(
        accessToken = session.accessToken,
        refreshToken = session.refreshToken,
        expiresAtMillis = now() + session.expiresIn * 1000L,
    )

    // Transport

    private suspend fun execute(request: ApiRequest, accessToken: String?): ByteArray {
        val url = baseUrl.newBuilder()
            .addPathSegments("v1/${request.path}")
            .apply { request.query.forEach { (name, value) -> addQueryParameter(name, value) } }
            .build()
        val body = request.body?.toRequestBody(request.contentType.toMediaType())
            ?: if (request.method == ApiRequest.Method.POST || request.method == ApiRequest.Method.PUT) {
                ByteArray(0).toRequestBody(null)
            } else {
                null
            }
        val builder = Request.Builder().url(url).method(request.method.value, body)
        if (accessToken != null) builder.header("Authorization", "Bearer $accessToken")

        val client = request.timeoutMillis?.let {
            http.newBuilder()
                .callTimeout(it, TimeUnit.MILLISECONDS)
                .readTimeout(it, TimeUnit.MILLISECONDS)
                .build()
        } ?: http

        val (status, data) = try {
            client.newCall(builder.build()).await()
        } catch (e: IOException) {
            throw ApiError.Transport
        }
        if (status !in 200..299) throw error(status, data)
        return data
    }

    private fun error(status: Int, data: ByteArray): ApiError {
        val body = try {
            ApiJson.decodeFromString(ErrorBody.serializer(), data.decodeToString())
        } catch (e: IllegalArgumentException) {
            return ApiError.Server(status, "HTTP_$status", "", retryable = status >= 500, requestId = null)
        }
        return ApiError.Server(
            status = status,
            code = body.code,
            message = body.message,
            retryable = body.retryable,
            requestId = body.requestId,
            // A `details` value read as text, whatever JSON scalar it is. Nested values become "".
            details = body.details?.mapValues { (_, value) -> (value as? JsonPrimitive)?.content ?: "" },
        )
    }

    @Serializable
    private data class ErrorBody(
        val code: String,
        val message: String,
        val retryable: Boolean,
        val requestId: String? = null,
        val details: Map<String, JsonElement>? = null,
    )

    private companion object {
        /** Refresh this long before expiry instead of waiting for a 401. */
        const val EXPIRY_MARGIN_MILLIS = 60_000L

        fun unauthorized() = ApiError.Server(
            status = 401, code = "UNAUTHORIZED", message = "Not signed in", retryable = false, requestId = null,
        )
    }
}

private suspend fun Call.await(): Pair<Int, ByteArray> = suspendCancellableCoroutine { continuation ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                val result = response.use { it.code to it.body!!.bytes() }
                continuation.resume(result)
            } catch (e: IOException) {
                continuation.resumeWithException(e)
            }
        }
    })
    continuation.invokeOnCancellation { cancel() }
}
