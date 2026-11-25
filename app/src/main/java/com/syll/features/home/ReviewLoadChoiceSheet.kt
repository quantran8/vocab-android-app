package com.syll.features.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syll.R
import com.syll.app.LocalAppSettingsStore
import com.syll.app.UserMessage
import com.syll.app.text
import com.syll.designsystem.components.CircleIconButton
import com.syll.designsystem.components.Eyebrow
import com.syll.designsystem.components.PlanSectionTitle
import com.syll.designsystem.components.PrimaryButton
import com.syll.designsystem.components.SecondaryButton
import com.syll.designsystem.components.SelectionMark
import com.syll.designsystem.components.cardSurface
import com.syll.designsystem.components.pressScaleClickable
import com.syll.designsystem.tokens.Radii
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography
import com.syll.features.plan.WordsPerDayStepper
import com.syll.network.NewWordOverrideMode
import com.syll.domain.NewWordReason
import com.syll.network.ReviewLoad
import com.syll.network.asApiError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * iOS `ReviewLoadChoiceSheet`: offered from Home when many learned words wait for review. Three equal choices: none is
 * pushed. Any way out of the sheet counts as seen ([onDismiss] acknowledges it).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewLoadChoiceSheet(
    load: ReviewLoad,
    /** The learner's daily goal; a temporary value goes from 1 to one below it. */
    goal: Int,
    onDismiss: () -> Unit,
    onExtraReview: () -> Unit,
    onLower: suspend (value: Int, mode: NewWordOverrideMode) -> Unit,
) {
    val settings = LocalAppSettingsStore.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isPickingLower by remember { mutableStateOf(false) }
    var value by remember { mutableIntStateOf(maxOf(1, minOf(goal - 1, Math.round(goal / 2.0).toInt()))) }
    var mode by remember { mutableStateOf(NewWordOverrideMode.UNTIL_DATE) }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<UserMessage?>(null) }
    // While the checkpoint gate holds, Daily has no new words to lower.
    val canLower = goal > 1 && load.newWordReason != NewWordReason.CHECKPOINT_GATE

    fun close(then: () -> Unit = {}) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            then()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = Radii.sheet, topEnd = Radii.sheet),
        containerColor = SyllColors.canvas,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Spacing.screenGutter)
                .padding(bottom = Spacing.l),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow(stringResource(R.string.review), Modifier.weight(1f))
                CircleIconButton(Icons.Outlined.Close, stringResource(R.string.close), onClick = { close() })
            }
            Text(
                stringResource(R.string.you_have_x_learned_words_waiting_for_review, load.backlog),
                style = Typography.screenTitle.copy(letterSpacing = Typography.tightTracking.sp),
                color = SyllColors.ink,
                modifier = Modifier.padding(top = Spacing.xs).semantics { heading() },
            )
            Text(
                stringResource(R.string.each_session_reviews_x_words_so_some_words_will, load.dueCap),
                style = Typography.body,
                color = SyllColors.mutedInk,
                modifier = Modifier.padding(top = Spacing.s),
            )

            Column(Modifier.padding(top = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                SecondaryButton(stringResource(R.string.keep_learning_as_usual), onClick = { close() })
                SecondaryButton(stringResource(R.string.review_one_more_session), onClick = { close(onExtraReview) })
                if (canLower) {
                    SecondaryButton(stringResource(R.string.fewer_new_words_for_now), onClick = { isPickingLower = !isPickingLower })
                }
            }

            AnimatedVisibility(isPickingLower, enter = fadeIn(), exit = fadeOut()) {
                Column(
                    Modifier.padding(top = Spacing.l).cardSurface(radius = Radii.card - 2.dp).padding(Spacing.m),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    PlanSectionTitle(stringResource(R.string.new_words_per_session))
                    WordsPerDayStepper(
                        value = value,
                        range = 1..maxOf(1, goal - 1),
                        label = stringResource(R.string.new_words_per_session),
                        onAdjust = { value = (value + it).coerceIn(1, maxOf(1, goal - 1)) },
                    )
                    PlanSectionTitle(stringResource(R.string.for_how_long))
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        DurationRow(stringResource(R.string.until_the_end_of_sunday), mode == NewWordOverrideMode.UNTIL_DATE) {
                            mode = NewWordOverrideMode.UNTIL_DATE
                            settings.feedback.selectionChanged()
                        }
                        DurationRow(stringResource(R.string.until_10_or_fewer_words_are_waiting), mode == NewWordOverrideMode.UNTIL_BACKLOG_CLEAR) {
                            mode = NewWordOverrideMode.UNTIL_BACKLOG_CLEAR
                            settings.feedback.selectionChanged()
                        }
                    }
                    error?.let { Text(it.text(), style = Typography.micro, color = SyllColors.mutedInk) }
                    PrimaryButton(
                        stringResource(R.string.lower_new_words),
                        enabled = !isSaving,
                        onClick = {
                            scope.launch {
                                isSaving = true
                                try {
                                    onLower(value, mode)
                                    settings.feedback.success()
                                    close()
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    error = UserMessage.Api(e.asApiError)
                                } finally {
                                    isSaving = false
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DurationRow(title: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Spacing.minTouchTarget)
            .pressScaleClickable(onClick = onClick)
            .semantics(mergeDescendants = true) { selected = isSelected },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = Typography.secondary.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
            color = SyllColors.ink,
            modifier = Modifier.weight(1f),
        )
        SelectionMark(isSelected)
    }
}
