package com.syll.network

import com.syll.domain.CefrLevel
import com.syll.domain.practice.CommitResponse
import com.syll.domain.practice.CommitStatus
import com.syll.domain.practice.PlanRequest
import com.syll.domain.practice.RecordedAudio
import com.syll.domain.practice.RecordingAssessment
import com.syll.domain.practice.SessionPlan
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.time.format.DateTimeFormatter

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

    suspend fun learnHome(): LearnHome
    /** Records that the review-load choice was shown, so other devices don't ask again. */
    suspend fun acknowledgeReviewLoad(backlog: Int)
    /** [until] null with [NewWordOverrideMode.UNTIL_DATE] lets the server pick the end of the learner's Sunday. */
    suspend fun setNewWordOverride(value: Int, mode: NewWordOverrideMode, until: Instant? = null): NewWordOverride
    suspend fun clearNewWordOverride()
    suspend fun setReviewPaused(collectionId: String, paused: Boolean)

    /** One page of the learned library; [query] filters by spelling or meaning. */
    suspend fun words(limit: Int, offset: Int, query: String?): List<WordProgress>
    suspend fun wordStats(): WordStats
    suspend fun wordDetail(id: String): WordDetail

    suspend fun planSession(request: PlanRequest): SessionPlan
    suspend fun assessRecording(sessionId: String, taskId: String, audio: RecordedAudio): RecordingAssessment
    /** [body] is sent as is: a retried commit must repeat the first attempt's bytes. */
    suspend fun commitSession(sessionId: String, body: ByteArray): CommitResponse
    suspend fun commitStatus(sessionId: String): CommitStatus
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

    override suspend fun learnHome(): LearnHome = client.send(get("me/learn-home"), LearnHome.serializer())

    override suspend fun acknowledgeReviewLoad(backlog: Int) {
        client.sendRaw(post("me/review-load/ack", ReviewLoadAck(backlog), ReviewLoadAck.serializer()))
    }

    override suspend fun setNewWordOverride(value: Int, mode: NewWordOverrideMode, until: Instant?): NewWordOverride {
        val body = OverrideRequest(value, mode, until?.let(DateTimeFormatter.ISO_INSTANT::format))
        val request = post("me/new-word-override", body, OverrideRequest.serializer()).copy(method = ApiRequest.Method.PUT)
        return client.send(request, OverrideResponse.serializer()).override
    }

    override suspend fun clearNewWordOverride() {
        client.sendRaw(ApiRequest(ApiRequest.Method.DELETE, "me/new-word-override"))
    }

    override suspend fun setReviewPaused(collectionId: String, paused: Boolean) {
        val method = if (paused) ApiRequest.Method.PUT else ApiRequest.Method.DELETE
        client.sendRaw(ApiRequest(method, "me/review-pauses/$collectionId"))
    }

    override suspend fun planSession(request: PlanRequest): SessionPlan =
        client.send(postJson("sessions/plan", request.toJson()), SessionPlan.serializer())

    override suspend fun words(limit: Int, offset: Int, query: String?): List<WordProgress> {
        val params = listOf("limit" to "$limit", "offset" to "$offset") + listOfNotNull(query?.takeIf { it.isNotEmpty() }?.let { "q" to it })
        return client.send(get("me/words").copy(query = params), WordsResponse.serializer()).words
    }

    override suspend fun wordStats(): WordStats = client.send(get("me/words/stats"), WordStats.serializer())

    override suspend fun wordDetail(id: String): WordDetail = client.send(get("words/$id"), WordDetail.serializer())

    override suspend fun assessRecording(sessionId: String, taskId: String, audio: RecordedAudio): RecordingAssessment {
        val form = MultipartForm()
        form.addField("taskId", taskId)
        form.addFile("audio", "recording.wav", audio.contentType, audio.data)
        val request = ApiRequest(
            method = ApiRequest.Method.POST,
            path = "sessions/$sessionId/recording-assess",
            body = form.finished(),
            contentType = form.contentType,
        )
        return client.send(request, RecordingAssessment.serializer())
    }

    override suspend fun commitSession(sessionId: String, body: ByteArray): CommitResponse {
        // The commit is idempotent, so it may retry harder than other requests.
        val request = ApiRequest(ApiRequest.Method.POST, "sessions/$sessionId/commit", body = body, retries = COMMIT_RETRIES)
        return client.send(request, CommitResponse.serializer())
    }

    override suspend fun commitStatus(sessionId: String): CommitStatus =
        client.send(get("sessions/$sessionId/commit-status"), CommitStatus.serializer())

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
