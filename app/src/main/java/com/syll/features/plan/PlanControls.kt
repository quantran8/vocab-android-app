package com.syll.features.plan

import androidx.compose.runtime.getValue
import com.syll.designsystem.motion.MotionTokens
import com.syll.designsystem.motion.LocalMotion
import androidx.compose.animation.core.animateFloatAsState
import com.syll.designsystem.motion.RollingNumber
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.ConfigurationCompat
import com.syll.R
import com.syll.designsystem.components.CardDivider
import com.syll.designsystem.components.ChoiceCard
import com.syll.designsystem.components.ChoiceRow
import com.syll.designsystem.components.CollectionMarkDot
import com.syll.designsystem.components.DayToggle
import com.syll.designsystem.components.Eyebrow
import com.syll.designsystem.components.IconBadge
import com.syll.designsystem.components.IconBadgeTone
import com.syll.designsystem.components.InfoPopoverButton
import com.syll.designsystem.components.PlanSectionTitle
import com.syll.designsystem.components.SymbolIcons
import com.syll.designsystem.components.TextLink
import com.syll.designsystem.components.TopicChip
import com.syll.designsystem.components.cardSurface
import com.syll.designsystem.components.pressScaleClickable
import com.syll.designsystem.tokens.Radii
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography
import com.syll.domain.AppLanguage
import com.syll.domain.CefrLevel
import com.syll.domain.LearningPreferences
import com.syll.domain.VocabCollection
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

// Learning-plan inputs shared by onboarding and the Profile plan editor (iOS `PlanControls.swift`).

val CefrLevel.title: Int
    get() = when (this) {
        CefrLevel.A1 -> R.string.beginner
        CefrLevel.A2 -> R.string.elementary
        CefrLevel.B1 -> R.string.intermediate
        CefrLevel.B2 -> R.string.upper_intermediate
        CefrLevel.C1 -> R.string.advanced
        CefrLevel.C2 -> R.string.proficient
    }

/** What a learner at this level can do, so choosing by hand has something to go on. */
val CefrLevel.example: Int
    get() = when (this) {
        CefrLevel.A1 -> R.string.greetings_introducing_yourself
        CefrLevel.A2 -> R.string.shopping_asking_the_way_daily_errands
        CefrLevel.B1 -> R.string.telling_stories_talking_about_work
        CefrLevel.B2 -> R.string.reading_the_news_meetings_debates
        CefrLevel.C1 -> R.string.reading_specialist_texts
        CefrLevel.C2 -> R.string.close_to_a_native_speaker
    }

/** iOS `LevelList`: the six levels as rows in one card: code, name and an example of what it covers. */
@Composable
fun LevelList(selected: CefrLevel?, onSelect: (CefrLevel) -> Unit) {
    ChoiceCard(CefrLevel.entries) { level ->
        val isSelected = level == selected
        ChoiceRow(isSelected, onClick = { onSelect(level) }) {
            Box(
                Modifier
                    .size(44.dp, 36.dp)
                    .background(if (isSelected) SyllColors.interactive else SyllColors.neutralSurface, RoundedCornerShape(Radii.badge)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    level.code,
                    style = Typography.secondary.copy(fontWeight = FontWeight.ExtraBold),
                    color = if (isSelected) androidx.compose.ui.graphics.Color.White else SyllColors.ink700,
                )
            }
            Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(level.title),
                    style = Typography.secondary.copy(fontWeight = FontWeight.Bold),
                    color = if (isSelected) SyllColors.selectedInk else SyllColors.ink,
                )
                Text(stringResource(level.example), style = Typography.micro, color = SyllColors.mutedInk, maxLines = 1)
            }
        }
    }
}

