package com.syll.features.challenge

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.syll.R
import com.syll.designsystem.components.RiveArtboard
import com.syll.designsystem.components.WordTile
import com.syll.designsystem.components.WordTileState
import com.syll.designsystem.components.cardSurface
import com.syll.features.practice.shake
import com.syll.designsystem.motion.LocalMotion
import com.syll.designsystem.tokens.Radii
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography
import com.syll.domain.challenge.ChallengeLevel
import com.syll.domain.challenge.ChallengeStarCriteria
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.ceil

/** iOS `StarRow`: one to three stars, filled up to [filled]. Achievements only, never a skill check. */
@Composable
fun StarRow(filled: Int, modifier: Modifier = Modifier, total: Int = ChallengeLevel.MAX_STARS, size: Dp = 16.dp) {
    val label = stringResource(R.string.x_of_x_stars, filled, total)
    Row(
        modifier.clearAndSetSemantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(size * 0.2f),
    ) {
        repeat(total) { index ->
            val isFilled = index < filled
            Box {
                // A darker keyline so a filled star doesn't rest on yellow alone.
                if (isFilled) {
                    Icon(Icons.Filled.Star, null, tint = SyllColors.rewardDepth, modifier = Modifier.size(size).padding(top = 1.dp))
                }
                Icon(
                    if (isFilled) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    null,
                    tint = if (isFilled) SyllColors.reward else SyllColors.stroke,
                    modifier = Modifier.size(size),
                )
            }
        }
    }
}

/** iOS `ChallengeTimeFormat`. */
object ChallengeTimeFormat {
    /** "1:23": minutes and seconds, rounded up so the clock reads 0:00 only when time is over. */
    fun countdown(milliseconds: Int): String {
        val seconds = ceil(maxOf(milliseconds, 0) / 1000.0).toInt()
        return "%d:%02d".format(Locale.ROOT, seconds / 60, seconds % 60)
    }

    /** "42.3" for results. */
    fun elapsed(milliseconds: Int, locale: Locale): String =
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
        }.format(milliseconds / 1000.0)

    fun whole(value: Int, locale: Locale): String = NumberFormat.getIntegerInstance(locale).format(value)
}

/**
 * iOS `MatchMadnessTile`: a word or meaning tile on the board. Looks and presses like a choice tile. State never rests
 * on colour alone: selected tiles get a thick outline, a wrong pair shakes and is announced.
 */
@Composable
fun MatchMadnessTile(
    text: String,
    isWord: Boolean,
    isSelected: Boolean,
    isWrong: Boolean,
    shakes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCorrect: Boolean = false,
    minHeight: Dp = Spacing.matchTileMinHeight,
) {
    val motion = LocalMotion.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var bounce by remember { mutableIntStateOf(0) }
    val state = when {
        isCorrect -> WordTileState.CORRECT
        isWrong -> WordTileState.INCORRECT
        isSelected -> WordTileState.SELECTED
        else -> WordTileState.IDLE
    }
    val canPress = state == WordTileState.IDLE || state == WordTileState.SELECTED
    val label = if (isWord) stringResource(R.string.word_x, text) else stringResource(R.string.meaning_x, text)
    WordTile(
        text = text,
        state = if (pressed && canPress) WordTileState.PRESSED else state,
        minHeight = minHeight,
        bounceTrigger = bounce,
        style = Typography.choice,
        modifier = modifier
            .shake(shakes)
            .clickable(interaction, indication = null, enabled = !isCorrect) {
                // A quick tap often never shows as pressed, so the tile also sinks on tap.
                if (!motion.reduceMotion) bounce += 1
                onClick()
            }
            .clearAndSetSemantics {
                contentDescription = label
                selected = isSelected
            },
    )
}

/** iOS `StarRules`: the level's star rules. One star is any clear; two and three need both bounds. */
@Composable
fun StarRules(criteria: ChallengeStarCriteria, timeLimitMs: Int, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().cardSurface(radius = Radii.tile, fill = SyllColors.quietSurface).padding(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        StarRuleRow(1, stringResource(R.string.clear_within_x_seconds, timeLimitMs / 1000))
        StarRuleRow(2, stringResource(R.string.within_x_seconds_and_at_most_x_mistakes, criteria.two.maxElapsedMs / 1000, criteria.two.maxMistakes))
        StarRuleRow(3, stringResource(R.string.within_x_seconds_and_at_most_x_mistakes, criteria.three.maxElapsedMs / 1000, criteria.three.maxMistakes))
    }
}

@Composable
private fun StarRuleRow(stars: Int, text: String) {
    Row(Modifier.semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
        StarRow(stars, size = 11.dp)
        Text(text, style = Typography.secondary, color = SyllColors.ink)
    }
}

/** iOS `ConfettiBurst`: the confetti Lottie, played once over the screen. Decorative. */
@Composable
fun ConfettiBurst(modifier: Modifier = Modifier) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.confetti))
    val progress by animateLottieCompositionAsState(composition, iterations = 1)
    LottieAnimation(composition, { progress }, modifier.fillMaxSize().clearAndSetSemantics { })
}

/** iOS `SyllieCelebrationView`: Syllie's full-screen cheer (the `Celebrate` artboard and state machine). */
@Composable
fun SyllieCelebrationView(modifier: Modifier = Modifier) {
    RiveArtboard(artboard = "Celebrate", stateMachine = "Celebrate", modifier = modifier.fillMaxSize().clearAndSetSemantics { })
}

/** When Syllie nears the top of its loop: the moment for the confetti (iOS `SyllieCelebrationView.confettiCue`). */
const val CELEBRATION_CONFETTI_CUE_MS = 1_100L

/**
 * iOS `accessibilityVoiceOverEnabled || accessibilitySwitchControlEnabled`: TalkBack (touch exploration) or another
 * service that drives the UI. Updates while the screen is shown.
 */
@Composable
fun rememberAssistiveTechEnabled(): Boolean {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager }
    var enabled by remember { mutableStateOf(manager.isTouchExplorationEnabled) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled = it }
        manager.addTouchExplorationStateChangeListener(listener)
        onDispose { manager.removeTouchExplorationStateChangeListener(listener) }
    }
    return enabled
}

/** A darker edge under a round face, like a key (the map's nodes and trophy). */
fun Modifier.roundDepth(color: androidx.compose.ui.graphics.Color, depth: Dp): Modifier = drawBehind {
    drawCircle(color, radius = size.minDimension / 2, center = Offset(size.width / 2, size.height / 2 + depth.toPx()))
}
