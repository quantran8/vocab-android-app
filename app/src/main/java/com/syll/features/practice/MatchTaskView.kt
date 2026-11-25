package com.syll.features.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import com.syll.R
import com.syll.app.LocalPracticeSessionStore
import com.syll.designsystem.components.ChoiceTileButton
import com.syll.designsystem.components.WordTileState
import com.syll.designsystem.tokens.Spacing
import com.syll.domain.practice.PairKey
import com.syll.domain.practice.TaskAttempt
import com.syll.domain.practice.TaskBody
import com.syll.domain.practice.TaskOption
import kotlinx.coroutines.delay

/** iOS `MatchTaskView`: tap one item on each side, in either order. Each pairing is sent as it happens. */
@Composable
fun MatchTaskView(attempt: TaskAttempt) {
    val store = LocalPracticeSessionStore.current
    val body = attempt.task.body as? TaskBody.Match ?: return
    var selectedLeft by remember(attempt.task.id) { mutableStateOf<Int?>(null) }
    var selectedRight by remember(attempt.task.id) { mutableStateOf<Int?>(null) }
    // The pair that just missed: both tiles show the error and shake, then return to normal.
    var flashing by remember { mutableStateOf<PairKey?>(null) }
    var shake by remember { mutableIntStateOf(0) }
    // Tallest tile face seen so far; every tile uses it so both columns line up.
    var tileHeight by remember { mutableStateOf(Spacing.matchTileMinHeight) }
    val density = LocalDensity.current

    LaunchedEffect(attempt.events.size) {
        val wrong = attempt.lastWrongPair ?: return@LaunchedEffect
        flashing = wrong
        shake += 1
        delay(650)
        flashing = null
    }

    fun isMatched(id: Int, isLeft: Boolean) =
        if (isLeft) id in attempt.matchedLeftIds else body.correct.any { it.rightId == id && it.leftId in attempt.matchedLeftIds }

    /** Both sides of a pair that ran out of tries sit out, so its answer can't draw more misses. */
    fun isOutOfTries(id: Int, isLeft: Boolean) =
        if (isLeft) id in attempt.exhaustedLeftIds else body.correct.any { it.rightId == id && it.leftId in attempt.exhaustedLeftIds }

    fun isFlashing(id: Int, isLeft: Boolean) = flashing?.let { (if (isLeft) it.leftId else it.rightId) == id } ?: false

    fun state(id: Int, isLeft: Boolean): WordTileState = when {
        isMatched(id, isLeft) -> WordTileState.CORRECT
        isFlashing(id, isLeft) -> WordTileState.INCORRECT
        // Only the left item shows the miss: marking its right partner would name the answer on the board.
        isLeft && id in attempt.exhaustedLeftIds -> WordTileState.INCORRECT
        attempt.isFinished -> WordTileState.DISABLED
        (if (isLeft) selectedLeft else selectedRight) == id -> WordTileState.SELECTED
        isOutOfTries(id, isLeft) -> WordTileState.DISABLED
        else -> WordTileState.IDLE
    }

    fun pick(id: Int, isLeft: Boolean) {
        if (isMatched(id, isLeft) || isOutOfTries(id, isLeft) || attempt.isFinished) return
        if (isLeft) selectedLeft = if (selectedLeft == id) null else id else selectedRight = if (selectedRight == id) null else id
        val left = selectedLeft
        val right = selectedRight
        if (left == null || right == null) {
            store.answerPicked()
            return
        }
        store.submitPair(left, right)
        selectedLeft = null
        selectedRight = null
    }

    @Composable
    fun column(items: List<TaskOption>, isLeft: Boolean, modifier: Modifier) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            items.forEach { item ->
                ChoiceTileButton(
                    text = item.text,
                    state = state(item.id, isLeft),
                    minHeight = tileHeight,
                    onClick = { pick(item.id, isLeft) },
                    modifier = Modifier
                        .shake(if (isFlashing(item.id, isLeft)) shake else 0)
                        .onSizeChanged { size ->
                            val face = with(density) { size.height.toDp() } - Spacing.tileDepth
                            if (face > tileHeight) tileHeight = face
                        },
                )
            }
        }
    }

    TaskScaffold(
        attempt,
        content = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                column(body.left, isLeft = true, Modifier.weight(1f))
                column(body.right, isLeft = false, Modifier.weight(1f))
            }
        },
        action = {
            TaskActionBar(attempt, requirement = stringResource(R.string.x_pairs_matched, attempt.matchedLeftIds.size))
        },
    )
}
