package com.vocabflow.designsystem.motion

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * iOS `MotionCoordinator`. All animation goes through it so both the system setting (Settings → Accessibility →
 * Remove animations) and the in-app Reduce Motion setting apply. Read it with `LocalMotion.current`.
 *
 * SwiftUI's `motion.perform(animation) { state = … }` becomes: change the state, and animate with
 * `motion.animation(MotionTokens.x)` as the spec (`animateFloatAsState`, `AnimatedContent`, …).
 */
@Immutable
data class Motion(val reduceMotion: Boolean) {
    val pressedScale: Float get() = if (reduceMotion) 1f else MotionTokens.pressedScale
    val liftedScale: Float get() = if (reduceMotion) 1f else MotionTokens.liftedScale
    val pulseScale: Float get() = if (reduceMotion) 1f else MotionTokens.pulseScale

    /** iOS `motion.animation(_:)`: a short crossfade instead of the motion when reduced. */
    fun <T> animation(spec: MotionSpec): FiniteAnimationSpec<T> =
        if (reduceMotion) MotionTokens.reducedCrossfade.spec() else spec.spec()

    /** iOS `motion.travel(_:)`: movement that is dropped entirely (jumps) when reduced. */
    fun <T> travel(spec: MotionSpec): FiniteAnimationSpec<T> = if (reduceMotion) snap() else spec.spec()

    /** Whole-screen swap in `AppRoot`: fade, plus a short rise for the incoming screen. */
    fun routeTransition(enterOffsetPx: Int): ContentTransform {
        val fade = animation<Float>(MotionTokens.transition)
        if (reduceMotion) return fadeIn(fade) togetherWith fadeOut(fade)
        val rise = slideInVertically(MotionTokens.transition.spec<IntOffset>()) { enterOffsetPx }
        return (fadeIn(fade) + rise) togetherWith fadeOut(fade)
    }

    /**
     * Step-to-step content inside a flow. Forward: the new step slides in from the end edge, the old one leaves a
     * little toward the start (parallax, like a system push). Back: the reverse.
     */
    fun stepTransition(isMovingBack: Boolean): ContentTransform {
        if (reduceMotion) {
            val fade = MotionTokens.reducedCrossfade.spec<Float>()
            return fadeIn(fade) togetherWith fadeOut(fade)
        }
        val slide = MotionTokens.stepSlide.spec<IntOffset>()
        val fade = MotionTokens.stepSlide.spec<Float>()
        val enter: EnterTransition
        val exit: ExitTransition
        if (isMovingBack) {
            enter = slideInHorizontally(slide) { -(it * PARALLAX).roundToInt() } + fadeIn(fade)
            exit = slideOutHorizontally(slide) { it } + fadeOut(fade)
        } else {
            enter = slideInHorizontally(slide) { it } + fadeIn(fade)
            exit = slideOutHorizontally(slide) { -(it * PARALLAX).roundToInt() } + fadeOut(fade)
        }
        return enter togetherWith exit
    }

    val popEnter: EnterTransition
        get() = if (reduceMotion) fadeIn(MotionTokens.reducedCrossfade.spec()) else
            scaleIn(MotionTokens.snap.spec(), initialScale = 0.88f) + fadeIn(MotionTokens.snap.spec())

    val popExit: ExitTransition
        get() = if (reduceMotion) fadeOut(MotionTokens.reducedCrossfade.spec()) else
            scaleOut(MotionTokens.snap.spec(), targetScale = 0.88f) + fadeOut(MotionTokens.snap.spec())

    private companion object {
        /** How far the page beneath shifts, as a share of the width. */
        const val PARALLAX = 0.3f
    }
}

val LocalMotion = staticCompositionLocalOf { Motion(reduceMotion = false) }

/** The system's "Remove animations" (animator duration scale 0), read once per composition of the root. */
fun systemReducesMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

@Composable
fun rememberEnterOffsetPx(): Int {
    val density = LocalDensity.current
    return remember(density) { with(density) { MotionTokens.enterOffset.dp.roundToPx() } }
}

@Composable
fun rememberSystemReducesMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) { systemReducesMotion(context) }
}
