package com.syll.features.home

import android.icu.text.ListFormatter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.os.ConfigurationCompat
import com.syll.R
import com.syll.app.LocalLearnerStore
import com.syll.app.LocalLearningPlanStore
import com.syll.designsystem.components.CardDivider
import com.syll.designsystem.components.CollectionMarkDot
import com.syll.designsystem.components.Eyebrow
import com.syll.designsystem.components.HomeSyllieScene
import com.syll.designsystem.components.HomeSyllieView
import com.syll.designsystem.components.IconBadge
import com.syll.designsystem.components.IconBadgeTone
import com.syll.designsystem.components.PrimaryButton
import com.syll.designsystem.components.ActionButtonKind
import com.syll.designsystem.components.ProgressTrack
import com.syll.designsystem.components.SecondaryButton
import com.syll.designsystem.components.SymbolIcons
import com.syll.designsystem.components.TextLink
import com.syll.designsystem.components.cardSurface
import com.syll.designsystem.motion.LocalMotion
import com.syll.designsystem.tokens.Borders
import com.syll.designsystem.tokens.Radii
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography
import com.syll.domain.Topic
import com.syll.domain.VocabCollection
import com.syll.features.plan.CatalogState
import com.syll.features.plan.CollectionMarkColor
import com.syll.features.plan.displayName
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Where Home's actions lead. Practice, Random practice and Focus arrive with later steps; the shell wires them. */
class HomeActions(
    val onStartDaily: () -> Unit = {},
    val onStartReviewOnly: () -> Unit = {},
    val onOpenRandomPractice: () -> Unit = {},
    /** Opens the Focus editor (topics and collection). */
    val onChooseTopics: () -> Unit = {},
    /** True while a session is on screen: the review-load choice never shows then. */
    val isPracticeActive: () -> Boolean = { false },
)

/** iOS `HomeView`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeView(actions: HomeActions, modifier: Modifier = Modifier) {
    val learner = LocalLearnerStore.current
    val plan = LocalLearningPlanStore.current
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    var isChoosingReviewLoad by remember { mutableStateOf(false) }
    val home = learner.home
    val due = home?.dueCount ?: 0
    val status = DailyLessonStatus.of(home, plan.current.wordsPerDay)

    // Never mid-session (Home isn't on screen then), at most once per launch.
    LaunchedEffect(home?.reviewLoad?.promptChoice) {
        if (learner.shouldOfferReviewChoice && !actions.isPracticeActive()) isChoosingReviewLoad = true
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                learner.refresh()
                isRefreshing = false
            }
        },
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = Spacing.screenGutter)
                .padding(bottom = Spacing.l),
        ) {
            Header()
            DailyLessonCard(status, due, actions, Modifier.padding(top = LessonMascot.BOTTOM_MARGIN))
            CatalogState.collection(plan.current.collectionId)?.let { collection ->
                LearningSection(collection, actions.onChooseTopics, Modifier.padding(top = Spacing.xl + Spacing.xs))
            }
        }
    }

    val load = home?.reviewLoad
    if (isChoosingReviewLoad && load != null) {
        ReviewLoadChoiceSheet(
            load = load,
            goal = learner.profile?.settings?.dailyNewWordGoal ?: plan.current.wordsPerDay,
            onDismiss = {
                isChoosingReviewLoad = false
                scope.launch { learner.acknowledgeReviewChoice() }
            },
            onExtraReview = actions.onStartReviewOnly,
            onLower = { value, mode -> learner.setNewWordOverride(value, mode) },
        )
    }
}

/**
 * One row, no wordmark and no greeting: Syllie on the left. The lifetime LP pill on the right arrives with Ranking
 * (Phase 2); until then the profile has no tier summary, as with an older backend on iOS.
 */
@Composable
private fun Header() {
    // The artboard keeps room above Syllie for his props; that room may run under the status bar.
    Row(Modifier.overflowTop(LessonMascot.TOP_MARGIN - Spacing.xs)) {
        LessonMascot()
    }
}

