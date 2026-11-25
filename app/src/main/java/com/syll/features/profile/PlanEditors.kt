package com.syll.features.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import android.icu.text.ListFormatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.syll.R
import com.syll.app.LocalAppSettingsStore
import com.syll.app.LocalLearnerStore
import com.syll.app.LocalPlacementTestStore
import com.syll.designsystem.components.CircleIconButton
import com.syll.designsystem.components.Eyebrow
import com.syll.designsystem.components.PinnedActionBar
import com.syll.designsystem.components.PlanSectionTitle
import com.syll.designsystem.components.PrimaryButton
import com.syll.designsystem.components.TopicChip
import com.syll.designsystem.tokens.Radii
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography
import com.syll.domain.CefrLevel
import com.syll.domain.LearningPreferences
import com.syll.domain.TopicCatalog
import com.syll.features.placement.PlacementTestView
import com.syll.features.plan.CatalogState
import com.syll.features.plan.CollectionMarkColor
import com.syll.features.plan.LearningDaysPicker
import com.syll.features.plan.LevelList
import com.syll.features.plan.PlacementTestCard
import com.syll.features.plan.ReminderPicker
import com.syll.features.plan.ReviewsPerSessionPicker
import com.syll.features.plan.TopicPicker
import com.syll.features.plan.WordsPerDayStepper
import com.syll.features.plan.displayName

/**
 * The plan editors' sheet (iOS `.presentationDetents([.large])`): ✕ in the corner, the title, the form scrolling,
 * and the save bar pinned at the bottom. Dismissing discards the draft.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanEditorSheet(
    title: String,
    onDismiss: () -> Unit,
    bar: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = Radii.sheet, topEnd = Radii.sheet),
        containerColor = SyllColors.canvas,
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screenGutter)
                    .padding(top = Spacing.s, bottom = Spacing.l),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    CircleIconButton(Icons.Outlined.Close, stringResource(R.string.close), onClick = onDismiss)
                }
                Text(
                    title,
                    style = Typography.sheetTitle.copy(letterSpacing = Typography.tightTracking.sp),
                    color = SyllColors.ink,
                    modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.l).semantics { heading() },
                )
                content()
            }
            PinnedActionBar(content = bar)
        }
    }
}

/** iOS `EditLevelView`: take the placement test, or choose the level. */
@Composable
fun EditLevelSheet(initial: CefrLevel?, onSave: (CefrLevel) -> Unit, onPlaced: () -> Unit, onDismiss: () -> Unit) {
    val settings = LocalAppSettingsStore.current
    val placement = LocalPlacementTestStore.current
    /** What the server holds. A finished test changes it while this sheet stays open. */
    var saved by remember { mutableStateOf(initial) }
    var draft by remember { mutableStateOf(initial) }
    var isTakingTest by remember { mutableStateOf(false) }

    PlanEditorSheet(
        stringResource(R.string.your_level),
        onDismiss,
        bar = {
            PrimaryButton(
                stringResource(R.string.save_changes),
                requirement = stringResource(R.string.choose_your_level),
                enabled = draft != null && draft != saved,
                onClick = {
                    val level = draft ?: return@PrimaryButton
                    settings.feedback.success()
                    onSave(level)
                },
            )
        },
    ) {
        PlacementTestCard(onClick = {
            placement.start()
            isTakingTest = true
        })
        Eyebrow(stringResource(R.string.or_choose_it_yourself), Modifier.padding(top = Spacing.l, bottom = Spacing.s - 2.dp))
        LevelList(draft) { level ->
            if (level != draft) {
                draft = level
                settings.feedback.selectionChanged()
            }
        }
    }

    // The test covers the whole screen, over the sheet.
    if (isTakingTest) {
        Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Box(Modifier.fillMaxSize().background(SyllColors.canvas)) {
                PlacementTestView(
                    doneTitle = stringResource(R.string.done),
                    onDone = {
                        isTakingTest = false
                        onPlaced()
                    },
                    onChooseLevel = { result ->
                        result?.let {
                            saved = it.proficiencyLevel
                            draft = it.proficiencyLevel
                        }
                        isTakingTest = false
                    },
                    onLeave = { isTakingTest = false },
                )
            }
        }
    }
}

/** Names at most this many dropped topics; beyond that the list would swamp the pinned bar. */
private const val NAMED_DROP_LIMIT = 2

