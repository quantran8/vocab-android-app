package com.syll.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.syll.BuildConfig
import com.syll.data.KeystoreTokenStore
import com.syll.data.SharedPreferencesKeyValueStore
import com.syll.features.auth.AuthStore
import com.syll.features.settings.AppSettingsStore
import com.syll.network.ApiClient
import com.syll.network.LiveVocabApi
import com.syll.network.VocabApi
import com.syll.features.main.LearnerStore
import com.syll.features.onboarding.OnboardingStore
import com.syll.features.placement.PlacementTestStore
import com.syll.features.practice.PracticeSessionStore
import com.syll.platform.NoVoiceRecorder
import com.syll.platform.SilentAudioClips
import com.syll.features.plan.CatalogState
import com.syll.features.plan.KeyValuePreferencesStore
import com.syll.features.plan.LearningPlanStore
import com.syll.features.plan.NoReminders
import com.syll.platform.FeedbackProviding
import com.syll.platform.SettingsAwareFeedback
import com.syll.platform.VibratorFeedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.withContext

/**
 * iOS `VocabFlowApp.init`: builds every store once, for the whole process, and wires them together.
 * Views read them through the `Local…` composition locals below (iOS `@Environment(StoreType.self)`).
 *
 * Previews and UI tests build stores with test doubles instead (in-memory storage, offline API), like the iOS
 * `#Preview` in `AppRootView`.
 */
class AppContainer(context: Context) {
    val apiClient = ApiClient(baseUrl = BuildConfig.API_URL, tokens = KeystoreTokenStore(context))
    private val storage = SharedPreferencesKeyValueStore(context)
    val settings = AppSettingsStore(storage, SettingsAwareFeedback(VibratorFeedback(context)))
    private val haptics: FeedbackProviding = settings.feedback
    /** The app's own scope for work that outlives a screen (iOS unstructured `Task`s). Main thread. */
    private val appScope = MainScope()
    val api: VocabApi = LiveVocabApi(apiClient)
    val auth = AuthStore(api)
    val plan = LearningPlanStore(KeyValuePreferencesStore(storage), NoReminders)
    val learner = LearnerStore(api, plan, language = { settings.language })
    val onboarding = OnboardingStore(plan, api, haptics)
    val placement = PlacementTestStore(api, haptics, appScope)
    // Clip playback, the recorder, on-device recognition and sound cues land with steps 4c and 5.
    val practice = PracticeSessionStore(api, SilentAudioClips, NoVoiceRecorder, haptics, appScope)

    init {
        apiClient.setSessionEndedHandler {
            withContext(Dispatchers.Main) { auth.sessionEnded() }
        }
        CatalogState.install()
        placement.onPlaced = { learner.reloadProfile() }
        // Free learners are scored on the device; read per session, since the plan can change in between.
        practice.scoringForSession = { learner.recordingScoring }
        auth.onSignedOut = {
            // Later steps add practice, custom words, challenge and ranking.
            placement.cancel()
            practice.close()
            learner.reset()
            onboarding.reset()
        }
        auth.onAccountDeleted = {
            plan.erase()
        }
    }
}

val LocalAuthStore = staticCompositionLocalOf<AuthStore> { error("AuthStore not provided") }
val LocalAppSettingsStore = staticCompositionLocalOf<AppSettingsStore> { error("AppSettingsStore not provided") }
val LocalLearningPlanStore = staticCompositionLocalOf<LearningPlanStore> { error("LearningPlanStore not provided") }
val LocalLearnerStore = staticCompositionLocalOf<LearnerStore> { error("LearnerStore not provided") }
val LocalOnboardingStore = staticCompositionLocalOf<OnboardingStore> { error("OnboardingStore not provided") }
val LocalPracticeSessionStore = staticCompositionLocalOf<PracticeSessionStore> { error("PracticeSessionStore not provided") }
val LocalPlacementTestStore = staticCompositionLocalOf<PlacementTestStore> { error("PlacementTestStore not provided") }

@Composable
fun ProvideStores(container: AppContainer, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalAuthStore provides container.auth,
        LocalAppSettingsStore provides container.settings,
        LocalLearningPlanStore provides container.plan,
        LocalLearnerStore provides container.learner,
        LocalOnboardingStore provides container.onboarding,
        LocalPlacementTestStore provides container.placement,
        LocalPracticeSessionStore provides container.practice,
        content = content,
    )
}
