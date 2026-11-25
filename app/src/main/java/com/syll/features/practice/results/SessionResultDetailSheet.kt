package com.syll.features.practice.results

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import com.syll.features.words.WordDetailSheet
import android.icu.text.ListFormatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.syll.R
import com.syll.app.LocalLearnerStore
import com.syll.designsystem.components.CardDivider
import com.syll.designsystem.components.FullScreenDialog
import com.syll.designsystem.components.Eyebrow
import com.syll.designsystem.components.cardSurface
import com.syll.designsystem.components.pressScaleClickable
import com.syll.designsystem.motion.LocalMotion
import com.syll.designsystem.motion.MotionTokens
import com.syll.designsystem.motion.SymbolReplace
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography
import com.syll.domain.practice.PracticeResult
import com.syll.domain.practice.PronunciationBand
import com.syll.domain.practice.ResultCause
import com.syll.domain.practice.ResultDateText
import com.syll.domain.practice.ResultItem
import com.syll.domain.practice.ResultSection
import com.syll.domain.practice.ResultSheetBlock
import com.syll.domain.practice.ResultSheetGroup
import com.syll.domain.practice.ResultSheetModel
import com.syll.domain.practice.ResultSkill
import com.syll.features.practice.title
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * iOS `SessionResultDetailSheet`: the words behind the tiles, grouped as the server decided. Opens scrolled to the
 * tapped tile's block. (Tapping a word opens its page once My Words lands, step 7.)
 */
@Composable
fun SessionResultDetailSheet(result: PracticeResult, focus: ResultSection, onDismiss: () -> Unit) {
    val learner = LocalLearnerStore.current
    val zone = ResultDateText.zone(learner.profile?.settings?.timeZone)
    val blocks = remember(result) { ResultSheetModel.blocks(result) }
    val listState = rememberLazyListState()
    // Keys of groups opened against their default, and of groups closed against it.
    val expanded = remember { mutableStateListOf<String>() }
    val collapsed = remember { mutableStateListOf<String>() }
    var selectedWord by remember { mutableStateOf<ResultItem?>(null) }

    LaunchedEffect(focus) {
        // The first block of the tapped tile's section.
        val index = blocks.indexOfFirst { it.section == focus }
        if (index > 0) listState.scrollToItem(index)
    }

    FullScreenDialog(title = stringResource(R.string.session_details), onDismiss = onDismiss) { padding ->
        LazyColumn(
            Modifier.fillMaxWidth().padding(padding).navigationBarsPadding(),
            state = listState,
            contentPadding = PaddingValues(start = Spacing.screenGutter, end = Spacing.screenGutter, top = Spacing.s, bottom = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l + Spacing.xxs),
        ) {
            itemsIndexed(blocks, key = { _, block -> "${block.section}-${block.title}" }) { _, block ->
                BlockView(block, zone, onOpenWord = { selectedWord = it }, isExpanded = { group ->
                    val key = "${block.section}-${block.title}-${group.kind}"
                    if (group.collapsedByDefault) key in expanded else key !in collapsed
                }, onToggle = { group ->
                    val key = "${block.section}-${block.title}-${group.kind}"
                    val list = if (group.collapsedByDefault) expanded else collapsed
                    if (key in list) list.remove(key) else list.add(key)
                })
            }
        }
    }
    selectedWord?.let { item ->
        WordDetailSheet(
            wordId = item.wordId ?: return@let,
            lemma = item.lemma,
            ipa = null,
            onDismiss = { selectedWord = null },
            focusMeaningId = item.meaningId,
        )
    }
}

