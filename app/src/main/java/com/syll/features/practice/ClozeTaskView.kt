package com.syll.features.practice

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
 * iOS `ClozeTaskView` (`clozeSelect`): a word bank, and the picked word goes into the blank. Nothing is sent until
 * Check. (The iOS word flight between bank and blank is a later polish; here the word swaps places.)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClozeTaskView(attempt: TaskAttempt) {
    val store = LocalPracticeSessionStore.current
    val body = attempt.task.body as? TaskBody.Choice ?: return
    val sentence = body.sentence ?: return
    var selectedId by remember(attempt.task.id) { mutableStateOf<Int?>(null) }

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

    TaskScaffold(
        attempt,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xl + Spacing.xs)) {
                ClozeSentence(sentence.let(ClozeLine::of), sentence.before, sentence.after, filled, filledState) {
                    if (attempt.status == TaskAttempt.Status.ANSWERING) {
                        selectedId = null
                        store.answerPicked()
                    }
                }
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CLOZE_GAP, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(CLOZE_GAP),
                ) {
                    body.options.forEach { option ->
                        if (option.id == filledId) {
                            // Holds the word's place so the bank doesn't reflow.
                            Box {
                                ClozeTile(option.text, WordTileState.IDLE, Modifier.alpha(0f))
                                Box(
                                    Modifier
                                        .matchParentSize()
                                        .padding(bottom = Spacing.tileDepth)
                                        .background(SyllColors.neutralSurface, RoundedCornerShape(Radii.chip)),
                                )
                            }
                        } else {
                            val label = stringResource(R.string.x_option, option.text)
                            ClozeTile(
                                option.text,
                                if (attempt.isFinished) WordTileState.DISABLED else WordTileState.IDLE,
                                Modifier
                                    .pressScaleClickable(enabled = attempt.status == TaskAttempt.Status.ANSWERING) {
                                        selectedId = option.id
                                        store.answerPicked()
                                    }
                                    .semantics { contentDescription = label },
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

/** The sentence as separate words, so the blank sits in the line where the word lands. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClozeSentence(
    line: ClozeLine,
    before: String,
    after: String,
    filled: TaskOption?,
    filledState: WordTileState,
    onRemove: () -> Unit,
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
                ClozeTile(filled.text, filledState, Modifier.pressScaleClickable(onClick = onRemove))
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
