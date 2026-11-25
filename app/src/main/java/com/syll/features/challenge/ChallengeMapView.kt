package com.syll.features.challenge

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syll.R
import com.syll.app.LocalAppScope
import com.syll.app.LocalChallengeMapStore
import com.syll.app.LocalChallengeRunStore
import com.syll.app.LocalPracticeSessionStore
import com.syll.app.text
import com.syll.designsystem.components.Eyebrow
import com.syll.designsystem.components.PageScaffold
import com.syll.designsystem.components.ProgressTrack
import com.syll.designsystem.components.SecondaryButton
import com.syll.designsystem.components.cardSurface
import com.syll.designsystem.motion.LocalMotion
import com.syll.designsystem.motion.MotionTokens
import com.syll.designsystem.tokens.Radii
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography
import com.syll.domain.challenge.ChallengeChapter
import com.syll.domain.challenge.ChallengeChapterPreview
import com.syll.domain.challenge.ChallengeLevel
import com.syll.domain.challenge.ChallengeMap
import com.syll.domain.challenge.ChallengeNodeRef
import com.syll.domain.challenge.ChallengeRunPlan
import com.syll.domain.practice.SessionMode
import kotlinx.coroutines.launch

/**
 * iOS `ChallengeMapView`: chapters of three Match Madness levels, oldest at the top, the next chapter's preview at the
 * bottom. Every state comes from the server; stars are achievements, not skill checks. A page pushed from Home.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChallengeMapView(onBack: () -> Unit) {
    val store = LocalChallengeMapStore.current
    val run = LocalChallengeRunStore.current
    val practice = LocalPracticeSessionStore.current
    val appScope = LocalAppScope.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<ChallengeNodeRef?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    var didScrollToCurrent by rememberSaveable { mutableStateOf(false) }
    var viewportHeight by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) { store.refresh() }

    // Runs on the app scope: issuing swaps this page out for the game.
    fun play(node: ChallengeNodeRef, level: ChallengeLevel?) {
        selected = null
        appScope.launch { run.play(node, level) }
    }

    /** Once per visit: a refresh after a run keeps the learner's scroll position. */
    fun scrollToCurrent(centerY: Float) {
        if (didScrollToCurrent) return
        didScrollToCurrent = true
        scope.launch { scroll.scrollTo((centerY - viewportHeight / 2).toInt().coerceAtLeast(0)) }
    }

    val map = store.map
    PageScaffold(
        title = stringResource(R.string.challenge),
        onBack = onBack,
        actions = {
            if (map != null && map.chapters.isNotEmpty()) TotalStars(map.totalStars)
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    store.refresh()
                    isRefreshing = false
                }
            },
            modifier = Modifier.fillMaxSize().padding(padding).onSizeChanged { viewportHeight = it.height.toFloat() },
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = Spacing.screenGutter)
                    .padding(top = Spacing.m, bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.xl),
            ) {
                store.error?.let { error -> ErrorBanner(error.text()) { scope.launch { store.refresh() } } }

                if (map != null) {
                    val target = map.currentNode
                    map.chapters.forEachIndexed { index, chapter ->
                        ChallengeChapterSection(
                            chapter = chapter,
                            waveStart = ChallengeMapWave.start(index, map),
                            currentNode = map.currentNode,
                            onSelect = { selected = it },
                            onCurrentPositioned = { if (target != null) scrollToCurrent(it) },
                        )
                    }
                    map.nextChapter?.let { next ->
                        NextChapterSection(
                            preview = next,
                            waveStart = ChallengeMapWave.start(map.chapters.size, map),
                            onPlay = { play(ChallengeNodeRef(next.chapterNo, 1), null) },
                            onLearn = { appScope.launch { practice.start(SessionMode.DAILY) } },
                            modifier = Modifier.onGloballyPositioned { if (target == null) scrollToCurrent(it.boundsInParent().center.y) },
                        )
                    }
                } else if (store.isLoading || store.error == null) {
                    Box(Modifier.fillMaxWidth().padding(top = Spacing.xl), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = SyllColors.mutedInk)
                    }
                }
            }
        }
    }

    selected?.let { node ->
        ChallengeLevelSheet(
            node = node,
            chapter = store.chapter(node.chapterNo),
            level = store.level(node),
            onPlay = { play(node, store.level(node)) },
            onPracticeUntimed = {
                selected = null
                run.startSandbox(ChallengeRunPlan.sandbox(), timed = false)
            },
            onDismiss = { selected = null },
        )
    }
}

@Composable
private fun TotalStars(total: Int) {
    val label = stringResource(R.string.x_stars_in_total, total)
    Row(
        Modifier.padding(end = Spacing.m).clearAndSetSemantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Star, null, tint = SyllColors.reward, modifier = Modifier.size(20.dp))
        Text("$total", style = Typography.emphasis.copy(fontFeatureSettings = "tnum"), color = SyllColors.ink)
    }
}