/** iOS `EditFocusView`: the collection and its topics; the routine is untouched. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditFocusSheet(initial: LearningPreferences, onSave: (LearningPreferences) -> Unit, onDismiss: () -> Unit) {
    val settings = LocalAppSettingsStore.current
    val learner = LocalLearnerStore.current
    var draft by remember { mutableStateOf(initial) }
    val maxTopics = learner.maxTopics
    val withinLimit = maxTopics?.let { draft.topicIds.size <= it } ?: true
    // Topics the draft drops that the learner has learned from: their words stay in review.
    val dropped = TopicCatalog.topics(initial.topicIds - draft.topicIds).filter { (it.learned ?: 0) > 0 }
    val locale = LocalConfiguration.current.locales[0]

    PlanEditorSheet(
        stringResource(R.string.your_focus),
        onDismiss,
        bar = {
            if (dropped.isNotEmpty()) {
                val note = if (dropped.size <= NAMED_DROP_LIMIT) {
                    val collection = initial.collection
                    val names = dropped.map { topic -> collection?.let { topic.displayName(it) } ?: topic.name }
                    stringResource(R.string.words_you_learned_in_x_will_still_come_back_for, ListFormatter.getInstance(locale).format(names))
                } else {
                    stringResource(R.string.words_you_learned_in_the_topics_you_removed_will)
                }
                Text(note, style = Typography.micro, color = SyllColors.mutedInk, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            if (!withinLimit && maxTopics != null) {
                // A scope kept from Premium can stay, but saving needs it within the free limit. Premium: Phase 4.
                Text(
                    stringResource(R.string.the_free_plan_includes_x_topics_remove_some_to_s, maxTopics),
                    style = Typography.micro.copy(fontWeight = FontWeight.SemiBold),
                    color = SyllColors.errorInk,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (draft.topicIds.isNotEmpty()) {
                Text(
                    stringResource(R.string.x_topics_selected, draft.topicIds.size),
                    style = Typography.micro.copy(fontWeight = FontWeight.SemiBold),
                    color = SyllColors.mutedInk,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            PrimaryButton(
                stringResource(R.string.save_changes),
                requirement = stringResource(R.string.choose_at_least_one_topic),
                enabled = draft.isComplete && withinLimit,
                onClick = {
                    settings.feedback.success()
                    onSave(draft)
                },
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                PlanSectionTitle(stringResource(R.string.collection))
                // Switching collection clears the topics. A free learner stays on the saved collection.
                val locked = learner.lockedCollectionId(initial.collectionId)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    CatalogState.collections.forEach { collection ->
                        val isLocked = locked?.let { it != collection.id } ?: false
                        TopicChip(
                            title = collection.displayName(),
                            isSelected = collection.id == draft.collectionId,
                            onClick = {
                                if (!isLocked && collection.id != draft.collectionId) {
                                    draft = draft.selectCollection(collection.id)
                                    settings.feedback.selectionChanged()
                                }
                            },
                            mark = CollectionMarkColor.color(collection),
                            isLocked = isLocked,
                        )
                    }
                }
            }
            draft.collection?.let { collection ->
                TopicPicker(
                    collection = collection,
                    selectedIds = draft.topicIds,
                    onToggleTopic = { id ->
                        draft.toggleTopic(id, maxTopics)?.let {
                            draft = it
                            settings.feedback.selectionChanged()
                        }
                    },
                    onToggleAll = {
                        draft = draft.toggleAllTopics(maxTopics)
                        settings.feedback.selectionChanged()
                    },
                    maxTopics = maxTopics,
                )
            }
        }
    }
}

/** iOS `EditRoutineView`: daily goal, reviews per session, learning days and reminder; topics are untouched. */
@Composable
fun EditRoutineSheet(initial: LearningPreferences, onSave: (LearningPreferences) -> Unit, onDismiss: () -> Unit) {
    val settings = LocalAppSettingsStore.current
    var draft by remember { mutableStateOf(initial) }
    PlanEditorSheet(
        stringResource(R.string.your_routine),
        onDismiss,
        bar = {
            PrimaryButton(
                stringResource(R.string.save_changes),
                requirement = stringResource(R.string.choose_at_least_one_learning_day),
                enabled = draft.isComplete,
                onClick = {
                    settings.feedback.success()
                    onSave(draft)
                },
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s - 2.dp)) {
                PlanSectionTitle(stringResource(R.string.new_words_day))
                WordsPerDayStepper(draft.wordsPerDay, onAdjust = {
                    draft = draft.adjustWordsPerDay(it)
                    settings.feedback.selectionChanged()
                })
            }
            ReviewsPerSessionPicker(draft, onChange = { draft = it }, onSelectionChanged = settings.feedback::selectionChanged)
            LearningDaysPicker(
                draft.learningDays,
                onToggleDay = {
                    draft = draft.toggleDay(it)
                    settings.feedback.selectionChanged()
                },
                onToggleEveryDay = {
                    draft = draft.toggleEveryDay()
                    settings.feedback.selectionChanged()
                },
            )
            ReminderPicker(draft, onChange = { draft = it })
        }
    }
}
