package com.lamls.vocabflow.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Fit
import app.rive.runtime.kotlin.core.Loop
import com.lamls.vocabflow.R

/**
 * One artboard of `res/raw/syllie.riv` (the same file as iOS `Resources/Animations/syllie.riv`; keep artboard,
 * timeline, state machine and view model names identical to iOS).
 *
 * Plays either a timeline ([animation]) or a state machine ([stateMachine]). [onReady] hands back the view for
 * imperative control (play another timeline, fire a trigger), like iOS `RiveViewModel`.
 * Phase 1 adds view-model data binding (`Syllie.mood`, `CelebrateV2.variant`, `HomeSyllie.scene`).
 */
@Composable
fun RiveArtboard(
    artboard: String,
    modifier: Modifier = Modifier,
    animation: String? = null,
    stateMachine: String? = null,
    autoplay: Boolean = true,
    fit: Fit = Fit.CONTAIN,
    loop: Loop = Loop.AUTO,
    onReady: (RiveAnimationView) -> Unit = {},
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            RiveAnimationView(context).apply {
                setRiveResource(
                    R.raw.syllie,
                    artboardName = artboard,
                    animationName = animation,
                    stateMachineName = stateMachine,
                    autoplay = autoplay,
                    fit = fit,
                    loop = loop,
                )
                onReady(this)
            }
        },
    )
}
