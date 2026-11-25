package com.syll.features.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syll.app.UserMessage
import com.syll.domain.CefrLevel
import com.syll.domain.TopicCatalog
import com.syll.features.plan.LearningPlanStore
import com.syll.network.ApiError
import com.syll.network.LearningScope
import com.syll.network.PlacementResult
import com.syll.network.SettingsUpdate
import com.syll.network.VocabApi
import com.syll.network.asApiError
import com.syll.platform.FeedbackProviding
import kotlinx.coroutines.CancellationException

/** iOS `OnboardingStore`. */
class OnboardingStore(
    private val plan: LearningPlanStore,
    private val api: VocabApi,
    val haptics: FeedbackProviding,
) {
    enum class Step { WELCOME, LEVEL, PLACEMENT, COLLECTION, TOPICS, ROUTINE, READY }

    var step by mutableStateOf(Step.WELCOME)
        private set
    /** Which way the last step change went, so the slide can follow it. */
    var isMovingBack = false
        private set
    /** Resumes from the last saved choices if the learner quit before finishing. */
    var draft by mutableStateOf(plan.current)
    var level by mutableStateOf<CefrLevel?>(null)
        private set
    /** What the server holds, so a level the placement test already saved isn't sent again. */
    private var savedLevel: CefrLevel? = null
    /** A request is in flight; the step's action stays disabled until it answers. */
    var isSaving by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<UserMessage?>(null)
        private set

    val canContinueFromCollection: Boolean get() = draft.collection != null
    val canContinueFromTopics: Boolean get() = draft.topicIds.isNotEmpty()

    private fun go(next: Step) {
        isMovingBack = next.ordinal < step.ordinal
        step = next
    }

    // Navigation

    fun begin() = go(Step.LEVEL)

    /** A new account may be finishing onboarding that a previous account left half done. */
    fun reset() {
        go(Step.WELCOME)
        level = null
        savedLevel = null
        errorMessage = null
        draft = plan.current
    }

    fun back() {
        // The placement test is a detour off the level step, not a stop on the way back.
        if (step == Step.COLLECTION) {
            go(Step.LEVEL)
            return
        }
        Step.entries.getOrNull(step.ordinal - 1)?.let(::go)
    }

    fun openPlacementTest() {
        if (isSaving) return
        errorMessage = null
        go(Step.PLACEMENT)
    }

    /** The test saved the level already, so this only has the catalog left to load. */
    suspend fun acceptPlacement(result: PlacementResult) {
        adoptPlacement(result)
        continueToCollection()
    }

    /** Back to the level list: the test had no content or failed, or the learner disagrees with its result. */
    fun chooseLevelByHand(after: PlacementResult?) {
        after?.let(::adoptPlacement)
        errorMessage = null
        go(Step.LEVEL)
    }

    private fun adoptPlacement(result: PlacementResult) {
        level = result.proficiencyLevel
        savedLevel = result.proficiencyLevel
    }

    /** Saves the level, then loads the catalog, which needs the account the level was saved on. */
    suspend fun continueToCollection() {
        val level = level ?: return
        save {
            if (level != savedLevel) {
                api.updateLevels(level)
                savedLevel = level
            }
            val catalog = api.catalog()
            if (catalog.isEmpty()) throw ApiError.InvalidResponse
            TopicCatalog.update(catalog.map { it.toEntry() })
            if (draft.collection == null) {
                TopicCatalog.collections.firstOrNull()?.let { first ->
                    draft = draft.copy(collectionId = first.id, topicIds = emptySet())
                }
            }
            go(Step.COLLECTION)
        }
    }

    fun continueToTopics() {
        if (canContinueFromCollection) go(Step.TOPICS)
    }

    fun continueToRoutine() {
        if (canContinueFromTopics) go(Step.ROUTINE)
    }

    /** Sends the scope and daily goal, saves the choices and schedules reminders, then shows the summary. */
    suspend fun createFlow() {
        val draft = draft
        if (!draft.isComplete) return
        save {
            api.updateScope(LearningScope(draft.collectionId, draft.topicIds.toList()))
            var settings = SettingsUpdate(dailyNewWordGoal = draft.wordsPerDay, weeklySessionGoal = draft.weeklySessionGoal)
            // Left out while it follows the new-word goal, so a backend without the field never sees it.
            draft.reviewsPerSession?.let { settings = settings.copy(sendsReviewsPerSession = true, reviewsPerSession = it) }
            // Off is the backend's default, so it is only sent once the learner opts in.
            if (draft.dailySpeaking) settings = settings.copy(dailySpeaking = true)
            api.updateSettings(settings)
            plan.save(draft)
            haptics.success()
            go(Step.READY)
        }
    }

    fun finish() {
        plan.completeOnboarding()
    }

    private suspend fun save(work: suspend () -> Unit) {
        if (isSaving) return
        isSaving = true
        errorMessage = null
        try {
            work()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorMessage = UserMessage.Api(e.asApiError)
        } finally {
            isSaving = false
        }
    }

    // Intents

    fun selectLevel(level: CefrLevel) {
        if (level == this.level) return
        this.level = level
        haptics.selectionChanged()
    }

    fun selectCollection(id: String) {
        if (id == draft.collectionId) return
        draft = draft.selectCollection(id)
        haptics.selectionChanged()
    }

    /** False when the free plan's [limit] stopped the topic being added. */
    fun toggleTopic(id: String, limit: Int? = null): Boolean {
        val next = draft.toggleTopic(id, limit) ?: return false
        draft = next
        haptics.selectionChanged()
        return true
    }

    fun toggleAllTopics(limit: Int? = null) {
        draft = draft.toggleAllTopics(limit)
        haptics.selectionChanged()
    }

    fun toggleDay(weekday: Int) {
        draft = draft.toggleDay(weekday)
        haptics.selectionChanged()
    }

    fun toggleEveryDay() {
        draft = draft.toggleEveryDay()
        haptics.selectionChanged()
    }

    fun adjustWordsPerDay(amount: Int) {
        val before = draft.wordsPerDay
        draft = draft.adjustWordsPerDay(amount)
        if (draft.wordsPerDay != before) haptics.selectionChanged()
    }
}
