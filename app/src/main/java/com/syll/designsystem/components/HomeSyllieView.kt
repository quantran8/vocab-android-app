package com.syll.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Loop
import com.syll.R
import com.syll.designsystem.motion.LocalMotion
import kotlinx.coroutines.delay

/**
 * iOS `HomeSyllieScene`: what Syllie is doing on Home. [timelines] play together on the `HomeSyllie` artboard
 * (no Rive view models): the scene's own timeline plus its prop layer.
 */
enum class HomeSyllieScene(val timelines: List<String>, val loops: Boolean, val durationMillis: Long?) {
    IDLE(listOf("idle"), loops = true, durationMillis = null),
    SLEEPY(listOf("sleepy", "snore_bubbles"), loops = true, durationMillis = null),
    /** One-shot from [SLEEPY] back to awake; a tap plays it. */
    WAKE_UP(listOf("wake_up", "snore_off"), loops = false, durationMillis = 1_000),
    /** One-shot: a letter flies in, Syllie bounces it up with his top lobe, it falls and bounces, he's pleased. */
    PLAY_LETTERS(listOf("play_letters"), loops = false, durationMillis = 5_700),
    /** One-shot: a paper plane loops over his head and zooms off; he leans after it, puzzled, then settles. */
    PLANE_CHASE(listOf("plane_chase"), loops = false, durationMillis = 5_200),
    ;

    companion object {
        /** The play scenes Home picks from while Syllie is awake. */
        val plays = listOf(PLAY_LETTERS, PLANE_CHASE)
    }
}

/**
 * iOS `HomeSyllieView`: Syllie on Home, from the `HomeSyllie` artboard (its own copy of Syllie plus the letter and
 * plane props). Decorative, except that with [onWake] a sleeping Syllie is a button: a tap plays [HomeSyllieScene.WAKE_UP],
 * then calls [onWake].
 */
@Composable
fun HomeSyllieView(scene: HomeSyllieScene, modifier: Modifier = Modifier, onWake: (() -> Unit)? = null) {
    val motion = LocalMotion.current
    var view by remember { mutableStateOf<RiveAnimationView?>(null) }
    var isWaking by remember { mutableStateOf(false) }
    val shown = if (isWaking) HomeSyllieScene.WAKE_UP else scene
    val canWake = scene == HomeSyllieScene.SLEEPY && onWake != null && !isWaking
    val label = stringResource(R.string.syllie_is_asleep)
    val hint = stringResource(R.string.double_tap_to_wake_him_up)

    LaunchedEffect(scene) { isWaking = false }
    LaunchedEffect(isWaking) {
        if (!isWaking) return@LaunchedEffect
        delay(HomeSyllieScene.WAKE_UP.durationMillis ?: 0)
        isWaking = false
        onWake?.invoke()
    }
    LaunchedEffect(view, shown, motion.reduceMotion) {
        val rive = view ?: return@LaunchedEffect
        rive.stop()
        runCatching {
            rive.play(shown.timelines, if (shown.loops) Loop.LOOP else Loop.ONESHOT)
            if (motion.reduceMotion) rive.pause()
        }
    }

    RiveArtboard(
        artboard = "HomeSyllie",
        animation = "idle",
        autoplay = false,
        onReady = { view = it },
        modifier = if (canWake) {
            modifier
                .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                    if (motion.reduceMotion) onWake?.invoke() else isWaking = true
                }
                .semantics(mergeDescendants = true) {
                    contentDescription = "$label. $hint"
                    role = Role.Button
                }
        } else {
            modifier.clearAndSetSemantics {}
        },
    )
}
