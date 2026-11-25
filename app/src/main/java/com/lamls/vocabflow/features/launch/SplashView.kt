package com.lamls.vocabflow.features.launch

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.rive.runtime.kotlin.core.Loop
import com.lamls.vocabflow.designsystem.components.RiveArtboard
import com.lamls.vocabflow.designsystem.motion.LocalMotion

/**
 * iOS `SplashView`: first screen on launch, Syllie blinking and nothing else. `AppRoot` keeps it up for at least
 * `MotionTokens.splashHold` and until the account has loaded, so no loading screen flashes in between.
 *
 * The `Syllie` state machine has no blink-only mood, so the `loading_blink` timeline plays on its own.
 */
@Composable
fun SplashView() {
    val motion = LocalMotion.current
    Box(
        Modifier.fillMaxSize().semantics(mergeDescendants = true) { contentDescription = "Syll" },
        contentAlignment = Alignment.Center,
    ) {
        RiveArtboard(
            artboard = "Syllie",
            animation = "loading_blink",
            autoplay = !motion.reduceMotion,
            loop = Loop.LOOP,
            // `Syllie` artboard (520×430) at the scale that makes the body as wide as the launch image;
            // the body isn't centred in the artboard, so move it onto the screen centre.
            modifier = Modifier.size(217.dp, 179.dp).offset(x = 4.5.dp, y = (-17.6).dp),
        )
    }
}
