package com.syll.network

import com.syll.domain.CefrLevel
import com.syll.domain.journey.JourneyPeriod
import com.syll.domain.journey.LearningJourney
import com.syll.domain.report.ContentReportRequest
import com.syll.domain.report.ContentReportTarget
import com.syll.domain.challenge.ChallengeCommitResponse
import com.syll.domain.challenge.ChallengeMap
import com.syll.domain.challenge.ChallengeRunPlan
import com.syll.domain.challenge.ChallengeRunStatus
import com.syll.domain.customwords.CustomWord
import com.syll.domain.customwords.CustomWordAdded
import com.syll.domain.customwords.CustomWordAddition
import com.syll.domain.customwords.CustomWordEdits
import com.syll.domain.customwords.CustomWordLookup
import com.syll.domain.customwords.CustomWordQuery
import com.syll.domain.ranking.Leaderboard
import com.syll.domain.ranking.LeaderboardView
import com.syll.domain.ranking.MyRanking
import com.syll.domain.ranking.PublicProfile
import com.syll.domain.ranking.PublicProfileUpdate
import com.syll.domain.ranking.RankingHistory
import com.syll.domain.ranking.RankingMeta
import com.syll.domain.ranking.ReportReason
import com.syll.domain.ranking.TierDetail
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.syll.domain.practice.CommitResponse
import com.syll.domain.practice.CommitStatus
import com.syll.domain.practice.PlanRequest
import com.syll.domain.practice.RecordedAudio
import com.syll.domain.practice.RecordingAssessment
import com.syll.domain.practice.SessionPlan
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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

    // Challenge (Match Madness). Separate from learning sessions: nothing here changes SRS or Daily.
    suspend fun challengeMap(): ChallengeMap
    /** Freezes a run plan (`issued`); the clock doesn't start yet. */
    suspend fun issueChallengeRun(chapterNo: Int, levelNo: Int): ChallengeRunPlan
    /** Idempotent. The clock starts once this succeeds. */
    suspend fun startChallengeRun(runId: String)
    /** [body] is sent as is: a retried commit must repeat the first attempt's bytes. */
    suspend fun commitChallengeRun(runId: String, body: ByteArray): ChallengeCommitResponse
    suspend fun challengeRunStatus(runId: String): ChallengeRunStatus
    /** Cancels an issued run, or ends a started one with 0 stars. */
    suspend fun quitChallengeRun(runId: String)

    // LP, Tier and the weekly ranking. The leaderboard and reports answer 403 RANKING_DISABLED until launch.
    suspend fun rankingMeta(): RankingMeta
    suspend fun myRanking(): MyRanking
    suspend fun tierDetail(): TierDetail
    suspend fun leaderboard(view: LeaderboardView): Leaderboard
    suspend fun rankingHistory(weeks: Int): RankingHistory
    suspend fun markRankingIntroSeen()
    suspend fun updatePublicProfile(update: PublicProfileUpdate): PublicProfile
    suspend fun reportLearner(username: String, reason: ReportReason)

    // Custom words: the learner's own words, looked up or generated by the backend.
    /** Not retried: every generation counts against the learner's daily allowance. */
    suspend fun lookupCustomWord(query: CustomWordQuery): CustomWordLookup
    suspend fun addCustomWord(addition: CustomWordAddition): CustomWordAdded
    suspend fun customWords(): List<CustomWord>
    suspend fun removeCustomWord(meaningId: String)
    suspend fun overrideCustomWord(meaningId: String, edits: CustomWordEdits)
    suspend fun clearCustomWordOverride(meaningId: String)

    // Premium (FRONTEND_FLOW §16)

    /** `POST /me/subscriptions/playstore`: a Play purchase for the server to verify; answers the learner's plan. */
    suspend fun syncPlayStorePurchase(purchaseToken: String, productId: String): PremiumPlan
    /** `POST /me/redeem`: a Syll code (only while `features.redeemEnabled`). */
    suspend fun redeem(code: String): PremiumPlan

    // Learning journey and content reports (FRONTEND_FLOW §17–18)
    suspend fun learningJourney(period: JourneyPeriod): LearningJourney
    /** Safe to retry: the server replays its first answer for the same `clientReportId`. */
    suspend fun submitContentReport(report: ContentReportRequest): ContentReportReceipt
}

/** iOS `ContentReportReceipt`. */
@kotlinx.serialization.Serializable
data class ContentReportReceipt(val reportId: String, val duplicate: Boolean = false)

