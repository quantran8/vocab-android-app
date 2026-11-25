package com.syll.features.debug

import com.syll.app.LocalChallengeMapStore
import com.syll.app.LocalChallengeRunStore
import com.syll.app.LocalRankingStore
import com.syll.app.LocalTemplateSandboxStore
import com.syll.features.lab.LabTaskView
import com.syll.features.lab.PracticeLabView
import com.syll.app.LocalCustomWordStore
import com.syll.features.customwords.CustomWordStore
import com.syll.platform.SilentAudioClips
import com.syll.features.ranking.RankingStore
import com.syll.features.challenge.ChallengeMapStore
import com.syll.features.challenge.ChallengeRunStore
import com.syll.features.challenge.MatchMadnessGameView
import com.syll.features.main.ShellPage

import com.syll.app.LocalSpeechPlayer
import com.syll.app.LocalSoundEffects
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
fun OfflineOnboarding(onClose: () -> Unit, startOnChallengeMap: Boolean = false) {
    val scope = rememberCoroutineScope()
    val api = remember { OfflineVocabApi() }
    val auth = remember { AuthStore(api).also { it.restore() } }
    val plan = remember { LearningPlanStore(InMemoryPreferencesStore(hasCompletedOnboarding = startOnChallengeMap), NoReminders) }
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

    val challengeMap = remember { ChallengeMapStore(api) }
    val customWords = remember { CustomWordStore(api, SilentAudioClips, scope).also { it.onRoadmapChanged = { learner.refresh() } } }
    val ranking = remember { RankingStore(api).also { it.onProfileChanged = { learner.reloadProfile() } } }
    val challengeRun = remember { ChallengeRunStore(api, NoOpFeedback, scope).also { it.onSettled = { challengeMap.refresh() } } }

    val purchases = remember { com.syll.features.premium.PurchaseStore(com.syll.features.premium.NoPlayBilling, api, learner, scope) }
    val ads = remember { com.syll.features.ads.AdStore(com.syll.features.ads.NoInterstitialAds, learner, scope) }

    // The app's Lab: it never touches the backend, so it works the same here.
    val lab = LocalTemplateSandboxStore.current
    var tab by remember { mutableStateOf(MainTab.HOME) }
    var homePage by remember { mutableStateOf(if (startOnChallengeMap) ShellPage.CHALLENGE_MAP else null) }

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
        LocalChallengeMapStore provides challengeMap,
        LocalChallengeRunStore provides challengeRun,
        LocalRankingStore provides ranking,
        LocalCustomWordStore provides customWords,
        com.syll.app.LocalPurchaseStore provides purchases,
        com.syll.app.LocalAdStore provides ads,
        LocalAppScope provides scope,
        LocalSoundEffects provides container.sounds,
        LocalSpeechPlayer provides container.speech,
    ) {
        when {
            !plan.hasCompletedOnboarding -> OnboardingView()
            practice.isActive -> CompositionLocalProvider(LocalActionContext provides ActionContext.PRACTICE) { PracticeSessionView() }
            challengeRun.isActive -> CompositionLocalProvider(LocalActionContext provides ActionContext.PRACTICE) { MatchMadnessGameView() }
            lab?.isOpen == true -> if (lab.activeTemplate != null) {
                CompositionLocalProvider(LocalActionContext provides ActionContext.PRACTICE) { LabTaskView(lab) }
            } else {
                PracticeLabView(lab)
            }
            else -> MainShellView(
                tab,
                onTabChange = { tab = it },
                homePage = homePage,
                onHomePageChange = { homePage = it },
                homeActions = homeActions(practice, scope),
            )
        }
    }
}
