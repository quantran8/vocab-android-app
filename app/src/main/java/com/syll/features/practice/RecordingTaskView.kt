package com.syll.features.practice

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.syll.R
import com.syll.app.LocalPracticeSessionStore
import com.syll.designsystem.components.PrimaryButton
import com.syll.domain.practice.TaskAttempt

/**
 * Stand-in for iOS `RecordingTaskView` until Speaking (step 5) brings the recorder: the plan asks for no recordings
 * while `capabilities.microphone` is false, and one that still arrives is ended as `unavailable`, which keeps the
 * commit valid.
 */
@Composable
fun RecordingTaskView(attempt: TaskAttempt) {
    val store = LocalPracticeSessionStore.current
    TaskScaffold(
        attempt,
        content = {},
        action = { PrimaryButton(stringResource(R.string.s_continue), onClick = store::acceptRecording) },
    )
}
