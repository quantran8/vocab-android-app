package com.syll.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.syll.R
import com.syll.designsystem.components.PrimaryButton
import com.syll.designsystem.components.StatusScreen
import com.syll.designsystem.motion.LocalMotion
import com.syll.designsystem.motion.Motion
import com.syll.designsystem.motion.MotionTokens
import com.syll.designsystem.motion.rememberEnterOffsetPx
import com.syll.designsystem.motion.rememberSystemReducesMotion
import com.syll.designsystem.tokens.SyllColors
import com.syll.features.auth.AuthStore
import com.syll.features.auth.AuthView
import com.syll.features.main.LearnerStore
import com.syll.features.main.MainShellView
import com.syll.features.main.MainTab
import com.syll.features.main.ShellPage
import com.syll.features.challenge.MatchMadnessGameView
import com.syll.features.lab.LabTaskView
import com.syll.features.lab.PracticeLabView
import com.syll.features.home.HomeActions
import com.syll.features.practice.PracticeSessionStore
import com.syll.features.practice.PracticeSessionView
import com.syll.domain.practice.SessionMode
import com.syll.designsystem.components.ActionContext
import com.syll.designsystem.components.LocalActionContext
import kotlinx.coroutines.CoroutineScope
import com.syll.features.onboarding.OnboardingView
import com.syll.designsystem.components.SecondaryButton
import com.syll.features.debug.DesignGallery
import com.syll.features.debug.OfflineOnboarding
import com.syll.features.launch.SplashView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * iOS `RouteID`: one identity per whole screen. There is no navigation stack at the root: the route is derived
 * from store state, and changing a screen means changing store state.
 *
 */
private sealed interface RouteID {
    data object Splash : RouteID
    data object Loading : RouteID
    data object Auth : RouteID
    /** iOS shows the failure inside `.loading`'s identity; a separate id here keeps the swap animated the same. */
    data object LoadFailed : RouteID
    data object Onboarding : RouteID
    data object Shell : RouteID
    /** One identity per session, so screens never carry over between sessions. */
    data class Practice(val token: java.util.UUID) : RouteID
    /** One identity per run (iOS `.challenge(token:)`); the Practice Lab's Match Madness rounds run here too. */
    data class Challenge(val token: String) : RouteID
    /** The Practice Lab's list, or one of its templates. */
    data class Lab(val template: com.syll.domain.practice.TaskTemplate?) : RouteID
    /** Debug builds only: tokens, buttons, Rive and Lottie on the device. */
    data object DesignGallery : RouteID
    /** Debug builds only: onboarding on the offline API. */
    data object OfflineOnboarding : RouteID
}

