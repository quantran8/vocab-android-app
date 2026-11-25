package com.syll.features.practice

import android.icu.text.ListFormatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syll.R
import com.syll.designsystem.motion.LocalMotion
import com.syll.designsystem.motion.MotionTokens
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography
import com.syll.domain.practice.PronunciationBand
import com.syll.domain.practice.PronunciationSegment
import com.syll.domain.practice.PronunciationSyllable
import com.syll.domain.practice.SyllableBand
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** iOS `RecordingPromptStyle`: how a backend-scored Recording draws its word. */
sealed interface RecordingPromptStyle {
    /** Sweeping while the take is scored. */
    data object Scoring : RecordingPromptStyle
    /** Coloured per syllable; null-band segments (the text between syllables) stay ink. */
    data class Scored(val segments: List<PronunciationSegment>) : RecordingPromptStyle
}

/** iOS `RecordingPromptWord`: the prompt word of a backend-scored Recording. [style] comes from `TaskPromptView`. */
@Composable
fun RecordingPromptWord(text: String, style: RecordingPromptStyle, textStyle: TextStyle) {
    when (style) {
        RecordingPromptStyle.Scoring -> ScoringSweepText(text, textStyle)
        // A new take's segments start a fresh reveal (one colour animation per segment).
        is RecordingPromptStyle.Scored -> key(style.segments) { ScoredWord(text, style.segments, textStyle) }
    }
}

/** Syllables take their colour in reading order once the score ring has filled. */
@Composable
private fun ScoredWord(text: String, segments: List<PronunciationSegment>, textStyle: TextStyle) {
    val motion = LocalMotion.current
    val syllableCount = segments.count { it.band != null }
    var coloured by remember(segments) { mutableIntStateOf(if (motion.reduceMotion) syllableCount else 0) }
    LaunchedEffect(segments, motion.reduceMotion) {
        if (motion.reduceMotion || syllableCount == 0) {
            coloured = syllableCount
            return@LaunchedEffect
        }
        coloured = 0
        delay((MotionTokens.scoreRingFillDuration * 1000).toLong())
        for (index in 1..syllableCount) {
            coloured = index
            delay((MotionTokens.syllableColorStagger * 1000).toLong())
        }
    }
    // One animated colour per syllable (iOS `withAnimation(syllableColor)` per step).
    var syllableIndex = 0
    val colors = segments.map { segment ->
        val band = segment.band
        val target = if (band != null && syllableIndex++ < coloured) band.wordColor else SyllColors.ink
        animateColorAsState(target, motion.animation(MotionTokens.syllableColor), label = "syllable").value
    }
    val annotated = buildAnnotatedString {
        segments.forEachIndexed { index, segment -> withStyle(SpanStyle(color = colors[index])) { append(segment.text) } }
    }
    val summary = PronunciationSummary.text(segments.filter { it.band == SyllableBand.POOR }.map { it.text })
    Text(
        annotated,
        style = textStyle,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics {
            contentDescription = text
            stateDescription = summary
        },
    )
}

/** iOS `ScoringSweepText`: the word in Ink 300 with a light sweeping across it while the take is scored. Reduce motion: no sweep. */
@Composable
private fun ScoringSweepText(text: String, textStyle: TextStyle) {
    val motion = LocalMotion.current
    val phase = if (motion.reduceMotion) {
        null
    } else {
        rememberInfiniteTransition(label = "sweep").animateFloat(
            0f, 1f,
            infiniteRepeatable(tween((MotionTokens.scoringSweepPeriod * 1000).toInt(), easing = LinearEasing)),
            label = "sweepPhase",
        )
    }
    Text(
        text,
        style = textStyle,
        color = SyllColors.ink300,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .semantics { contentDescription = text }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val progress = phase?.value ?: return@drawWithContent
                // The word is English: the sweep runs left to right in every app language. Drawn only on the glyphs.
                val band = size.width * 0.5f
                val start = -band + (size.width + band) * progress
                drawRect(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, SyllColors.ink50, Color.Transparent),
                        startX = start,
                        endX = start + band,
                    ),
                    blendMode = BlendMode.SrcAtop,
                )
            },
    )
}

/** iOS `PronunciationScoreRing`: the overall score in the mic's place, a ring that fills while the number counts up. */
@Composable
fun PronunciationScoreRing(score: Double, band: PronunciationBand) {
    val motion = LocalMotion.current
    val shown = remember { Animatable(if (motion.reduceMotion) score.toFloat() else 0f) }
    LaunchedEffect(score) {
        if (motion.reduceMotion) shown.snapTo(score.toFloat()) else shown.animateTo(score.toFloat(), MotionTokens.scoreRingFill.spec())
    }
    val label = PronunciationSummary.scoreText(score)
    val ring = band.ringColor
    Box(
        Modifier.size(SCORE_RING_SIZE).clearAndSetSemantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(SCORE_RING_SIZE)) {
            val line = SCORE_RING_LINE.toPx()
            // SwiftUI strokes centred on a circle inset by half the line, so the ring fills the box exactly.
            val inset = line / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(SyllColors.neutralSurface, 0f, 360f, false, topLeft, arcSize, style = Stroke(line))
            val fraction = shown.value.coerceIn(0f, 100f) / 100f
            if (fraction > 0f) {
                drawArc(ring, -90f, 360f * fraction, false, topLeft, arcSize, style = Stroke(line, cap = StrokeCap.Round))
            }
        }
        Text(
            "${shown.value.roundToInt()}",
            style = Typography.scoreHero.copy(letterSpacing = Typography.displayTracking.sp, fontFeatureSettings = "tnum"),
            color = SyllColors.ink,
        )
    }
}

