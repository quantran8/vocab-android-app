package com.syll.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Fit
import app.rive.runtime.kotlin.core.Loop
import android.util.Log
import com.syll.BuildConfig
import com.syll.R

/**
 * One artboard of `res/raw/syllie.riv` (the same file as iOS `Resources/Animations/syllie.riv`; keep artboard,
 * timeline and state machine names identical to iOS). No view models (deprecated in Rive).
 *
 * Plays either a timeline ([animation]) or a state machine ([stateMachine]). [onReady] hands back the view for
 * imperative control (play another timeline, set a state machine input).
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
                if (BuildConfig.DEBUG) {
                    runCatching {
                        val board = controller.activeArtboard
                        Log.d("RiveArtboard", "$artboard: timelines=${board?.animationNames} stateMachines=${board?.stateMachineNames}")
                        board?.animationNames?.forEach { name ->
                            val a = board.animation(name)
                            Log.d("RiveArtboard", "$artboard/$name duration=${a.effectiveDurationInSeconds}s loop=${a.loop} fps=${a.fps}")
                        }
                        board?.stateMachineNames?.forEach { name ->
                            val inputs = board.stateMachine(name).inputs.map { "${it.name}(${it::class.simpleName})" }
                            Log.d("RiveArtboard", "$artboard/$name inputs=$inputs")
                        }
                    }
                }
                onReady(this)
            }
        },
    )
}
