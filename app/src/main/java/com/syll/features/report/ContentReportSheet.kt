package com.syll.features.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.syll.R
import com.syll.app.LocalAppScope
import com.syll.app.LocalContentReportStore
import com.syll.app.text
import com.syll.designsystem.components.CardDivider
import com.syll.designsystem.components.CardRow
import com.syll.designsystem.components.Eyebrow
import com.syll.designsystem.components.PrimaryButton
import com.syll.designsystem.components.SecondaryButton
import com.syll.designsystem.components.SelectionMark
import com.syll.designsystem.components.cardSurface
import com.syll.designsystem.components.fieldSurface
import com.syll.designsystem.components.pressScaleClickable
import com.syll.designsystem.tokens.Radii
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography
import com.syll.domain.report.ContentReportReason
import com.syll.domain.report.ContentReportRequest
import com.syll.domain.report.ContentReportTarget
import kotlinx.coroutines.launch

/**
 * iOS `ContentReportSheet`: report a problem with what's on screen (backend FRONTEND_FLOW §18): one reason, an optional
 * note, send. The task or card behind it keeps its state; nothing here is scored. A Material bottom sheet (drag
 * handle, no ✕), as the platform's short sheets are; the caller confirms a sent report with a snackbar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentReportSheet(target: ContentReportTarget, onDismiss: () -> Unit, onSent: () -> Unit) {
    val store = LocalContentReportStore.current
    // The send outlives the sheet: it closes on success, and must not cancel a request in flight.
    val appScope = LocalAppScope.current
    var reason by remember(target.id) { mutableStateOf<ContentReportReason?>(null) }
    var note by remember(target.id) { mutableStateOf("") }
    var isNoteFocused by remember { mutableStateOf(false) }
    val noteFocus = remember { FocusRequester() }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        // A send in flight is bounded by the API timeout; closing then would lose its answer.
        confirmValueChange = { !store.isSubmitting },
    )
    LaunchedEffect(target.id) { store.begin() }

    val trimmed = note.trim()
    val isNoteTooLong = trimmed.scalarCount() > ContentReportRequest.MAX_NOTE_LENGTH
    val requirement = when {
        reason == null -> stringResource(R.string.choose_what_s_wrong)
        reason == ContentReportReason.OTHER && trimmed.isEmpty() -> stringResource(R.string.tell_us_what_s_wrong)
        isNoteTooLong -> stringResource(R.string.shorten_your_note)
        else -> null
    }

    fun send() {
        val chosen = reason ?: return
        if (requirement != null) return
        appScope.launch { if (store.submit(target, chosen, note)) onSent() }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!store.isSubmitting) onDismiss() },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = Radii.sheet, topEnd = Radii.sheet),
        containerColor = SyllColors.canvas,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = Spacing.practiceGutter)
                .padding(bottom = Spacing.m),
        ) {
            Text(
                stringResource(R.string.report_a_problem),
                style = Typography.sheetTitle,
                color = SyllColors.ink,
                modifier = Modifier.semantics { heading() },
            )
            target.subtitle?.takeIf { it.isNotEmpty() }?.let {
                Text(it, style = Typography.secondary, color = SyllColors.mutedInk, maxLines = 2, modifier = Modifier.padding(top = Spacing.xxs))
            }

            Eyebrow(stringResource(R.string.what_s_wrong), Modifier.padding(top = Spacing.l, bottom = Spacing.xs).semantics { heading() })
            Column(Modifier.fillMaxWidth().cardSurface()) {
                target.reasons.forEachIndexed { index, option ->
                    if (index > 0) CardDivider()
                    CardRow(
                        Modifier.selectable(
                            selected = reason == option,
                            enabled = !store.isSubmitting,
                            role = Role.RadioButton,
                            onClick = {
                                reason = option
                                if (option == ContentReportReason.OTHER) noteFocus.requestFocus()
                            },
                        ),
                    ) {
                        SelectionMark(reason == option)
                        Text(
                            stringResource(option.title(target.isRecording)),
                            style = Typography.body.copy(fontWeight = FontWeight.SemiBold),
                            color = SyllColors.ink,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            val noteTitle = stringResource(if (reason == ContentReportReason.OTHER) R.string.details else R.string.details_optional)
            Column(Modifier.padding(top = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Eyebrow(noteTitle, color = if (isNoteFocused) SyllColors.selectedInk else SyllColors.mutedInk)
                BasicTextField(
                    value = note,
                    onValueChange = { note = it },
                    enabled = !store.isSubmitting,
                    textStyle = Typography.body.copy(color = SyllColors.ink),
                    cursorBrush = SolidColor(SyllColors.interactive),
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fieldSurface(isNoteFocused, isError = isNoteTooLong)
                        .focusRequester(noteFocus)
                        .onFocusChanged { isNoteFocused = it.isFocused }
                        .padding(vertical = Spacing.s)
                        .semantics { contentDescription = noteTitle },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isNoteTooLong) {
                        Text(
                            stringResource(R.string.keep_it_under_x_characters_2, ContentReportRequest.MAX_NOTE_LENGTH),
                            style = Typography.micro,
                            color = SyllColors.errorInk,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${trimmed.scalarCount()}/${ContentReportRequest.MAX_NOTE_LENGTH}",
                        style = Typography.micro.copy(fontFeatureSettings = "tnum"),
                        color = if (isNoteTooLong) SyllColors.errorInk else SyllColors.mutedInk,
                    )
                }
            }

            (store.phase as? ContentReportStore.Phase.Failed)?.let { failed ->
                Text(failed.message.text(), style = Typography.secondary, color = SyllColors.errorInk, modifier = Modifier.padding(top = Spacing.s))
            }

            PrimaryButton(
                title = stringResource(if (store.isSubmitting) R.string.sending else R.string.send_report),
                onClick = ::send,
                requirement = requirement,
                enabled = requirement == null && !store.isSubmitting,
                modifier = Modifier.padding(top = Spacing.l),
            )
            SecondaryButton(
                stringResource(R.string.cancel),
                onClick = { if (!store.isSubmitting) onDismiss() },
                modifier = Modifier.padding(top = Spacing.s),
            )
        }
    }
}

fun ContentReportReason.title(isRecording: Boolean): Int = when (this) {
    ContentReportReason.INCORRECT_MEANING -> R.string.the_meaning_or_translation_is_wrong
    ContentReportReason.INCORRECT_DEFINITION -> R.string.the_definition_is_wrong
    ContentReportReason.INCORRECT_EXAMPLE -> R.string.the_example_is_wrong_or_unnatural
    ContentReportReason.ANSWER_SHOULD_BE_ACCEPTED ->
        if (isRecording) R.string.my_pronunciation_was_marked_wrong else R.string.my_answer_should_be_accepted
    ContentReportReason.INCORRECT_QUESTION -> R.string.the_question_or_answer_doesn_t_make_sense
    ContentReportReason.AUDIO_ISSUE -> R.string.problem_with_the_audio_or_pronunciation
    ContentReportReason.OTHER -> R.string.something_else
}

/** iOS `ContentReportButton`: the flag that opens the report sheet, Ink 400 in a 44 dp target. */
@Composable
fun ContentReportButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.report_a_problem)
    Box(
        modifier
            .size(Spacing.minTouchTarget)
            .pressScaleClickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Flag, contentDescription = null, tint = if (enabled) SyllColors.ink400 else SyllColors.ink200, modifier = Modifier.size(22.dp))
    }
}

/** iOS `ReportMeaningLink`-style text action: "Report a problem" with a flag. */
@Composable
fun ReportLink(onClick: () -> Unit, modifier: Modifier = Modifier) {
    com.syll.designsystem.components.TextLink(
        stringResource(R.string.report_a_problem),
        onClick = onClick,
        modifier = modifier,
        color = SyllColors.mutedInk,
        icon = Icons.Outlined.Flag,
    )
}
