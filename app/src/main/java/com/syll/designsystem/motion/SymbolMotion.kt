package com.syll.designsystem.motion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay

// iOS `SymbolMotion.swift` (which icons move, and when) and `.contentTransition(.numericText())`.

/**
 * iOS `symbolReplace(value:)`: when [value] swaps the glyph, the old one shrinks and fades as the new one grows in
 * (SF Symbols' replace effect). An instant swap with reduced motion.
 */
@Composable
fun <T> SymbolReplace(value: T, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    val motion = LocalMotion.current
    AnimatedContent(
        value,
        modifier,
        transitionSpec = {
            if (motion.reduceMotion) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                val spec = MotionTokens.symbolSwap
                (scaleIn(spec.spec(), initialScale = 0.5f) + fadeIn(spec.spec())) togetherWith
                    (scaleOut(spec.spec(), targetScale = 0.5f) + fadeOut(spec.spec()))
            }
        },
        contentAlignment = Alignment.Center,
        label = "symbolReplace",
    ) { content(it) }
}

/**
 * iOS `symbolBounceOnce(after:when:)`: one bounce of the icon, [delaySeconds] after it appears or [enabled] turns
 * on. Like SF Symbols' bounce: a quick grow, then a springy settle.
 */
fun Modifier.symbolBounceOnce(delaySeconds: Double = 0.0, enabled: Boolean = true): Modifier = composed {
    val motion = LocalMotion.current
    val scale = remember { Animatable(1f) }
    LaunchedEffect(enabled, motion.reduceMotion) {
        if (!enabled || motion.reduceMotion) return@LaunchedEffect
        delay((delaySeconds * 1000).toLong())
        scale.animateTo(1.22f, tween(110))
        scale.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 520f))
    }
    graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}

/**
 * iOS `.contentTransition(.numericText())` under [spec]: each digit that changes rolls to its new value, up when the
 * number grows and down when it shrinks. Instant with reduced motion.
 */
@Composable
fun RollingNumber(
    value: Int,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    spec: MotionSpec = MotionTokens.snap,
    text: (Int) -> String = { it.toString() },
) {
    val motion = LocalMotion.current
    val shown = text(value)
    Row(modifier.clearAndSetSemantics { contentDescription = shown }) {
        // Digits are keyed from the end, so units stay units when the number gains a digit.
        shown.reversed().withIndex().reversed().forEach { (position, char) ->
            androidx.compose.runtime.key(position) {
                AnimatedContent(
                    targetState = DigitState(char, value),
                    transitionSpec = {
                        if (motion.reduceMotion || initialState.char == targetState.char) {
                            ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)
                        } else {
                            val up = targetState.number > initialState.number
                            val slide = spec.spec<IntOffset>()
                            (slideInVertically(slide) { if (up) it else -it } + fadeIn(spec.spec())) togetherWith
                                (slideOutVertically(slide) { if (up) -it else it } + fadeOut(spec.spec())) using
                                SizeTransform(clip = true)
                        }
                    },
                    contentKey = { it.char },
                    label = "digit",
                ) { Text(it.char.toString(), style = style.copy(fontFeatureSettings = "tnum"), color = color) }
            }
        }
    }
}

private data class DigitState(val char: Char, val number: Int)
