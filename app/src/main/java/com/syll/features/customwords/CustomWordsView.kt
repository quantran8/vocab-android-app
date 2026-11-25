package com.syll.features.customwords

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syll.R
import com.syll.app.LocalAppScope
import com.syll.app.LocalCustomWordStore
import com.syll.app.text
import com.syll.designsystem.components.CardDivider
import com.syll.designsystem.components.ConfirmSheet
import com.syll.designsystem.components.ConfirmSheetStyle
import com.syll.designsystem.components.FullScreenDialog
import com.syll.designsystem.components.SecondaryButton
import com.syll.designsystem.components.StatusPill
import com.syll.designsystem.components.StatusPillTone
import com.syll.designsystem.components.cardSurface
import com.syll.designsystem.components.pressScaleClickable
import com.syll.designsystem.tokens.Borders
import com.syll.designsystem.tokens.Radii
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography
import com.syll.domain.customwords.CustomWord
import com.syll.domain.customwords.CustomWordEditDraft
import com.syll.domain.customwords.CustomWordGroup
import com.syll.features.words.WordDetailSheet
import kotlinx.coroutines.launch

private enum class RowAction { EDIT, RESTORE, REMOVE }

/**
 * iOS `CustomWordsView`: the words the learner added, one row per word: tap for details. The row's ⋯ is a menu (edit,
 * restore the original, remove — which asks first); a word with several meanings opens a sheet of them first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomWordsView(onDismiss: () -> Unit) {
    val store = LocalCustomWordStore.current
    val appScope = LocalAppScope.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<CustomWord?>(null) }
    var editing by remember { mutableStateOf<CustomWord?>(null) }
    var acting by remember { mutableStateOf<CustomWordGroup?>(null) }
    var removing by remember { mutableStateOf<CustomWord?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { store.loadWords() }
    DisposableEffect(Unit) { onDispose { store.stopAudio() } }

    fun perform(action: RowAction, word: CustomWord) {
        when (action) {
            RowAction.EDIT -> editing = word
            RowAction.RESTORE -> appScope.launch { store.restoreOriginal(word) }
            RowAction.REMOVE -> removing = word
        }
    }

    FullScreenDialog(title = stringResource(R.string.words_you_added), onDismiss = onDismiss) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    store.loadWords()
                    isRefreshing = false
                }
            },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when (val phase = store.listPhase) {
                CustomWordStore.ListPhase.Loading -> Box(Modifier.fillMaxSize().padding(top = Spacing.xl), contentAlignment = Alignment.TopCenter) {
                    CircularProgressIndicator(color = SyllColors.mutedInk)
                }
                is CustomWordStore.ListPhase.Failed -> Column(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.screenGutter, vertical = Spacing.s),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    Text(phase.message.text(), style = Typography.secondary, color = SyllColors.errorInk)
                    SecondaryButton(stringResource(R.string.try_again_2), onClick = { scope.launch { store.loadWords() } }, icon = Icons.Outlined.Refresh)
                }
                CustomWordStore.ListPhase.Ready -> {
                    val groups = CustomWordGroup.grouping(store.words)
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.screenGutter, vertical = Spacing.s),
                    ) {
                        store.listError?.let { error ->
                            item { Text(error.text(), style = Typography.secondary, color = SyllColors.errorInk, modifier = Modifier.padding(bottom = Spacing.s)) }
                        }
                        if (groups.isEmpty()) {
                            item { Text(stringResource(R.string.words_you_add_will_collect_here), style = Typography.secondary, color = SyllColors.mutedInk) }
                        }
                        items(groups, key = { it.id }) { group ->
                            GroupRow(
                                group,
                                onOpen = { group.firstEditable?.let { selected = it } },
                                onMore = { acting = group },
                                onAction = ::perform,
                            )
                        }
                    }
                }
            }
        }
    }

    selected?.let { word ->
        WordDetailSheet(
            wordId = word.wordId,
            lemma = word.lemma,
            ipa = word.ipa,
            onDismiss = { selected = null },
            focusMeaningId = word.meaningId,
            playAudio = word.audioUrl?.let { { store.play(word) } },
        )
    }
    editing?.let { word ->
        CustomWordEditView(
            initial = CustomWordEditDraft.of(word),
            onSave = { edited ->
                editing = null
                edited.edits?.let { edits -> appScope.launch { store.saveOverride(edits, word) } }
            },
            onDismiss = { editing = null },
        )
    }
    acting?.let { group ->
        // Picking an action closes this sheet first, so the next one can show.
        MeaningsSheet(group, onAction = { action, word ->
            acting = null
            perform(action, word)
        }, onDismiss = { acting = null })
    }
    removing?.let { word ->
        val isOnlyMeaning = store.words.count { it.wordId == word.wordId } <= 1
        ConfirmSheet(
            style = ConfirmSheetStyle.Destructive(Icons.Outlined.Delete),
            title = stringResource(if (isOnlyMeaning) R.string.remove_this_word else R.string.remove_this_meaning),
            message = stringResource(R.string.it_won_t_be_scheduled_for_review_and_your_edits),
            cancelTitle = stringResource(R.string.cancel),
            confirmTitle = stringResource(R.string.remove),
            onDismiss = { removing = null },
            onConfirm = {
                removing = null
                appScope.launch { store.remove(word) }
            },
        )
    }
}

@Composable
private fun GroupRow(group: CustomWordGroup, onOpen: () -> Unit, onMore: () -> Unit, onAction: (RowAction, CustomWord) -> Unit) {
    val store = LocalCustomWordStore.current
    val isBusy = group.meanings.any { it.meaningId in store.busyMeaningIds }
    val hint = if (group.firstEditable != null) stringResource(R.string.shows_every_meaning_of_the_word) else ""
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (isBusy) 0.5f else 1f)
            .drawBehind {
                val y = size.height - Borders.hairline.toPx() / 2
                drawLine(SyllColors.divider, Offset(0f, y), Offset(size.width, y), Borders.hairline.toPx())
            }
            .padding(vertical = Spacing.s + 2.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .pressScaleClickable(enabled = !isBusy, onClick = onOpen)
                .semantics(mergeDescendants = true) { if (hint.isNotEmpty()) contentDescription = "${group.lemma}, $hint" },
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs / 2),
        ) {
            Text(
                group.lemma,
                style = Typography.wordClass.copy(fontWeight = FontWeight.Bold),
                color = if (group.recalled) SyllColors.disabledInk else SyllColors.ink,
            )
            WordStatus(group.meaningText, group.translationPending, group.recalled)
        }
        // One meaning: its menu straight away. Several: a sheet listing them, each with its own menu.
        val only = group.meanings.singleOrNull()
        if (only != null) {
            ActionMenu(only, enabled = !isBusy) { onAction(it, only) }
        } else {
            val label = stringResource(R.string.more_actions)
            IconButton(onClick = onMore, enabled = !isBusy, modifier = Modifier.semantics { contentDescription = label }) {
                Icon(Icons.Outlined.MoreHoriz, null, tint = SyllColors.mutedInk)
            }
        }
    }
}

/** A word row shows its meanings and, below them, the pill while any meaning still translates. */
@Composable
private fun WordStatus(meaningText: String?, pending: Boolean, recalled: Boolean) {
    if (recalled) {
        Text(stringResource(R.string.no_longer_available), style = Typography.secondary, color = SyllColors.destructiveInk)
        return
    }
    meaningText?.let { Text(it, style = Typography.secondary, color = SyllColors.mutedInk) }
    if (pending) {
        val hint = stringResource(R.string.it_joins_your_sessions_once_the_translation_is_r)
        StatusPill(
            stringResource(R.string.translating),
            StatusPillTone.REWARD,
            modifier = Modifier.padding(top = Spacing.xxs / 2).semantics { contentDescription = hint },
        )
    }
}

