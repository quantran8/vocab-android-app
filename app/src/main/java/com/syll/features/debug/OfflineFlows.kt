package com.syll.features.debug

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import com.syll.app.LocalAuthStore
import com.syll.app.LocalLearnerStore
import com.syll.app.LocalLearningPlanStore
import com.syll.app.LocalOnboardingStore
import com.syll.app.LocalPlacementTestStore
import com.syll.app.OfflineVocabApi
import com.syll.domain.AppLanguage
import com.syll.features.auth.AuthStore
import com.syll.app.LocalAppScope
import com.syll.app.LocalPracticeSessionStore
import com.syll.app.homeActions
import com.syll.designsystem.components.ActionContext
import com.syll.designsystem.components.LocalActionContext
import com.syll.features.practice.PracticeSessionStore
import com.syll.features.practice.PracticeSessionView
import com.syll.platform.VoiceRecorder
import com.syll.platform.AudioClipPlayer
import com.syll.VocabFlowApplication
import androidx.compose.ui.platform.LocalContext
import com.syll.features.main.LearnerStore
import com.syll.features.main.MainShellView
import com.syll.features.main.MainTab
import com.syll.features.onboarding.OnboardingStore
import com.syll.features.onboarding.OnboardingView
import com.syll.features.placement.PlacementTestStore
import com.syll.features.plan.InMemoryPreferencesStore
import com.syll.features.plan.LearningPlanStore
import com.syll.features.plan.NoReminders
import com.syll.platform.NoOpFeedback

/**
 * Debug only: the signed-in app with its own stores on [OfflineVocabApi] and in-memory storage, so onboarding (with
 * the placement test) and then Home can be walked on a device without a backend or an account. Log out closes it.
 */
@Composable
fun OfflineOnboarding(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val api = remember { OfflineVocabApi() }
    val auth = remember { AuthStore(api).also { it.restore() } }
    val plan = remember { LearningPlanStore(InMemoryPreferencesStore(), NoReminders) }
    val learner = remember { LearnerStore(api, plan, language = { AppLanguage.ENGLISH }) }
    val onboarding = remember { OnboardingStore(plan, api, NoOpFeedback) }
    val placement = remember { PlacementTestStore(api, NoOpFeedback, scope) }
    // Real audio, recorder and cues (as iOS's offline preview), so the read-aloud can be tried on a device.
    val context = LocalContext.current.applicationContext
    val container = (context as VocabFlowApplication).container
    val practice = remember {
        PracticeSessionStore(
            api, AudioClipPlayer(context), VoiceRecorder(container.microphone), NoOpFeedback, scope, sounds = container.sounds,
        )
    }

    var tab by remember { mutableStateOf(MainTab.HOME) }

    LaunchedEffect(auth.phase) {
        if (auth.phase == AuthStore.Phase.SIGNED_OUT) onClose()
    }
    CompositionLocalProvider(
        LocalAuthStore provides auth,
        LocalLearningPlanStore provides plan,
        LocalLearnerStore provides learner,
        LocalOnboardingStore provides onboarding,
        LocalPlacementTestStore provides placement,
        LocalPracticeSessionStore provides practice,
        LocalAppScope provides scope,
    ) {
        when {
            !plan.hasCompletedOnboarding -> OnboardingView()
            practice.isActive -> CompositionLocalProvider(LocalActionContext provides ActionContext.PRACTICE) { PracticeSessionView() }
            else -> MainShellView(tab, onTabChange = { tab = it }, homeActions = homeActions(practice, scope))
        }
    }
}