/** iOS `CollectionList`: one row per collection: mark dot, name, summary and topic count. Exactly one is selected. */
@Composable
fun CollectionList(selectedId: String, onSelect: (String) -> Unit) {
    ChoiceCard(CatalogState.collections) { collection ->
        val isSelected = collection.id == selectedId
        ChoiceRow(isSelected, onClick = { onSelect(collection.id) }, alignTop = true) {
            Box(Modifier.padding(top = 6.dp)) { CollectionMarkDot(CollectionMarkColor.color(collection)) }
            Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(collection.displayName(), style = Typography.emphasis, color = if (isSelected) SyllColors.selectedInk else SyllColors.ink)
                val summary = collection.displaySummary()
                if (summary.isNotEmpty()) Text(summary, style = Typography.micro, color = SyllColors.mutedInk)
                Text(
                    stringResource(R.string.x_topics, collection.topics.size),
                    style = Typography.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = SyllColors.mutedInk,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** iOS `PlacementTestCard`: "Not sure? Take the test", first on a level screen. */
@Composable
fun PlacementTestCard(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressScaleClickable(onClick = onClick)
            .cardSurface()
            .padding(Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(Icons.Outlined.Checklist, IconBadgeTone.ACCENT)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.not_sure_take_the_test), style = Typography.emphasis, color = SyllColors.ink)
            Text(stringResource(R.string.syll_scores_it_and_picks_your_level), style = Typography.micro, color = SyllColors.mutedInk)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = SyllColors.ink400)
    }
}

/**
 * iOS `TopicPicker`: the topics of one collection, with a "Select all / Clear all" shortcut. Under a free topic
 * limit the unselected chips dim once the limit is reached but stay tappable.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TopicPicker(
    collection: VocabCollection,
    selectedIds: Set<String>,
    onToggleTopic: (String) -> Unit,
    onToggleAll: () -> Unit,
    countsSelection: Boolean = false,
    showsProgress: Boolean = true,
    maxTopics: Int? = null,
) {
    val hasAll = selectedIds == collection.topicIds
    val selected = selectedIds.intersect(collection.topicIds).size
    val limit = maxTopics?.takeIf { it < collection.topics.size }
    val atLimit = maxTopics?.let { selectedIds.size >= it } ?: false
    val name = collection.displayName()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(
                when {
                    limit != null -> stringResource(R.string.x_x_x_free_topics, name, selected, limit)
                    countsSelection -> stringResource(R.string.x_x_x_topics, name, selected, collection.topics.size)
                    else -> name
                },
                Modifier.weight(1f),
            )
            if (limit == null || selected > 0) {
                TextLink(
                    stringResource(if (hasAll || limit != null) R.string.clear_all else R.string.select_all),
                    onClick = onToggleAll,
                )
            }
        }
        // Topics with new words left first; finished ones last but still selectable.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            (collection.topics.filter { !it.isFinished } + collection.topics.filter { it.isFinished }).forEach { topic ->
                val isSelected = topic.id in selectedIds
                TopicChip(
                    title = topic.displayName(collection),
                    isSelected = isSelected,
                    onClick = { onToggleTopic(topic.id) },
                    icon = SymbolIcons.icon(topic.icon),
                    progress = if (showsProgress) topic.total?.let { (topic.learned ?: 0) to it } else null,
                    isFinished = showsProgress && topic.isFinished,
                    isLocked = atLimit && !isSelected,
                )
            }
        }
    }
}

/** iOS `WordsPerDayStepper`: − value + inside a card, clamped to [range]. */
@Composable
fun WordsPerDayStepper(
    value: Int,
    onAdjust: (Int) -> Unit,
    range: IntRange = LearningPreferences.WORDS_PER_DAY_RANGE,
    label: String = stringResource(R.string.new_words_per_day),
    decreaseLabel: String = stringResource(R.string.decrease_new_words_per_day),
    increaseLabel: String = stringResource(R.string.increase_new_words_per_day),
) {
    val valueText = stringResource(R.string.x_words, value)
    Row(
        Modifier.fillMaxWidth().cardSurface().padding(Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton(Icons.Outlined.Remove, decreaseLabel, enabled = value > range.first) { onAdjust(-1) }
        Row(
            Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) {
                    contentDescription = label
                    stateDescription = valueText
                    progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), range.first.toFloat()..range.last.toFloat(), range.last - range.first - 1)
                    setProgress { target ->
                        val delta = target.toInt() - value
                        if (delta != 0) onAdjust(delta.coerceIn(-1, 1))
                        delta != 0
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs - 2.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Bottom,
        ) {
            RollingNumber(value, Typography.stat.copy(letterSpacing = Typography.tightTracking.sp), SyllColors.ink)
            Text(
                stringResource(R.string.words),
                style = Typography.body.copy(fontWeight = FontWeight.SemiBold),
                color = SyllColors.mutedInk,
                modifier = Modifier.padding(bottom = 5.dp),
            )
        }
        StepperButton(Icons.Outlined.Add, increaseLabel, enabled = value < range.last) { onAdjust(1) }
    }
}

@Composable
private fun StepperButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(Spacing.fieldHeight)
            .alpha(if (enabled) 1f else 0.35f)
            .pressScaleClickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label }
            .background(SyllColors.neutralSurface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = SyllColors.ink, modifier = Modifier.size(22.dp))
    }
}

