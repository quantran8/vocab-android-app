package com.syll.features.practice

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syll.R
import com.syll.designsystem.motion.LocalMotion
import com.syll.designsystem.motion.MotionSpec
import com.syll.designsystem.motion.MotionTokens
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography
import com.syll.domain.practice.IntroductionPresentation
import com.syll.domain.practice.IntroductionTemplateID
import com.syll.domain.practice.PracticeAnswer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** iOS `IntroMotionState`. */
private enum class IntroMotionState {
    /** Holding until the step transition has landed, so letters never animate while sliding in. */
    WAITING, REVEALING, SETTLED, SKIPPED
}

/** 44 sp heavy, tracking -0.5 (iOS `IntroMotionHost`). */
private val HERO_STYLE: TextStyle = Typography.wordHero.copy(letterSpacing = (-0.5).sp, color = SyllColors.ink)

/**
 * iOS `IntroMotionHost`: renders only the English spelling with one of six motion recipes; tapping it skips or
 * replays. Owns presentation state only, never attempts, scores or session advancement. Long words shrink to fit
 * instead of clipping or wrapping mid-word. Reduced motion (or [animates] off) shows the word at rest.
 */
@Composable
fun IntroMotionHost(word: String, presentation: IntroductionPresentation, animates: Boolean = true) {
    val motion = LocalMotion.current
    val isStill = !animates || motion.reduceMotion
    var generation by remember { mutableIntStateOf(0) }
    var state by remember(word, presentation) { mutableStateOf(IntroMotionState.WAITING) }
    val isAnimating = state == IntroMotionState.WAITING || state == IntroMotionState.REVEALING

    fun skipOrReplay() {
        if (isStill) return
        if (isAnimating) {
            state = IntroMotionState.SKIPPED
        } else {
            state = IntroMotionState.REVEALING
            generation += 1
        }
    }

    // Decorative timing only (never gates Next). Restarted on replay, so stale runs are ignored.
    LaunchedEffect(word, presentation, generation, isStill) {
        if (isStill) return@LaunchedEffect
        if (state == IntroMotionState.WAITING) {
            delay((MotionTokens.transitionDuration * 1000).toLong())
            if (state != IntroMotionState.WAITING) return@LaunchedEffect
            state = IntroMotionState.REVEALING
        }
        if (state != IntroMotionState.REVEALING) return@LaunchedEffect
        val duration = presentation.revealDuration(
            word, MotionTokens.introLetterStagger, MotionTokens.introChunkStagger, MotionTokens.introChunkHold,
        )
        delay((duration * 1000).toLong())
        if (state == IntroMotionState.REVEALING) state = IntroMotionState.SETTLED
    }

    val actionLabel = stringResource(if (isAnimating) R.string.show_word_now else R.string.replay)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = with(density) { (HERO_STYLE.fontSize * 1.6f).toDp() })
            .clickable(remember { MutableInteractionSource() }, indication = null, enabled = !isStill) { skipOrReplay() }
            .clearAndSetSemantics {
                contentDescription = word
                heading()
                if (!isStill) customActions = listOf(CustomAccessibilityAction(actionLabel) { skipOrReplay(); true })
            },
        contentAlignment = Alignment.Center,
    ) {
        val natural = remember(word, density) { measurer.measure(word, HERO_STYLE, softWrap = false, maxLines = 1).size.width }
        val available = constraints.maxWidth
        val fit = if (natural > 0 && available > 0) minOf(1f, available.toFloat() / natural) else 1f
        Box(
            Modifier
                .wrapContentWidth(unbounded = true)
                .graphicsLayer { scaleX = fit; scaleY = fit },
            contentAlignment = Alignment.Center,
        ) {
            // A static, hidden copy of the word sizes the stage; the animation draws on top of it.
            Text(word, style = HERO_STYLE.copy(color = SyllColors.ink.copy(alpha = 0f)), softWrap = false, maxLines = 1)
            when {
                isStill || state == IntroMotionState.SKIPPED -> Text(word, style = HERO_STYLE, softWrap = false, maxLines = 1)
                state != IntroMotionState.WAITING -> androidx.compose.runtime.key(generation) {
                    AnimatedWord(word, presentation)
                }
            }
        }
    }
}

