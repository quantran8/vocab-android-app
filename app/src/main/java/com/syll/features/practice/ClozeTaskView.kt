package com.syll.features.practice

import com.syll.designsystem.motion.MotionTokens
import com.syll.designsystem.motion.LocalMotion
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.offset
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.syll.R
import com.syll.app.LocalPracticeSessionStore
import com.syll.designsystem.components.WordTileState
import com.syll.designsystem.components.pressScaleClickable
import com.syll.designsystem.tokens.Borders
import com.syll.designsystem.tokens.Radii
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography
import com.syll.domain.practice.ClozeLine
import com.syll.domain.practice.TaskAttempt
import com.syll.domain.practice.TaskBody
import com.syll.domain.practice.TaskOption

/**
 * iOS `ClozeTaskView` (`clozeSelect`): a word bank, and the picked word flies into the blank. Nothing is sent until
 * Check. Each word is one tile for the whole task, drawn above the sentence and the bank; flying only changes which
 * frame (its bank slot or the blank) it is placed on, so no copy of a word is left behind to catch the next move.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClozeTaskView(attempt: TaskAttempt) {
    val store = LocalPracticeSessionStore.current
    val motion = LocalMotion.current
    val body = attempt.task.body as? TaskBody.Choice ?: return
    val sentence = body.sentence ?: return
    var selectedId by remember(attempt.task.id) { mutableStateOf<Int?>(null) }
    /** Words ignore taps while any word is in the air, so a double-tap can't send one straight back. */
    var lastFlight by remember { mutableLongStateOf(0L) }
    /** "Try again" sends the wrong word home with `returnToOrigin`; every other move is a `wordFlight`. */
    var isReturning by remember { mutableStateOf(false) }
    var previousStatus by remember { mutableStateOf(attempt.status) }
    LaunchedEffect(attempt.status) {
        if (previousStatus == TaskAttempt.Status.WRONG && attempt.status == TaskAttempt.Status.ANSWERING) {
            isReturning = true
            lastFlight = SystemClock.uptimeMillis()
        }
        previousStatus = attempt.status
    }

    val filledId = when (attempt.status) {
        TaskAttempt.Status.SOLVED, TaskAttempt.Status.REVEALED -> body.correctOptionId
        TaskAttempt.Status.WRONG, TaskAttempt.Status.EXHAUSTED -> attempt.lastWrongOptionId
        else -> selectedId
    }
    val filledState = when (attempt.status) {
        TaskAttempt.Status.SOLVED, TaskAttempt.Status.REVEALED -> WordTileState.CORRECT
        TaskAttempt.Status.WRONG, TaskAttempt.Status.EXHAUSTED -> WordTileState.INCORRECT
        else -> WordTileState.SELECTED
    }
    val filled = body.options.firstOrNull { it.id == filledId }
    val frames = remember { TileFrames<ClozeTarget>() }

    fun tap(option: TaskOption, isFilled: Boolean) {
        if (attempt.status != TaskAttempt.Status.ANSWERING) return
        val now = SystemClock.uptimeMillis()
        if (now - lastFlight < (MotionTokens.wordFlightSettle * 1000).toLong()) return
        lastFlight = now
        isReturning = false
        selectedId = if (isFilled) null else option.id
        store.answerPicked()
    }

    TaskScaffold(
        attempt,
        content = {
            Box(Modifier.fillMaxWidth().onGloballyPositioned { frames.container = it }) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xl + Spacing.xs)) {
                    ClozeSentence(sentence.let(ClozeLine::of), sentence.before, sentence.after, filled, frames)
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(CLOZE_GAP, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(CLOZE_GAP),
                    ) {
                        body.options.forEach { option ->
                            // Holds the word's place so the bank doesn't reflow. The gray stand-in shows whenever the
                            // word is away (its tile flies above it).
                            Box(Modifier.onGloballyPositioned { frames.place(ClozeTarget.Slot(option.id), it) }) {
                                ClozeTile(option.text, WordTileState.IDLE, Modifier.alpha(0f).clearAndSetSemantics { })
                                Box(
                                    Modifier
                                        .matchParentSize()
                                        .padding(bottom = Spacing.tileDepth)
                                        .background(SyllColors.neutralSurface, RoundedCornerShape(Radii.chip)),
                                )
                            }
                        }
                    }
                }
                // Above both the sentence and the bank, so a flying word is never drawn under either.
                Box(Modifier.matchParentSize()) {
                    body.options.forEach { option ->
                        key(option.id) {
                            val isFilled = option.id == filledId
                            val target = frames[if (isFilled) ClozeTarget.Blank else ClozeTarget.Slot(option.id)]?.topLeft
                            val spec = if (isReturning) MotionTokens.returnToOrigin else MotionTokens.wordFlight
                            val position = rememberFlightPosition(target, motion.travel(spec))
                            val label = stringResource(R.string.x_option, option.text)
                            ClozeTile(
                                option.text,
                                if (isFilled) filledState else if (attempt.isFinished) WordTileState.DISABLED else WordTileState.IDLE,
                                Modifier
                                    .offset { position?.round() ?: IntOffset.Zero }
                                    .alpha(if (position == null) 0f else 1f)
                                    .pressScaleClickable(enabled = attempt.status == TaskAttempt.Status.ANSWERING) { tap(option, isFilled) }
                                    // The sentence reads the filled word.
                                    .then(if (isFilled) Modifier.clearAndSetSemantics { } else Modifier.semantics { contentDescription = label }),
                            )
                        }
                    }
                }
            }
        },
        action = {
            TaskActionBar(attempt, canCheck = selectedId != null, onCheck = {
                selectedId?.let {
                    store.submitOption(it)
                    selectedId = null
                }
            })
        },
    )
}

