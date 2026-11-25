package com.syll.features.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.syll.R
import com.syll.app.LocalPracticeSessionStore
import com.syll.designsystem.components.Eyebrow
import com.syll.designsystem.components.FieldInput
import com.syll.designsystem.components.fieldSurface
import com.syll.designsystem.motion.MotionTokens
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.Typography
import com.syll.domain.practice.PracticeAnswer
import com.syll.domain.practice.TaskAttempt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** iOS `TypingTaskView`: type the English word. */
@Composable
fun TypingTaskView(attempt: TaskAttempt) {
    val store = LocalPracticeSessionStore.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    var text by remember(attempt.task.id) { mutableStateOf("") }
    var isFocused by remember { mutableStateOf(false) }
    // Set while Check waits for the keyboard to go down, so a second tap doesn't submit twice.
    var submitJob by remember { mutableStateOf<Job?>(null) }
    val canCheck = PracticeAnswer.normalize(text).isNotEmpty()
    val isEnabled = attempt.status == TaskAttempt.Status.ANSWERING

    LaunchedEffect(attempt.status) {
        if (attempt.status != TaskAttempt.Status.ANSWERING) {
            focusManager.clearFocus()
        } else if (attempt.submitCount > 0) {
            // Back from "Try again": a fresh field, ready to type.
            text = ""
            runCatching { focus.requestFocus() }
        }
    }

    /** Closes the keyboard first, then submits once it's down, so the feedback isn't fighting the keyboard. */
    fun check() {
        if (!canCheck || submitJob?.isActive == true) return
        if (!isFocused) {
            store.submitText(text)
            return
        }
        focusManager.clearFocus()
        submitJob = scope.launch {
            delay((MotionTokens.keyboardDismiss * 1000).toLong())
            store.submitText(text)
        }
    }

    TaskScaffold(
        attempt,
        onTapOutside = { focusManager.clearFocus() },
        content = {
            val label = stringResource(R.string.your_answer)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Eyebrow(stringResource(R.string.your_answer))
                FieldInput(
                    value = text,
                    onValueChange = { if (isEnabled) text = it },
                    placeholder = stringResource(R.string.type_the_english_word),
                    focusRequester = focus,
                    onFocusChange = { isFocused = it },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done,
                    ),
                    onSubmit = ::check,
                    textStyle = Typography.tileLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fieldSurface(isFocused, minHeight = Spacing.fieldHeight + Spacing.xs)
                        .semantics { contentDescription = label },
                )
            }
        },
        action = {
            if (attempt.status == TaskAttempt.Status.ANSWERING) HintControl(attempt, onReveal = { focusManager.clearFocus() })
            TaskActionBar(attempt, canCheck = canCheck, onCheck = ::check, showsReveal = false)
        },
    )
}
