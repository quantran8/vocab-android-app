package com.syll.features.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syll.R
import com.syll.app.LocalPracticeSessionStore
import com.syll.designsystem.components.pressScaleClickable
import com.syll.designsystem.tokens.Borders
import com.syll.designsystem.tokens.Radii
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.domain.practice.TaskAttempt
import com.syll.domain.practice.TaskBody
import com.syll.domain.practice.TaskOption

private val TILE_WIDTH_MAX = 54.dp
private val TILE_HEIGHT = 54.dp
private val TILE_SPACING = Spacing.xxs + 2.dp
/** Keeps a tile's depth clear of the ruled line it sits above. */
private val RULED_LINE_GAP = Spacing.xxs
private val RULED_ROW_HEIGHT = TILE_HEIGHT + Spacing.tileDepth + Spacing.xs + RULED_LINE_GAP
private val TILE_TEXT = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 22.sp)

/**
 * iOS `ReorderTaskView` (`flyReorder`): tap tiles onto the ruled answer lines, tap to send them back. The row always
 * comes from `attempt.placedTileIds`, the state the backend will replay. (Drag to place and reorder is a later polish.)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReorderTaskView(attempt: TaskAttempt) {
    val store = LocalPracticeSessionStore.current
    val tiles = (attempt.task.body as? TaskBody.Reorder)?.tiles ?: return
    // A single word has no space tile and must always sit on one line.
    val isSingleWord = tiles.none { it.text == " " }
    val minimumLines = if (isSingleWord) 1 else 2
    val canAnswer = attempt.status == TaskAttempt.Status.ANSWERING

    TaskScaffold(
        attempt,
        content = {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // Tile width shrinks so every letter of a single word fits one line, in the row and in the bank.
                val tileWidth = if (isSingleWord) {
                    val count = tiles.size
                    ((maxWidth - TILE_SPACING * (count - 1)) / count - 1.dp).coerceIn(20.dp, TILE_WIDTH_MAX)
                } else {
                    TILE_WIDTH_MAX
                }
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                    // The answer row on notebook lines.
                    FlowRow(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = RULED_ROW_HEIGHT * minimumLines)
                            .drawBehind {
                                val row = RULED_ROW_HEIGHT.toPx()
                                val lines = maxOf(minimumLines, Math.round(size.height / row))
                                for (line in 1..lines) {
                                    val y = row * line - 1
                                    drawRect(SyllColors.stroke, topLeft = Offset(0f, y - Borders.card.toPx()), size = size.copy(height = Borders.card.toPx()))
                                }
                            },
                        horizontalArrangement = Arrangement.spacedBy(TILE_SPACING),
                    ) {
                        attempt.placedTiles.forEach { tile ->
                            Box(Modifier.height(RULED_ROW_HEIGHT).padding(bottom = RULED_LINE_GAP), contentAlignment = Alignment.BottomCenter) {
                                val label = stringResource(R.string.remove_answer)
                                LetterTile(
                                    tile, tileWidth, attempt, isPlaced = true,
                                    Modifier
                                        .pressScaleClickable(enabled = canAnswer) { store.removeTile(tile.id) }
                                        .semantics { contentDescription = "${tile.text}. $label" },
                                )
                            }
                        }
                    }
                    // The bank: every tile keeps its slot, so the bank doesn't reflow as tiles leave it.
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(TILE_SPACING, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(TILE_SPACING),
                    ) {
                        tiles.forEach { tile ->
                            val isPlaced = tile.id in attempt.placedTileIds
                            Box {
                                if (isPlaced) {
                                    LetterTile(tile, tileWidth, attempt, isPlaced = false, Modifier.alpha(0f))
                                    Box(
                                        Modifier
                                            .matchParentSize()
                                            .padding(bottom = Spacing.tileDepth)
                                            .background(SyllColors.neutralSurface, RoundedCornerShape(Radii.chip)),
                                    )
                                } else {
                                    LetterTile(
                                        tile, tileWidth, attempt, isPlaced = false,
                                        Modifier
                                            .pressScaleClickable(enabled = canAnswer) { store.tapTile(tile.id) }
                                            .semantics { contentDescription = tile.text },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        action = {
            if (attempt.status == TaskAttempt.Status.ANSWERING) HintControl(attempt)
            TaskActionBar(attempt, canCheck = attempt.placedTileIds.isNotEmpty(), onCheck = store::submitTiles, showsReveal = false)
        },
    )
}

@Composable
private fun LetterTile(tile: TaskOption, width: Dp, attempt: TaskAttempt, isPlaced: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Radii.chip)
    val isWrong = (attempt.status == TaskAttempt.Status.WRONG || attempt.status == TaskAttempt.Status.EXHAUSTED) && isPlaced
    val isRight = attempt.status == TaskAttempt.Status.SOLVED
    val ink = if (isRight) SyllColors.successInk else if (isWrong) SyllColors.errorInk else SyllColors.ink
    val face = if (isRight) SyllColors.successSurface else if (isWrong) SyllColors.errorSurface else SyllColors.surface
    val border = if (isRight) SyllColors.success else if (isWrong) SyllColors.error else SyllColors.stroke
    val depth = if (isRight) SyllColors.successDepth else if (isWrong) SyllColors.errorDepth else SyllColors.tileDepth
    Box(
        modifier
            .padding(bottom = Spacing.tileDepth)
            .drawBehind {
                drawRoundRect(depth, topLeft = Offset(0f, Spacing.tileDepth.toPx()), size = size, cornerRadius = CornerRadius(Radii.chip.toPx()))
            }
            .background(face, shape)
            .border(Borders.card, border, shape)
            .widthIn(min = width)
            .defaultMinSize(minHeight = TILE_HEIGHT)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        // A space is a real tile; show something that can be tapped.
        Text(if (tile.text == " ") "␣" else tile.text, style = TILE_TEXT, color = ink)
    }
}