/** The ring's own size, not the mic's halo box: the title has to stay on screen above the panel on a small phone. */
val SCORE_RING_SIZE = 176.dp
private val SCORE_RING_LINE = 14.dp

/** The band's title under the ring. */
@Composable
fun PronunciationBandTitle(band: PronunciationBand) {
    Text(
        band.title(),
        style = Typography.sheetTitle.copy(letterSpacing = Typography.tightTracking.sp),
        color = band.titleColor,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() },
    )
}

/**
 * iOS `PronunciationDetail`: the feedback panel of a scored take, one line naming the syllables to practise and
 * "Details", which expands the panel in place to every syllable's sounds and score.
 */
@Composable
fun PronunciationDetail(syllables: List<PronunciationSyllable>, passed: Boolean) {
    val motion = LocalMotion.current
    var isExpanded by remember { mutableStateOf(false) }
    val ink = if (passed) SyllColors.successInk else SyllColors.errorInk
    val chevron by animateFloatAsState(if (isExpanded) 180f else 0f, motion.animation(MotionTokens.panelExpand), label = "chevron")
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = Spacing.minTouchTarget)
                .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { isExpanded = !isExpanded }
                .semantics { selected = isExpanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(
                PronunciationSummary.text(syllables.filter { it.band == SyllableBand.POOR }.map { it.text }),
                style = Typography.secondary,
                color = ink,
                modifier = Modifier.weight(1f),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.details), style = Typography.actionSmall, color = ink)
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = ink,
                    modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = chevron },
                )
            }
        }
        // Like a sheet changing detent: one spring for opening and closing; the rows are revealed by the panel's edge.
        AnimatedVisibility(
            isExpanded,
            enter = expandVertically(motion.animation(MotionTokens.panelExpand), expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(motion.animation(MotionTokens.panelExpand), shrinkTowards = Alignment.Top) + fadeOut(),
        ) {
            Column {
                Box(Modifier.fillMaxWidth().height(1.5.dp).background(if (passed) SyllColors.green100 else SyllColors.coral100))
                syllables.forEach { SyllableRow(it) }
            }
        }
    }
}

@Composable
private fun SyllableRow(syllable: PronunciationSyllable) {
    val bold = Typography.secondary.copy(fontWeight = FontWeight.Bold)
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 36.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Text(syllable.text, style = bold, color = syllable.band.wordColor, modifier = Modifier.widthIn(min = 56.dp))
        Text(PronunciationSummary.ipa(syllable.ipa), style = Typography.secondary, color = SyllColors.mutedInk, modifier = Modifier.weight(1f))
        syllable.score?.let {
            Text("${it.roundToInt()}", style = bold.copy(fontFeatureSettings = "tnum"), color = syllable.band.wordColor)
        }
    }
}

/** iOS `PronunciationSummary`: copy shared by the word's spoken value, the panel line and the score ring. */
object PronunciationSummary {
    @Composable
    fun text(poor: List<String>): String {
        if (poor.isEmpty()) return stringResource(R.string.all_sounds_were_good)
        val locale = LocalConfiguration.current.locales[0]
        return stringResource(R.string.needs_practice_x, ListFormatter.getInstance(locale).format(poor))
    }

    @Composable
    fun scoreText(score: Double): String = stringResource(R.string.pronunciation_score_x, score.roundToInt())

    /** The server may send the sounds with or without slashes. */
    fun ipa(ipa: String?): String {
        if (ipa.isNullOrEmpty()) return ""
        return if (ipa.startsWith("/")) ipa else "/$ipa/"
    }
}

@Composable
fun PronunciationBand.title(): String = stringResource(
    when (this) {
        PronunciationBand.EXCELLENT -> R.string.excellent
        PronunciationBand.GOOD, PronunciationBand.UNKNOWN -> R.string.good_job
        PronunciationBand.FAIR -> R.string.almost_there
        PronunciationBand.POOR -> R.string.keep_practicing
    },
)

val PronunciationBand.titleColor: Color
    get() = when (this) {
        PronunciationBand.EXCELLENT, PronunciationBand.GOOD, PronunciationBand.UNKNOWN -> SyllColors.green700
        PronunciationBand.FAIR -> SyllColors.sun700
        PronunciationBand.POOR -> SyllColors.coral700
    }

val PronunciationBand.ringColor: Color
    get() = when (this) {
        PronunciationBand.EXCELLENT, PronunciationBand.GOOD, PronunciationBand.UNKNOWN -> SyllColors.green600
        PronunciationBand.FAIR -> SyllColors.sun600
        PronunciationBand.POOR -> SyllColors.coral600
    }

val SyllableBand.wordColor: Color
    get() = when (this) {
        SyllableBand.GOOD -> SyllColors.green700
        SyllableBand.POOR -> SyllColors.coral600
        SyllableBand.UNKNOWN -> SyllColors.ink
    }
