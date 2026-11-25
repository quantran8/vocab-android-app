package com.syll.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Loop
import com.syll.designsystem.motion.LocalMotion

/**
 * iOS `SyllieMood`. [timeline] is the mood's timeline on the `Syllie` artboard of `syllie.riv`.
 * [loops]: idle-like moods repeat, reactions play once and hold their last frame.
 */
enum class SyllieMood(val timeline: String, val loops: Boolean) {
    IDLE("idle", true),
    HAPPY("happy", false),
    EXCITED("excited", false),
    THINKING("thinking_wait", true),
    OOPS("oops", false),
    SLEEPY("sleepy", true),
    HAPPY_FLOAT("happy_float", false),
    PROUD("proud", false),
    OOPS_SQUISH("oops_squish", false),
    OOPS_WOBBLE("oops_wobble", false),
    WAKE_UP("wake_up", false),
    BREATH("breath", true),
    REVIEW_TO_LEARN("review_to_learn", false),
    DEEP_THOUGHT("deep_thought_in", false),
    DEEP_THOUGHT_HOLD("deep_thought_hold", true),
}

/**
 * iOS `SyllieView`: Syllie the mascot, decorative. Android plays the mood's timeline directly (no Rive view
 * models); a timeline the file doesn't have falls back to `idle`. [replayToken] replays the current mood.
 * With reduced motion Syllie shows the mood's first frame and doesn't move.
 */
@Composable
fun SyllieView(mood: SyllieMood, modifier: Modifier = Modifier, replayToken: Int = 0) {
    val motion = LocalMotion.current
    var view by remember { mutableStateOf<RiveAnimationView?>(null) }

    RiveArtboard(
        artboard = "Syllie",
        animation = SyllieMood.IDLE.timeline,
        autoplay = false,
        modifier = modifier,
        onReady = { view = it },
    )
    LaunchedEffect(view, mood, replayToken, motion.reduceMotion) {
        view?.play(mood, reduceMotion = motion.reduceMotion)
    }
}

private fun RiveAnimationView.play(mood: SyllieMood, reduceMotion: Boolean) {
    val names = runCatching { controller.activeArtboard?.animationNames.orEmpty() }.getOrDefault(emptyList())
    val timeline = if (mood.timeline in names) mood.timeline else SyllieMood.IDLE.timeline
    val loops = if (timeline == mood.timeline) mood.loops else true
    stop()
    runCatching {
        play(timeline, if (loops) Loop.LOOP else Loop.ONESHOT)
        if (reduceMotion) pause()
    }
}
