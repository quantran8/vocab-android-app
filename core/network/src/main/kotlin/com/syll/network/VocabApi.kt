package com.syll.network

import kotlinx.serialization.KSerializer

/**
 * iOS `VocabAPI`: everything the app asks of the backend. Stores depend on this interface, so tests and previews
 * can stub it. Grows endpoint by endpoint as Phase 1 ports each store.
 */
interface VocabApi {
    fun hasStoredSession(): Boolean
    suspend fun signUp(email: String, password: String): SignupResult
    suspend fun logIn(email: String, password: String)
    /** [nonce] is the raw value whose SHA-256 went into the Google request. */
    suspend fun logInWithGoogle(idToken: String, nonce: String?)
    suspend fun logInWithApple(idToken: String, nonce: String?)
    /** Emails a reset-password code. Succeeds whether or not the address has an account. */
    suspend fun requestPasswordReset(email: String)
    /** Checks the emailed reset code, sets the new password and signs in. */
    suspend fun resetPassword(email: String, code: String, newPassword: String)
    suspend fun changePassword(current: String, new: String)
    suspend fun signOut()
    /** Permanent. On failure nothing was deleted. */
    suspend fun deleteAccount()
}

/** iOS `LiveVocabAPI`. */
class LiveVocabApi(private val client: ApiClient) : VocabApi {
    override fun hasStoredSession(): Boolean = client.hasStoredSession

    override suspend fun signUp(email: String, password: String): SignupResult {
        val result = client.send(post("auth/signup", Credentials(email, password), Credentials.serializer(), requiresAuth = false), SignupResult.serializer())
        result.session?.let(client::store)
        return result
    }

    override suspend fun logIn(email: String, password: String) {
        storeSession(post("auth/login", Credentials(email, password), Credentials.serializer(), requiresAuth = false))
    }

    override suspend fun logInWithGoogle(idToken: String, nonce: String?) {
        storeSession(post("auth/google", IdTokenCredentials(idToken, nonce), IdTokenCredentials.serializer(), requiresAuth = false))
    }

    override suspend fun logInWithApple(idToken: String, nonce: String?) {
        storeSession(post("auth/apple", IdTokenCredentials(idToken, nonce), IdTokenCredentials.serializer(), requiresAuth = false))
    }

    override suspend fun requestPasswordReset(email: String) {
        client.sendRaw(post("auth/recover", PasswordReset(email), PasswordReset.serializer(), requiresAuth = false))
    }

    override suspend fun resetPassword(email: String, code: String, newPassword: String) {
        storeSession(
            post("auth/recover/verify", ResetVerification(email, code, newPassword), ResetVerification.serializer(), requiresAuth = false),
        )
    }

    override suspend fun changePassword(current: String, new: String) {
        client.sendRaw(
            post("me/password", PasswordChange(current, new), PasswordChange.serializer()).copy(method = ApiRequest.Method.PUT),
        )
    }

    override suspend fun signOut() {
        client.signOut()
    }

    override suspend fun deleteAccount() {
        client.sendRaw(ApiRequest(ApiRequest.Method.DELETE, "me"))
    }

    private suspend fun storeSession(request: ApiRequest) {
        client.store(client.send(request, AuthSession.serializer()))
    }

    private fun get(path: String) = ApiRequest(ApiRequest.Method.GET, path, retries = READ_RETRIES)

    private fun <T> post(path: String, body: T, serializer: KSerializer<T>, requiresAuth: Boolean = true) = ApiRequest(
        method = ApiRequest.Method.POST,
        path = path,
        body = ApiJson.encodeToString(serializer, body).toByteArray(),
        requiresAuth = requiresAuth,
    )

    private companion object {
        /** The commit is idempotent, so it may retry harder than other requests. */
        const val COMMIT_RETRIES = 3
        const val READ_RETRIES = 2
    }
}