@Composable
private fun ErrorBanner(text: String, onRetry: () -> Unit) {
    Row(
        Modifier.cardSurface(radius = Radii.tile, fill = SyllColors.errorSurface).padding(horizontal = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Text(text, style = Typography.secondary, color = SyllColors.errorInk, modifier = Modifier.weight(1f).padding(vertical = Spacing.s))
        Box(
            Modifier.defaultMinSize(minHeight = Spacing.minTouchTarget).clickable(role = Role.Button, onClick = onRetry),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.retry), style = Typography.secondary.copy(fontWeight = FontWeight.Bold), color = SyllColors.interactive)
        }
    }
}

// region Path

/**
 * iOS `ChallengeMapWave`: where each stop sits on the winding path. The wave runs on across chapters (levels plus the
 * chapter's trophy), so the whole map reads as one road.
 */
object ChallengeMapWave {
    private val offsets = listOf(0f, 0.55f, 1f, 0.55f, 0f, -0.55f, -1f, -0.55f)
    private val amplitude = 78.dp
    /** Levels shown for a chapter the server hasn't materialized yet. */
    const val PREVIEW_LEVEL_COUNT = 3

    fun offset(index: Int): Dp = amplitude * offsets[Math.floorMod(index, offsets.size)]

    fun start(chapterIndex: Int, map: ChallengeMap): Int = map.chapters.take(chapterIndex).sumOf { it.levels.size + 1 }
}

// endregion

// region Chapter

private enum class ChapterBannerStyle { ACTIVE, COMPLETED, MUTED }

/** iOS `ChallengeChapterSection`: a banner, then its levels and a trophy along the path. Cleared levels stay replayable. */
@Composable
private fun ChallengeChapterSection(
    chapter: ChallengeChapter,
    waveStart: Int,
    currentNode: ChallengeNodeRef?,
    onSelect: (ChallengeNodeRef) -> Unit,
    onCurrentPositioned: (Float) -> Unit,
) {
    val (status, style) = when (chapter.state) {
        ChallengeChapter.State.COMPLETED -> R.string.cleared to ChapterBannerStyle.COMPLETED
        ChallengeChapter.State.CONTENT_UNAVAILABLE -> R.string.learn_more_words_to_keep_playing to ChapterBannerStyle.MUTED
        else -> R.string.in_progress to ChapterBannerStyle.ACTIVE
    }
    var sectionTop by remember { mutableFloatStateOf(0f) }
    Column(
        Modifier.onGloballyPositioned { sectionTop = it.boundsInParent().top },
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        ChapterBanner(chapter.chapterNo, stringResource(status), style, stars = chapter.starsEarned to chapter.maxStars)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.l), horizontalAlignment = Alignment.CenterHorizontally) {
            chapter.levels.forEachIndexed { index, level ->
                val node = ChallengeNodeRef(chapter.chapterNo, level.levelNo)
                val isCurrent = node == currentNode
                ChallengeLevelNode(
                    level = level,
                    node = node,
                    isCurrent = isCurrent,
                    onClick = { onSelect(node) },
                    modifier = Modifier
                        .offset(x = ChallengeMapWave.offset(waveStart + index))
                        .then(if (isCurrent) Modifier.onGloballyPositioned { onCurrentPositioned(sectionTop + it.boundsInParent().center.y) } else Modifier),
                )
            }
            ChapterTrophy(
                isEarned = chapter.state == ChallengeChapter.State.COMPLETED,
                modifier = Modifier.offset(x = ChallengeMapWave.offset(waveStart + chapter.levels.size)),
            )
        }
    }
}

