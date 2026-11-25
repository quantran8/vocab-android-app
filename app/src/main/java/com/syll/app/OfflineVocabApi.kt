package com.syll.app

import com.syll.domain.CefrLevel
import com.syll.domain.TopicCatalog
import com.syll.domain.practice.CommitResponse
import com.syll.domain.practice.CommitStatus
import com.syll.domain.practice.CommitSummary
import com.syll.domain.practice.PlanRequest
import com.syll.domain.practice.RecordedAudio
import com.syll.domain.practice.RecordingAssessment
import com.syll.domain.practice.PronunciationBand
import com.syll.domain.practice.PronunciationSyllable
import com.syll.domain.practice.SyllableBand
import com.syll.domain.practice.SessionPlan
import com.syll.network.CatalogCollection
import com.syll.network.CatalogTopic
import com.syll.network.LearnHome
import com.syll.network.LearningScope
import com.syll.network.NewWordOverride
import com.syll.network.NewWordOverrideMode
import com.syll.domain.NewWordReason
import com.syll.network.ReviewLoad
import com.syll.network.ScopeState
import com.syll.network.ScopeStatus
import com.syll.network.PlacementAnswer
import com.syll.network.PlacementQuestion
import com.syll.network.PlacementResult
import com.syll.network.SettingsUpdate
import com.syll.network.SignupResult
import com.syll.network.UserProfile
import com.syll.network.UserSettings
import com.syll.network.VocabApi
import kotlinx.coroutines.delay
import java.time.Instant

/**
 * iOS `OfflineVocabAPI`: answers every call without a backend, for previews and the debug gallery's offline flows.
 * A new account with no plan; the bundled catalog; a three-question placement test that places at B1.
 */
class OfflineVocabApi : VocabApi {
    override fun hasStoredSession() = true
    override suspend fun signUp(email: String, password: String) = SignupResult(confirmationRequired = true)
    override suspend fun logIn(email: String, password: String) {}
    override suspend fun logInWithGoogle(idToken: String, nonce: String?) {}
    override suspend fun logInWithApple(idToken: String, nonce: String?) {}
    override suspend fun requestPasswordReset(email: String) {}
    override suspend fun resetPassword(email: String, code: String, newPassword: String) {}
    override suspend fun changePassword(current: String, new: String) {}
    override suspend fun signOut() {}
    override suspend fun deleteAccount() {}

    override suspend fun profile() = UserProfile(
        id = "offline",
        settings = UserSettings("vi", "en-US", "UTC", dailyNewWordGoal = 5),
    )

    override suspend fun updateLevels(level: CefrLevel) {}

    override suspend fun placementTest(): List<PlacementQuestion> {
        delay(1_200) // long enough to see the flying Syllie
        return listOf(
            question("q1", "clarify", "make clear", "make dirty", "make heavy", "make late"),
            question("q2", "reluctant", "unwilling", "eager", "noisy", "careful"),
            question("q3", "ubiquitous", "found everywhere", "very rare", "very old", "hard to see"),
        )
    }

    override suspend fun submitPlacementTest(answers: List<PlacementAnswer>): PlacementResult {
        delay(1_200)
        return PlacementResult(CefrLevel.B1, correctCount = answers.count { it.optionId?.endsWith("-0") == true }, totalCount = answers.size)
    }

    override suspend fun updateSettings(settings: SettingsUpdate) {}
    override suspend fun updateScope(scope: LearningScope) {}

    override suspend fun catalog(): List<CatalogCollection> = TopicCatalog.collections.map { collection ->
        CatalogCollection(
            id = collection.id,
            slug = collection.id,
            title = collection.name,
            description = collection.summary,
            topics = collection.topics.map { CatalogTopic(it.id, it.id, it.name) },
        )
    }

    /** A learner with a backlog the server would ask about, so Home shows the review-load choice. */
    override suspend fun learnHome() = LearnHome(
        dueCount = 34, checkpointAvailable = 0, checkpointSessionsDone = 2, checkpointSessionsRequired = 5, learnedTotal = 80,
        scope = ScopeState(ScopeStatus.LOW, remainingNewMeanings = 12, pendingCustomMeanings = 0, sessionsLeftEstimate = 3),
        reviewLoad = ReviewLoad(
            backlog = 34, dueCap = 10, effectiveNewPerSession = 5, newWordReason = NewWordReason.GOAL,
            autoBalance = false, promptChoice = true,
        ),
    )

    override suspend fun acknowledgeReviewLoad(backlog: Int) {}
    override suspend fun setNewWordOverride(value: Int, mode: NewWordOverrideMode, until: Instant?) = NewWordOverride(value, mode, until)
    override suspend fun clearNewWordOverride() {}
    override suspend fun setReviewPaused(collectionId: String, paused: Boolean) {}

    override suspend fun planSession(request: PlanRequest): SessionPlan = OfflinePlans.plan(request)
    private var takes = 0

    /** The offline read-aloud is "clarify": the first take misses one syllable, the next one passes. */
    override suspend fun assessRecording(sessionId: String, taskId: String, audio: RecordedAudio): RecordingAssessment {
        delay(900)
        val take = takes++
        val passed = take % 2 == 1
        return RecordingAssessment(
            assessmentReceiptId = "offline-$take",
            validity = "valid",
            passed = passed,
            accuracyScore = if (passed) 93.0 else 68.0,
            band = if (passed) PronunciationBand.EXCELLENT else PronunciationBand.FAIR,
            syllables = listOf(
                PronunciationSyllable("cla", "klæ", 92.0, SyllableBand.GOOD),
                PronunciationSyllable("ri", "rə", if (passed) 90.0 else 41.0, if (passed) SyllableBand.GOOD else SyllableBand.POOR),
                PronunciationSyllable("fy", "faɪ", 88.0, SyllableBand.GOOD),
            ),
        )
    }
    override suspend fun commitSession(sessionId: String, body: ByteArray): CommitResponse {
        delay(800)
        return CommitResponse(sessionId, CommitSummary(newWordGoalSlots = 2, reviewedMeanings = 2, practiceTasks = 6))
    }
    override suspend fun commitStatus(sessionId: String) = CommitStatus("not_committed")

    private fun question(id: String, prompt: String, vararg options: String) = PlacementQuestion(
        id = id,
        prompt = PlacementQuestion.Prompt(prompt),
        question = "What does this word mean?",
        options = options.mapIndexed { index, text -> PlacementQuestion.Option("$id-$index", text) },
    )
}
