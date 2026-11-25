package com.syll.features.practice.results

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syll.R
import com.syll.designsystem.components.cardSurface
import com.syll.designsystem.components.pressScaleClickable
import com.syll.designsystem.motion.LocalMotion
import com.syll.designsystem.motion.MotionTokens
import com.syll.designsystem.motion.RollingNumber
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography
import com.syll.domain.practice.ResultTile
import com.syll.features.practice.SyllieReactionView
import com.syll.designsystem.components.SyllieMood

/**
 * iOS `ResultTileView`: one of the equal-height tiles on the results screen, a title, one number and a caption.
 * Tapping it opens the detail sheet at its block. [revealed] false: the number waits at 0 and counts up once true.
 */
@Composable
fun ResultTileView(tile: ResultTile, revealed: Boolean, modifier: Modifier = Modifier, onTap: (() -> Unit)? = null) {
    val motion = LocalMotion.current
    val hint = stringResource(R.string.shows_the_words)
    val numberLabel = numberLabel(tile.number)
    val title = tile.title()
    val caption = tile.caption.text()
    Column(
        modifier
            .fillMaxHeight()
            .then(if (onTap != null) Modifier.pressScaleClickable(onClick = onTap) else Modifier)
            .cardSurface()
            .padding(Spacing.m)
            .semantics(mergeDescendants = true) { contentDescription = "$title, $numberLabel $caption${if (onTap != null) ". $hint" else ""}" },
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                title,
                style = Typography.micro.copy(fontWeight = FontWeight.SemiBold),
                color = SyllColors.mutedInk,
                maxLines = 2,
                modifier = Modifier.weight(1f),
            )
            if (tile.celebrates) {
                SyllieReactionView(if (motion.reduceMotion) SyllieMood.IDLE else SyllieMood.PROUD, Modifier.size(36.dp, 32.dp))
            }
        }
        val numberStyle = Typography.stat.copy(letterSpacing = Typography.tightTracking.sp)
        Box(Modifier.padding(top = Spacing.xxs)) {
            when (val number = tile.number) {
                ResultTile.Number.None -> Text("—", style = numberStyle, color = SyllColors.ink)
                is ResultTile.Number.Fraction -> RollingNumber(
                    if (revealed) number.part else 0, numberStyle, SyllColors.ink, spec = MotionTokens.progress,
                    text = { "$it/${number.total}" },
                )
                is ResultTile.Number.Plus -> RollingNumber(
                    if (revealed) number.value else 0, numberStyle, SyllColors.ink, spec = MotionTokens.progress, text = { "+$it" },
                )
                is ResultTile.Number.Count -> Text("${number.value}", style = numberStyle.copy(fontFeatureSettings = "tnum"), color = SyllColors.ink)
            }
        }
        Text(caption, style = Typography.micro, color = SyllColors.mutedInk)
        Spacer(Modifier.weight(1f, fill = false))
    }
}

@Composable
private fun numberLabel(number: ResultTile.Number): String = when (number) {
    ResultTile.Number.None -> stringResource(R.string.none)
    is ResultTile.Number.Fraction -> stringResource(R.string.x_of_x, number.part, number.total)
    is ResultTile.Number.Plus -> "+${number.value}"
    is ResultTile.Number.Count -> "${number.value}"
}

/**
 * iOS `SegmentedResultBar`: three proportional parts, solid, light and striped, so the levels read without colour.
 * Grows from the leading edge once [revealed].
 */