/** iOS `Weekday`: weekday labels in the app's language, starting on the locale's first day (Monday in Vietnamese). */
data class Weekday(
    /** `Calendar` weekday number, 1 = Sunday. */
    val id: Int,
    val short: String,
    val full: String,
) {
    companion object {
        fun ordered(locale: Locale, language: AppLanguage): List<Weekday> {
            // A Vietnamese week runs T2 → CN whatever the device region says.
            val first = if (language == AppLanguage.VIETNAMESE) DayOfWeek.MONDAY else WeekFields.of(locale).firstDayOfWeek
            return (0 until 7).map { offset ->
                val day = first.plus(offset.toLong())
                val id = day.value % 7 + 1
                val short = if (language == AppLanguage.VIETNAMESE) {
                    if (id == 1) "CN" else "T$id"
                } else {
                    day.getDisplayName(TextStyle.SHORT, locale)
                }
                Weekday(id, short, day.getDisplayName(TextStyle.FULL, locale))
            }
        }
    }
}

@Composable
fun rememberWeekdays(): List<Weekday> {
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    val language = AppLanguage.fromLanguageTag(locale.toLanguageTag()) ?: AppLanguage.ENGLISH
    return Weekday.ordered(locale, language)
}

/** iOS `LearningDaysSummary`: "Every day", a run such as "T2 – T6", or the days listed. */
@Composable
fun learningDaysSummary(days: Set<Int>): String {
    if (days == LearningPreferences.ALL_WEEKDAYS) return stringResource(R.string.every_day)
    val ordered = rememberWeekdays()
    val picked = ordered.withIndex().filter { it.value.id in days }
    val first = picked.firstOrNull() ?: return "–"
    val last = picked.last()
    if (picked.size >= 3 && last.index - first.index == picked.size - 1) return "${first.value.short} – ${last.value.short}"
    return picked.joinToString(", ") { it.value.short }
}

/** iOS `LearningDaysPicker`: title with an "Every day / Clear all" link, seven day toggles and a summary line. */
@Composable
fun LearningDaysPicker(selected: Set<Int>, onToggleDay: (Int) -> Unit, onToggleEveryDay: () -> Unit) {
    val isEveryDay = selected == LearningPreferences.ALL_WEEKDAYS
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s - 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlanSectionTitle(stringResource(R.string.learning_days), Modifier.weight(1f))
            TextLink(stringResource(if (isEveryDay) R.string.clear_all else R.string.every_day), onClick = onToggleEveryDay)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs - 2.dp)) {
            rememberWeekdays().forEach { day ->
                DayToggle(day.short, day.full, day.id in selected, onClick = { onToggleDay(day.id) }, modifier = Modifier.weight(1f))
            }
        }
        Text(
            when {
                selected.isEmpty() -> stringResource(R.string.choose_at_least_one_day)
                isEveryDay -> stringResource(R.string.every_day)
                else -> stringResource(R.string.x_days_a_week, selected.size)
            },
            style = Typography.micro,
            color = SyllColors.mutedInk,
        )
    }
}

/** iOS `ReviewsPerSessionPicker`: follows the new-word goal until the learner sets a number. */
@Composable
fun ReviewsPerSessionPicker(preferences: LearningPreferences, onChange: (LearningPreferences) -> Unit, onSelectionChanged: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s - 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlanSectionTitle(stringResource(R.string.reviews_session), Modifier.weight(1f))
            if (preferences.reviewsPerSession != null) {
                TextLink(stringResource(R.string.match_new_words), onClick = {
                    onChange(preferences.copy(reviewsPerSession = null))
                    onSelectionChanged()
                })
            }
        }
        WordsPerDayStepper(
            value = preferences.effectiveReviewsPerSession,
            range = LearningPreferences.REVIEWS_PER_SESSION_RANGE,
            label = stringResource(R.string.reviews_per_session),
            decreaseLabel = stringResource(R.string.decrease_reviews_per_session),
            increaseLabel = stringResource(R.string.increase_reviews_per_session),
            onAdjust = { amount ->
                val next = preferences.adjustReviewsPerSession(amount)
                if (next.effectiveReviewsPerSession != preferences.effectiveReviewsPerSession) onSelectionChanged()
                onChange(next)
            },
        )
        Text(
            if (preferences.reviewsPerSession == null) {
                stringResource(R.string.follows_your_new_words_at_least_x, LearningPreferences.REVIEWS_PER_SESSION_FOLLOW_MIN)
            } else {
                stringResource(R.string.words_due_for_review_wait_for_the_next_session)
            },
            style = Typography.micro,
            color = SyllColors.mutedInk,
        )
    }
}