/** The ⋯ menu of one meaning: Edit and Restore original when it can be edited, then Remove. */
@Composable
private fun ActionMenu(word: CustomWord, enabled: Boolean = true, onAction: (RowAction) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = stringResource(R.string.more_actions)
    Box {
        IconButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.semantics { contentDescription = label }) {
            Icon(Icons.Outlined.MoreHoriz, null, tint = SyllColors.mutedInk)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = SyllColors.surface) {
            fun choose(action: RowAction) {
                expanded = false
                onAction(action)
            }
            if (word.isEditable) {
                DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, onClick = { choose(RowAction.EDIT) }, leadingIcon = { Icon(Icons.Outlined.Edit, null) })
                if (word.overridden) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.restore_original)) },
                        onClick = { choose(RowAction.RESTORE) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Undo, null) },
                    )
                }
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.remove), color = SyllColors.destructiveInk) },
                onClick = { choose(RowAction.REMOVE) },
                leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = SyllColors.destructiveInk) },
            )
        }
    }
}

/** The ⋯ sheet: the word, then one card with a row per added meaning; each row's ⋯ is a menu of its actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeaningsSheet(group: CustomWordGroup, onAction: (RowAction, CustomWord) -> Unit, onDismiss: () -> Unit) {
    val store = LocalCustomWordStore.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = Radii.sheet, topEnd = Radii.sheet),
        containerColor = SyllColors.canvas,
    ) {
        Column(Modifier.padding(horizontal = Spacing.screenGutter).padding(top = Spacing.s, bottom = Spacing.m)) {
            Text(
                group.lemma,
                style = Typography.screenTitle.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = Typography.tightTracking.sp),
                color = SyllColors.ink,
                modifier = Modifier.semantics { heading() },
            )
            Column(Modifier.padding(top = Spacing.m).cardSurface()) {
                group.meanings.forEachIndexed { index, word ->
                    if (index > 0) CardDivider()
                    val busy = word.meaningId in store.busyMeaningIds
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .alpha(if (busy) 0.5f else 1f)
                            .defaultMinSize(minHeight = Spacing.rowMinHeight)
                            .padding(start = Spacing.m, end = Spacing.xs, top = Spacing.xxs, bottom = Spacing.xxs),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) { WordStatus(word.meaningText, word.translationPending, word.recalled) }
                        if (word.overridden && word.isEditable) StatusPill(stringResource(R.string.edited))
                        ActionMenu(word, enabled = !busy) { onAction(it, word) }
                    }
                }
            }
        }
    }
}

