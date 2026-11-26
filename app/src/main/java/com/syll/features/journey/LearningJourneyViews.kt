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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.semantics.heading
import kotlinx.coroutines.coroutineScope
import com.syll.features.words.WordDetailSheet
import com.syll.domain.journey.JourneyWord
import com.syll.domain.journey.JourneyWordDay
import com.syll.designsystem.motion.LocalMotion
import com.syll.designsystem.tokens.Radii

// iOS `LearningJourneyCard` / `LearningJourneyView` (backend FRONTEND_FLOW §17). History only: these start nothing.

/**
 * iOS `LearningJourneyCard`: Home's journey block, after "Learning" (docs/app-redesign-v1.html, 1.1). This week's new
 * words and study days, the days drawn as a stamp card. "See details" (and the whole card) opens the journey page. A
 * failed load shows a retry line and never zeros.
 */
@Composable
fun LearningJourneyCard(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val journey = LocalLearningJourneyStore.current
    val scope = rememberCoroutineScope()
    Column(modifier) {
        val summary = journey.summary
        SectionTitle(stringResource(R.string.your_journey)) {
            if (summary != null) TextLink(stringResource(R.string.see_details), onClick = onOpen)
        }
        when {
            summary != null -> Column(Modifier.fillMaxWidth().cardSurface().pressScaleClickable(onClick = onOpen)) {
                if (summary.hasHistory) {
                    NewWordsBlock(summary.week)
                    CardDivider()
                    StudyDaysBlock(summary.week)
                } else {
                    EmptyJourneyRow()
                }
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
            else -> Box(Modifier.fillMaxWidth().height(160.dp).cardSurface().clearAndSetSemantics {}) {
                Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Box(Modifier.size(64.dp, 28.dp).background(SyllColors.ink100, RoundedCornerShape(6.dp)))
                    Box(Modifier.size(160.dp, 16.dp).background(SyllColors.ink100, RoundedCornerShape(6.dp)))
                    Box(Modifier.size(180.dp, 56.dp).background(SyllColors.ink100, RoundedCornerShape(6.dp)))
                }
            }
        }
    }
}

