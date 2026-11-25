package com.syll.features.customwords

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syll.R
import com.syll.designsystem.components.Eyebrow
import com.syll.designsystem.components.FullScreenDialog
import com.syll.designsystem.components.PinnedActionBar
import com.syll.designsystem.components.PrimaryButton
import com.syll.designsystem.components.fieldSurface
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography
import com.syll.domain.customwords.CustomWordEditDraft
import com.syll.domain.customwords.CustomWordInput
import com.syll.domain.customwords.TargetLocator

/**
 * iOS `CustomWordEditView`: editing the learner's own copy of a word. The word itself, its IPA and level stay fixed.
 * Dismissing discards the changes.
 */
@Composable
fun CustomWordEditView(initial: CustomWordEditDraft, onSave: (CustomWordEditDraft) -> Unit, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(initial) }
    FullScreenDialog(
        title = stringResource(R.string.edit_word),
        onDismiss = onDismiss,
        bottomBar = {
            PinnedActionBar(Modifier.imePadding()) {
                PrimaryButton(
                    stringResource(R.string.save),
                    onClick = { onSave(draft) },
                    requirement = stringResource(R.string.change_something_and_fill_in_every_field),
                    enabled = draft.isValid && draft != initial,
                )
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenGutter)
                .padding(top = Spacing.s, bottom = Spacing.l),
        ) {
            EditWordTitle(draft.lemma)
            Text(
                stringResource(R.string.your_changes_apply_only_to_you_other_learners_ke),
                style = Typography.secondary,
                color = SyllColors.bodyInk,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            CustomWordEditFields(draft, { draft = it }, Modifier.padding(top = Spacing.l))
        }
    }
}

/** iOS `EditWordTitle`: "Edit word" over the word itself. */
@Composable
fun EditWordTitle(lemma: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Eyebrow(stringResource(R.string.edit_word).uppercase())
        Text(
            lemma,
            style = Typography.stat.copy(letterSpacing = Typography.tightTracking.sp),
            color = SyllColors.ink,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/** iOS `CustomWordEditFields`: the editable text of a word: meaning, definition and each example. */
@Composable
fun CustomWordEditFields(draft: CustomWordEditDraft, onChange: (CustomWordEditDraft) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.screenGutter)) {
        EditorField(stringResource(R.string.meaning), draft.meaningText, { onChange(draft.withMeaning(it)) }, maxLength = CustomWordInput.MAX_FIELD_LENGTH)
        EditorField(
            stringResource(R.string.definition), draft.definitionText, { onChange(draft.withDefinition(it)) },
            maxLength = CustomWordInput.MAX_FIELD_LENGTH, minLines = 2, maxLines = 5,
        )
        draft.examples.forEach { example ->
            val locator = draft.locator(example)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                EditorField(
                    stringResource(R.string.example), example.text, { onChange(draft.withExample(example.id, text = it)) },
                    maxLength = CustomWordInput.MAX_FIELD_LENGTH, minLines = 2, maxLines = 5, isError = locator == null,
                )
                if (locator == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.ErrorOutline, null, tint = SyllColors.errorInk, modifier = Modifier.size(14.dp))
                        Text(stringResource(R.string.the_example_must_contain_x, draft.lemma), style = Typography.micro, color = SyllColors.errorInk)
                    }
                } else {
                    Text(highlightedSentence(example.text.trim(), locator), style = Typography.secondary, color = SyllColors.ink)
                }
                EditorField(
                    stringResource(R.string.translation), example.translation, { onChange(draft.withExample(example.id, translation = it)) },
                    maxLength = CustomWordInput.MAX_FIELD_LENGTH, minLines = 2, maxLines = 5, modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
}

/** iOS `HighlightedSentence`: the target word in bold; the whole sentence plain when the locator is off. */
fun highlightedSentence(sentence: String, locator: TargetLocator?): AnnotatedString {
    val range = locator?.range(sentence) ?: return AnnotatedString(sentence)
    return buildAnnotatedString {
        append(sentence.substring(0, range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = SyllColors.ink)) { append(sentence.substring(range)) }
        append(sentence.substring(range.last + 1))
    }
}

/** iOS `EditorField`: a multi-line labelled text field with the app's outlined look. */
@Composable
fun EditorField(
    title: String,
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    maxLength: Int? = null,
    minLines: Int = 1,
    maxLines: Int = 5,
    focusesOnAppear: Boolean = false,
    isError: Boolean = false,
) {
    var isFocused by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val isTooLong = maxLength != null && text.length > maxLength
    LaunchedEffect(Unit) { if (focusesOnAppear) focus.requestFocus() }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Eyebrow(title.uppercase(), color = if (isFocused) SyllColors.selectedInk else SyllColors.mutedInk)
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            textStyle = Typography.body.copy(color = SyllColors.ink),
            cursorBrush = SolidColor(SyllColors.interactive),
            minLines = minLines,
            maxLines = maxLines,
            modifier = Modifier
                .fillMaxWidth()
                .fieldSurface(isFocused, isError = isTooLong || isError)
                .focusRequester(focus)
                .onFocusChanged { isFocused = it.isFocused }
                .padding(vertical = Spacing.s)
                .semantics { contentDescription = title },
        )
        if (isTooLong && maxLength != null) {
            Text(stringResource(R.string.keep_this_under_x_characters, maxLength), style = Typography.micro, color = SyllColors.errorInk)
        }
    }
}
