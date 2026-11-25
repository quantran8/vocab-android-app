package com.syll.designsystem.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import kotlin.math.PI

/**
 * One animation as iOS describes it, turned into a Compose spec of any type with [spec].
 *
 * SwiftUI springs are `(response, dampingFraction)`; Compose takes `(dampingRatio, stiffness)` with mass 1, so
 * stiffness = (2π / response)². That keeps the feel of every iOS spring without re-tuning.
 */
sealed interface MotionSpec {
    fun <T> spec(): FiniteAnimationSpec<T>

    data class Spring(val response: Double, val dampingFraction: Double) : MotionSpec {
        override fun <T> spec(): FiniteAnimationSpec<T> {
            val omega = 2 * PI / response
            return spring(dampingRatio = dampingFraction.toFloat(), stiffness = (omega * omega).toFloat())
        }
    }

    data class Tween(val seconds: Double, val easing: Easing, val delaySeconds: Double = 0.0) : MotionSpec {
        val millis: Int get() = (seconds * 1000).toInt()

        override fun <T> spec(): FiniteAnimationSpec<T> =
            tween(durationMillis = millis, delayMillis = (delaySeconds * 1000).toInt(), easing = easing)
    }
}

/** SwiftUI's named curves. */
object Curves {
    val easeOut: Easing = CubicBezierEasing(0f, 0f, 0.58f, 1f)
    val easeIn: Easing = CubicBezierEasing(0.42f, 0f, 1f, 1f)
    val easeInOut: Easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
    val linear: Easing = LinearEasing
}

private fun iosSpring(response: Double, damping: Double) = MotionSpec.Spring(response, damping)
private fun iosEaseOut(seconds: Double, delay: Double = 0.0) = MotionSpec.Tween(seconds, Curves.easeOut, delay)
private fun iosEaseInOut(seconds: Double) = MotionSpec.Tween(seconds, Curves.easeInOut)
private fun iosLinear(seconds: Double) = MotionSpec.Tween(seconds, Curves.linear)

/** iOS `MotionTokens.swift` + `ChallengeMotionTokens.swift`. Times in seconds, like iOS. Keep both apps in sync. */
object MotionTokens {
    val press = iosEaseOut(0.12)
    val lift = iosSpring(0.24, 0.82)
    val snap = iosSpring(0.32, 0.76)
    /** Time for a [snap] move to come to rest. */
    const val snapSettle = 0.4
    /** A cloze word flying between the bank and the blank. */
    val wordFlight = iosSpring(0.22, 0.88)
    /** Time for a [wordFlight] move to come to rest. */
    const val wordFlightSettle = 0.26
    val returnToOrigin = iosSpring(0.34, 0.84)
    val feedback = iosSpring(0.36, 0.68)
    /** The feedback panel growing or shrinking around its detail (read-aloud syllables). */
    val panelExpand = iosSpring(0.45, 0.88)
    /** One full wrong-answer shake. */
    val shake = iosLinear(0.4)
    /** Time for an answer's own feedback to finish; Syllie reacts to the answer only after it. */
    const val answerFeedbackSettle = 0.1
    /** Time for the keyboard to slide down, before an answer typed on it is checked. */
    const val keyboardDismiss = 0.3
    /** One wave of `SpeakerWaveIcon` appearing, and the time between waves while it plays. */
    val speakerWave = iosEaseOut(0.18)
    const val speakerWaveStep = 0.28
    /** An icon swapping glyph (radio, password eye, chevron). */
    val symbolSwap = iosEaseInOut(0.2)
    /** The feedback check bounces once the panel has slid in. */
    const val feedbackSymbolBounce = 0.25
    /** LP bolt and trophy on a results screen bounce once Syllie has landed and the confetti started. */
    const val resultSymbolBounce = 1.4
    const val transitionDuration = 0.36
    val transition = MotionSpec.Tween(transitionDuration, CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f))
    /** A step sliding in a flow that goes both ways: settles without overshoot, like a system push. */
    val stepSlide = iosSpring(0.4, 1.0)
    val progress = iosEaseOut(0.4)
    val entrance = iosSpring(0.5, 0.62)
    val reducedCrossfade = iosEaseInOut(0.15)
    /** Shortest time the launch splash stays up, so it reads as a splash rather than a flicker. */
    const val splashHold = 1.4
    /** Time for the shimmer band to cross a button. */
    const val shimmerSweep = 1.4
    /** Time from one shimmer sweep to the next, including the pause between them. */
    const val shimmerCycle = 4.0

    // Word introduction recipes (visual only — never a navigation gate)
    val introLetter = iosSpring(0.38, 0.82)
    val introBounce = iosSpring(0.36, 0.58)
    val introReveal = iosEaseOut(0.28)
    val introAccent = iosSpring(0.32, 0.75)
    val introSweep = iosEaseInOut(0.75)
    const val introLetterStagger = 0.055
    const val introChunkStagger = 0.22
    const val introChunkHold = 0.45

    // Review list before a session (visual only — never a navigation gate)
    val reviewTileLanding = iosSpring(0.55, 0.74)
    /** Fade-in of a scattered tile, run alongside (not as part of) its landing spring. */
    val reviewTileFade = iosEaseOut(0.16)
    const val reviewTileStagger = 0.11
    val reviewTrainLanding = iosSpring(0.42, 0.9)
    const val reviewTrainStagger = 0.2
    /** Time for one word to visibly come to rest after its own landing starts. */
    const val reviewTileSettle = 0.6
    const val reviewTrainSettle = 0.45
    val reviewWigglePause = 1.1..2.4
    const val streamCharacter = 0.022

    // Scored read-aloud (visual only)
    const val scoreRingFillDuration = 0.9
    /** The score ring filling and its number counting up. */
    val scoreRingFill = iosEaseOut(scoreRingFillDuration)
    /** Syllables take their colour one by one, in reading order, after the ring has filled. */
    const val syllableColorStagger = 0.14
    val syllableColor = iosEaseOut(0.3)
    /** One pass of the light sweeping over the word while a take is scored. */
    const val scoringSweepPeriod = 1.3

    // End-of-session results (visual only)
    const val celebrationStatsDelay = 0.25
    const val celebrationStagger = 0.08

    const val pressedScale = 0.97f
    const val liftedScale = 1.04f
    const val pulseScale = 1.03f
    /** dp */
    const val enterOffset = 13f

    // Match Madness: nothing on the board blocks the next tap for more than ~200 ms.
    /** How long a matched pair shows as correct before it fades out. */
    const val matchCorrectHold = 0.3
    /** A matched pair fading out of its slots. */
    val matchFadeOut = iosEaseOut(0.16)
    /** The next pair fading into the freed slots, once the matched pair is mostly gone. */
    val matchFadeIn = iosEaseOut(0.22, delay = 0.12)
    /** A wrong pair's shake (one full shake cycle). */
    val matchShake = iosLinear(0.18)
    /** How long the full-screen celebration stays over the results before it clears. */
    const val challengeCelebrationHold = 2.6
    /** Every this many matches in a row, Syllie gives a small cheer. */
    const val comboMilestone = 5
    /** The "Start" callout over the current map level bobbing up and down (repeats, reverses). */
    val mapCalloutBob = iosEaseInOut(0.9)
}