private val CLOZE_GAP = Spacing.xs + 2.dp

/** The frames a word can sit in. */
private sealed interface ClozeTarget {
    data object Blank : ClozeTarget
    data class Slot(val id: Int) : ClozeTarget
}

/** The sentence as separate words, so the blank sits in the line where the word lands. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClozeSentence(
    line: ClozeLine,
    before: String,
    after: String,
    filled: TaskOption?,
    frames: TileFrames<ClozeTarget>,
) {
    val blankWord = stringResource(R.string.blank)
    val label = before + (filled?.text ?: blankWord) + after
    @Composable
    fun word(text: String) {
        if (text.isEmpty()) return
        Text(
            text,
            style = Typography.sentence.copy(fontWeight = FontWeight.SemiBold),
            color = SyllColors.ink,
            modifier = Modifier.defaultMinSize(minHeight = Spacing.minTouchTarget).padding(top = 8.dp),
        )
    }
    FlowRow(
        Modifier.fillMaxWidth().padding(vertical = Spacing.m).semantics(mergeDescendants = true) { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs + 2.dp, Alignment.CenterHorizontally),
    ) {
        line.wordsBefore.forEach { word(it) }
        Row(verticalAlignment = Alignment.Bottom) {
            word(line.attachedBefore)
            if (filled != null) {
                // The blank takes the word's size; the word itself is the flying tile drawn above.
                ClozeTile(
                    filled.text,
                    WordTileState.SELECTED,
                    Modifier.alpha(0f).onGloballyPositioned { frames.place(ClozeTarget.Blank, it) },
                )
            } else {
                Box(
                    Modifier
                        .width(72.dp)
                        .height(Spacing.minTouchTarget)
                        .drawBehind {
                            drawRect(SyllColors.interactive, topLeft = Offset(0f, size.height - 2.dp.toPx()), size = size.copy(height = 2.dp.toPx()))
                        },
                )
            }
            word(line.attachedAfter)
        }
        line.wordsAfter.forEach { word(it) }
    }
}

/** A word tile in the reorder style, the same in the bank and in the blank. */
@Composable
private fun ClozeTile(text: String, state: WordTileState, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Radii.chip)
    val (ink, surface, depth, border) = when (state) {
        WordTileState.CORRECT -> listOf(SyllColors.successInk, SyllColors.successSurface, SyllColors.successDepth, SyllColors.success)
        WordTileState.INCORRECT -> listOf(SyllColors.errorInk, SyllColors.errorSurface, SyllColors.errorDepth, SyllColors.error)
        WordTileState.SELECTED -> listOf(SyllColors.selectedInk, SyllColors.selectedSurface, SyllColors.selectedDepth, SyllColors.interactive)
        else -> listOf(SyllColors.ink, SyllColors.surface, SyllColors.tileDepth, SyllColors.stroke)
    }
    Box(
        modifier
            .alpha(if (state == WordTileState.DISABLED) 0.6f else 1f)
            .padding(bottom = Spacing.tileDepth)
            .drawBehind {
                drawRoundRect(depth, topLeft = Offset(0f, Spacing.tileDepth.toPx()), size = size, cornerRadius = CornerRadius(Radii.chip.toPx()))
            }
            .background(surface, shape)
            .border(Borders.card, border, shape)
            .defaultMinSize(minHeight = Spacing.minTouchTarget)
            .padding(horizontal = Spacing.m),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Typography.tileLarge, color = ink)
    }
}
