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
import com.syll.features.debug.DesignGallery
import com.syll.features.launch.SplashView
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
                    RouteID.Auth -> AuthView(onOpenGallery = { showsGallery = true })
                    RouteID.Shell -> ShellPlaceholder()
                    RouteID.DesignGallery -> DesignGallery(onClose = { showsGallery = false })
                }
            }
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
