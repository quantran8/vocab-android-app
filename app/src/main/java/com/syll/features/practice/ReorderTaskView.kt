package com.syll.features.practice

import kotlin.math.abs
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.syll.designsystem.motion.MotionTokens
import com.syll.designsystem.motion.LocalMotion
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.animation.core.animateFloatAsState
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
 * iOS `ReorderTaskView` (`flyReorder`): tap tiles onto the ruled answer lines, tap to send them back, drag to place
 * or reorder. The row always comes from `attempt.placedTileIds`, the state the backend will replay; while a tile is
 * dragged, the row previews where it would drop. Each tile is drawn once above the row and the bank and springs
 * (`snap`) between its places, like iOS `matchedGeometryEffect`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReorderTaskView(attempt: TaskAttempt) {
    val store = LocalPracticeSessionStore.current
    val motion = LocalMotion.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val tiles = (attempt.task.body as? TaskBody.Reorder)?.tiles ?: return
    // A single word has no space tile and must always sit on one line.
    val isSingleWord = tiles.none { it.text == " " }
    val minimumLines = if (isSingleWord) 1 else 2
    val canAnswer = attempt.status == TaskAttempt.Status.ANSWERING
    val frames = remember { TileFrames<ReorderSlot>() }
    // The drag gesture lives across updates of the attempt; its helpers read the latest one.
    val latest by rememberUpdatedState(attempt)

    var dragId by remember { mutableStateOf<Int?>(null) }
    var dragFromBank by remember { mutableStateOf(false) }
    /** The dragged tile's top-left, in content coordinates. */
    var dragTopLeft by remember { mutableStateOf(Offset.Zero) }
    var insertIndex by remember { mutableStateOf<Int?>(null) }
    /** Tiles still travelling after a tap. Tapping one again mid-flight would send it straight back. */
    val flyingIds = remember { mutableStateListOf<Int>() }

    // The row as it would look if dropped now: the dragged tile's slot is held open at the insert index.
    fun previewRow(attempt: TaskAttempt): List<TaskOption> {
        val id = dragId
        val index = insertIndex
        val dragged = tiles.firstOrNull { it.id == id }
        if (id == null || index == null || dragged == null) return attempt.placedTiles
        return attempt.placedTiles.filter { it.id != id }.toMutableList().apply { add(minOf(index, size), dragged) }
    }
    val panelTiles = previewRow(attempt)
    val panelIds = panelTiles.map { it.id }.toSet()

    fun fly(id: Int, body: () -> Unit) {
        if (id in flyingIds) return
        if (motion.reduceMotion) return body()
        flyingIds += id
        body()
        scope.launch {
            delay((MotionTokens.snapSettle * 1000).toLong())
            flyingIds -= id
        }
    }

    /**
     * Where the row would insert a tile centred at [point] (the dragged tile, not the finger), or null outside the
     * answer panel. Over another tile it takes that tile's place; between tiles it goes after those whose centre it passed.
     */
    fun slotIndex(point: Offset): Int? {
        val panel = frames[ReorderSlot.Panel] ?: return null
        val margin = with(density) { Spacing.s.toPx() }
        if (!panel.inflate(margin).contains(point)) return null
        val row = previewRow(latest)
        val hovered = row.indexOfFirst { it.id != dragId && frames[ReorderSlot.Row(it.id)]?.contains(point) == true }
        if (hovered >= 0) return hovered
        val rowHeight = with(density) { RULED_ROW_HEIGHT.toPx() }
        return latest.placedTiles.filter { it.id != dragId }.count { tile ->
            val frame = frames[ReorderSlot.Row(tile.id)] ?: return@count false
            if (abs(frame.center.y - point.y) < rowHeight / 2) frame.center.x < point.x else frame.center.y < point.y
        }
    }

    /** Drops from the bank append first, then move; drops from the row only move. */
    fun place(id: Int, index: Int) {
        if (latest.bankTiles.any { it.id == id }) store.tapTile(id)
        val current = store.currentAttempt?.placedTileIds ?: return
        if (id !in current) return
        store.moveTile(id, minOf(index, current.size - 1))
    }

    /** The tile under [point]: where it is drawn now, row tiles first (they sit above the bank). */
    fun tileAt(point: Offset): Pair<Int, Boolean>? {
        latest.placedTiles.firstOrNull { frames[ReorderSlot.Row(it.id)]?.contains(point) == true }?.let { return it.id to false }
        return latest.bankTiles.firstOrNull { frames[ReorderSlot.Bank(it.id)]?.contains(point) == true }?.let { it.id to true }
    }

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
                Box(
                    Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { frames.container = it }
                        // One gesture for both intents, so a drag can never also fire a tap: a short touch is a
                        // tap, movement is a drag.
                        .pointerInput(attempt.task.id, canAnswer) {
                            if (!canAnswer) return@pointerInput
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val (id, fromBank) = tileAt(down.position) ?: return@awaitEachGesture
                                val start = frames[if (fromBank) ReorderSlot.Bank(id) else ReorderSlot.Row(id)] ?: return@awaitEachGesture
                                val grab = start.topLeft - down.position
                                val grabToCenter = start.center - down.position
                                val dragSlop = 6.dp.toPx()
                                val tapSlop = 10.dp.toPx()
                                var dragging = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    val moved = (change.position - down.position).getDistance()
                                    if (!change.pressed) {
                                        if (dragging) {
                                            val target = insertIndex
                                            if (target != null) place(id, target)
                                            dragId = null
                                            insertIndex = null
                                        } else if (moved < tapSlop) {
                                            fly(id) { if (fromBank) store.tapTile(id) else store.removeTile(id) }
                                        }
                                        break
                                    }
                                    if (!dragging && moved >= dragSlop && id !in flyingIds) {
                                        dragging = true
                                        dragId = id
                                        dragFromBank = fromBank
                                        store.haptics.selectionChanged()
                                    }
                                    if (dragging) {
                                        change.consume()
                                        dragTopLeft = change.position + grab
                                        val index = slotIndex(change.position + grabToCenter)
                                        if (index != insertIndex) {
                                            insertIndex = index
                                            if (index != null) store.haptics.selectionChanged()
                                        }
                                    }
                                }
                                // A cancelled gesture (scroll took over) puts the tile back where it was.
                                if (dragId == id) {
                                    dragId = null
                                    insertIndex = null
                                }
                            }
                        },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                        // The answer row on notebook lines: places only, the tiles are drawn above. The lines live on
                        // a wrapper: an empty FlowRow measures 0×0, so lines drawn on it vanish until a tile is placed.
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = RULED_ROW_HEIGHT * minimumLines)
                                .onGloballyPositioned { frames.place(ReorderSlot.Panel, it) }
                                .drawBehind {
                                    val row = RULED_ROW_HEIGHT.toPx()
                                    val lines = maxOf(minimumLines, Math.round(size.height / row))
                                    for (line in 1..lines) {
                                        val y = row * line - 1
                                        drawRect(SyllColors.stroke, topLeft = Offset(0f, y - Borders.card.toPx()), size = size.copy(height = Borders.card.toPx()))
                                    }
                                },
                        ) {
                            FlowRow(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(TILE_SPACING),
                            ) {
                                panelTiles.forEach { tile ->
                                    key(tile.id) {
                                        Box(Modifier.height(RULED_ROW_HEIGHT).padding(bottom = RULED_LINE_GAP), contentAlignment = Alignment.BottomCenter) {
                                            // The drop preview: a gray stand-in where the dragged tile would land.
                                            Box {
                                                LetterTile(
                                                    tile, tileWidth, attempt, isPlaced = true,
                                                    Modifier
                                                        .alpha(0f)
                                                        .clearAndSetSemantics { }
                                                        .onGloballyPositioned { frames.place(ReorderSlot.Row(tile.id), it) },
                                                )
                                                if (tile.id == dragId) SlotPlaceholder(Modifier.matchParentSize())
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        // The bank: every tile keeps its slot, so the bank doesn't reflow as tiles leave it. The gray
                        // slot sits under the tile, so it stays until a returning tile lands.
                        FlowRow(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(TILE_SPACING, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(TILE_SPACING),
                        ) {
                            tiles.forEach { tile ->
                                Box {
                                    SlotPlaceholder(Modifier.matchParentSize())
                                    LetterTile(
                                        tile, tileWidth, attempt, isPlaced = false,
                                        Modifier
                                            .alpha(0f)
                                            .clearAndSetSemantics { }
                                            .onGloballyPositioned { frames.place(ReorderSlot.Bank(tile.id), it) },
                                    )
                                }
                            }
                        }
                    }

                    // The tiles themselves, above the row and the bank; the dragged one last, so it is on top.
                    Box(Modifier.matchParentSize()) {
                        val removeLabel = stringResource(R.string.remove_answer)
                        tiles.sortedBy { it.id == dragId }.forEach { tile ->
                            key(tile.id) {
                                val isDragged = tile.id == dragId
                                val inRow = tile.id in panelIds
                                val target = frames[if (inRow) ReorderSlot.Row(tile.id) else ReorderSlot.Bank(tile.id)]?.topLeft
                                val position = rememberFlightPosition(
                                    target,
                                    motion.travel(MotionTokens.snap),
                                    held = if (isDragged) dragTopLeft else null,
                                )
                                val lift by animateFloatAsState(
                                    if (isDragged) motion.liftedScale else 1f,
                                    motion.animation(MotionTokens.lift),
                                    label = "tileLift",
                                )
                                val isPlaced = tile.id in attempt.placedTileIds
                                LetterTile(
                                    tile, tileWidth, attempt, isPlaced = if (isDragged) !dragFromBank else isPlaced,
                                    Modifier
                                        .offset { position?.round() ?: IntOffset.Zero }
                                        .graphicsLayer {
                                            alpha = if (position == null) 0f else 1f
                                            scaleX = lift
                                            scaleY = lift
                                            shadowElevation = if (isDragged) 8.dp.toPx() else 0f
                                            shape = RoundedCornerShape(Radii.chip)
                                        }
                                        .semantics {
                                            contentDescription = if (isPlaced) "${tile.text}. $removeLabel" else tile.text
                                            role = Role.Button
                                            if (canAnswer) {
                                                onClick {
                                                    if (isPlaced) store.removeTile(tile.id) else store.tapTile(tile.id)
                                                    true
                                                }
                                            }
                                        },
                                )
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

/** The places a tile can be drawn at, and the answer panel (for drop hit-testing). */
private sealed interface ReorderSlot {
    data class Row(val id: Int) : ReorderSlot
    data class Bank(val id: Int) : ReorderSlot
    data object Panel : ReorderSlot
}

/** Gray stand-in for a letter that left its slot: the drop preview in the row, the empty slot in the bank. */
@Composable
private fun SlotPlaceholder(modifier: Modifier) {
    Box(modifier.padding(bottom = Spacing.tileDepth).background(SyllColors.neutralSurface, RoundedCornerShape(Radii.chip)))
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
