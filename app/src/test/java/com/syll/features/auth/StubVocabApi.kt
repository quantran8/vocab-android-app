package com.syll.features.auth

import com.syll.domain.CefrLevel
import com.syll.domain.TopicCatalog
import com.syll.network.ApiError
import com.syll.network.CatalogCollection
import com.syll.network.CatalogTopic
import com.syll.network.LearnHome
import com.syll.network.LearningScope
import com.syll.network.NewWordOverride
import com.syll.network.NewWordOverrideMode
import com.syll.network.PlacementAnswer
import com.syll.network.PlacementQuestion
import com.syll.network.PlacementResult
import com.syll.network.SettingsUpdate
import com.syll.network.SignupResult
import com.syll.network.UserProfile
import com.syll.network.UserSettings
import com.syll.network.VocabApi
import java.time.Instant

/** iOS `StubVocabAPI`: records calls and answers with what the test configured. */
class StubVocabApi(var storedSession: Boolean = true) : VocabApi {
    var logInError: ApiError? = null
    var deleteAccountError: ApiError? = null
    var signupResult = SignupResult(session = null, confirmationRequired = true)
    var deleteAccountCalls = 0
    var signOutCalls = 0
    var logInCalls = 0

    var profileResult: Result<UserProfile> = Result.success(Companion.profile())
    val levelUpdates = mutableListOf<CefrLevel>()
    val settingsUpdates = mutableListOf<SettingsUpdate>()
    val scopeUpdates = mutableListOf<LearningScope>()
    /** Defaults to the bundled catalog, like iOS `OfflineVocabAPI`. */
    var catalogCollections: List<CatalogCollection> = TopicCatalog.collections.map { collection ->
        CatalogCollection(
            id = collection.id, slug = collection.id, title = collection.name, description = collection.summary,
            topics = collection.topics.map { CatalogTopic(it.id, it.id, it.name) },
        )
    }
    var placementQuestions: Result<List<PlacementQuestion>> = Result.success(emptyList())
    var placementResults = ArrayDeque<Result<PlacementResult>>()
    val placementSubmissions = mutableListOf<List<PlacementAnswer>>()
    var learnHomeResult: Result<LearnHome> = Result.success(LearnHome(0, 0, 0, 7, 0))
    val acknowledgedBacklogs = mutableListOf<Int>()
    val overrides = mutableListOf<Pair<Int, NewWordOverrideMode>>()
    var overrideClears = 0
    val reviewPauses = mutableListOf<Pair<String, Boolean>>()
    /** Runs inside `placementTest()`, before it answers (to hold it open). */
    var onPlacementTest: suspend () -> Unit = {}

    override fun hasStoredSession() = storedSession
    override suspend fun signUp(email: String, password: String) = signupResult
    override suspend fun logIn(email: String, password: String) {
        logInCalls += 1
        logInError?.let { throw it }
    }
    override suspend fun logInWithGoogle(idToken: String, nonce: String?) {
        logInError?.let { throw it }
    }
    override suspend fun logInWithApple(idToken: String, nonce: String?) {
        logInError?.let { throw it }
    }
    override suspend fun requestPasswordReset(email: String) {}
    override suspend fun resetPassword(email: String, code: String, newPassword: String) {}
    override suspend fun changePassword(current: String, new: String) {}
    override suspend fun signOut() {
        signOutCalls += 1
    }
    override suspend fun deleteAccount() {
        deleteAccountCalls += 1
        deleteAccountError?.let { throw it }
    }

    override suspend fun profile(): UserProfile = profileResult.getOrThrow()
    override suspend fun updateLevels(level: CefrLevel) {
        levelUpdates += level
    }
    override suspend fun placementTest(): List<PlacementQuestion> {
        onPlacementTest()
        return placementQuestions.getOrThrow()
    }
    override suspend fun submitPlacementTest(answers: List<PlacementAnswer>): PlacementResult {
        placementSubmissions += answers
        return placementResults.removeFirst().getOrThrow()
    }
    override suspend fun updateSettings(settings: SettingsUpdate) {
        settingsUpdates += settings
    }
    override suspend fun updateScope(scope: LearningScope) {
        scopeUpdates += scope
    }
    override suspend fun catalog() = catalogCollections

    override suspend fun learnHome(): LearnHome = learnHomeResult.getOrThrow()
    override suspend fun acknowledgeReviewLoad(backlog: Int) {
        acknowledgedBacklogs += backlog
    }
    override suspend fun setNewWordOverride(value: Int, mode: NewWordOverrideMode, until: Instant?): NewWordOverride {
        overrides += value to mode
        return NewWordOverride(value, mode, until)
    }
    override suspend fun clearNewWordOverride() {
        overrideClears += 1
    }
    override suspend fun setReviewPaused(collectionId: String, paused: Boolean) {
        reviewPauses += collectionId to paused
    }

    companion object {
        fun profile(
            level: CefrLevel? = null,
            scope: LearningScope? = null,
            settings: UserSettings = UserSettings("vi", "en-US", "UTC", dailyNewWordGoal = 5),
        ) = UserProfile(id = "u1", proficiencyLevel = level, settings = settings, scope = scope)
    }
}