@Composable
private fun AnimatedWord(word: String, presentation: IntroductionPresentation) {
    when (presentation.templateId) {
        // Letters rise one by one into place.
        IntroductionTemplateID.LETTER_CASCADE -> GlyphRow(
            word,
            hidden = { _, _ -> GlyphStyle(offsetY = 26f, opacity = 0f) },
            spec = MotionTokens.introLetter,
            delayOf = { it * MotionTokens.introLetterStagger },
        )
        // Letters converge from short, deterministic offsets into the final word.
        IntroductionTemplateID.WORD_ASSEMBLE -> GlyphRow(
            word,
            hidden = { index, _ ->
                // Stable pseudo-scatter: same word, same offsets on every replay.
                GlyphStyle(
                    offsetX = ((index * 7) % 5 - 2) * 14f,
                    offsetY = ((index * 11) % 5 - 2) * 12f,
                    scale = 0.7f,
                    opacity = 0f,
                )
            },
            spec = MotionTokens.introLetter,
            delayOf = { (it % 3) * 0.04 },
        )
        // Letters arrive in a quick staggered bounce, then sit still.
        IntroductionTemplateID.LETTER_WAVE -> GlyphRow(
            word,
            hidden = { _, _ -> GlyphStyle(offsetY = -22f, scale = 0.6f, opacity = 0f) },
            spec = MotionTokens.introBounce,
            delayOf = { it * 0.05 },
        )
        // Each letter rotates upright around its horizontal axis like a small tile.
        IntroductionTemplateID.FLIP_LETTERS -> GlyphRow(
            word,
            hidden = { _, _ -> GlyphStyle(opacity = 0f, rotationX = -90f) },
            spec = MotionTokens.introLetter,
            delayOf = { it * 0.06 },
        )
        IntroductionTemplateID.SWIPE_REVEAL -> SwipeReveal(word)
        IntroductionTemplateID.CHUNK_MERGE -> ChunkMerge(presentation.introChunks ?: listOf(word))
    }
}

/** iOS `IntroGlyphStyle`: the transform of one grapheme before it settles. The rest state is the identity. */
private data class GlyphStyle(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    val opacity: Float = 1f,
    val rotationX: Float = 0f,
)

/**
 * iOS `IntroGlyphRow`: the word as per-grapheme texts (never UTF-16 units), each animating from `hidden` to rest
 * with its own staggered spring. Opacity rides the same spring, clamped where it overshoots.
 */
@Composable
private fun GlyphRow(
    word: String,
    hidden: (index: Int, count: Int) -> GlyphStyle,
    spec: MotionSpec,
    delayOf: (Int) -> Double,
) {
    val glyphs = remember(word) { PracticeAnswer.graphemes(word) }
    val progress = remember(word) { glyphs.map { Animatable(0f) } }
    LaunchedEffect(word) {
        glyphs.indices.forEach { index ->
            launch {
                delay((delayOf(index) * 1000).toLong())
                progress[index].animateTo(1f, spec.spec())
            }
        }
    }
    Row(horizontalArrangement = Arrangement.Start) {
        glyphs.forEachIndexed { index, glyph ->
            val from = hidden(index, glyphs.size)
            Text(
                glyph,
                style = HERO_STYLE,
                softWrap = false,
                modifier = Modifier.graphicsLayer {
                    val t = progress[index].value
                    val away = 1 - t
                    translationX = from.offsetX.dp.toPx() * away
                    translationY = from.offsetY.dp.toPx() * away
                    val scale = 1 + (from.scale - 1) * away
                    scaleX = scale
                    scaleY = scale
                    alpha = (1 + (from.opacity - 1) * away).coerceIn(0f, 1f)
                    rotationX = from.rotationX * away
                    // SwiftUI `perspective: 0.5`: a close camera, so the flip reads as a tile turning.
                    cameraDistance = 6 * density
                },
            )
        }
    }
}

/** iOS `SwipeRevealView`: a single left-to-right clipping mask reveals the whole word. */
@Composable
private fun SwipeReveal(word: String) {
    val progress = remember(word) { Animatable(0f) }
    LaunchedEffect(word) { progress.animateTo(1f, MotionTokens.introSweep.spec()) }
    Text(
        word,
        style = HERO_STYLE,
        softWrap = false,
        maxLines = 1,
        // The word is English: the mask runs left to right in every app language.
        modifier = Modifier.drawWithContent {
            clipRect(right = size.width * progress.value.coerceIn(0f, 1f)) { this@drawWithContent.drawContent() }
        },
    )
}

/** iOS `ChunkMergeView`: curated spelling groups arrive one by one, then join into one word. */
@Composable
private fun ChunkMerge(chunks: List<String>) {
    val arrivals = remember(chunks) { chunks.map { Animatable(0f) } }
    val merge = remember(chunks) { Animatable(0f) }
    LaunchedEffect(chunks) {
        chunks.indices.forEach { index ->
            launch {
                delay((index * MotionTokens.introChunkStagger * 1000).toLong())
                arrivals[index].animateTo(1f, MotionTokens.introAccent.spec())
            }
        }
        // The join is a separate move after the groups have arrived.
        delay(((chunks.size * MotionTokens.introChunkStagger + MotionTokens.introChunkHold) * 1000).toLong())
        merge.animateTo(1f, MotionTokens.introLetter.spec())
    }
    val apart = 1 - merge.value
    Row(horizontalArrangement = Arrangement.spacedBy((Spacing.s.value * apart).coerceAtLeast(0f).dp)) {
        chunks.forEachIndexed { index, chunk ->
            val t = arrivals[index].value
            Text(
                chunk,
                style = HERO_STYLE,
                softWrap = false,
                modifier = Modifier
                    .padding(horizontal = (Spacing.xs.value * apart).coerceAtLeast(0f).dp)
                    .graphicsLayer {
                        alpha = t.coerceIn(0f, 1f)
                        translationY = 18.dp.toPx() * (1 - t)
                    },
            )
        }
    }
}