@Composable
private fun SyllSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, contentDescription: String) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedTrackColor = SyllColors.interactive,
            checkedThumbColor = androidx.compose.ui.graphics.Color.White,
            uncheckedTrackColor = SyllColors.ink200,
            uncheckedThumbColor = androidx.compose.ui.graphics.Color.White,
            uncheckedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
        ),
        modifier = Modifier.semantics { this.contentDescription = contentDescription },
    )
}

/** iOS `DailySpeakingToggle`: "Speaking practice" with an ⓘ saying it never changes a word's progress. */
@Composable
fun DailySpeakingToggle(preferences: LearningPreferences, onChange: (LearningPreferences) -> Unit, onSelectionChanged: () -> Unit) {
    val title = stringResource(R.string.speaking_practice_2)
    Row(
        Modifier
            .fillMaxWidth()
            .cardSurface()
            .defaultMinSize(minHeight = Spacing.rowMinHeight)
            .padding(start = Spacing.m, end = Spacing.m, top = Spacing.xxs, bottom = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlanSectionTitle(title)
        InfoPopoverButton(
            stringResource(R.string.read_words_aloud_in_your_lessons_right_or_wrong),
            stringResource(R.string.about_speaking_practice),
        )
        Spacer(Modifier.weight(1f))
        SyllSwitch(preferences.dailySpeaking, { onChange(preferences.copy(dailySpeaking = it)); onSelectionChanged() }, title)
    }
}

/**
 * iOS `ReminderPicker`: a "Remind me" switch row above the reminder time. Tapping the time opens the platform time
 * picker; the row dims while reminders are off.
 */
@Composable
fun ReminderPicker(preferences: LearningPreferences, onChange: (LearningPreferences) -> Unit) {
    val context = LocalContext.current
    val isOn = preferences.reminderEnabled
    // The time row dims with the switch (iOS animates the toggle's binding with `press`).
    val timeAlpha by animateFloatAsState(if (isOn) 1f else 0.5f, LocalMotion.current.animation(MotionTokens.press), label = "reminderDim")
    val remindMe = stringResource(R.string.remind_me)
    val time = LocalTime.of(preferences.reminderHour, preferences.reminderMinute)
    val interaction = remember { MutableInteractionSource() }
    Column(Modifier.fillMaxWidth().cardSurface()) {
        Row(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = Spacing.rowMinHeight).padding(horizontal = Spacing.m, vertical = Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlanSectionTitle(remindMe, Modifier.weight(1f))
            SyllSwitch(isOn, { onChange(preferences.copy(reminderEnabled = it)) }, remindMe)
        }
        CardDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .alpha(timeAlpha)
                .clickable(interaction, indication = null, enabled = isOn) {
                    TimePickerDialog(
                        context,
                        { _, hour, minute -> onChange(preferences.copy(reminderHour = hour, reminderMinute = minute)) },
                        preferences.reminderHour,
                        preferences.reminderMinute,
                        DateFormat.is24HourFormat(context),
                    ).show()
                }
                .defaultMinSize(minHeight = Spacing.rowMinHeight)
                .padding(horizontal = Spacing.m, vertical = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Schedule, contentDescription = null, tint = SyllColors.mutedInk, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.reminder_time), style = Typography.body, color = SyllColors.bodyInk, modifier = Modifier.weight(1f))
            Text(
                reminderTimeText(time),
                style = Typography.body,
                color = SyllColors.ink,
                modifier = Modifier
                    .background(SyllColors.neutralSurface, RoundedCornerShape(Radii.badge))
                    .padding(horizontal = Spacing.s, vertical = Spacing.xs - 2.dp),
            )
        }
    }
}

/** The reminder time in the app's language and the device's 12/24-hour setting. */
@Composable
fun reminderTimeText(time: LocalTime): String {
    val context = LocalContext.current
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    val pattern = DateFormat.getBestDateTimePattern(locale, if (DateFormat.is24HourFormat(context)) "Hm" else "hma")
    return java.time.format.DateTimeFormatter.ofPattern(pattern, locale).format(time)
}

