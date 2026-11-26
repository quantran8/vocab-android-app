package com.syll.features.journey

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.syll.R
import com.syll.app.LocalLearningJourneyStore
import com.syll.app.text
import com.syll.designsystem.components.CardDivider
import com.syll.designsystem.components.IconBadge
import com.syll.designsystem.components.IconBadgeTone
import com.syll.designsystem.components.PageScaffold
import com.syll.designsystem.components.SecondaryButton
import com.syll.designsystem.components.SectionTitle
import com.syll.designsystem.components.StatusPill
import com.syll.designsystem.components.StatusPillTone
import com.syll.designsystem.components.TextLink
import com.syll.designsystem.components.cardSurface
import com.syll.designsystem.components.pressScaleClickable
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography
import com.syll.domain.journey.JourneyPeriod
import com.syll.domain.journey.LearningJourney
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

// iOS `LearningJourneyCard` / `LearningJourneyView` (backend FRONTEND_FLOW §17). History only: these start nothing.

/** Home's journey block, after "Learning". A failed load shows a retry line and never zeros. */
@Composable
fun LearningJourneyCard(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val journey = LocalLearningJourneyStore.current
    val scope = rememberCoroutineScope()
    Column(modifier) {
        SectionTitle(stringResource(R.string.your_journey))
        val summary = journey.summary
        when {
            summary != null -> Column(Modifier.fillMaxWidth().cardSurface().pressScaleClickable(onClick = onOpen)) {
                if (summary.hasHistory) FilledWeek(summary.week) else EmptyJourneyRow()
                if (journey.summaryLoad is LearningJourneyStore.Load.Failed) {
                    Text(
                        stringResource(R.string.not_up_to_date),
                        style = Typography.micro,
                        color = SyllColors.mutedInk,
                        modifier = Modifier.padding(start = Spacing.m, end = Spacing.m, bottom = Spacing.s),
                    )
                }
            }
            journey.summaryLoad is LearningJourneyStore.Load.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.couldn_t_load_your_journey), style = Typography.secondary, color = SyllColors.mutedInk)
                Spacer(Modifier.width(Spacing.xs))
                TextLink(stringResource(R.string.try_again_2), onClick = { scope.launch { journey.refreshSummary(force = true) } })
            }
            else -> Box(Modifier.fillMaxWidth().height(148.dp).cardSurface().clearAndSetSemantics {}) {
                Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Box(Modifier.size(64.dp, 28.dp).background(SyllColors.ink100, RoundedCornerShape(6.dp)))
                    Box(Modifier.size(160.dp, 16.dp).background(SyllColors.ink100, RoundedCornerShape(6.dp)))
                    Box(Modifier.fillMaxWidth().height(16.dp).background(SyllColors.ink100, RoundedCornerShape(6.dp)))
                }
            }
        }
    }
}

@Composable
private fun FilledWeek(week: LearningJourney.Week) {
    val label = stringResource(R.string.x_new_words_this_week, week.newWords)
    Row(
        Modifier.fillMaxWidth().padding(Spacing.m).clearAndSetSemantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text("+${formatNumber(week.newWords)}", style = Typography.screenTitle.copy(fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"), color = SyllColors.ink)
            Text(stringResource(R.string.new_words_this_week), style = Typography.secondary, color = SyllColors.mutedInk)
        }
        WeekSparkline(week.cumulativeNewWords, Modifier.size(96.dp, 40.dp))
    }
    CardDivider()
    WeekRhythm(week, Modifier.padding(Spacing.m))
}

@Composable
private fun EmptyJourneyRow() {
    Row(
        Modifier.fillMaxWidth().padding(Spacing.m).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(Icons.Outlined.Route, IconBadgeTone.ACCENT)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(stringResource(R.string.your_journey_starts_with_one_word), style = Typography.secondary.copy(fontWeight = FontWeight.Bold), color = SyllColors.ink)
            Text(stringResource(R.string.finish_your_first_session_to_begin), style = Typography.micro, color = SyllColors.mutedInk)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = SyllColors.ink400)
    }
}