@Composable
private fun BlockView(
    block: ResultSheetBlock,
    zone: ZoneId,
    onOpenWord: (ResultItem) -> Unit,
    isExpanded: (ResultSheetGroup) -> Boolean,
    onToggle: (ResultSheetGroup) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp)) {
            Eyebrow(block.title.text(), Modifier.semantics { heading() })
            // The block's proportions; each group header below carries its part's swatch, so the names are the key.
            block.bar?.let { SegmentedResultBar(it, revealed = true, modifier = Modifier.semantics(mergeDescendants = true) {}) }
        }
        block.spoken?.let {
            Text(
                stringResource(R.string.said_right_x_of_x_words, it.passed, it.total),
                style = Typography.secondary.copy(fontWeight = FontWeight.SemiBold),
                color = SyllColors.ink,
            )
        }
        if (block.groups.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().cardSurface()) {
                block.groups.forEachIndexed { index, group ->
                    if (index > 0) CardDivider()
                    GroupView(group, hasBar = block.bar != null, open = isExpanded(group), zone = zone, onToggle = { onToggle(group) }, onOpenWord = onOpenWord)
                }
            }
        }
        block.excluded?.takeIf { it.count > 0 }?.let { excluded ->
            Text(
                stringResource(R.string.x_answers_weren_t_scored_x, excluded.count, reasonList(excluded.reasons)),
                style = Typography.micro,
                color = SyllColors.mutedInk,
            )
        }
        if (block.countedAsReview.isNotEmpty()) {
            Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                val count = block.countedAsReview.size
                Text(
                    pluralStringResource(R.plurals.x_due_words_counted_as_review, count, count),
                    style = Typography.secondary.copy(fontWeight = FontWeight.SemiBold),
                    color = SyllColors.ink,
                )
                block.countedAsReview.first().nextReviewAt?.let {
                    Text(nextReviewText(it, zone), style = Typography.micro, color = SyllColors.mutedInk)
                }
            }
        }
        if (block.title == ResultSheetBlock.Title.SPEAKING) {
            Text(stringResource(R.string.speaking_never_changes_your_progress), style = Typography.micro, color = SyllColors.mutedInk)
        }
    }
}

/** The 14 × 10 group swatch and the gap after it (gap-2.5). */
private val SWATCH_WIDTH = 14.dp
private val SWATCH_HEIGHT = 10.dp
private val SWATCH_GAP = Spacing.xs + 2.dp

@Composable
private fun GroupView(group: ResultSheetGroup, hasBar: Boolean, open: Boolean, zone: ZoneId, onToggle: () -> Unit, onOpenWord: (ResultItem) -> Unit) {
    val motion = LocalMotion.current
    val state = stringResource(if (open) R.string.expanded else R.string.collapsed)
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = Spacing.minTouchTarget)
                .pressScaleClickable(onClick = onToggle)
                .semantics(mergeDescendants = true) { stateDescription = state }
                .padding(horizontal = Spacing.m, vertical = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(SWATCH_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The swatch keys the group to its bar segment; without one the column stays, so words line up.
            val level = group.kind.barLevel
            if (hasBar && level != null) ResultSwatch(level, Modifier.size(SWATCH_WIDTH, SWATCH_HEIGHT)) else Spacer(Modifier.width(SWATCH_WIDTH))
            Column(Modifier.weight(1f)) {
                Text(group.kind.header(group.items.size), style = Typography.secondary.copy(fontWeight = FontWeight.Bold), color = SyllColors.ink)
                subtitle(group, zone)?.let { Text(it, style = Typography.caption, color = SyllColors.mutedInk) }
            }
            SymbolReplace(open) { isOpen ->
                Icon(
                    if (isOpen) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    tint = SyllColors.ink400,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        // Opens and closes with the `snap` spring; the rows fade in place.
        AnimatedVisibility(
            open,
            enter = expandVertically(motion.animation(MotionTokens.snap), expandFrom = Alignment.Top) + fadeIn(motion.animation(MotionTokens.snap)),
            exit = shrinkVertically(motion.animation(MotionTokens.snap), shrinkTowards = Alignment.Top) + fadeOut(motion.animation(MotionTokens.snap)),
        ) {
            Column { group.items.forEach { ItemRow(it, zone, onOpenWord) } }
        }
    }
}

@Composable
private fun ItemRow(item: ResultItem, zone: ZoneId, onOpen: (ResultItem) -> Unit) {
    val hint = stringResource(R.string.opens_the_word)
    Row(
        Modifier
            .fillMaxWidth()
            // A row with a word opens its page.
            .then(if (item.wordId != null) Modifier.pressScaleClickable { onOpen(item) }.semantics { stateDescription = hint } else Modifier)
            // Words line up under the group title, past the swatch column.
            .padding(start = Spacing.m + SWATCH_WIDTH + SWATCH_GAP, end = Spacing.m, bottom = Spacing.s)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.lemma, style = Typography.body.copy(fontWeight = FontWeight.SemiBold), color = SyllColors.ink)
            if (item.gloss.isNotEmpty()) Text(item.gloss, style = Typography.micro, color = SyllColors.mutedInk)
        }
        Spacer(Modifier.width(Spacing.xs))
        Column(horizontalAlignment = Alignment.End) {
            tag(item)?.let {
                Text(it, style = Typography.micro.copy(fontWeight = FontWeight.SemiBold), color = SyllColors.selectedInk, textAlign = TextAlign.End)
            }
            detail(item, zone)?.let { Text(it, style = Typography.caption, color = SyllColors.mutedInk, textAlign = TextAlign.End) }
        }
    }
}