@Composable
fun SegmentedResultBar(segments: List<Int>, revealed: Boolean, modifier: Modifier = Modifier) {
    val motion = LocalMotion.current
    val grow = remember { Animatable(if (revealed || motion.reduceMotion) 1f else 0f) }
    LaunchedEffect(revealed) {
        if (!revealed || grow.value == 1f) return@LaunchedEffect
        // iOS `progress.delay(celebrationStatsDelay)`.
        grow.animateTo(1f, MotionTokens.progress.copy(delaySeconds = MotionTokens.celebrationStatsDelay).spec())
    }
    BoxWithConstraints(modifier.fillMaxWidth().height(BAR_HEIGHT).clip(CircleShape).background(SyllColors.track)) {
        val width = maxWidth
        val total = maxOf(segments.sum(), 1)
        val parts = segments.withIndex().filter { it.value > 0 }
        val gaps = maxOf(parts.size - 1, 0) * 2
        Row(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = grow.value
                    transformOrigin = TransformOrigin(0f, 0.5f)
                },
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            parts.forEach { (level, count) ->
                ResultSwatchFill(level, Modifier.width((width - gaps.dp) * count / total).fillMaxHeight())
            }
        }
    }
}

private val BAR_HEIGHT = 10.dp

/** iOS `ResultSwatch`: one bar segment's look, used in the bar and as its key next to the group headers. */
@Composable
fun ResultSwatch(level: Int, modifier: Modifier = Modifier) {
    ResultSwatchFill(level, modifier.clip(RoundedCornerShape(2.dp)))
}

@Composable
private fun ResultSwatchFill(level: Int, modifier: Modifier) {
    when (level) {
        0 -> Box(modifier.background(SyllColors.resultStrong))
        1 -> Box(modifier.background(SyllColors.resultMid))
        // Diagonal hatch for the weakest part.
        else -> Canvas(modifier.background(SyllColors.resultWeakSurface)) {
            val step = 4.dp.toPx()
            var x = -size.height
            while (x < size.width) {
                drawLine(SyllColors.resultWeakStripe, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 1.5.dp.toPx())
                x += step
            }
        }
    }
}

@Composable
fun ResultTile.title(): String = stringResource(
    when (kind) {
        ResultTile.Kind.REVIEW -> R.string.review
        ResultTile.Kind.NEW_WORDS -> R.string.new_words
        ResultTile.Kind.PRACTICE -> R.string.practice
        ResultTile.Kind.WEAK_REMAINING -> R.string.still_to_practise
        ResultTile.Kind.SPEAKING -> R.string.speaking
    },
)

@Composable
fun ResultTile.Caption.text(): String = stringResource(
    when (this) {
        ResultTile.Caption.REMEMBERED -> R.string.remembered_2
        ResultTile.Caption.NO_REVIEWS -> R.string.no_reviews_today
        ResultTile.Caption.PICKED_UP -> R.string.picked_up
        ResultTile.Caption.NO_NEW_WORDS -> R.string.no_new_words_today
        ResultTile.Caption.PRACTICE_CLEAN -> R.string.right_on_your_own
        ResultTile.Caption.NO_SKILL_MEASURED -> R.string.no_skills_measured_this_session
        ResultTile.Caption.WEAK_REMAINING -> R.string.words_to_practise
        ResultTile.Caption.NO_WEAK_WORDS_LEFT -> R.string.no_weak_words_left
        ResultTile.Caption.SAID_RIGHT -> R.string.said_right
    },
)

/** iOS `ResultTile.Line.text`. */
@Composable
fun ResultTile.Line.text(): String = when (this) {
    is ResultTile.Line.Relearn -> stringResource(R.string.x_to_relearn, count)
    is ResultTile.Line.Shaky -> stringResource(R.string.x_not_solid_yet, count)
    is ResultTile.Line.NeededRetry -> stringResource(R.string.x_needed_a_retry, count)
    is ResultTile.Line.SawAnswer -> stringResource(R.string.x_saw_the_answer, count)
    is ResultTile.Line.AlreadyKnew -> stringResource(R.string.x_already_knew, count)
    is ResultTile.Line.Missed -> stringResource(R.string.x_not_done_yet, count)
    is ResultTile.Line.Helped -> stringResource(R.string.x_needed_help, count)
    is ResultTile.Line.WeakDelta -> stringResource(R.string.x_after_this_session, ResultTile.Line.signed(delta))
    is ResultTile.Line.BecameFluent -> stringResource(R.string.x_now_fluent, count)
    is ResultTile.Line.Slipped -> stringResource(R.string.x_to_practise_again, count)
}
