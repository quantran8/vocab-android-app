package com.syll.network

import com.syll.domain.CefrLevel
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject

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

    suspend fun profile(): UserProfile
    suspend fun updateLevels(level: CefrLevel)
    /** Empty when there isn't enough content yet; the learner then picks a level by hand. */
    suspend fun placementTest(): List<PlacementQuestion>
    suspend fun submitPlacementTest(answers: List<PlacementAnswer>): PlacementResult
    suspend fun updateSettings(settings: SettingsUpdate)
    suspend fun updateScope(scope: LearningScope)
    suspend fun catalog(): List<CatalogCollection>
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

    override suspend fun profile(): UserProfile = client.send(get("me"), UserProfile.serializer())

    override suspend fun updateLevels(level: CefrLevel) {
        client.sendRaw(post("me/levels", Levels(level), Levels.serializer()).copy(method = ApiRequest.Method.PUT))
    }

    override suspend fun placementTest(): List<PlacementQuestion> =
        client.send(get("me/placement-test"), PlacementTestResponse.serializer()).questions

    override suspend fun submitPlacementTest(answers: List<PlacementAnswer>): PlacementResult =
        client.send(postJson("me/placement-test", answers.submissionJson()), PlacementResult.serializer())

    override suspend fun updateSettings(settings: SettingsUpdate) {
        client.sendRaw(postJson("me/settings", settings.toJson()).copy(method = ApiRequest.Method.PUT))
    }

    override suspend fun updateScope(scope: LearningScope) {
        client.sendRaw(post("me/scope", scope, LearningScope.serializer()).copy(method = ApiRequest.Method.PUT))
    }

    override suspend fun catalog(): List<CatalogCollection> =
        client.send(get("catalog/collections"), CatalogResponse.serializer()).collections

    private fun postJson(path: String, body: JsonObject) = ApiRequest(
        method = ApiRequest.Method.POST,
        path = path,
        body = body.toString().toByteArray(),
    )

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