@Composable
private fun tag(item: ResultItem): String? {
    // Speaking: the Azure score; a device-scored take has none.
    item.score?.let { return stringResource(R.string.score_x, it.roundToInt()) }
    item.skill?.name()?.let { return it }
    // Review and new words: what the deciding answer was.
    return item.cause?.takeIf { it != ResultCause.UNKNOWN }?.text()
}

/** Under the reason: the skill it happened in, and for a review word learned this week, when. Under a score: its band. */
@Composable
private fun detail(item: ResultItem, zone: ZoneId): String? {
    val locale = currentLocale()
    val parts = buildList {
        if (item.score != null) item.band?.takeIf { it != PronunciationBand.UNKNOWN }?.let { add(it.title()) }
        if (item.cause != null) item.struggledAt?.name()?.let { add(stringResource(R.string.while_x, it.lowercase(locale))) }
        ResultDateText.recentlyLearned(item.learnedAt, Instant.now(), zone)?.let { add(stringResource(R.string.learned_x_2, weekday(it, zone, locale))) }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** What puts a word in this group, then when the group comes back. */
@Composable
private fun subtitle(group: ResultSheetGroup, zone: ZoneId): String? {
    val explanation = group.kind.explanation()
    val next = if (group.showsNextReview) group.items.firstOrNull()?.nextReviewAt?.let { nextReviewText(it, zone) } else null
    return listOfNotNull(explanation, next).takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@Composable
private fun nextReviewText(date: Instant, zone: ZoneId): String =
    when (val next = ResultDateText.nextReview(date, Instant.now(), zone)) {
        ResultDateText.NextReview.Tonight -> stringResource(R.string.back_tonight)
        ResultDateText.NextReview.Tomorrow -> stringResource(R.string.back_tomorrow)
        is ResultDateText.NextReview.Weekday -> stringResource(R.string.back_on_x, weekday(next.date, zone, currentLocale()))
        is ResultDateText.NextReview.InDays -> stringResource(R.string.back_in_x_days, next.days.toInt())
    }

private fun weekday(date: Instant, zone: ZoneId, locale: Locale): String =
    date.atZone(zone).dayOfWeek.getDisplayName(TextStyle.FULL, locale)

@Composable
private fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

@Composable
private fun reasonList(reasons: List<String>): String {
    val names = reasons.mapNotNull { reason ->
        when (reason) {
            "invalid_recording" -> R.string.unclear_recordings
            "unavailable" -> R.string.couldn_t_record
            // Sent by an older backend only.
            "weak_template" -> R.string.easier_exercise_types
            "audio_not_played" -> R.string.audio_didn_t_play
            "quality_unqualified" -> R.string.exercises_still_in_review
            else -> null
        }
    }.map { stringResource(it) }
    return ListFormatter.getInstance(currentLocale()).format(names)
}

@Composable
fun ResultSheetBlock.Title.text(): String = stringResource(
    when (this) {
        ResultSheetBlock.Title.REVIEW -> R.string.review
        ResultSheetBlock.Title.NEW_WORDS -> R.string.new_words
        ResultSheetBlock.Title.ANSWERS -> R.string.answers
        ResultSheetBlock.Title.SPEAKING -> R.string.speaking
    },
)

@Composable
private fun ResultCause.text(): String? = when (this) {
    ResultCause.HINTED -> stringResource(R.string.used_a_hint)
    ResultCause.RETRIED -> stringResource(R.string.right_after_another_try)
    ResultCause.PRIMED -> stringResource(R.string.saw_it_just_before)
    ResultCause.RECOGNIZED_ONLY -> stringResource(R.string.recognised_not_recalled)
    ResultCause.REVEALED -> stringResource(R.string.saw_the_answer)
    ResultCause.OUT_OF_TRIES -> stringResource(R.string.not_right_after_all_tries)
    ResultCause.WRONG_CHOICE -> stringResource(R.string.picked_another_answer)
    ResultCause.NOT_PASSED -> stringResource(R.string.pronunciation_not_passed_yet)
    ResultCause.UNKNOWN -> null
}

/** Same words as the skill rows on the word page; null for a skill this app doesn't know. */
@Composable
private fun ResultSkill.name(): String? = when (this) {
    ResultSkill.SPELLING -> stringResource(R.string.spelling)
    ResultSkill.LISTENING -> stringResource(R.string.listening)
    ResultSkill.SPEAKING -> stringResource(R.string.speaking)
    ResultSkill.UNKNOWN -> null
}

/** One plain line on what puts a word in this group. */
@Composable
private fun ResultSheetGroup.Kind.explanation(): String = stringResource(
    when (this) {
        ResultSheetGroup.Kind.RELEARN -> R.string.didn_t_come_back_on_your_own
        ResultSheetGroup.Kind.SHAKY -> R.string.right_but_not_fully_on_your_own
        ResultSheetGroup.Kind.SOLID, ResultSheetGroup.Kind.CLEAN -> R.string.right_first_time_on_your_own
        ResultSheetGroup.Kind.REVEALED -> R.string.saw_the_answer_or_ran_out_of_tries
        ResultSheetGroup.Kind.RETRIED -> R.string.right_after_a_hint_or_another_try
        ResultSheetGroup.Kind.SMOOTH -> R.string.right_first_time_no_hints
        ResultSheetGroup.Kind.KNOWN -> R.string.you_already_knew_these
        ResultSheetGroup.Kind.MISSED -> R.string.not_right_this_time
        ResultSheetGroup.Kind.HELPED -> R.string.right_with_a_hint_or_another_try
        ResultSheetGroup.Kind.NOT_PASSED -> R.string.not_passed_on_the_first_try
        ResultSheetGroup.Kind.SPOKE_RIGHT -> R.string.passed_on_the_first_try
        ResultSheetGroup.Kind.BECAME_FLUENT -> R.string.said_right_again_and_again
        ResultSheetGroup.Kind.SLIPPED -> R.string.fluent_before_not_right_this_time
    },
)

@Composable
private fun ResultSheetGroup.Kind.header(count: Int): String = stringResource(
    when (this) {
        ResultSheetGroup.Kind.RELEARN -> R.string.relearn_x
        ResultSheetGroup.Kind.SHAKY -> R.string.not_solid_yet_x
        ResultSheetGroup.Kind.SOLID -> R.string.remembered_x
        ResultSheetGroup.Kind.REVEALED -> R.string.saw_the_answer_x
        ResultSheetGroup.Kind.RETRIED -> R.string.needed_a_retry_x
        ResultSheetGroup.Kind.SMOOTH -> R.string.picked_up_right_away_x
        ResultSheetGroup.Kind.KNOWN -> R.string.already_knew_x
        ResultSheetGroup.Kind.MISSED -> R.string.not_done_yet_x
        ResultSheetGroup.Kind.HELPED -> R.string.right_with_help_x
        ResultSheetGroup.Kind.CLEAN -> R.string.right_on_your_own_x
        ResultSheetGroup.Kind.NOT_PASSED -> R.string.not_passed_yet_x
        ResultSheetGroup.Kind.SPOKE_RIGHT -> R.string.said_right_x
        ResultSheetGroup.Kind.BECAME_FLUENT -> R.string.now_fluent_x
        ResultSheetGroup.Kind.SLIPPED -> R.string.need_practice_again_x
    },
    count,
)
