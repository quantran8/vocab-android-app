package com.syll.features.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syll.app.UserMessage
import com.syll.domain.AppLanguage
import com.syll.domain.CefrLevel
import com.syll.domain.LearningPreferences
import com.syll.domain.TopicCatalog
import com.syll.features.plan.LearningPlanStore
import com.syll.network.LearningScope
import com.syll.network.PremiumPlan
import com.syll.network.SettingsUpdate
import com.syll.network.UserProfile
import com.syll.network.UserSettings
import com.syll.network.VocabApi
import com.syll.network.asApiError
import kotlinx.coroutines.CancellationException
import java.util.TimeZone

/**
 * iOS `LearnerStore`: the signed-in learner's server state. Phase 1 step 2 ports the profile, plan and Premium
 * parts; Home figures, words and the Journey arrive with Home (step 3) and My Words (step 7).
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
     * Runs whenever the main shell appears. Non-fatal: Home keeps what it has if a refresh fails.
     * Home, challenges, words and the Journey join it in later steps.
     */
    suspend fun refresh() {
        reloadProfile()
        // Topic progress ("45/120", "Finished") moves with every session.
        runCatchingApi { api.catalog() }?.let { catalog -> TopicCatalog.update(catalog.map { it.toEntry() }) }
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
        reloadProfile()
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

    // Premium (FRONTEND_FLOW §16)

    /** Null while nothing limits the learner: Premium, freemium off on the server, or an older backend. */
    val freeLimits: PremiumPlan.Limits? get() = profile?.plan?.limits
    val isPremium: Boolean get() = profile?.plan?.isPremium == true
    /**
     * Premium can be bought. Always false on Android until Play Billing lands (Phase 4): limits still apply,
     * but nothing offers an upgrade.
     */
    val offersPremium: Boolean get() = false
    val canRedeem: Boolean get() = false
    /** The most topics the learner may choose; null means no limit. */
    val maxTopics: Int? get() = freeLimits?.maxTopics

    /** The collection a limited learner is kept on: the backend answers `FREE_COLLECTION_LOCKED` to any other. */
    fun lockedCollectionId(saved: String): String? = if (freeLimits == null) null else saved

    suspend fun reloadProfile() {
        runCatchingApi { api.profile() }?.let { profile = it }
    }

    fun reset() {
        phase = Phase.Loading
        profile = null
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
}