/** The `POST /me/content-reports` body (iOS `ContentReportRequest.encode`). */
fun ContentReportRequest.toJson(): JsonObject = buildJsonObject {
    put("clientReportId", clientReportId)
    put("source", source.raw)
    put("reason", reason.raw)
    note?.let { put("note", it) }
    put("context", buildJsonObject {
        when (val c = context) {
            is ContentReportTarget.Context.LearnCard -> {
                put("sessionId", c.sessionId)
                put("learnStepId", c.learnStepId)
            }
            is ContentReportTarget.Context.PracticeTask -> {
                put("sessionId", c.sessionId)
                put("taskId", c.taskId)
                attemptExcerpt?.let { excerpt ->
                    put("attemptExcerpt", buildJsonObject {
                        excerpt.submittedText?.let { put("submittedText", it) }
                        excerpt.optionId?.let { put("optionId", it) }
                        excerpt.tileIds?.let { ids -> put("tileIds", kotlinx.serialization.json.JsonArray(ids.map(::JsonPrimitive))) }
                        excerpt.receiptId?.let { put("receiptId", it) }
                    })
                }
            }
            is ContentReportTarget.Context.WordDetail -> put("meaningId", c.meaningId)
        }
    })
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

    override suspend fun challengeMap(): ChallengeMap = client.send(get("me/challenge-map"), ChallengeMap.serializer())

    override suspend fun issueChallengeRun(chapterNo: Int, levelNo: Int): ChallengeRunPlan =
        client.send(emptyPost("challenge/chapters/$chapterNo/levels/$levelNo/runs"), ChallengeRunPlan.serializer())

    override suspend fun startChallengeRun(runId: String) {
        // Idempotent on the server, so it may retry.
        client.sendRaw(emptyPost("challenge/runs/$runId/start").copy(retries = COMMIT_RETRIES))
    }

    override suspend fun commitChallengeRun(runId: String, body: ByteArray): ChallengeCommitResponse {
        val request = ApiRequest(ApiRequest.Method.POST, "challenge/runs/$runId/commit", body = body, retries = COMMIT_RETRIES)
        return client.send(request, ChallengeCommitResponse.serializer())
    }

    override suspend fun challengeRunStatus(runId: String): ChallengeRunStatus =
        client.send(get("challenge/runs/$runId/status"), ChallengeRunStatus.serializer())

    override suspend fun quitChallengeRun(runId: String) {
        client.sendRaw(emptyPost("challenge/runs/$runId/quit"))
    }

    override suspend fun rankingMeta(): RankingMeta = client.send(get("ranking/meta"), RankingMeta.serializer())

    override suspend fun myRanking(): MyRanking = client.send(get("me/ranking"), MyRanking.serializer())

    override suspend fun tierDetail(): TierDetail = client.send(get("me/tier"), TierDetail.serializer())

    override suspend fun leaderboard(view: LeaderboardView): Leaderboard =
        client.send(get("ranking/leaderboard").copy(query = listOf("view" to view.raw)), Leaderboard.serializer())

    override suspend fun rankingHistory(weeks: Int): RankingHistory =
        client.send(get("me/ranking/history").copy(query = listOf("weeks" to "$weeks")), RankingHistory.serializer())

    override suspend fun markRankingIntroSeen() {
        client.sendRaw(emptyPost("me/ranking/intro-seen"))
    }

    override suspend fun updatePublicProfile(update: PublicProfileUpdate): PublicProfile {
        val body = buildJsonObject {
            update.username?.let { put("username", it) }
            update.avatarKey?.let { put("avatarKey", it) }
        }
        return client.send(postJson("me/profile", body).copy(method = ApiRequest.Method.PUT), PublicProfile.serializer())
    }

    override suspend fun reportLearner(username: String, reason: ReportReason) {
        client.sendRaw(postJson("ranking/reports", buildJsonObject {
            put("username", username)
            put("reason", reason.raw)
        }))
    }

    override suspend fun lookupCustomWord(query: CustomWordQuery): CustomWordLookup =
        client.send(postJson("custom-words/lookup", query.toJson()).copy(timeoutMillis = CUSTOM_WORD_TIMEOUT), CustomWordLookup.serializer())

    override suspend fun addCustomWord(addition: CustomWordAddition): CustomWordAdded =
        client.send(postJson("custom-words", addition.toJson()).copy(timeoutMillis = CUSTOM_WORD_TIMEOUT), CustomWordAdded.serializer())

    override suspend fun customWords(): List<CustomWord> = client.send(get("custom-words"), CustomWordsResponse.serializer()).words

    override suspend fun removeCustomWord(meaningId: String) {
        client.sendRaw(ApiRequest(ApiRequest.Method.DELETE, "custom-words/$meaningId"))
    }

    override suspend fun overrideCustomWord(meaningId: String, edits: CustomWordEdits) {
        client.sendRaw(postJson("custom-words/$meaningId/override", edits.toJson()).copy(method = ApiRequest.Method.PUT))
    }

    override suspend fun clearCustomWordOverride(meaningId: String) {
        client.sendRaw(ApiRequest(ApiRequest.Method.DELETE, "custom-words/$meaningId/override"))
    }

    override suspend fun syncPlayStorePurchase(purchaseToken: String, productId: String): PremiumPlan = client.send(
        postJson("me/subscriptions/playstore", buildJsonObject {
            put("purchaseToken", purchaseToken)
            put("productId", productId)
        }),
        PremiumPlan.serializer(),
    )

    override suspend fun redeem(code: String): PremiumPlan =
        client.send(postJson("me/redeem", buildJsonObject { put("code", code) }), PremiumPlan.serializer())

    override suspend fun learningJourney(period: JourneyPeriod): LearningJourney =
        client.send(get("me/learning-journey").copy(query = listOf("period" to period.raw)), LearningJourney.serializer())

    override suspend fun submitContentReport(report: ContentReportRequest): ContentReportReceipt =
        client.send(postJson("me/content-reports", report.toJson()).copy(retries = READ_RETRIES), ContentReportReceipt.serializer())

    @kotlinx.serialization.Serializable
    private data class CustomWordsResponse(val words: List<CustomWord>)

    private fun emptyPost(path: String) = ApiRequest(ApiRequest.Method.POST, path, body = "{}".toByteArray())

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
        /** A new word is generated by the backend, which can take up to a minute. */
        const val CUSTOM_WORD_TIMEOUT = 70_000L
    }
}
