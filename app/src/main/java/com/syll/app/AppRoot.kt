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
import com.syll.features.home.HomeActions
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
 * Later steps add practice(token), challenge(token), lab(template) and the main shell.
 */
private sealed interface RouteID {
    data object Splash : RouteID
    data object Loading : RouteID
    data object Auth : RouteID
    /** iOS shows the failure inside `.loading`'s identity; a separate id here keeps the swap animated the same. */
    data object LoadFailed : RouteID
    data object Onboarding : RouteID
    data object Shell : RouteID
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
    val motion = Motion(reduceMotion = rememberSystemReducesMotion() || settings.reduceMotion)
    val enterOffsetPx = rememberEnterOffsetPx()

    // Set once the splash has been up for `splashHold`.
    var splashHeld by remember { mutableStateOf(false) }
    // Set once the splash is gone; it doesn't come back for the rest of the launch (a language change recreates
    // the activity, so this survives it).
    var launched by rememberSaveable { mutableStateOf(false) }
    var showsGallery by rememberSaveable { mutableStateOf(false) }
    var showsOfflineOnboarding by rememberSaveable { mutableStateOf(false) }
    // Owned here: the route swap unmounts the shell during a session, and closing one returns to this tab.
    var shellTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    // The next account starts on Home.
    LaunchedEffect(auth.phase) {
        if (auth.phase == AuthStore.Phase.SIGNED_OUT) shellTab = MainTab.HOME
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
                    RouteID.Shell -> MainShellView(shellTab, onTabChange = { shellTab = it }, homeActions = HomeActions())
                    RouteID.DesignGallery -> DesignGallery(
                        onClose = { showsGallery = false },
                        onOpenOfflineOnboarding = { showsOfflineOnboarding = true },
                    )
                    RouteID.OfflineOnboarding -> OfflineOnboarding(onClose = { showsOfflineOnboarding = false })
                }
            }
        }
    }
}

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

