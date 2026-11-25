package com.syll.features.main

import com.syll.network.WordDetail
import com.syll.network.WordStats
import com.syll.network.WordProgress
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syll.app.UserMessage
import com.syll.domain.AppLanguage
import com.syll.domain.CefrLevel
import com.syll.domain.LearningPreferences
import com.syll.domain.TopicCatalog
import com.syll.domain.practice.RecordingScoring
import com.syll.features.plan.LearningPlanStore
import com.syll.network.LearnHome
import com.syll.network.LearningScope
import com.syll.network.NewWordOverrideMode
import com.syll.network.PremiumPlan
import com.syll.network.SettingsUpdate
import com.syll.network.UserProfile
import com.syll.network.UserSettings
import com.syll.network.VocabApi
import com.syll.network.asApiError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.TimeZone

/**
 * iOS `LearnerStore`: the signed-in learner's server state: profile, plan, Premium and Home figures. Words and the
 * Journey arrive with My Words (step 7); checkpoint challenges with Challenge (Phase 2).
 */
class LearnerStore(
    private val api: VocabApi,
    private val plan: LearningPlanStore,
    /** The app's interface language (iOS `AppLanguage.active`). */
    private val language: () -> AppLanguage,
    private val timeZone: () -> String = { TimeZone.getDefault().id },
) {
    sealed interface Phase {
        data object Loading : Phase
        data object Ready : Phase
        data class Failed(val message: UserMessage) : Phase
    }

    var phase by mutableStateOf<Phase>(Phase.Loading)
        private set
    var profile by mutableStateOf<UserProfile?>(null)
        private set
    var home by mutableStateOf<LearnHome?>(null)
        private set
    /** The review-load choice shows at most once per launch, whatever the server says. */
    var hasShownReviewChoice by mutableStateOf(false)

    // My Words

    /** The pages loaded so far for [wordsQuery], not the whole library. */
    var words by mutableStateOf<List<WordProgress>>(emptyList())
        private set
    var wordStats by mutableStateOf<WordStats?>(null)
        private set
    var wordsQuery by mutableStateOf("")
        private set
    var hasMoreWords by mutableStateOf(false)
        private set
    var isSearchingWords by mutableStateOf(false)
        private set
    var isLoadingMoreWords by mutableStateOf(false)
        private set
    var wordsError by mutableStateOf<UserMessage?>(null)
        private set
    /** Bumped by every new load, so an answer for an older query or a reset is ignored. */
    private var wordsGeneration = 0
        private set

    /**
     * Runs after sign-in. `GET /me` decides between onboarding and Home, so the route stays on the loading screen
     * until it answers.
     */
    suspend fun load() {
        phase = Phase.Loading
        try {
            val profile = api.profile()
            this.profile = profile
            // Read before `adopt`, which stores the defaults when this device has no plan yet.
            val localWeeklyGoal = plan.preferences?.weeklySessionGoal
            syncDeviceSettings(profile.settings, localWeeklyGoal)
            val scope = profile.scope
            if (scope != null && profile.hasFinishedOnboarding) {
                TopicCatalog.update(api.catalog().map { it.toEntry() })
                plan.adopt(
                    collectionId = scope.collectionId,
                    topicIds = scope.topicIds,
                    wordsPerDay = profile.settings.dailyNewWordGoal,
                    reviewsPerSession = profile.settings.reviewsPerSession,
                    dailySpeaking = profile.settings.dailySpeaking ?: false,
                )
            } else {
                plan.resetOnboarding()
            }
            phase = Phase.Ready
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            phase = Phase.Failed(UserMessage.Api(e.asApiError))
        }
    }

    /**
     * Runs whenever the main shell appears, so also after onboarding and every session. Non-fatal: Home keeps what it
     * has if a refresh fails. The profile is included because onboarding saves the level and scope after [load].
     */
    suspend fun refresh() = coroutineScope {
        val profile = async { reloadProfile() }
        val home = async { runCatchingApi { api.learnHome() } }
        // Topic progress ("45/120", "Finished") moves with every session.
        val catalog = async { runCatchingApi { api.catalog() } }
        val stats = async { runCatchingApi { api.wordStats() } }
        val library = async { loadWords(wordsQuery) }
        home.await()?.let { this@LearnerStore.home = it }
        stats.await()?.let { wordStats = it }
        library.await()
        catalog.await()?.let { list -> TopicCatalog.update(list.map { it.toEntry() }) }
        profile.await()
    }

    /** Scope and goal go to the server first, so a failed save leaves the local plan unchanged. */
    suspend fun savePlan(preferences: LearningPreferences) {
        api.updateScope(LearningScope(preferences.collectionId, preferences.topicIds.toList()))
        val current = profile?.settings
        var update = SettingsUpdate(
            dailyNewWordGoal = preferences.wordsPerDay,
            weeklySessionGoal = preferences.weeklySessionGoal,
        )
        // Sent only when changed, so a backend without the field never sees it.
        if (preferences.reviewsPerSession != current?.reviewsPerSession) {
            update = update.copy(sendsReviewsPerSession = true, reviewsPerSession = preferences.reviewsPerSession)
        }
        if (preferences.dailySpeaking != (current?.dailySpeaking ?: false)) {
            update = update.copy(dailySpeaking = preferences.dailySpeaking)
        }
        api.updateSettings(update)
        plan.save(preferences)
        // Home's review count (`dueCap`) follows both numbers.
        reloadProfile()
        refreshHome()
    }

    /** The level goes to the server first; the profile is re-read so Profile shows what the server kept. */
    suspend fun updateLevel(level: CefrLevel) {
        api.updateLevels(level)
        reloadProfile()
    }

    /** Saved straight from the Profile switch; applies from the next planned session. */
    suspend fun setDailySpeaking(enabled: Boolean) {
        api.updateSettings(SettingsUpdate(dailySpeaking = enabled))
        plan.save(plan.current.copy(dailySpeaking = enabled))
        reloadProfile()
    }

    /**
     * The server holds the catalog's language, and scheduled reminders hold their text. A failed save is retried
     * by the next [load], which compares `uiLocale` with the server's.
     */
    suspend fun languageDidChange() {
        plan.rescheduleReminders()
        runCatchingApi { api.updateSettings(SettingsUpdate(uiLocale = language().uiLocale)) }
        refresh()
    }

    /** Whether Home should offer the review-load choice now. The caller also keeps it out of a session. */
    val shouldOfferReviewChoice: Boolean
        get() = !hasShownReviewChoice && home?.reviewLoad?.promptChoice == true

    /** Any choice, including dismissing, counts as seen here and on the learner's other devices. */
    suspend fun acknowledgeReviewChoice() {
        hasShownReviewChoice = true
        val backlog = home?.reviewLoad?.backlog ?: return
        runCatchingApi { api.acknowledgeReviewLoad(backlog) }
        refreshHome()
    }

    /** Lowers new words for now. [NewWordOverrideMode.UNTIL_DATE] ends with the learner's Sunday. */
    suspend fun setNewWordOverride(value: Int, mode: NewWordOverrideMode) {
        api.setNewWordOverride(value, mode)
        refreshHome()
    }

    suspend fun clearNewWordOverride() {
        api.clearNewWordOverride()
        refreshHome()
    }

    suspend fun setAutoBalance(enabled: Boolean) {
        api.updateSettings(SettingsUpdate(autoBalanceNewWords = enabled))
        coroutineScope {
            launch { reloadProfile() }
            launch { refreshHome() }
        }
    }

    /** The active collection is always reviewed; the server rejects pausing it. */
    suspend fun setReviewPaused(paused: Boolean, collectionId: String) {
        api.setReviewPaused(collectionId, paused)
        coroutineScope {
            launch { reloadProfile() }
            launch { refreshHome() }
        }
    }

    fun isReviewPaused(collectionId: String): Boolean = profile?.reviewPauses?.any { it.collectionId == collectionId } ?: false

    // Premium (FRONTEND_FLOW §16)

    /** Null while nothing limits the learner: Premium, freemium off on the server, or an older backend. */
    val freeLimits: PremiumPlan.Limits? get() = profile?.plan?.limits
    val isPremium: Boolean get() = profile?.plan?.isPremium == true
    /** Premium can be bought: the server runs freemium, or the learner already has it (to manage it). */
    val offersPremium: Boolean get() = profile?.features?.freemiumEnabled == true || isPremium
    val canRedeem: Boolean get() = profile?.features?.redeemEnabled == true
    /**
     * iOS `accountToken`: the Play purchase's `obfuscatedAccountId`, so the backend's Play notifications find this
     * account.
     */
    val accountId: String? get() = profile?.id

    /** Applies the plan a purchase, restore or redeem answered with, then re-reads what it unlocks. */
    suspend fun adoptPlan(premium: PremiumPlan) {
        profile = profile?.copy(plan = premium)
        coroutineScope {
            launch { reloadProfile() }
            launch { refreshHome() }
        }
    }

    suspend fun redeem(code: String) {
        adoptPlan(api.redeem(code))
    }
    /** The most topics the learner may choose; null means no limit. */
    val maxTopics: Int? get() = freeLimits?.maxTopics

    /** Free learners are scored on the device; Premium by the backend. */
    val recordingScoring: RecordingScoring get() = if (freeLimits == null) RecordingScoring.BACKEND else RecordingScoring.ON_DEVICE_ONLY

    /** The collection a limited learner is kept on: the backend answers `FREE_COLLECTION_LOCKED` to any other. */
    fun lockedCollectionId(saved: String): String? = if (freeLimits == null) null else saved

    private suspend fun refreshHome() {
        runCatchingApi { api.learnHome() }?.let { home = it }
    }

    suspend fun reloadProfile() {
        runCatchingApi { api.profile() }?.let { profile = it }
    }

    /**
     * Replaces the list with the first page for [query]. A failed reload keeps what is on screen, but a failed new
     * search clears it: those rows don't answer the new query.
     */
    suspend fun loadWords(query: String) {
        val generation = ++wordsGeneration
        val isNewQuery = query != wordsQuery
        wordsQuery = query
        isSearchingWords = true
        isLoadingMoreWords = false
        wordsError = null
        try {
            val page = api.words(WORDS_PAGE_SIZE, 0, query)
            if (generation != wordsGeneration) return
            words = page
            hasMoreWords = page.size == WORDS_PAGE_SIZE
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (generation != wordsGeneration) return
            if (isNewQuery) {
                words = emptyList()
                hasMoreWords = false
            }
            wordsError = UserMessage.Api(e.asApiError)
        } finally {
            if (generation == wordsGeneration) isSearchingWords = false
        }
    }

    /** Appends the next page, if there is one and nothing is loading already. */
    suspend fun loadMoreWords() {
        if (!hasMoreWords || isLoadingMoreWords || isSearchingWords) return
        val generation = wordsGeneration
        isLoadingMoreWords = true
        wordsError = null
        try {
            val page = api.words(WORDS_PAGE_SIZE, words.size, wordsQuery)
            if (generation != wordsGeneration) return
            // Learning a word between pages shifts the offsets, so a word can come back twice.
            val known = words.map { it.id }.toSet()
            words = words + page.filter { it.id !in known }
            hasMoreWords = page.size == WORDS_PAGE_SIZE
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (generation != wordsGeneration) return
            wordsError = UserMessage.Api(e.asApiError)
        } finally {
            if (generation == wordsGeneration) isLoadingMoreWords = false
        }
    }

    /** Skill states are only read when a word is opened. */
    suspend fun wordDetail(id: String): WordDetail = api.wordDetail(id)

    fun reset() {
        phase = Phase.Loading
        profile = null
        home = null
        hasShownReviewChoice = false
        wordsGeneration += 1
        words = emptyList()
        wordStats = null
        wordsQuery = ""
        hasMoreWords = false
        isSearchingWords = false
        isLoadingMoreWords = false
        wordsError = null
    }

    /**
     * `timeZone` defines the learner's "day" for the daily goal; `uiLocale` localizes the catalog. The learning
     * days only live on the device, so their count goes up here too. A device without a saved plan sends nothing.
     */
    private suspend fun syncDeviceSettings(settings: UserSettings, weeklySessionGoal: Int?) {
        val timeZone = timeZone()
        val uiLocale = language().uiLocale
        val goal = weeklySessionGoal?.takeIf { settings.weeklySessionGoal != it }
        if (settings.timeZone == timeZone && settings.uiLocale == uiLocale && goal == null) return
        runCatchingApi { api.updateSettings(SettingsUpdate(uiLocale = uiLocale, timeZone = timeZone, weeklySessionGoal = goal)) }
    }

    /** iOS `try?`: a failure is dropped, cancellation still propagates. */
    private suspend fun <T> runCatchingApi(work: suspend () -> T): T? = try {
        work()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    companion object {
        const val WORDS_PAGE_SIZE = 50
    }
}
