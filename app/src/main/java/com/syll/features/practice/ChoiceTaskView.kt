package com.syll.features.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.syll.app.LocalPracticeSessionStore
import com.syll.designsystem.components.ChoiceTileButton
import com.syll.designsystem.components.WordTileState
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.Typography
import com.syll.domain.practice.TaskAttempt
import com.syll.domain.practice.TaskBody

/** iOS `ChoiceTaskView` (`select`). Options keep the backend's order; their ids aren't positions. */
@Composable
fun ChoiceTaskView(attempt: TaskAttempt) {
    val store = LocalPracticeSessionStore.current
    val body = attempt.task.body as? TaskBody.Choice ?: return
    var selectedId by remember(attempt.task.id) { mutableStateOf<Int?>(null) }
    // Per option, so only the option just picked wrongly moves.
    val shakes = remember(attempt.task.id) { mutableStateMapOf<Int, Int>() }

    LaunchedEffect(attempt.status) {
        val wrong = attempt.lastWrongOptionId
        if ((attempt.status == TaskAttempt.Status.WRONG || attempt.status == TaskAttempt.Status.EXHAUSTED) && wrong != null) {
            shakes[wrong] = (shakes[wrong] ?: 0) + 1
        }
    }

    fun state(id: Int): WordTileState = when {
        attempt.status == TaskAttempt.Status.EXHAUSTED -> if (attempt.lastWrongOptionId == id) WordTileState.INCORRECT else WordTileState.DISABLED
        attempt.isFinished -> if (id == body.correctOptionId) WordTileState.CORRECT else WordTileState.DISABLED
        attempt.status == TaskAttempt.Status.WRONG && attempt.lastWrongOptionId == id -> WordTileState.INCORRECT
        selectedId == id -> WordTileState.SELECTED
        else -> WordTileState.IDLE
    }

    TaskScaffold(
        attempt,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                body.options.forEach { option ->
                    ChoiceTileButton(
                        text = option.text,
                        state = state(option.id),
                        onClick = {
                            selectedId = if (selectedId == option.id) null else option.id
                            store.answerPicked()
                        },
                        modifier = Modifier.shake(shakes[option.id] ?: 0),
                        style = Typography.choiceOption,
                    )
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
