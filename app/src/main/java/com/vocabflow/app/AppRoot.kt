package com.vocabflow.app

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
import com.vocabflow.BuildConfig
import com.vocabflow.R
import com.vocabflow.designsystem.components.PrimaryButton
import com.vocabflow.designsystem.components.SecondaryButton
import com.vocabflow.designsystem.components.StatusScreen
import com.vocabflow.designsystem.motion.LocalMotion
import com.vocabflow.designsystem.motion.Motion
import com.vocabflow.designsystem.motion.MotionTokens
import com.vocabflow.designsystem.motion.rememberEnterOffsetPx
import com.vocabflow.designsystem.motion.rememberSystemReducesMotion
import com.vocabflow.designsystem.tokens.SyllColors
import com.vocabflow.features.auth.AuthStore
import com.vocabflow.features.debug.DesignGallery
import com.vocabflow.features.launch.SplashView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * iOS `RouteID`: one identity per whole screen. There is no navigation stack at the root: the route is derived
 * from store state, and changing a screen means changing store state.
 *
 * Phase 1 adds onboarding, practice(token), challenge(token), lab(template) and the main shell.
 */
private sealed interface RouteID {
    data object Splash : RouteID
    data object Loading : RouteID
    data object Auth : RouteID
    data object Shell : RouteID
    /** Debug builds only: tokens, buttons, Rive and Lottie on the device. */
    data object DesignGallery : RouteID
}

/** iOS `AppRootView`. */
@Composable
fun AppRoot() {
    val auth = LocalAuthStore.current
    val settings = LocalAppSettingsStore.current
    val motion = Motion(reduceMotion = rememberSystemReducesMotion() || settings.reduceMotion)
    val enterOffsetPx = rememberEnterOffsetPx()

    // Set once the splash has been up for `splashHold`.
    var splashHeld by remember { mutableStateOf(false) }
    // Set once the splash is gone; it doesn't come back for the rest of the launch (a language change recreates
    // the activity, so this survives it).
    var launched by rememberSaveable { mutableStateOf(false) }
    var showsGallery by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (auth.phase == AuthStore.Phase.LAUNCHING) auth.restore()
    }
    LaunchedEffect(Unit) {
        delay((MotionTokens.splashHold * 1000).toLong())
        splashHeld = true
    }
    val isLaunchLoading = auth.phase == AuthStore.Phase.LAUNCHING
    LaunchedEffect(splashHeld, isLaunchLoading) {
        if (!launched && splashHeld && !isLaunchLoading) launched = true
    }

    val route: RouteID = when {
        !launched -> RouteID.Splash
        showsGallery -> RouteID.DesignGallery
        auth.phase == AuthStore.Phase.LAUNCHING -> RouteID.Loading
        auth.phase == AuthStore.Phase.SIGNED_OUT -> RouteID.Auth
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
                    RouteID.Auth -> AuthPlaceholder(onOpenGallery = { showsGallery = true })
                    RouteID.Shell -> ShellPlaceholder()
                    RouteID.DesignGallery -> DesignGallery(onClose = { showsGallery = false })
                }
            }
        }
    }
}

/** Phase 0 stand-in for `AuthView` (Phase 1). */
@Composable
private fun AuthPlaceholder(onOpenGallery: () -> Unit) {
    StatusScreen(title = stringResource(R.string.syll), modifier = Modifier.safeDrawingPadding()) {
        if (BuildConfig.DEBUG) {
            SecondaryButton(title = stringResource(R.string.debug_design_gallery), onClick = onOpenGallery)
        }
    }
}

/** Phase 0 stand-in for `MainShellView` (Phase 1). */
@Composable
private fun ShellPlaceholder() {
    val auth = LocalAuthStore.current
    val scope = rememberCoroutineScope()
    StatusScreen(title = stringResource(R.string.syll), modifier = Modifier.safeDrawingPadding()) {
        PrimaryButton(title = stringResource(R.string.log_out), onClick = { scope.launch { auth.signOut() } })
    }
}