/** This week's running total of new words: no future points, a dot when only one day has passed. */
@Composable
fun WeekSparkline(values: List<Int>, modifier: Modifier = Modifier) {
    // At the largest text sizes the number and label need the room.
    if (values.isEmpty() || LocalDensity.current.fontScale >= 1.5f) return
    val color = SyllColors.blue500
    Canvas(modifier.clearAndSetSemantics {}) {
        val top = (values.last()).coerceAtLeast(1).toFloat()
        val inset = 4.dp.toPx()
        fun point(index: Int, value: Int) = Offset(
            inset + (size.width - 2 * inset) * index / 6f,
            size.height - inset - (size.height - 2 * inset) * value / top,
        )
        if (values.size == 1) {
            drawCircle(color, radius = 3.dp.toPx(), center = point(0, values[0]))
            return@Canvas
        }
        val path = Path().apply {
            values.forEachIndexed { index, value -> point(index, value).let { if (index == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
        }
        drawPath(path, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Sessions done against the weekly goal, over Monday–Sunday marks. Each mark has a shape, not only a colour. */
@Composable
fun WeekRhythm(week: LearningJourney.Week, modifier: Modifier = Modifier) {
    val goal = week.weeklySessionGoal
    val summary = if (goal != null) {
        stringResource(R.string.x_of_x_sessions_this_week, week.completedSessions, goal)
    } else {
        stringResource(R.string.x_sessions_this_week, week.completedSessions)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Row(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = summary }, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.sessions_this_week), style = Typography.secondary.copy(fontWeight = FontWeight.SemiBold), color = SyllColors.ink, modifier = Modifier.weight(1f))
            if (week.goalReached) {
                StatusPill(stringResource(R.string.weekly_goal_reached), StatusPillTone.SUCCESS, Icons.Filled.Check, Modifier.padding(end = Spacing.xs))
            }
            Text(
                if (goal != null) "${week.completedSessions}/$goal" else "${week.completedSessions}",
                style = Typography.secondary.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                color = SyllColors.ink,
            )
        }
        Row(Modifier.fillMaxWidth()) {
            val todayId = week.today?.date
            week.days.forEach { day -> DayMark(day, isToday = day.date == todayId, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun DayMark(day: LearningJourney.Day, isToday: Boolean, modifier: Modifier) {
    val locale = appLocale()
    val weekday = day.localDate?.dayOfWeek
    val name = weekday?.getDisplayName(TextStyle.FULL, locale) ?: day.date
    val label = when {
        day.completed == true -> stringResource(R.string.x_studied, name)
        day.completed == false && isToday -> stringResource(R.string.x_today_not_studied_yet, name)
        day.completed == false -> stringResource(R.string.x_not_studied, name)
        else -> stringResource(R.string.x_upcoming, name)
    }
    Column(
        modifier.clearAndSetSemantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            weekday?.getDisplayName(TextStyle.NARROW, locale).orEmpty(),
            style = Typography.micro.copy(fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal),
            color = if (isToday) SyllColors.ink else SyllColors.mutedInk,
        )
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            when {
                day.completed == true -> Icon(Icons.Filled.CheckCircle, null, tint = SyllColors.blue500, modifier = Modifier.size(22.dp))
                day.completed == false && isToday -> Box(Modifier.size(20.dp).border(2.dp, SyllColors.ink400, CircleShape))
                day.completed == false -> Icon(Icons.Outlined.Remove, null, tint = SyllColors.ink300, modifier = Modifier.size(16.dp))
                else -> Box(Modifier.size(8.dp).background(SyllColors.ink100, CircleShape))
            }
        }
    }
}

// Detail page

/** iOS `LearningJourneyView`: pushed from Home's journey card. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningJourneyView(onBack: () -> Unit) {
    val journey = LocalLearningJourneyStore.current
    var period by rememberSaveable { mutableStateOf(JourneyPeriod.MONTH) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(period) { journey.load(period) }

    PageScaffold(title = stringResource(R.string.learning_journey), onBack = onBack) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    journey.load(period, force = true)
                    isRefreshing = false
                }
            },
            modifier = Modifier.padding(padding),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screenGutter)
                    .padding(bottom = Spacing.l),
            ) {
                val data = journey.periods[period]
                if (data != null && !data.hasHistory) {
                    EmptyJourneyCard()
                    return@Column
                }
                SectionTitle(stringResource(R.string.your_vocabulary))
                GrowthCard(period, data, journey.periodLoads[period], onPeriodChange = { period = it }, onRetry = { scope.launch { journey.load(period, force = true) } })

                if (data != null) {
                    val week = (journey.periods[JourneyPeriod.WEEK] ?: data).week
                    SectionTitle(stringResource(R.string.this_week_s_rhythm), Modifier.padding(top = Spacing.xl))
                    WeekRhythm(week, Modifier.fillMaxWidth().cardSurface().padding(Spacing.m))

                    SectionTitle(stringResource(activityTitle(period)), Modifier.padding(top = Spacing.xl))
                    Activity(data.period)
                    data.historyAvailableFrom?.let { from ->
                        Text(
                            stringResource(R.string.tracking_since_x, longDate(from)),
                            style = Typography.micro,
                            color = SyllColors.mutedInk,
                            modifier = Modifier.padding(top = Spacing.s),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GrowthCard(
    period: JourneyPeriod,
    data: LearningJourney?,
    load: LearningJourneyStore.Load?,
    onPeriodChange: (JourneyPeriod) -> Unit,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().cardSurface().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        // iOS uses its segmented control; Android's is the Material segmented button row.
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val options = listOf(JourneyPeriod.WEEK to R.string.week, JourneyPeriod.MONTH to R.string.month, JourneyPeriod.ALL to R.string.all_time)
            options.forEachIndexed { index, (value, title) ->
                SegmentedButton(
                    selected = period == value,
                    onClick = { onPeriodChange(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = SyllColors.selectedSurface,
                        activeContentColor = SyllColors.selectedInk,
                        inactiveContainerColor = SyllColors.surface,
                        inactiveContentColor = SyllColors.ink,
                    ),
                ) { Text(stringResource(title), style = Typography.secondary.copy(fontWeight = FontWeight.SemiBold)) }
            }
        }
        when {
            data != null -> Growth(period, data.period)
            load is LearningJourneyStore.Load.Failed -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(load.message.text(), style = Typography.secondary, color = SyllColors.errorInk)
                SecondaryButton(stringResource(R.string.try_again_2), onClick = onRetry, icon = Icons.Filled.Refresh)
            }
            else -> Box(Modifier.fillMaxWidth().heightIn(min = 180.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), color = SyllColors.mutedInk, strokeWidth = 2.5.dp)
            }
        }
    }
}

@Composable
private fun Growth(period: JourneyPeriod, data: LearningJourney.Period) {
    val start = data.wordsAtStart
    val end = data.wordsAtEnd
    if (start == null || end == null) {
        Text(stringResource(R.string.no_learning_yet_in_this_period), style = Typography.secondary, color = SyllColors.mutedInk)
        return
    }
    Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text("+${formatNumber(end - start)}", style = Typography.screenTitle.copy(fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"), color = SyllColors.ink)
        Text(stringResource(newWordsCaption(period)), style = Typography.secondary, color = SyllColors.mutedInk)
    }
    val chartLabel = stringResource(R.string.vocabulary_grew_from_x_to_x_words, start, end)
    GrowthChart(data, Modifier.fillMaxWidth().height(180.dp).clearAndSetSemantics { contentDescription = chartLabel })
    Row(Modifier.fillMaxWidth()) {
        Figure(stringResource(R.string.at_start), start)
        Spacer(Modifier.weight(1f))
        Figure(stringResource(R.string.now), end)
    }
}

@Composable
private fun Figure(title: String, value: Int) {
    Row(Modifier.semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(title, style = Typography.secondary, color = SyllColors.mutedInk)
        Text(formatNumber(value), style = Typography.secondary.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"), color = SyllColors.ink)
    }
}

/**
 * Cumulative words learned, from the period's start to today, on real date spacing. No future points, no smoothing;
 * one point draws as a dot. Dates along the bottom, the range along the end edge.
 */
@Composable
private fun GrowthChart(period: LearningJourney.Period, modifier: Modifier) {
    val samples = remember(period) { samples(period) }
    if (samples.isEmpty()) return
    val low = samples.minOf { it.second }
    val high = maxOf(samples.maxOf { it.second }, low + 1)
    val first = samples.first().first
    val span = maxOf(ChronoUnit.DAYS.between(first, samples.last().first), 1).toFloat()
    val line = SyllColors.blue500
    val area = SyllColors.blue50
    val grid = SyllColors.ink100
    Column(modifier) {
        Row(Modifier.weight(1f)) {
            Canvas(Modifier.weight(1f).fillMaxWidth().height(150.dp)) {
                val inset = 4.dp.toPx()
                fun x(date: LocalDate) = inset + (size.width - 2 * inset) * ChronoUnit.DAYS.between(first, date) / span
                fun y(words: Int) = size.height - inset - (size.height - 2 * inset) * (words - low) / (high - low).toFloat()
                listOf(0f, 0.5f, 1f).forEach { f ->
                    val gy = inset + (size.height - 2 * inset) * f
                    drawLine(grid, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx())
                }
                if (samples.size == 1) {
                    drawCircle(line, 4.dp.toPx(), Offset(x(samples[0].first), y(samples[0].second)))
                    return@Canvas
                }
                val stroke = Path()
                samples.forEachIndexed { index, (date, words) -> if (index == 0) stroke.moveTo(x(date), y(words)) else stroke.lineTo(x(date), y(words)) }
                val fill = Path().apply {
                    addPath(stroke)
                    lineTo(x(samples.last().first), size.height - inset)
                    lineTo(x(first), size.height - inset)
                    close()
                }
                drawPath(fill, area)
                drawPath(stroke, line, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            Column(Modifier.height(150.dp).padding(start = Spacing.xs), verticalArrangement = Arrangement.SpaceBetween) {
                Text(formatNumber(high), style = Typography.caption, color = SyllColors.mutedInk)
                Text(formatNumber(low), style = Typography.caption, color = SyllColors.mutedInk)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = Spacing.xs)) {
            Text(axisLabel(first, period.bucketGranularity), style = Typography.caption, color = SyllColors.mutedInk)
            Spacer(Modifier.weight(1f))
            Text(axisLabel(samples.last().first, period.bucketGranularity), style = Typography.caption, color = SyllColors.mutedInk)
        }
    }
}

/** The period's baseline, then one point per bucket; a bucket ending on the start day replaces the baseline. */
internal fun samples(period: LearningJourney.Period): List<Pair<LocalDate, Int>> {
    val result = mutableListOf<Pair<LocalDate, Int>>()
    val start = runCatching { LocalDate.parse(period.startDate) }.getOrNull()
    val baseline = period.wordsAtStart
    if (start != null && baseline != null) result.add(start to baseline)
    period.points.forEach { point ->
        val date = runCatching { LocalDate.parse(point.date) }.getOrNull() ?: return@forEach
        if (result.lastOrNull()?.first == date) result.removeAt(result.lastIndex)
        result.add(date to point.cumulativeWords)
    }
    return result
}

@Composable
private fun Activity(data: LearningJourney.Period) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            StatTile(stringResource(R.string.days_learned), data.activeDays, Icons.Outlined.CalendarMonth, Modifier.weight(1f))
            StatTile(stringResource(R.string.sessions_completed), data.completedSessions, Icons.Outlined.CheckCircleOutline, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            StatTile(stringResource(R.string.new_words), data.newWords, Icons.Outlined.AddCircleOutline, Modifier.weight(1f))
            StatTile(stringResource(R.string.meaning_reviews), data.reviewedMeanings, Icons.Filled.Refresh, Modifier.weight(1f))
        }
        Text(stringResource(R.string.each_finished_learning_or_review_part_counts_as), style = Typography.micro, color = SyllColors.mutedInk)
    }
}

@Composable
private fun StatTile(title: String, value: Int?, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Column(modifier.cardSurface().padding(Spacing.m).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        IconBadge(icon, IconBadgeTone.ACCENT, Spacing.iconButton)
        Text(if (value != null) formatNumber(value) else "–", style = Typography.screenTitle.copy(fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"), color = SyllColors.ink)
        Text(title, style = Typography.micro, color = SyllColors.mutedInk)
    }
}

@Composable
private fun EmptyJourneyCard() {
    Column(Modifier.fillMaxWidth().cardSurface().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        IconBadge(Icons.Outlined.Route, IconBadgeTone.ACCENT)
        Text(stringResource(R.string.your_journey_starts_with_one_word), style = Typography.emphasis, color = SyllColors.ink)
        Text(stringResource(R.string.finish_your_first_session_to_begin), style = Typography.secondary, color = SyllColors.mutedInk)
    }
}

private fun newWordsCaption(period: JourneyPeriod) = when (period) {
    JourneyPeriod.WEEK -> R.string.new_words_this_week
    JourneyPeriod.MONTH -> R.string.new_words_this_month
    JourneyPeriod.ALL -> R.string.new_words_since_you_started
}

private fun activityTitle(period: JourneyPeriod) = when (period) {
    JourneyPeriod.WEEK -> R.string.activity_this_week
    JourneyPeriod.MONTH -> R.string.activity_this_month
    JourneyPeriod.ALL -> R.string.activity_since_you_started
}

@Composable
private fun appLocale(): Locale = LocalConfiguration.current.locales[0]

@Composable
private fun formatNumber(value: Int): String = NumberFormat.getIntegerInstance(appLocale()).format(value)

@Composable
private fun longDate(day: String): String {
    val date = runCatching { LocalDate.parse(day) }.getOrNull() ?: return day
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(appLocale()).format(date)
}

@Composable
private fun axisLabel(date: LocalDate, granularity: String): String {
    val locale = appLocale()
    val pattern = when (granularity) {
        "day" -> "d/M"
        "year" -> "yyyy"
        else -> "MMM yy"
    }
    return DateTimeFormatter.ofPattern(pattern, locale).format(date)
}