/** The count and caption shared by both blocks, so they read as peers. */
@Composable
private fun JourneyFigure(value: String, caption: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs - 2.dp)) {
        Text(value, style = Typography.screenTitle.copy(fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"), color = SyllColors.ink)
        Text(caption, style = Typography.micro.copy(fontWeight = FontWeight.SemiBold), color = SyllColors.mutedInk)
    }
}

/** "+24 · New words this week", then the newest words by name so the number says what it is made of. */
@Composable
private fun NewWordsBlock(week: LearningJourney.Week) {
    val words = week.recentWords.orEmpty()
    val names = if (words.isEmpty()) {
        null
    } else {
        val list = words.joinToString(", ") { it.lemma }
        buildAnnotatedString {
            val emphasised = SpanStyle(fontWeight = FontWeight.SemiBold, color = SyllColors.ink800)
            if (week.otherNewWords > 0) {
                val sentence = stringResource(R.string.x_and_x_more, list, week.otherNewWords)
                val at = sentence.indexOf(list)
                append(sentence)
                if (at >= 0) addStyle(emphasised, at, at + list.length)
            } else {
                withStyle(emphasised) { append(list) }
            }
        }
    }
    Column(Modifier.fillMaxWidth().padding(Spacing.m).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        JourneyFigure("+${formatNumber(week.newWords)}", stringResource(R.string.new_words_this_week))
        names?.let { Text(it, style = Typography.secondary, color = SyllColors.mutedInk, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}

/** "2/3 · Study days this week" with its stamps right under it: the stamps are that count. */
@Composable
private fun StudyDaysBlock(week: LearningJourney.Week) {
    val goal = week.weeklySessionGoal
    val locale = appLocale()
    val summary = if (goal != null) {
        stringResource(R.string.x_of_x_study_days_this_week, week.activeDays, goal)
    } else {
        stringResource(R.string.x_study_days_this_week, week.activeDays)
    }
    val days = week.studiedDays.mapNotNull { it.localDate?.dayOfWeek?.getDisplayName(TextStyle.FULL, locale) }
    val label = if (days.isEmpty()) summary else stringResource(R.string.x_studied_on_x, summary, days.joinToString(", "))
    Column(
        Modifier.fillMaxWidth().padding(Spacing.m).clearAndSetSemantics { contentDescription = label },
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.weight(1f)) {
                JourneyFigure(if (goal != null) "${week.activeDays}/$goal" else "${week.activeDays}", stringResource(R.string.study_days_this_week))
            }
            Text(weekRange(week), style = Typography.micro.copy(fontWeight = FontWeight.Bold), color = SyllColors.ink400)
        }
        StampCard(week)
    }
}

/**
 * One stamp per day learned in as many slots as the goal: never the seven weekdays, so a planned day off is never a
 * miss. Stamps past the goal are green; a postmark is struck once the goal is met.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StampCard(week: LearningJourney.Week, stampWidth: Dp = 56.dp) {
    val studied = week.studiedDays
    val goal = week.weeklySessionGoal ?: studied.size
    val slots = maxOf(goal, studied.size)
    val locale = appLocale()
    Row(verticalAlignment = Alignment.Top) {
        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            repeat(slots) { index ->
                if (index < studied.size) {
                    val label = studied[index].localDate?.dayOfWeek?.getDisplayName(TextStyle.SHORT, locale).orEmpty()
                    Stamp(label, isBonus = index >= goal, index = index, width = stampWidth)
                } else {
                    EmptyStampSlot(stampWidth)
                }
            }
        }
        if (week.goalReached) Postmark(Modifier.offset(y = -Spacing.l))
    }
}

@Composable
private fun Stamp(label: String, isBonus: Boolean, index: Int, width: Dp) {
    val motion = LocalMotion.current
    val fill = if (isBonus) SyllColors.green500 else SyllColors.blue500
    val tilts = listOf(-4f, 3f, -2f, 4f, -3f, 2f, -1f)
    Box(
        Modifier
            .size(width, width * 1.18f)
            // Pressed by hand: a slight alternating tilt. Upright with reduced motion.
            .rotate(if (motion.reduceMotion) 0f else tilts[index % tilts.size]),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val hole = size.width / 18f
            val step = size.width / 7f
            drawRect(fill)
            // The perforations: half circles cut into every edge in the card's colour.
            var x = step / 2
            while (x < size.width) {
                drawCircle(Color.White, hole, Offset(x, 0f))
                drawCircle(Color.White, hole, Offset(x, size.height))
                x += step
            }
            var y = step / 2
            while (y < size.height) {
                drawCircle(Color.White, hole, Offset(0f, y))
                drawCircle(Color.White, hole, Offset(size.width, y))
                y += step
            }
            val inset = size.width / 9f
            drawRect(Color.White.copy(alpha = 0.55f), Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset), style = Stroke(1.2.dp.toPx()))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = Color.White, fontSize = (width.value * 0.25f).sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(width * 0.24f))
        }
    }
}

@Composable
private fun EmptyStampSlot(width: Dp) {
    val color = SyllColors.ink200
    Canvas(Modifier.size(width, width * 1.18f)) {
        drawRoundRect(
            color,
            cornerRadius = CornerRadius(4.dp.toPx()),
            style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
        )
    }
}

/** The round "reached" postmark struck over the stamps. */
@Composable
private fun Postmark(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.weekly_goal_reached)
    Box(
        modifier
            .size(72.dp)
            .rotate(-12f)
            .border(2.5.dp, SyllColors.green600, CircleShape)
            .clearAndSetSemantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("SYLL", color = SyllColors.green700, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
            Text(stringResource(R.string.reached).uppercase(appLocale()), color = SyllColors.green700, fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
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
    var openWord by remember { mutableStateOf<JourneyWord?>(null) }
    LaunchedEffect(period) {
        // Words are reloaded on every visit: a session committed since may have added some.
        coroutineScope {
            launch { journey.load(period) }
            launch { journey.loadWords(period, force = true) }
        }
    }

    PageScaffold(title = stringResource(R.string.learning_journey), onBack = onBack) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    coroutineScope {
                        launch { journey.load(period, force = true) }
                        launch { journey.loadWords(period, force = true) }
                    }
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
                    SectionTitle(stringResource(newWordsCaption(period)), Modifier.padding(top = Spacing.xl)) {
                        journey.wordTotals[period]?.let {
                            Text(formatNumber(it), style = Typography.secondary.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"), color = SyllColors.mutedInk)
                        }
                    }
                    NewWordsTimeline(period, onOpen = { openWord = it })

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
    openWord?.let { word ->
        WordDetailSheet(wordId = word.wordId, lemma = word.lemma, ipa = null, onDismiss = { openWord = null })
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
        // No new-word tile: the chart and the list above already carry that number.
        Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            StatTile(stringResource(R.string.days_learned), data.activeDays, Icons.Outlined.CalendarMonth, Modifier.weight(1f).fillMaxHeight())
            StatTile(stringResource(R.string.sessions_completed), data.completedSessions, Icons.Outlined.CheckCircleOutline, Modifier.weight(1f).fillMaxHeight())
            StatTile(stringResource(R.string.meaning_reviews), data.reviewedMeanings, Icons.Filled.Refresh, Modifier.weight(1f).fillMaxHeight())
        }
        Text(stringResource(R.string.each_finished_learning_or_review_part_counts_as), style = Typography.micro, color = SyllColors.mutedInk)
    }
}

/**
 * The period's new words, a day at a time: a dot on a line per study day, its words as tiles (word over its
 * meanings). A busy day reads as one at a glance; tapping a tile opens the word.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewWordsTimeline(period: JourneyPeriod, onOpen: (JourneyWord) -> Unit) {
    val journey = LocalLearningJourneyStore.current
    val scope = rememberCoroutineScope()
    val words = journey.words[period].orEmpty()
    val total = journey.wordTotals[period] ?: 0
    val load = journey.wordLoads[period]
    if (words.isEmpty()) {
        when (load) {
            is LearningJourneyStore.Load.Failed -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(load.message.text(), style = Typography.secondary, color = SyllColors.errorInk)
                SecondaryButton(stringResource(R.string.try_again_2), onClick = { scope.launch { journey.loadWords(period, force = true) } }, icon = Icons.Filled.Refresh)
            }
            LearningJourneyStore.Load.Loaded ->
                Text(stringResource(R.string.no_new_words_in_this_period), style = Typography.secondary, color = SyllColors.mutedInk)
            else -> Box(Modifier.fillMaxWidth().heightIn(min = 80.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), color = SyllColors.mutedInk, strokeWidth = 2.5.dp)
            }
        }
        return
    }
    val days = JourneyWordDay.grouped(words)
    val allLoaded = words.size >= total
    Column {
        days.forEachIndexed { index, day ->
            val isLast = index == days.lastIndex && allLoaded
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                // The line runs past the dot down to the next day.
                Column(Modifier.width(12.dp).fillMaxHeight().clearAndSetSemantics {}, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.padding(top = 4.dp).size(12.dp).background(SyllColors.blue500, CircleShape))
                    if (!isLast) Box(Modifier.width(2.dp).weight(1f).background(SyllColors.ink100))
                }
                Column(Modifier.weight(1f).padding(bottom = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Row(Modifier.semantics(mergeDescendants = true) { heading() }, verticalAlignment = Alignment.CenterVertically) {
                        Text(dayTitle(day.date), style = Typography.secondary.copy(fontWeight = FontWeight.Bold), color = SyllColors.ink, modifier = Modifier.weight(1f))
                        Text(
                            stringResource(R.string.x_words, day.words.size),
                            style = Typography.micro.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                            color = SyllColors.mutedInk,
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        day.words.forEach { word -> WordTile(word, onClick = { onOpen(word) }) }
                    }
                }
            }
        }
        if (!allLoaded) {
            val isLoading = load == LearningJourneyStore.Load.Loading
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Spacing.minTouchTarget)
                    .pressScaleClickable(enabled = !isLoading) { scope.launch { journey.loadWords(period, more = true) } },
                contentAlignment = Alignment.Center,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = SyllColors.mutedInk, strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.show_x_more_words, total - words.size), style = Typography.secondary.copy(fontWeight = FontWeight.SemiBold), color = SyllColors.link)
                }
            }
        }
    }
}

/** A word tile as in lessons (white, 2 dp Ink 200 border, depth edge): the word over its meanings. */
@Composable
private fun WordTile(word: JourneyWord, onClick: () -> Unit) {
    val description = if (word.meaningTexts.isEmpty()) word.lemma else "${word.lemma}, ${word.meaningText}"
    val shape = RoundedCornerShape(Radii.tile)
    Column(
        Modifier
            .padding(bottom = 3.dp)
            .drawBehind {
                drawRoundRect(SyllColors.ink200, topLeft = Offset(0f, 3.dp.toPx()), size = size, cornerRadius = CornerRadius(Radii.tile.toPx()))
            }
            .background(SyllColors.surface, shape)
            .border(2.dp, SyllColors.ink200, shape)
            .pressScaleClickable(onClick = onClick)
            .clearAndSetSemantics { contentDescription = description }
            .padding(horizontal = Spacing.s, vertical = Spacing.xs - 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(word.lemma, style = Typography.secondary.copy(fontWeight = FontWeight.SemiBold), color = SyllColors.ink)
        if (word.meaningTexts.isNotEmpty()) Text(word.meaningText, style = Typography.micro, color = SyllColors.mutedInk)
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

@Composable
private fun dayTitle(day: String): String {
    val date = runCatching { LocalDate.parse(day) }.getOrNull() ?: return day
    return DateTimeFormatter.ofPattern("EEEE, d MMM", appLocale()).format(date)
}

@Composable
private fun weekRange(week: LearningJourney.Week): String {
    val start = runCatching { LocalDate.parse(week.startDate) }.getOrNull() ?: return week.startDate
    val end = runCatching { LocalDate.parse(week.endDateExclusive) }.getOrNull()?.minusDays(1) ?: return week.startDate
    val day = DateTimeFormatter.ofPattern("d", appLocale())
    val dayMonth = DateTimeFormatter.ofPattern("d/M", appLocale())
    return if (start.month == end.month) "${day.format(start)}–${dayMonth.format(end)}" else "${dayMonth.format(start)}–${dayMonth.format(end)}"
}
