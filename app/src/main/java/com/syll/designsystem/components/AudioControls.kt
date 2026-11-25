package com.syll.designsystem.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.syll.R
import com.syll.designsystem.motion.LocalMotion
import com.syll.designsystem.motion.MotionTokens
import com.syll.designsystem.tokens.SyllColors
import kotlinx.coroutines.delay

/**
 * iOS `SpeakerWaveIcon`: a speaker with three sound waves. While [isPlaying] the waves cycle 1 → 2 → 3 → 1 …; at
 * rest all three show. When playback stops mid-cycle the waves keep stepping at the same pace until all three are
 * back, so the icon never freezes on a partial cycle. With reduced motion the waves stay put. Decorative.
 */
@Composable
fun SpeakerWaveIcon(isPlaying: Boolean, tint: Color, side: Dp = 28.dp) {
    val motion = LocalMotion.current
    var visibleWaves by remember { mutableIntStateOf(WAVE_RADII.size) }
    // Each wave fades and grows in when it appears (iOS `.opacity` + `.scaleEffect(0.7)` under `speakerWave`).
    val shown = WAVE_RADII.indices.map { index ->
        animateFloatAsState(
            if (index < visibleWaves) 1f else 0f,
            motion.animation(MotionTokens.speakerWave),
            label = "wave$index",
        )
    }
    fun step() {
        visibleWaves = if (visibleWaves >= WAVE_RADII.size) 1 else visibleWaves + 1
    }
    val stepMillis = (MotionTokens.speakerWaveStep * 1000).toLong()
    // A new key cancels the previous loop; the stopping loop picks up from wherever the cycle is.
    LaunchedEffect(isPlaying, motion.reduceMotion) {
        if (motion.reduceMotion) {
            visibleWaves = WAVE_RADII.size
        } else if (isPlaying) {
            // Starts right away so the tap gets instant feedback.
            while (true) {
                step()
                delay(stepMillis)
            }
        } else {
            while (visibleWaves < WAVE_RADII.size) {
                delay(stepMillis)
                step()
            }
        }
    }
    Canvas(Modifier.size(side)) {
        val w = size.width
        fun point(x: Float, y: Float) = Offset(x * w, y * size.height)
        val body = Path().apply {
            moveTo(point(0.08f, 0.37f).x, point(0.08f, 0.37f).y)
            listOf(0.24f to 0.37f, 0.44f to 0.2f, 0.44f to 0.8f, 0.24f to 0.63f, 0.08f to 0.63f).forEach { (x, y) ->
                lineTo(point(x, y).x, point(x, y).y)
            }
            close()
        }
        drawPath(body, tint)
        drawPath(body, tint, style = Stroke(width = w * 0.06f, join = StrokeJoin.Round))
        val source = point(0.46f, 0.5f)
        WAVE_RADII.forEachIndexed { index, radius ->
            val amount = shown[index].value
            if (amount <= 0f) return@forEachIndexed
            scale(0.7f + 0.3f * amount, pivot = source) {
                val r = radius * w
                drawArc(
                    tint.copy(alpha = tint.alpha * amount),
                    startAngle = -45f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(source.x - r, source.y - r),
                    size = Size(r * 2, r * 2),
                    style = Stroke(width = w * 0.075f, cap = StrokeCap.Round),
                )
            }
        }
    }
}

private val WAVE_RADII = listOf(0.14f, 0.27f, 0.40f)

/** iOS `SoundControlButtonStyle`: a raised control sinks to its depth edge while pressed; a flat one scales. */
fun Modifier.soundControlClickable(
    enabled: Boolean,
    isRaised: Boolean,
    depth: Dp = 5.dp,
    onClick: () -> Unit,
): Modifier = composed {
    val motion = LocalMotion.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val sink by animateDpAsState(
        if (pressed && isRaised && enabled) depth else 0.dp,
        motion.animation(MotionTokens.press),
        label = "soundSink",
    )
    val scale by animateFloatAsState(
        if (pressed && !isRaised && enabled) motion.pressedScale else 1f,
        motion.animation(MotionTokens.press),
        label = "soundScale",
    )
    offset(y = sink)
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
}

/** iOS `AudioPlayButton`: a user-triggered pronunciation control. Never autoplays and never reveals the answer in its label. */
@Composable
fun AudioPlayButton(isSpeaking: Boolean, isAvailable: Boolean, onClick: () -> Unit, isLarge: Boolean = false) {
    val size = if (isLarge) 128.dp else 44.dp
    val label = stringResource(if (isAvailable) R.string.play_pronunciation else R.string.audio_unavailable)
    val hint = stringResource(
        if (isAvailable) R.string.replays_the_word_you_can_play_it_as_often_as_you else R.string.use_the_written_clue_instead,
    )
    val playing = stringResource(R.string.playing)
    Box(
        Modifier
            .size(if (isLarge) 176.dp else 44.dp)
            .semantics {
                contentDescription = "$label. $hint"
                if (isSpeaking) stateDescription = playing
            },
        contentAlignment = Alignment.Center,
    ) {
        if (isLarge && isAvailable) {
            Box(Modifier.size(size).offset(y = 5.dp).background(SyllColors.interactiveDepth, CircleShape))
        }
        Box(
            Modifier
                .size(size)
                .soundControlClickable(enabled = isAvailable, isRaised = isLarge, onClick = onClick)
                .background(
                    when {
                        !isAvailable -> SyllColors.disabledSurface
                        isLarge -> SyllColors.interactive
                        else -> SyllColors.selectedSurface
                    },
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            val tint = if (!isAvailable) SyllColors.disabledInk else if (isLarge) Color.White else SyllColors.blue600
            if (isAvailable) {
                SpeakerWaveIcon(isSpeaking, tint, side = if (isLarge) 48.dp else 22.dp)
            } else {
                Icon(
                    Icons.AutoMirrored.Filled.VolumeOff,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(if (isLarge) 34.dp else 24.dp),
                )
            }
        }
    }
}