/** iOS `ChapterBanner`: the full-width header that opens a chapter on the path, with an optional detail inside. */
@Composable
private fun ChapterBanner(
    chapterNo: Int,
    status: String,
    style: ChapterBannerStyle,
    stars: Pair<Int, Int>? = null,
    detail: @Composable () -> Unit = {},
) {
    val fill = when (style) {
        ChapterBannerStyle.ACTIVE -> SyllColors.interactive
        ChapterBannerStyle.COMPLETED -> SyllColors.reward
        ChapterBannerStyle.MUTED -> SyllColors.disabledSurface
    }
    val depth = when (style) {
        ChapterBannerStyle.ACTIVE -> SyllColors.interactiveDepth
        ChapterBannerStyle.COMPLETED -> SyllColors.rewardDepth
        ChapterBannerStyle.MUTED -> SyllColors.tileDepth
    }
    val ink = if (style == ChapterBannerStyle.ACTIVE) Color.White else SyllColors.ink
    val shape = RoundedCornerShape(Radii.card)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.buttonDepth)
            .drawBehind {
                drawRoundRect(depth, topLeft = Offset(0f, Spacing.buttonDepth.toPx()), size = size, cornerRadius = CornerRadius(Radii.card.toPx()))
            }
            .background(fill, shape)
            .padding(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Row(Modifier.semantics(mergeDescendants = true) { heading() }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Eyebrow(stringResource(R.string.chapter_x, chapterNo).uppercase(), color = ink.copy(alpha = 0.75f))
                Text(status, style = Typography.tileLarge.copy(fontWeight = FontWeight.Bold), color = ink)
            }
            if (stars != null) {
                val label = stringResource(R.string.x_of_x_stars, stars.first, stars.second)
                Row(
                    Modifier
                        .background(SyllColors.surface, CircleShape)
                        .padding(horizontal = Spacing.s, vertical = Spacing.xs)
                        .clearAndSetSemantics { contentDescription = label },
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Star, null, tint = SyllColors.reward, modifier = Modifier.size(16.dp))
                    Text(
                        "${stars.first} / ${stars.second}",
                        style = Typography.secondary.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                        color = SyllColors.ink,
                    )
                }
            } else if (style == ChapterBannerStyle.MUTED) {
                Icon(Icons.Filled.Lock, null, tint = SyllColors.disabledInk, modifier = Modifier.size(22.dp))
            }
        }
        detail()
    }
}

private val NODE_SIZE = 72.dp
private val NODE_DEPTH = 7.dp

/**
 * iOS `ChallengeLevelNode`: a raised round button with a glyph for its state (lock, play, check) and its best stars
 * underneath. The current level wears a ring and a bobbing "Start" callout. [onClick] null can't be opened yet.
 */
@Composable
private fun ChallengeLevelNode(
    level: ChallengeLevel,
    node: ChallengeNodeRef,
    isCurrent: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val store = LocalChallengeMapStore.current
    val motion = LocalMotion.current
    val unlockScale = remember { Animatable(1f) }
    LaunchedEffect(node) {
        // Plays once, after the server confirmed the unlock.
        if (node !in store.newlyUnlocked) return@LaunchedEffect
        store.consumeUnlock(node)
        if (motion.reduceMotion) return@LaunchedEffect
        unlockScale.snapTo(0.6f)
        unlockScale.animateTo(1f, MotionTokens.entrance.spec())
    }
    val (face, depth) = when (level.state) {
        ChallengeLevel.State.AVAILABLE -> SyllColors.interactive to SyllColors.interactiveDepth
        ChallengeLevel.State.CLEARED -> SyllColors.reward to SyllColors.rewardDepth
        else -> SyllColors.disabledSurface to SyllColors.tileDepth
    }
    val label = when (level.state) {
        ChallengeLevel.State.AVAILABLE -> stringResource(R.string.level_x_ready_to_play, level.levelNo)
        ChallengeLevel.State.CLEARED -> stringResource(R.string.level_x_cleared_x_of_3_stars, level.levelNo, level.bestStars)
        else -> stringResource(R.string.level_x_locked, level.levelNo)
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val sink = if (pressed && onClick != null) NODE_DEPTH - 1.dp else 0.dp

    Column(
        modifier
            .padding(top = if (isCurrent) Spacing.xl + Spacing.s else 0.dp)
            .graphicsLayer { scaleX = unlockScale.value; scaleY = unlockScale.value }
            .clickable(interaction, indication = null, enabled = onClick != null) { onClick?.invoke() }
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isCurrent) {
                Box(
                    Modifier
                        .size(NODE_SIZE + 20.dp)
                        .offset(y = NODE_DEPTH / 2)
                        .border(5.dp, SyllColors.interactive.copy(alpha = 0.3f), CircleShape),
                )
            }
            Box(
                Modifier.size(NODE_SIZE).roundDepth(depth, NODE_DEPTH).offset(y = sink).background(face, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                when (level.state) {
                    ChallengeLevel.State.AVAILABLE -> Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(36.dp))
                    ChallengeLevel.State.CLEARED -> Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(34.dp))
                    else -> Icon(Icons.Filled.Lock, null, tint = SyllColors.disabledInk, modifier = Modifier.size(28.dp))
                }
            }
            if (isCurrent) StartCallout(Modifier.align(Alignment.TopCenter).offset(y = -(Spacing.xl + Spacing.s)))
        }
        Spacer(Modifier.size(NODE_DEPTH))
        if (level.state == ChallengeLevel.State.CLEARED) StarRow(level.bestStars, size = 14.dp)
    }
}

private val CALLOUT_TAIL = 8.dp