/** Lays the content out as if it were [amount] shorter at the top, drawing that part above its slot. */
private fun Modifier.overflowTop(amount: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val overflow = amount.roundToPx().coerceAtMost(placeable.height)
    layout(placeable.width, placeable.height - overflow) {
        placeable.place(0, -overflow)
    }
}

/**
 * iOS `LessonMascot`: Syllie at the top of Home on a loop: a short idle, one random play scene, a nap for
 * [SLEEP_BEAT], waking up, and round again. A tap wakes him early. With reduced motion he skips the play, dozes off
 * and stays asleep until tapped.
 */
private object LessonMascot {
    const val IDLE_BEAT = 2_000L
    const val SLEEP_BEAT = 6_000L
    /** The 520×560 artboard at a size where his body is about 104 dp wide. */
    val width = 128.dp
    val height = 138.dp
    /** Empty room above his body (for the letter and the plane) and below it. */
    val TOP_MARGIN = 52.dp
    val BOTTOM_MARGIN = 13.dp
}

@Composable
private fun LessonMascot() {
    val motion = LocalMotion.current
    var scene by remember { mutableStateOf(HomeSyllieScene.IDLE) }
    LaunchedEffect(scene) {
        when (scene) {
            HomeSyllieScene.IDLE -> {
                delay(LessonMascot.IDLE_BEAT)
                scene = if (motion.reduceMotion) HomeSyllieScene.SLEEPY else HomeSyllieScene.plays.random()
            }
            HomeSyllieScene.PLAY_LETTERS, HomeSyllieScene.PLANE_CHASE -> {
                delay(scene.durationMillis ?: 0)
                scene = HomeSyllieScene.SLEEPY
            }
            HomeSyllieScene.SLEEPY -> {
                if (motion.reduceMotion) return@LaunchedEffect
                delay(LessonMascot.SLEEP_BEAT)
                scene = HomeSyllieScene.WAKE_UP
            }
            HomeSyllieScene.WAKE_UP -> {
                delay(scene.durationMillis ?: 0)
                scene = HomeSyllieScene.IDLE
            }
        }
    }
    HomeSyllieView(scene, Modifier.size(LessonMascot.width, LessonMascot.height), onWake = { scene = HomeSyllieScene.IDLE })
}

// Daily lesson

/** What the next session holds, naming only the parts it has ("8 new", not "0 to review · 8 new"). */
@Composable
private fun sessionCounts(status: DailyLessonStatus): String? = when {
    status.sessionReviews > 0 && status.newWords > 0 -> stringResource(R.string.x_to_review_x_new, status.sessionReviews, status.newWords)
    status.sessionReviews > 0 -> stringResource(R.string.x_to_review, status.sessionReviews)
    status.newWords > 0 -> stringResource(R.string.x_new, status.newWords)
    else -> null
}

/** The footer offers reviews beyond this session's allocation. Out of new words, the card's own button already reviews. */
private fun showsExtraReview(status: DailyLessonStatus): Boolean =
    status.scope != DailyLessonStatus.Scope.ExhaustedNoCustom && status.freeWordsUsedUp == null &&
        status.backlog > status.sessionReviews

