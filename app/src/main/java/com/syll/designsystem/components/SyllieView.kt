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
enum class SyllieMood(val timeline: String, val loops: Boolean, val reactionDurationMillis: Long?) {
    IDLE("idle", true, null),
    HAPPY("happy", false, 950),
    EXCITED("excited", false, 950),
    THINKING("thinking_wait", true, null),
    OOPS("oops", false, 1_500),
    SLEEPY("sleepy", true, 0),
    /** Short happy variants, each a different gesture. `PROUD` is for a streak or a notable result. */
    HAPPY_POP("happy_pop", false, 950),
    HAPPY_WIGGLE("happy_wiggle", false, 1_000),
    HAPPY_PUFF("happy_puff", false, 1_000),
    HAPPY_FLOAT("happy_float", false, 1_100),
    PROUD("proud", false, 1_300),
    /** Wrong-answer variants, each a different gesture. */
    OOPS_SQUISH("oops_squish", false, 1_100),
    OOPS_WOBBLE("oops_wobble", false, 1_200),
    OOPS_FREEZE("oops_freeze", false, 1_400),
    OOPS_SLIP("oops_slip", false, 1_350),
    /** One-shot from `SLEEPY` back to awake. */
    WAKE_UP("wake_up", false, 1_000),
    /** A stronger breathing loop than `IDLE`, for where Syllie is shown small. */
    BREATH("breath", true, null),
    /** One-shot "I'm ready" nod, from `REVIEW_WATCH`'s loop-boundary pose. */
    REVIEW_READY("review_ready", false, 1_050),
    /** Loop while a word list is arriving: scans it left to right. */
    REVIEW_WATCH("review_watch", true, null),
    /** One-shot for "review done, new words next". */
    REVIEW_TO_LEARN("review_to_learn", false, 1_350),
    /** One-shot into "hmm, let me think"; it ends on the pose `DEEP_THOUGHT_HOLD` loops on. */
    DEEP_THOUGHT("deep_thought_in", false, 2_200),
    DEEP_THOUGHT_HOLD("deep_thought_hold", true, null),
    ;

    /** Where Syllie goes once the one-shot is over: the held loop for an intro, otherwise idle. */
    val settledMood: SyllieMood get() = if (this == DEEP_THOUGHT) DEEP_THOUGHT_HOLD else IDLE

    companion object {
        /** The right-answer reactions, picked at random so Syllie doesn't repeat one gesture. */
        val happyReactions = listOf(HAPPY, HAPPY_POP, HAPPY_WIGGLE, HAPPY_PUFF, HAPPY_FLOAT)
        val oopsReactions = listOf(OOPS, OOPS_SQUISH, OOPS_WOBBLE, OOPS_FREEZE, OOPS_SLIP)
        /** The loops shown while a practice question is unanswered, picked at random per question. */
        val thinkingMoods = listOf(THINKING, DEEP_THOUGHT)
        /** Length of one `REVIEW_WATCH` loop; switch to `REVIEW_READY` on a multiple of it. */
        const val REVIEW_WATCH_LOOP_MILLIS = 1_600L

        fun nextThinking() = thinkingMoods.random()
        fun nextHappyReaction() = happyReactions.random()
        fun nextOopsReaction() = oopsReactions.random()
    }
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