/** iOS `StartCallout`: "Start" in a box with a tail pointing down, bobbing. */
@Composable
private fun StartCallout(modifier: Modifier = Modifier) {
    val motion = LocalMotion.current
    val tailPx = with(LocalDensity.current) { CALLOUT_TAIL.toPx() }
    val chipPx = with(LocalDensity.current) { Radii.chip.toPx() }
    val shape = remember(tailPx, chipPx) {
        GenericShape { size, _ ->
            val boxBottom = size.height - tailPx
            addRoundRect(RoundRect(0f, 0f, size.width, boxBottom, CornerRadius(chipPx)))
            moveTo(size.width / 2 - tailPx, boxBottom - 1)
            lineTo(size.width / 2, size.height)
            lineTo(size.width / 2 + tailPx, boxBottom - 1)
            close()
        }
    }
    val bob = if (motion.reduceMotion) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "callout")
        val value by transition.animateFloat(
            0f, 1f,
            infiniteRepeatable(tween(MotionTokens.mapCalloutBob.millis, easing = MotionTokens.mapCalloutBob.easing), RepeatMode.Reverse),
            label = "bob",
        )
        value
    }
    val rise = with(LocalDensity.current) { (Spacing.xxs + 1.dp).toPx() }
    Text(
        stringResource(R.string.start).uppercase(),
        style = Typography.eyebrow,
        color = SyllColors.interactive,
        modifier = modifier
            .graphicsLayer { translationY = -rise * bob }
            .background(SyllColors.surface, shape)
            .border(2.dp, SyllColors.stroke, shape)
            .padding(horizontal = Spacing.s)
            .padding(top = Spacing.xs, bottom = Spacing.xs + CALLOUT_TAIL)
            .clearAndSetSemantics { },
    )
}

/** iOS `ChapterTrophy`: the chapter's last stop, gold once every level is cleared. Decoration only. */
@Composable
private fun ChapterTrophy(isEarned: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(bottom = NODE_DEPTH)
            .size(64.dp)
            .roundDepth(if (isEarned) SyllColors.rewardDepth else SyllColors.tileDepth, NODE_DEPTH)
            .background(if (isEarned) SyllColors.reward else SyllColors.disabledSurface, CircleShape)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.EmojiEvents, null, tint = if (isEarned) Color.White else SyllColors.disabledInk, modifier = Modifier.size(30.dp))
    }
}

// endregion

// region Next chapter

/**
 * iOS `NextChapterSection`: the chapter after the last one, on the same path: locked with a word count until enough
 * eligible words are learned in Daily, then ready.
 */
@Composable
private fun NextChapterSection(
    preview: ChallengeChapterPreview,
    waveStart: Int,
    onPlay: () -> Unit,
    onLearn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isReady = preview.state == ChallengeChapterPreview.State.READY
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
        ChapterBanner(
            preview.chapterNo,
            stringResource(if (isReady) R.string.ready_to_play else R.string.locked),
            if (isReady) ChapterBannerStyle.ACTIVE else ChapterBannerStyle.MUTED,
        ) {
            if (!isReady) UnlockCondition(preview, onLearn)
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.l), horizontalAlignment = Alignment.CenterHorizontally) {
            for (levelNo in 1..ChallengeMapWave.PREVIEW_LEVEL_COUNT) {
                val playable = isReady && levelNo == 1
                ChallengeLevelNode(
                    level = ChallengeLevel(levelNo, if (playable) ChallengeLevel.State.AVAILABLE else ChallengeLevel.State.LOCKED, 0, 0),
                    node = ChallengeNodeRef(preview.chapterNo, levelNo),
                    isCurrent = playable,
                    onClick = if (playable) onPlay else null,
                    modifier = Modifier.offset(x = ChallengeMapWave.offset(waveStart + levelNo - 1)),
                )
            }
            ChapterTrophy(false, Modifier.offset(x = ChallengeMapWave.offset(waveStart + ChallengeMapWave.PREVIEW_LEVEL_COUNT)))
        }
    }
}

@Composable
private fun UnlockCondition(preview: ChallengeChapterPreview, onLearn: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        if (preview.state == ChallengeChapterPreview.State.LOCKED) {
            Text(stringResource(R.string.clear_chapter_x_to_unlock_this_chapter, preview.chapterNo - 1), style = Typography.secondary, color = SyllColors.mutedInk)
        } else {
            Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    stringResource(R.string.x_x_new_words, preview.eligibleNewCount, preview.requiredNewCount),
                    style = Typography.secondary.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                    color = SyllColors.ink,
                )
                val fraction = if (preview.requiredNewCount > 0) minOf(preview.eligibleNewCount.toFloat() / preview.requiredNewCount, 1f) else 0f
                ProgressTrack(fraction, Modifier.clearAndSetSemantics { })
            }
            Text(stringResource(R.string.learn_new_words_in_your_daily_lesson_to_unlock_t), style = Typography.secondary, color = SyllColors.mutedInk)
            SecondaryButton(stringResource(R.string.continue_daily), onClick = onLearn)
        }
    }
}

// endregion
