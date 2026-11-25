package com.vocabflow.network

/**
 * iOS `APIError`. The user-facing text for each code lives in the app (`ApiErrorMessages`), since it needs
 * string resources.
 */
sealed class ApiError : Exception() {
    /**
     * The backend answered with its `{code, message, retryable, requestId}` error body. [details] holds the body's
     * flat `details` object as strings (e.g. `runId`), when there is one.
     */
    data class Server(
        override val status: Int,
        override val code: String,
        override val message: String,
        val retryable: Boolean,
        val requestId: String?,
        val details: Map<String, String>? = null,
    ) : ApiError()

    /** No usable answer arrived, so the request may or may not have taken effect. */
    data object Transport : ApiError()

    /** The answer doesn't match the contract, including a template this build can't render. */
    data object InvalidResponse : ApiError()

    open val code: String? get() = null
    open val status: Int? get() = null

    fun detail(key: String): String? = (this as? Server)?.details?.get(key)

    val isRetryable: Boolean
        get() = when (this) {
            is Server -> retryable
            Transport -> true
            InvalidResponse -> false
        }
}

/** iOS `Error.asAPIError`: anything that isn't an [ApiError] counts as a transport failure. */
val Throwable.asApiError: ApiError get() = this as? ApiError ?: ApiError.Transport