/** The screen's one main action: this session's counts and the Blue practice button. */
@Composable
private fun DailyLessonCard(status: DailyLessonStatus, due: Int, actions: HomeActions, modifier: Modifier = Modifier) {
    val plan = LocalLearningPlanStore.current
    val heroStyle = Typography.screenTitle.copy(fontWeight = FontWeight.ExtraBold)
    Column(modifier.cardSurface()) {
        Column(Modifier.fillMaxWidth().padding(Spacing.screenGutter)) {
            Row(Modifier.defaultMinSize(minHeight = Spacing.l), verticalAlignment = Alignment.CenterVertically) {
                Eyebrow(stringResource(R.string.today_s_lesson), Modifier.semantics { heading() })
                if (status.scope != DailyLessonStatus.Scope.ExhaustedNoCustom && status.freeWordsUsedUp == null) {
                    LessonInfoButton(status, actions.onChooseTopics)
                }
            }

            val freeMax = status.freeWordsUsedUp
            when {
                status.scope == DailyLessonStatus.Scope.ExhaustedNoCustom -> Row(
                    Modifier.padding(top = Spacing.s),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    IconBadge(Icons.Filled.EmojiEvents, IconBadgeTone.REWARD)
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(
                            if (plan.current.hasAllTopics) {
                                stringResource(R.string.you_ve_learned_all_of_x, plan.current.collection?.displayName().orEmpty())
                            } else {
                                stringResource(R.string.you_ve_learned_all_your_selected_topics)
                            },
                            style = heroStyle,
                            color = SyllColors.ink,
                        )
                        Text(
                            stringResource(R.string.your_learned_words_will_still_be_reviewed_choose),
                            style = Typography.secondary,
                            color = SyllColors.bodyInk,
                        )
                    }
                }
                freeMax != null -> Row(Modifier.padding(top = Spacing.s), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    IconBadge(Icons.Outlined.WorkspacePremium, IconBadgeTone.PREMIUM)
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(stringResource(R.string.you_ve_learned_your_x_free_words, freeMax), style = heroStyle, color = SyllColors.ink)
                        Text(
                            stringResource(R.string.your_learned_words_will_still_be_reviewed_upgrad, "Premium"),
                            style = Typography.secondary,
                            color = SyllColors.bodyInk,
                        )
                    }
                }
                else -> sessionCounts(status)?.let {
                    Text(it, style = heroStyle, color = SyllColors.ink, modifier = Modifier.padding(top = Spacing.xxs))
                }
            }

            Column(Modifier.padding(top = Spacing.screenGutter), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                when {
                    status.scope == DailyLessonStatus.Scope.ExhaustedNoCustom || freeMax != null -> {
                        if (due > 0) {
                            PrimaryButton(
                                stringResource(R.string.review_x_words, status.sessionReviews),
                                onClick = actions.onStartReviewOnly,
                                kind = ActionButtonKind.PRACTICE,
                                shimmers = true,
                            )
                        } else {
                            PrimaryButton(
                                stringResource(R.string.practise),
                                onClick = actions.onOpenRandomPractice,
                                kind = ActionButtonKind.PRACTICE,
                                shimmers = true,
                            )
                        }
                        // Out of free words, Android offers no Premium until Play Billing (Phase 4).
                        if (freeMax == null) {
                            // Every topic is already chosen: only another collection brings new words.
                            SecondaryButton(
                                stringResource(if (plan.current.hasAllTopics) R.string.choose_another_collection else R.string.choose_more_topics),
                                onClick = actions.onChooseTopics,
                            )
                        }
                    }
                    else -> PrimaryButton(
                        stringResource(if (due > 0) R.string.start else R.string.start_flow),
                        onClick = actions.onStartDaily,
                        kind = ActionButtonKind.PRACTICE,
                        shimmers = true,
                    )
                }
            }
        }

        if (showsExtraReview(status)) {
            CardDivider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = actions.onStartReviewOnly)
                    .padding(horizontal = Spacing.screenGutter, vertical = Spacing.m),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(Icons.Outlined.Refresh, IconBadgeTone.SUCCESS)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Text(
                        stringResource(R.string.x_more_words_waiting, maxOf(0, status.backlog - status.sessionReviews)),
                        style = Typography.secondary.copy(fontWeight = FontWeight.Bold),
                        color = SyllColors.ink,
                    )
                    Text(
                        stringResource(R.string.review_x_words_separately_without_new_words, status.sessionReviews),
                        style = Typography.micro,
                        color = SyllColors.mutedInk,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = SyllColors.ink400)
            }
        }
    }
}

// Lesson info

@Composable
private fun appLocale(): Locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()

@Composable
private fun topicNames(): String {
    val plan = LocalLearningPlanStore.current
    val names = CatalogState.collections.flatMap { c -> c.topics.map { c to it } }
        .filter { (_, topic) -> topic.id in plan.current.topicIds }
        .map { (c, topic) -> topic.displayName(c) }
    return ListFormatter.getInstance(appLocale()).format(names)
}