/** iOS `AppRootView`. */
@Composable
fun AppRoot() {
    val auth = LocalAuthStore.current
    val settings = LocalAppSettingsStore.current
    val learner = LocalLearnerStore.current
    val plan = LocalLearningPlanStore.current
    val practice = LocalPracticeSessionStore.current
    val challengeRun = LocalChallengeRunStore.current
    val lab = LocalTemplateSandboxStore.current
    val appScope = LocalAppScope.current
    val motion = Motion(reduceMotion = rememberSystemReducesMotion() || settings.reduceMotion)
    val enterOffsetPx = rememberEnterOffsetPx()

    // Set once the splash has been up for `splashHold`.
    var splashHeld by remember { mutableStateOf(false) }
    // Set once the splash is gone; it doesn't come back for the rest of the launch (a language change recreates
    // the activity, so this survives it).
    var launched by rememberSaveable { mutableStateOf(false) }
    var showsGallery by rememberSaveable { mutableStateOf(false) }
    var showsOfflineOnboarding by rememberSaveable { mutableStateOf(false) }
    var offlineStartsOnChallenge by rememberSaveable { mutableStateOf(false) }
    // Owned here: the route swap unmounts the shell during a session, and closing one returns to this tab.
    var shellTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    // Owned here too: a run started from the Challenge Map comes back to it.
    var homePage by rememberSaveable { mutableStateOf<ShellPage?>(null) }
    // The next account starts on Home.
    LaunchedEffect(auth.phase) {
        if (auth.phase == AuthStore.Phase.SIGNED_OUT) {
            shellTab = MainTab.HOME
            homePage = null
        }
    }

    LaunchedEffect(Unit) {
        if (auth.phase == AuthStore.Phase.LAUNCHING) auth.restore()
    }
    LaunchedEffect(Unit) {
        delay((MotionTokens.splashHold * 1000).toLong())
        splashHeld = true
    }
    val canLoadLearner = auth.phase == AuthStore.Phase.SIGNED_IN
    LaunchedEffect(canLoadLearner) {
        if (canLoadLearner) learner.load()
    }
    // Once the account is known: a purchase made signed out or on another device reaches it.
    val purchases = LocalPurchaseStore.current
    val ads = LocalAdStore.current
    LaunchedEffect(learner.profile?.id) {
        if (learner.profile != null) {
            purchases.syncEntitlements()
            ads.prepare()
        }
    }
    // The account is still loading, which would otherwise show the loading screen.
    val isLaunchLoading = auth.phase == AuthStore.Phase.LAUNCHING ||
        (canLoadLearner && learner.phase == LearnerStore.Phase.Loading)
    LaunchedEffect(splashHeld, isLaunchLoading) {
        if (!launched && splashHeld && !isLaunchLoading) launched = true
    }

    val route: RouteID = when {
        !launched -> RouteID.Splash
        showsOfflineOnboarding -> RouteID.OfflineOnboarding
        showsGallery -> RouteID.DesignGallery
        auth.phase == AuthStore.Phase.LAUNCHING -> RouteID.Loading
        auth.phase == AuthStore.Phase.SIGNED_OUT -> RouteID.Auth
        learner.phase is LearnerStore.Phase.Failed -> RouteID.LoadFailed
        learner.phase == LearnerStore.Phase.Loading -> RouteID.Loading
        !plan.hasCompletedOnboarding -> RouteID.Onboarding
        practice.isActive -> RouteID.Practice(practice.sessionToken)
        // Before the Lab: the Lab's Match Madness rounds run here too, and closing one returns to the Lab.
        challengeRun.isActive -> RouteID.Challenge(challengeRun.runToken)
        lab?.isOpen == true -> RouteID.Lab(lab.activeTemplate)
        else -> RouteID.Shell
    }

    CompositionLocalProvider(LocalMotion provides motion) {
        Box(Modifier.fillMaxSize().background(SyllColors.canvas)) {
            AnimatedContent(
                targetState = route,
                transitionSpec = { motion.routeTransition(enterOffsetPx) },
                label = "route",
            ) { current ->
                when (current) {
                    RouteID.Splash -> SplashView()
                    RouteID.Loading -> StatusScreen(title = stringResource(R.string.syll), isBusy = true, modifier = Modifier.safeDrawingPadding())
                    RouteID.Auth -> AuthView(onOpenGallery = { showsGallery = true })
                    RouteID.LoadFailed -> LoadFailedScreen()
                    RouteID.Onboarding -> OnboardingView()
                    // Practice, Random practice and Focus are wired as they land (steps 4 and 7).
                    // Random practice and Focus are wired as they land (step 7).
                    RouteID.Shell -> MainShellView(
                        shellTab,
                        onTabChange = { shellTab = it },
                        homePage = homePage,
                        onHomePageChange = { homePage = it },
                        homeActions = homeActions(practice, appScope),
                    )
                    is RouteID.Practice -> CompositionLocalProvider(LocalActionContext provides ActionContext.PRACTICE) {
                        PracticeSessionView()
                    }
                    is RouteID.Challenge -> CompositionLocalProvider(LocalActionContext provides ActionContext.PRACTICE) {
                        MatchMadnessGameView()
                    }
                    is RouteID.Lab -> if (lab != null) {
                        if (current.template != null) {
                            CompositionLocalProvider(LocalActionContext provides ActionContext.PRACTICE) { LabTaskView(lab) }
                        } else {
                            PracticeLabView(lab)
                        }
                    }
                    RouteID.DesignGallery -> DesignGallery(
                        onClose = { showsGallery = false },
                        onOpenOfflineOnboarding = {
                            offlineStartsOnChallenge = false
                            showsOfflineOnboarding = true
                        },
                        onOpenOfflineChallenge = {
                            offlineStartsOnChallenge = true
                            showsOfflineOnboarding = true
                        },
                    )
                    RouteID.OfflineOnboarding -> OfflineOnboarding(
                        onClose = { showsOfflineOnboarding = false },
                        startOnChallengeMap = offlineStartsOnChallenge,
                    )
                }
            }
        }
    }
}

/** Home's session buttons, started on the app scope: the session replaces Home as soon as it starts planning. */
fun homeActions(practice: PracticeSessionStore, scope: CoroutineScope) = HomeActions(
    onStartDaily = { scope.launch { practice.start(SessionMode.DAILY) } },
    onStartReviewOnly = { scope.launch { practice.startReviewOnly() } },
    isPracticeActive = { practice.isActive },
)

/** "Couldn't load your account" with retry and log out (iOS `learner.phase == .failed`). */
@Composable
private fun LoadFailedScreen() {
    val auth = LocalAuthStore.current
    val learner = LocalLearnerStore.current
    val scope = rememberCoroutineScope()
    val message = (learner.phase as? LearnerStore.Phase.Failed)?.message
    StatusScreen(
        title = stringResource(R.string.couldn_t_load_your_account),
        message = message?.text(),
        modifier = Modifier.safeDrawingPadding(),
    ) {
        PrimaryButton(stringResource(R.string.try_again_2), onClick = { scope.launch { learner.load() } })
        SecondaryButton(stringResource(R.string.log_out), onClick = { scope.launch { auth.signOut() } })
    }
}

