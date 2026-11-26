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
import com.syll.designsystem.motion.LocalMotion
import com.syll.designsystem.tokens.Radii
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import com.syll.designsystem.components.SymbolIcons
import com.syll.designsystem.tokens.Borders

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
internal fun Stamp(label: String, isBonus: Boolean, index: Int, width: Dp, hole: Color = Color.White) {
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
            val radius = size.width / 18f
            val step = size.width / 7f
            drawRect(fill)
            // The perforations: half circles cut into every edge in the colour behind the stamp.
            var x = step / 2
            while (x < size.width) {
                drawCircle(hole, radius, Offset(x, 0f))
                drawCircle(hole, radius, Offset(x, size.height))
                x += step
            }
            var y = step / 2
            while (y < size.height) {
                drawCircle(hole, radius, Offset(0f, y))
                drawCircle(hole, radius, Offset(size.width, y))
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

/**
 * iOS `LearningJourneyView`: pushed from Home's journey card (docs/app-redesign-v1.html, 7.1). The period control sits
 * under the title, outside every card: it scopes the whole page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningJourneyView(onBack: () -> Unit) {
    val journey = LocalLearningJourneyStore.current
    var period by rememberSaveable { mutableStateOf(JourneyPeriod.MONTH) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var openTopic by remember { mutableStateOf<LearningJourney.TopicNewWords?>(null) }
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
                PeriodControl(period, onChange = { period = it }, Modifier.padding(top = Spacing.xs))

                SectionTitle(stringResource(R.string.your_vocabulary), Modifier.padding(top = Spacing.xl))
                GrowthCard(period, data, journey.periodLoads[period], onRetry = { scope.launch { journey.load(period, force = true) } })

                if (data != null) {
                    // An older backend sends no topics: the card is left out rather than shown empty.
                    data.period.newWordsByTopic?.let { topics ->
                        SectionTitle(stringResource(newWordsCaption(period)), Modifier.padding(top = Spacing.xl)) {
                            data.period.topicNewWordsTotal?.let {
                                Text(
                                    "+${formatNumber(it)}",
                                    style = Typography.secondary.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                                    color = SyllColors.mutedInk,
                                )
                            }
                        }
                        TopicBreakdown(period, topics, onOpen = { openTopic = it })
                    }

                    SectionTitle(stringResource(activityTitle(period)), Modifier.padding(top = Spacing.xl))
                    Activity(data.period)
                    data.historyAvailableFrom?.let { from ->
                        Text(
                            stringResource(R.string.tracking_since_x, longDate(from)),
                            style = Typography.micro,
                            color = SyllColors.mutedInk,
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                    }
                }
            }
        }
    }
    openTopic?.let { topic -> TopicWordsSheet(period, topic, onDismiss = { openTopic = null }) }
}

/** Week / Month / All time. iOS uses its segmented control; Android's is the Material segmented button row. */
@Composable
private fun PeriodControl(period: JourneyPeriod, onChange: (JourneyPeriod) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        val options = listOf(JourneyPeriod.WEEK to R.string.week, JourneyPeriod.MONTH to R.string.month, JourneyPeriod.ALL to R.string.all_time)
        options.forEachIndexed { index, (value, title) ->
            SegmentedButton(
                selected = period == value,
                onClick = { onChange(value) },
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
}

@Composable
private fun GrowthCard(period: JourneyPeriod, data: LearningJourney?, load: LearningJourneyStore.Load?, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().cardSurface().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
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

// New words by topic

/** A topic's colour from the backend; Ink 300 for the learner's own words, a topic without one or a bad value. */
internal fun topicMarkColor(topic: LearningJourney.TopicNewWords): Color =
    topic.markHex?.let { Color(0xFF000000 or it.toLong()) } ?: SyllColors.ink300

/**
 * Where the period's new words went: a stacked bar (one segment per topic, as long as its count, in the topic's
 * colour; every topic, hidden rows included), then one row per topic as its legend. Four rows, then "Show N more
 * topics" opens the rest in place. No word lists and no topic progress here.
 */
@Composable
private fun TopicBreakdown(period: JourneyPeriod, topics: List<LearningJourney.TopicNewWords>, onOpen: (LearningJourney.TopicNewWords) -> Unit) {
    // Keyed by the period, so a period change collapses it again.
    var expanded by rememberSaveable(period) { mutableStateOf(false) }
    if (topics.isEmpty()) {
        Text(stringResource(R.string.no_new_words_in_this_period), style = Typography.secondary, color = SyllColors.mutedInk)
        return
    }
    Column(Modifier.fillMaxWidth().cardSurface()) {
        TopicBar(topics, Modifier.padding(Spacing.m))
        LearningJourney.TopicNewWords.visible(topics, expanded).forEach { topic ->
            CardDivider()
            TopicRow(topic, onClick = { onOpen(topic) })
        }
        val hidden = LearningJourney.TopicNewWords.hiddenCount(topics)
        if (hidden > 0) {
            CardDivider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Spacing.fieldHeight)
                    .clickable(role = Role.Button) { expanded = !expanded },
                horizontalArrangement = Arrangement.spacedBy(Spacing.xxs + 2.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (expanded) stringResource(R.string.show_less) else pluralStringResource(R.plurals.show_x_more_topics, hidden, hidden),
                    style = Typography.secondary.copy(fontWeight = FontWeight.SemiBold),
                    color = SyllColors.link,
                )
                Icon(
                    if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    tint = SyllColors.link,
                    modifier = Modifier.size(Spacing.m),
                )
            }
        }
    }
}

/** 12 dp, fully rounded, 2 dp gaps. Decorative: the rows below say the same thing. */
@Composable
private fun TopicBar(topics: List<LearningJourney.TopicNewWords>, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(Spacing.s)
            .clip(CircleShape)
            .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        topics.filter { it.count > 0 }.forEach { topic ->
            Box(Modifier.weight(topic.count.toFloat()).fillMaxHeight().background(topicMarkColor(topic)))
        }
    }
}

@Composable
private fun TopicIcon(topic: LearningJourney.TopicNewWords, size: Dp, iconSize: Dp) {
    Box(Modifier.size(size).background(SyllColors.ink50, RoundedCornerShape(Radii.chip)), contentAlignment = Alignment.Center) {
        Icon(
            if (topic.isOwnWords) Icons.Outlined.EditNote else SymbolIcons.icon(topic.iosIcon),
            contentDescription = null,
            tint = SyllColors.ink700,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun topicTitle(topic: LearningJourney.TopicNewWords): String =
    if (topic.isOwnWords) stringResource(R.string.your_own_words) else topic.title.orEmpty()

@Composable
private fun TopicRow(topic: LearningJourney.TopicNewWords, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TopicIcon(topic, Spacing.iconButton, 18.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(topicMarkColor(topic), CircleShape))
                Text(
                    topicTitle(topic),
                    style = Typography.secondary.copy(fontWeight = FontWeight.SemiBold),
                    color = SyllColors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (topic.finishedInPeriod) StatusPill(stringResource(R.string.finished), StatusPillTone.SUCCESS, Icons.Filled.Check)
            }
            topic.recentLine?.let {
                Text(it, style = Typography.micro, color = SyllColors.mutedInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(
            "+${formatNumber(topic.count)}",
            style = Typography.secondary.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
            color = SyllColors.ink,
        )
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = SyllColors.ink400, modifier = Modifier.size(20.dp))
    }
}

/**
 * 7.3: one topic's new words in the period as a glossary (a fixed word column, the period's meanings as running text
 * of at most two lines). Pages of 30, the next asked for near the end; a failed page keeps the loaded rows. A row
 * opens the word page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopicWordsSheet(period: JourneyPeriod, topic: LearningJourney.TopicNewWords, onDismiss: () -> Unit) {
    val journey = LocalLearningJourneyStore.current
    val scope = rememberCoroutineScope()
    val key = LearningJourneyStore.TopicWordsKey(period, topic.wordsQueryKey)
    val words = journey.topicWords[key]
    val load = journey.topicWordLoads[key]
    var openWord by remember { mutableStateOf<JourneyWord?>(null) }
    val list = rememberLazyListState()
    // Also re-runs if the pages are dropped (a commit) while the sheet is open.
    LaunchedEffect(key, words == null) { journey.loadTopicWords(key) }
    LaunchedEffect(key) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index to list.layoutInfo.totalItemsCount }
            .collect { (last, count) ->
                if (last != null && last >= count - LOAD_MORE_AHEAD && journey.canLoadMoreTopicWords(key)) {
                    journey.loadTopicWords(key, more = true)
                }
            }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = Radii.sheet, topEnd = Radii.sheet),
        containerColor = SyllColors.canvas,
    ) {
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            contentPadding = PaddingValues(start = Spacing.screenGutter, end = Spacing.screenGutter, bottom = Spacing.l),
        ) {
            item(key = "header") {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = Spacing.s).semantics(mergeDescendants = true) { heading() },
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    verticalAlignment = Alignment.Top,
                ) {
                    TopicIcon(topic, Spacing.iconBadge, 20.dp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            topicTitle(topic),
                            style = Typography.screenTitle.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp),
                            color = SyllColors.ink,
                        )
                        Text(
                            pluralStringResource(topicCountCaption(period), topic.count, topic.count),
                            style = Typography.secondary.copy(fontFeatureSettings = "tnum"),
                            color = SyllColors.mutedInk,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
            val loaded = words.orEmpty()
            itemsIndexed(loaded, key = { _, word -> word.wordId }) { index, word ->
                if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(SyllColors.ink100))
                GlossaryRow(word, onClick = { openWord = word })
            }
            item(key = "footer") {
                when {
                    load is LearningJourneyStore.Load.Failed -> Column(
                        Modifier.fillMaxWidth().padding(top = Spacing.s),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        Text(load.message.text(), style = Typography.secondary, color = SyllColors.errorInk)
                        SecondaryButton(
                            stringResource(R.string.try_again_2),
                            onClick = { scope.launch { journey.loadTopicWords(key, more = loaded.isNotEmpty(), force = loaded.isEmpty()) } },
                            icon = Icons.Filled.Refresh,
                        )
                    }
                    words == null || load == LearningJourneyStore.Load.Loading -> Box(
                        Modifier.fillMaxWidth().heightIn(min = Spacing.minTouchTarget + Spacing.m),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = SyllColors.mutedInk, strokeWidth = 2.dp)
                    }
                    loaded.isEmpty() -> Text(
                        stringResource(R.string.no_new_words_in_this_period),
                        style = Typography.secondary,
                        color = SyllColors.mutedInk,
                        modifier = Modifier.padding(top = Spacing.s),
                    )
                }
            }
        }
    }
    openWord?.let { word ->
        WordDetailSheet(wordId = word.wordId, lemma = word.lemma, ipa = null, onDismiss = { openWord = null })
    }
}

/** A word in a 96 dp column, its meanings joined by " · " (a meaning's own synonyms keep their commas). */
@Composable
private fun GlossaryRow(word: JourneyWord, onClick: () -> Unit) {
    // TalkBack reads every meaning, not the two lines on screen.
    val description = if (word.meaningTexts.isEmpty()) word.lemma else "${word.lemma}, ${word.meaningText}"
    val meanings = buildAnnotatedString {
        word.meaningTexts.forEachIndexed { index, meaning ->
            if (index > 0) withStyle(SpanStyle(color = SyllColors.ink300, fontWeight = FontWeight.Bold)) { append(" · ") }
            append(meaning)
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = description }
            .padding(vertical = Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Text(word.lemma, style = Typography.tile, color = SyllColors.ink, modifier = Modifier.width(GLOSSARY_WORD_COLUMN).alignByBaseline())
        Text(
            meanings,
            style = Typography.secondary,
            color = SyllColors.ink600,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).alignByBaseline(),
        )
    }
}

private val GLOSSARY_WORD_COLUMN = 96.dp
/** Ask for the next page this many rows before the end. */
private const val LOAD_MORE_AHEAD = 5

// Activity

private enum class ActivityTone(val fill: Color, val border: Color, val number: Color, val label: Color) {
    BLUE(SyllColors.blue50, SyllColors.blue200, SyllColors.blue900, SyllColors.blue700),
    GREEN(SyllColors.green50, SyllColors.green200, SyllColors.green900, SyllColors.green700),
    // Sun 700 is too light for text on Sun 50.
    SUN(SyllColors.sun50, SyllColors.sun200, SyllColors.sun900, SyllColors.sun800),
}

@Composable
private fun Activity(data: LearningJourney.Period) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        // No new-word tile: the chart and the topics above already carry that number.
        Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp)) {
            ActivityTile(stringResource(R.string.days_learned), data.activeDays, ActivityTone.BLUE, Modifier.weight(1f).fillMaxHeight()) { DaysArt() }
            ActivityTile(stringResource(R.string.sessions_completed), data.completedSessions, ActivityTone.GREEN, Modifier.weight(1f).fillMaxHeight()) { SessionsArt() }
            ActivityTile(stringResource(R.string.meaning_reviews), data.reviewedMeanings, ActivityTone.SUN, Modifier.weight(1f).fillMaxHeight()) { ReviewsArt() }
        }
        Text(stringResource(R.string.each_finished_learning_or_review_part_counts_as), style = Typography.micro, color = SyllColors.mutedInk)
    }
}

/** A tile tinted in its art's colours (an exception to the Ink 200 card border): 50 fill, 200 border, 900 number. */
@Composable
private fun ActivityTile(title: String, value: Int?, tone: ActivityTone, modifier: Modifier, art: @Composable () -> Unit) {
    val shape = RoundedCornerShape(Radii.card)
    Column(
        modifier
            .background(tone.fill, shape)
            .border(Borders.card, tone.border, shape)
            .padding(Spacing.s + 2.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Box(Modifier.height(46.dp).clearAndSetSemantics {}, contentAlignment = Alignment.BottomStart) { art() }
        Text(
            if (value != null) formatNumber(value) else "–",
            style = Typography.sheetTitle.copy(fontFeatureSettings = "tnum"),
            color = tone.number,
            maxLines = 1,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        Text(
            title,
            style = Typography.micro.copy(fontWeight = FontWeight.SemiBold),
            color = tone.label,
            modifier = Modifier.padding(top = Spacing.xxs),
        )
    }
}

/** A day learned is a stamp earned: Home's stamp, small and tilted, its perforation in the tile's Blue 50. */
@Composable
private fun DaysArt() {
    Box(Modifier.size(58.dp, 44.dp)) {
        Box(Modifier.align(Alignment.BottomStart).alpha(0.5f)) { Stamp("", isBonus = false, index = 1, width = 30.dp, hole = SyllColors.blue50) }
        Box(Modifier.align(Alignment.BottomStart).offset(x = 20.dp, y = (-2).dp)) {
            Stamp("", isBonus = false, index = 0, width = 34.dp, hole = SyllColors.blue50)
        }
    }
}

/** Finished sessions stack up like done cards; the top one carries the check. */
@Composable
private fun SessionsArt() {
    val shape = RoundedCornerShape(7.dp)
    @Composable
    fun DoneCard(x: Dp, y: Dp, fill: Color, edge: Color, content: @Composable () -> Unit = {}) {
        Box(
            Modifier.offset(x, y).size(38.dp, 28.dp).background(fill, shape).border(2.dp, edge, shape),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
    Box(Modifier.size(52.dp, 44.dp)) {
        DoneCard(10.dp, 0.dp, SyllColors.green100, SyllColors.green200)
        DoneCard(5.dp, 6.dp, SyllColors.green200, SyllColors.green300)
        DoneCard(0.dp, 13.dp, SyllColors.green500, SyllColors.green600) {
            Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(Spacing.m))
        }
    }
}

/** One meaning goes round again and again: a card on a loop of two arrows (drawn in a 50 × 44 box). */
@Composable
private fun ReviewsArt() {
    val arrow = SyllColors.sun400
    val card = SyllColors.sun100
    val ink = SyllColors.sun700
    Canvas(Modifier.size(50.dp, 44.dp)) {
        val u = size.width / 50f
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        val round = Stroke(width = 3 * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        // Two arcs of a circle (radius 17) round the card, each ending in an arrow head.
        val ring = androidx.compose.ui.geometry.Rect(p(25f - 17f, 22.5f - 17f), p(25f + 17f, 22.5f + 17f))
        drawArc(arrow, startAngle = 175f, sweepAngle = 155f, useCenter = false, topLeft = ring.topLeft, size = ring.size, style = round)
        drawArc(arrow, startAngle = -2f, sweepAngle = 150f, useCenter = false, topLeft = ring.topLeft, size = ring.size, style = round)
        drawPath(Path().apply { moveTo(33 * u, 13 * u); lineTo(40 * u, 15 * u); lineTo(41 * u, 8 * u) }, arrow, style = round)
        drawPath(Path().apply { moveTo(17 * u, 33 * u); lineTo(10 * u, 31 * u); lineTo(9 * u, 38 * u) }, arrow, style = round)
        drawRoundRect(card, topLeft = p(17f, 14f), size = Size(16 * u, 17 * u), cornerRadius = CornerRadius(3 * u))
        drawRoundRect(ink, topLeft = p(17f, 14f), size = Size(16 * u, 17 * u), cornerRadius = CornerRadius(3 * u), style = Stroke(2 * u))
        drawLine(ink, p(21f, 20f), p(29f, 20f), strokeWidth = 2 * u, cap = StrokeCap.Round)
        drawLine(ink, p(21f, 25f), p(26f, 25f), strokeWidth = 2 * u, cap = StrokeCap.Round)
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

private fun topicCountCaption(period: JourneyPeriod) = when (period) {
    JourneyPeriod.WEEK -> R.plurals.x_new_words_this_week
    JourneyPeriod.MONTH -> R.plurals.x_new_words_this_month
    JourneyPeriod.ALL -> R.plurals.x_new_words_since_you_started
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
private fun weekRange(week: LearningJourney.Week): String {
    val start = runCatching { LocalDate.parse(week.startDate) }.getOrNull() ?: return week.startDate
    val end = runCatching { LocalDate.parse(week.endDateExclusive) }.getOrNull()?.minusDays(1) ?: return week.startDate
    val day = DateTimeFormatter.ofPattern("d", appLocale())
    val dayMonth = DateTimeFormatter.ofPattern("d/M", appLocale())
    return if (start.month == end.month) "${day.format(start)}–${dayMonth.format(end)}" else "${dayMonth.format(start)}–${dayMonth.format(end)}"
}