/** The card shows only this session's counts; why it looks this way (scope, lowered goal, backlog) sits behind ⓘ. */
@Composable
private fun LessonInfoButton(status: DailyLessonStatus, onChooseTopics: () -> Unit) {
    val plan = LocalLearningPlanStore.current
    val lines = noteLines(status)
    if (lines.isEmpty()) return
    var isShowing by remember { mutableStateOf(false) }
    val label = stringResource(R.string.about_today_s_session)
    Box {
        Box(
            Modifier
                .size(Spacing.minTouchTarget, Spacing.l)
                .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { isShowing = true }
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = SyllColors.mutedInk, modifier = Modifier.size(18.dp))
        }
        if (isShowing) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, 90),
                onDismissRequest = { isShowing = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    Modifier
                        .widthIn(max = 280.dp)
                        .background(SyllColors.surface, RoundedCornerShape(Radii.tile))
                        .border(Borders.hairline, SyllColors.stroke, RoundedCornerShape(Radii.tile))
                        .padding(Spacing.m),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    lines.forEach { Text(it, style = Typography.secondary, color = SyllColors.ink) }
                    if (status.scope is DailyLessonStatus.Scope.Low && !plan.current.hasAllTopics) {
                        Text(
                            stringResource(R.string.add_topics),
                            style = Typography.micro.copy(fontWeight = FontWeight.ExtraBold),
                            color = SyllColors.selectedInk,
                            modifier = Modifier
                                .defaultMinSize(minHeight = Spacing.minTouchTarget)
                                .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                                    isShowing = false
                                    onChooseTopics()
                                }
                                .padding(vertical = Spacing.s),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun noteLines(status: DailyLessonStatus): List<String> {
    val plan = LocalLearningPlanStore.current
    val names = topicNames()
    val lines = mutableListOf<String>()
    when (val scope = status.scope) {
        DailyLessonStatus.Scope.ExhaustedNoCustom -> lines += stringResource(
            if (plan.current.hasAllTopics) R.string.you_ve_learned_every_new_word_in_x_you_can_still else R.string.you_ve_learned_every_new_word_in_x_you_can_still_2,
            names,
        )
        is DailyLessonStatus.Scope.ExhaustedWithCustom -> lines += stringResource(R.string.no_new_words_left_in_your_topics_x_words_you_add, scope.custom)
        is DailyLessonStatus.Scope.Low -> lines += stringResource(R.string.x_new_words_left_about_x_sessions, scope.remaining, scope.sessions)
        DailyLessonStatus.Scope.Ok -> Unit
    }
    when (val reason = status.reason) {
        is DailyLessonStatus.Reason.UserOverride -> lines += if (reason.until != null) {
            val day = DateTimeFormatter.ofPattern("EEEE", appLocale()).format(reason.until.atZone(ZoneId.systemDefault()))
            stringResource(R.string.x_new_words_today_lowered_until_x, status.newWords, day)
        } else {
            stringResource(R.string.x_new_words_today_lowered_until_fewer_words_are, status.newWords)
        }
        DailyLessonStatus.Reason.AutoBalanced -> lines += stringResource(R.string.x_new_words_today_balanced_with_your_reviews, status.newWords)
        DailyLessonStatus.Reason.ScopeLimited -> if (status.scope == DailyLessonStatus.Scope.Ok) {
            lines += stringResource(R.string.only_x_new_words_left_today_in_x, status.newWords, names)
        }
        is DailyLessonStatus.Reason.FreeLimit -> lines += stringResource(R.string.x_of_your_x_free_new_words_left, reason.remaining, reason.max)
        null -> Unit
    }
    if (status.backlog > status.sessionReviews) lines += stringResource(R.string.x_words_waiting_for_review, status.backlog)
    return lines
}

// Learning now

private const val COLLAPSED_TOPIC_LIMIT = 5

/**
 * The collection being learned and its chosen topics (the first five until expanded); bars use the collection's
 * mark colour, a finished collection's or topic's bar is green.
 */
@Composable
private fun LearningSection(collection: VocabCollection, onChooseTopics: () -> Unit, modifier: Modifier = Modifier) {
    val plan = LocalLearningPlanStore.current
    var showsAllTopics by rememberSaveable { mutableStateOf(false) }
    val mark = CollectionMarkColor.color(collection)
    val topics = collection.topics.filter { it.id in plan.current.topicIds }

    Column(modifier) {
        Row(Modifier.padding(bottom = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.learning),
                style = Typography.sectionTitle.copy(letterSpacing = (Typography.tightTracking / 2).sp),
                color = SyllColors.ink,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            TextLink(stringResource(R.string.change), onClick = onChooseTopics)
        }

        Column(Modifier.cardSurface()) {
            Column(Modifier.padding(Spacing.m).semantics(mergeDescendants = true) {}) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    CollectionMarkDot(mark)
                    Text(
                        collection.displayName(),
                        style = Typography.wordClass.copy(fontWeight = FontWeight.Bold),
                        color = SyllColors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val total = collection.total
                if (total != null && total > 0) {
                    val learned = collection.learned ?: 0
                    ProgressTrack(
                        learned.toFloat() / total,
                        Modifier.padding(top = Spacing.s),
                        height = 10.dp,
                        color = if (learned >= total) SyllColors.success else mark,
                    )
                    Row(
                        Modifier.padding(top = Spacing.xs),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Under the bar: "128 / 600 words", or once finished the pill and the total.
                        if (learned >= total) {
                            CompletePill()
                            Text(stringResource(R.string.x_words, total), style = Typography.secondary, color = SyllColors.mutedInk)
                        } else {
                            Text(stringResource(R.string.x_x_words, learned, total), style = Typography.secondary, color = SyllColors.mutedInk)
                        }
                    }
                }
            }

            val visible = if (showsAllTopics) topics else topics.take(COLLAPSED_TOPIC_LIMIT)
            visible.forEach { topic ->
                CardDivider()
                TopicRow(topic, collection, mark)
            }
            if (topics.size > COLLAPSED_TOPIC_LIMIT) {
                CardDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = Spacing.minTouchTarget)
                        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                            showsAllTopics = !showsAllTopics
                        },
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xxs, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (showsAllTopics) stringResource(R.string.show_less) else stringResource(R.string.show_all_x, topics.size),
                        style = Typography.secondary.copy(fontWeight = FontWeight.SemiBold),
                        color = SyllColors.link,
                    )
                    Icon(
                        if (showsAllTopics) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                        contentDescription = null,
                        tint = SyllColors.link,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** iOS `StatusPill("Complete", tone: .success, systemImage: "checkmark")`. */
@Composable
private fun CompletePill() {
    Row(
        Modifier
            .background(SyllColors.green50, CircleShape)
            .height(24.dp)
            .padding(horizontal = Spacing.s - 2.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Check, contentDescription = null, tint = SyllColors.green800, modifier = Modifier.size(12.dp))
        Text(stringResource(R.string.complete), style = Typography.caption.copy(fontWeight = FontWeight.Bold), color = SyllColors.green800)
    }
}

@Composable
private fun TopicRow(topic: Topic, collection: VocabCollection, mark: Color) {
    val total = topic.total
    val learned = topic.learned ?: 0
    val progressLabel = total?.let { stringResource(R.string.x_of_x_words_learned, learned, it) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.m, vertical = Spacing.s + 2.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(SymbolIcons.icon(topic.icon), IconBadgeTone.NEUTRAL, size = Spacing.iconButton)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs - 2.dp)) {
            Text(
                topic.displayName(collection),
                style = Typography.secondary.copy(fontWeight = FontWeight.SemiBold),
                color = SyllColors.ink,
            )
            if (total != null && total > 0) {
                ProgressTrack(learned.toFloat() / total, height = 6.dp, color = if (topic.isFinished) SyllColors.success else mark)
            }
        }
        if (total != null) {
            Text(
                "$learned/$total",
                style = Typography.micro,
                color = SyllColors.mutedInk,
                modifier = Modifier
                    .widthIn(min = Spacing.xl + Spacing.m)
                    .semantics { contentDescription = progressLabel.orEmpty() },
            )
        }
    }
}
