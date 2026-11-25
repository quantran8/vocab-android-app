package com.syll.features.challenge

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.syll.R
import com.syll.app.LocalAppScope
import com.syll.app.LocalChallengeRunStore
import com.syll.app.LocalPracticeSessionStore
import com.syll.app.UserMessage
import com.syll.app.text
import com.syll.designsystem.components.ConfirmSheet
import com.syll.designsystem.components.ConfirmSheetStyle
import com.syll.designsystem.components.PrimaryButton
import com.syll.designsystem.components.ProgressTrack
import com.syll.designsystem.components.SecondaryButton
import com.syll.designsystem.components.StatusScreen
import com.syll.designsystem.components.cardSurface
import com.syll.designsystem.components.pressScaleClickable
import com.syll.designsystem.motion.LocalMotion
import com.syll.designsystem.motion.MotionTokens
import com.syll.designsystem.tokens.Radii
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography
import com.syll.domain.challenge.ChallengeBoardSlots
import com.syll.domain.challenge.MatchMadnessFeedback
import com.syll.domain.challenge.MatchMadnessState
import com.syll.domain.practice.SessionMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil

/** iOS `MatchMadnessGameView`: everything between tapping Play and returning to the map (or the Practice Lab). */
@Composable
fun MatchMadnessGameView() {
    val store = LocalChallengeRunStore.current
    val practice = LocalPracticeSessionStore.current
    val appScope = LocalAppScope.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // The run never pauses: back in the foreground, the clock is read again and may end the run.
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) store.tick() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    fun closeRun() = store.close()
    fun continueDaily() {
        store.close()
        appScope.launch { practice.start(SessionMode.DAILY) }
    }
    val backToMap = stringResource(R.string.back_to_map)
    val screen = Modifier.safeDrawingPadding()

    when (val stage = store.stage) {
        ChallengeRunStore.Stage.Idle -> Unit
        ChallengeRunStore.Stage.Issuing ->
            StatusScreen(stringResource(R.string.getting_your_board_ready), screen, isBusy = true, showsFlyingSyllie = true)
        ChallengeRunStore.Stage.Ready, ChallengeRunStore.Stage.Starting -> ChallengeReadyView(screen)
        ChallengeRunStore.Stage.Playing -> MatchMadnessBoardView(screen)
        ChallengeRunStore.Stage.Committing ->
            StatusScreen(stringResource(R.string.saving_your_result), screen, isBusy = true, showsFlyingSyllie = true)
        is ChallengeRunStore.Stage.Results -> ChallengeResultsView(stage.outcome)
        is ChallengeRunStore.Stage.SandboxResults -> ChallengeSandboxResultsView(stage.summary, screen)
        is ChallengeRunStore.Stage.CommitFailed -> StatusScreen(
            stringResource(R.string.your_result_isn_t_saved_yet),
            screen,
            message = stage.message.text() + "\n" + stringResource(R.string.if_you_leave_syll_saves_it_the_next_time_you_ope),
        ) {
            PrimaryButton(stringResource(R.string.try_saving_again), onClick = { appScope.launch { store.retryCommit() } })
            SecondaryButton(backToMap, onClick = ::closeRun)
        }
        is ChallengeRunStore.Stage.RunActive -> StatusScreen(
            stringResource(R.string.another_challenge_is_still_open),
            screen,
            message = stringResource(
                if (stage.runId == null) R.string.finish_it_where_you_started_it_or_try_again_in_a else R.string.leave_it_to_start_this_one_it_won_t_count_as_a_w,
            ),
        ) {
            if (stage.runId != null) {
                PrimaryButton(stringResource(R.string.leave_that_run), onClick = { appScope.launch { store.leaveActiveRunAndRetry() } })
            }
            SecondaryButton(backToMap, onClick = ::closeRun)
        }
        is ChallengeRunStore.Stage.Failed -> FailureScreen(stage.failure, screen, backToMap, ::closeRun, ::continueDaily)
    }

    // Back behaves like the ✕ of each screen; on the board it asks first (see the board).
    BackHandler(enabled = store.stage != ChallengeRunStore.Stage.Playing && store.stage != ChallengeRunStore.Stage.Committing) {
        when (store.stage) {
            ChallengeRunStore.Stage.Ready, ChallengeRunStore.Stage.Starting, ChallengeRunStore.Stage.Issuing ->
                appScope.launch { store.leave() }
            else -> closeRun()
        }
    }
}

@Composable
private fun FailureScreen(
    failure: ChallengeRunStore.Failure,
    modifier: Modifier,
    backToMap: String,
    closeRun: () -> Unit,
    continueDaily: () -> Unit,
) {
    when (failure) {
        ChallengeRunStore.Failure.NotEnoughVocabulary -> StatusScreen(
            stringResource(R.string.learn_a_few_more_words_first), modifier,
            message = stringResource(R.string.this_chapter_opens_once_you_ve_learned_enough_ne),
        ) {
            PrimaryButton(stringResource(R.string.continue_daily), onClick = continueDaily)
            SecondaryButton(backToMap, onClick = closeRun)
        }
        is ChallengeRunStore.Failure.ContentUnavailable -> StatusScreen(
            stringResource(R.string.this_challenge_isn_t_available_right_now), modifier,
            message = stringResource(R.string.your_stars_are_safe) + " " + failure.message.text(),
        ) {
            PrimaryButton(stringResource(R.string.keep_learning), onClick = continueDaily)
            SecondaryButton(backToMap, onClick = closeRun)
        }
        ChallengeRunStore.Failure.Expired -> StatusScreen(
            stringResource(R.string.this_run_has_ended), modifier,
            message = stringResource(R.string.runs_close_after_a_while_start_a_new_one_from_th),
        ) { SecondaryButton(backToMap, onClick = closeRun) }
        is ChallengeRunStore.Failure.InvalidEvents -> StatusScreen(
            stringResource(R.string.we_couldn_t_check_this_run), modifier, message = failure.message.text(),
        ) { SecondaryButton(backToMap, onClick = closeRun) }
        ChallengeRunStore.Failure.LevelLocked -> StatusScreen(
            stringResource(R.string.this_level_isn_t_open_yet), modifier,
            message = stringResource(R.string.clear_the_previous_level_to_unlock),
        ) { SecondaryButton(backToMap, onClick = closeRun) }
        is ChallengeRunStore.Failure.Issue -> StartFailed(failure.message, modifier, backToMap, closeRun)
        is ChallengeRunStore.Failure.Start -> StartFailed(failure.message, modifier, backToMap, closeRun)
    }
}

@Composable
private fun StartFailed(message: UserMessage, modifier: Modifier, backToMap: String, closeRun: () -> Unit) {
    StatusScreen(stringResource(R.string.couldn_t_start_the_challenge), modifier, message = message.text()) {
        SecondaryButton(backToMap, onClick = closeRun)
    }
}

/** iOS `Chip`: an upper-case caption on a capsule. */
@Composable
fun ChallengeChip(text: String, fill: androidx.compose.ui.graphics.Color = SyllColors.neutralSurface, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = Typography.eyebrow.copy(letterSpacing = 0.4.sp),
        color = SyllColors.ink,
        modifier = modifier.background(fill, CircleShape).padding(horizontal = Spacing.s - 2.dp, vertical = Spacing.xxs + 1.dp),
    )
}

/** The ✕ that leaves a run; it always sits at the top right. */
@Composable
fun CloseRunButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(Spacing.minTouchTarget)
            .pressScaleClickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Close, contentDescription = null, tint = SyllColors.ink400, modifier = Modifier.size(22.dp))
    }
}

// region Ready

/**
 * iOS `ChallengeReadyView`: the plan is frozen and the clock is stopped. Start asks the server to start the run; the
 * clock begins only then.
 */
@Composable
private fun ChallengeReadyView(modifier: Modifier) {
    val store = LocalChallengeRunStore.current
    val appScope = LocalAppScope.current
    val plan = store.plan
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = Spacing.screenGutter).padding(bottom = Spacing.m)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                CloseRunButton(stringResource(R.string.exit_challenge), onClick = { appScope.launch { store.leave() } })
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Spacer(Modifier.height(Spacing.l))
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    if (store.isSandbox) {
                        ChallengeChip(stringResource(R.string.practice_round))
                    } else if (plan != null) {
                        Text(
                            stringResource(R.string.chapter_x_level_x, plan.chapterNo, plan.levelNo).uppercase(),
                            style = Typography.eyebrow,
                            color = SyllColors.mutedInk,
                        )
                    }
                    Text(
                        stringResource(R.string.match_madness),
                        style = Typography.display,
                        color = SyllColors.ink,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (plan != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                            val goalLine = if (store.isTimed) {
                                stringResource(R.string.match_x_pairs_in_x_seconds, store.engineGoal, plan.timeLimitMs / 1000)
                            } else {
                                stringResource(R.string.match_x_pairs_no_timer, store.engineGoal)
                            }
                            Text(goalLine, style = Typography.secondary, color = SyllColors.mutedInk, textAlign = TextAlign.Center)
                            Text(stringResource(R.string.x_pairs_visible_at_once, plan.visiblePairs), style = Typography.secondary, color = SyllColors.mutedInk, textAlign = TextAlign.Center)
                        }
                    }
                }
                if (plan != null && store.isTimed && !store.isSandbox) {
                    StarRules(plan.starCriteria, plan.timeLimitMs, Modifier.padding(top = Spacing.l))
                }
                if (!store.hasSeenTutorial) TutorialHint(Modifier.padding(top = Spacing.xl))
                if (store.isSandbox) {
                    Text(
                        stringResource(R.string.nothing_here_is_scored_or_saved),
                        style = Typography.micro,
                        color = SyllColors.mutedInk,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = Spacing.l),
                    )
                }
                Spacer(Modifier.height(Spacing.l))
            }
            store.startError?.let { error ->
                Text(
                    error.text(),
                    style = Typography.secondary,
                    color = SyllColors.errorInk,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.s),
                )
            }
            PrimaryButton(
                stringResource(R.string.start),
                onClick = { appScope.launch { store.begin() } },
                enabled = store.stage == ChallengeRunStore.Stage.Ready,
                shimmers = true,
            )
        }
        if (store.stage == ChallengeRunStore.Stage.Starting) {
            CircularProgressIndicator(color = SyllColors.mutedInk, modifier = Modifier.align(Alignment.Center))
        }
    }
}

/** iOS `TutorialHint`: shown before the first ever game: one line, two example tiles. No tutorial series. */
@Composable
private fun TutorialHint(modifier: Modifier = Modifier) {
    Column(
        modifier.cardSurface(radius = Radii.tile, fill = SyllColors.quietSurface).padding(Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Row(Modifier.clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            ExampleTile("allocate")
            Icon(Icons.AutoMirrored.Filled.CompareArrows, null, tint = SyllColors.mutedInk)
            ExampleTile("phân bổ")
        }
        Text(
            stringResource(R.string.tap_a_word_then_its_meaning_in_either_order_wron),
            style = Typography.secondary,
            color = SyllColors.ink,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ExampleTile(text: String) {
    val shape = RoundedCornerShape(Radii.chip)
    Text(
        text,
        style = Typography.tile,
        color = SyllColors.selectedInk,
        modifier = Modifier
            .background(SyllColors.selectedSurface, shape)
            .border(2.dp, SyllColors.interactive, shape)
            .padding(horizontal = Spacing.s, vertical = Spacing.xs),
    )
}

// endregion

// region Board

/** Spoken once each as the clock passes them. */
private val ANNOUNCED_SECONDS = setOf(60, 30, 10)

/** iOS `MatchMadnessBoardView`. */
@Composable
private fun MatchMadnessBoardView(modifier: Modifier) {
    val store = LocalChallengeRunStore.current
    val appScope = LocalAppScope.current
    val motion = LocalMotion.current
    val view = LocalView.current
    val assistive = rememberAssistiveTechEnabled()
    val scope = rememberCoroutineScope()
    var confirmsExit by remember { mutableStateOf(false) }
    var wordSlots by remember { mutableStateOf<List<String?>>(emptyList()) }
    var meaningSlots by remember { mutableStateOf<List<String?>>(emptyList()) }
    /** Just-matched tiles still showing as correct before they fade out. */
    var correctTiles by remember { mutableStateOf<Set<String>>(emptySet()) }
    var lastOnBoard by remember { mutableStateOf<Set<String>>(emptySet()) }
    /** Tallest tile face seen so far; every tile uses it so both columns line up. */
    var tileHeightPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val tileHeight = maxOf(Spacing.matchTileMinHeight, with(density) { tileHeightPx.toDp() })

    val game = store.game
    val goal = store.engineGoal
    val matched = game?.correctCount ?: 0

    fun syncSlots() {
        val current = store.game ?: return
        val onBoard = (current.wordOrder + current.meaningOrder).toSet()
        if (lastOnBoard.intersect(onBoard).isEmpty()) {
            correctTiles = emptySet()
        } else {
            val gone = lastOnBoard - onBoard
            if (gone.isNotEmpty()) {
                correctTiles = correctTiles + gone
                scope.launch {
                    delay((MotionTokens.matchCorrectHold * 1000).toLong())
                    correctTiles = correctTiles - gone
                    syncSlots()
                }
            }
        }
        lastOnBoard = onBoard
        wordSlots = ChallengeBoardSlots.reconcile(wordSlots, current.wordOrder, correctTiles)
        meaningSlots = ChallengeBoardSlots.reconcile(meaningSlots, current.meaningOrder, correctTiles)
    }

    LaunchedEffect(game?.wordOrder, game?.meaningOrder) { syncSlots() }

    val matchText = stringResource(R.string.match)
    val notMatchText = stringResource(R.string.not_a_match)
    val allMatchedText = stringResource(R.string.all_pairs_matched)
    LaunchedEffect(store.feedback.count) {
        if (!assistive) return@LaunchedEffect
        when (store.feedback.value) {
            is MatchMadnessFeedback.Correct -> view.announceForAccessibility(matchText)
            is MatchMadnessFeedback.Wrong -> view.announceForAccessibility(notMatchText)
            MatchMadnessFeedback.GoalReached -> view.announceForAccessibility(allMatchedText)
            else -> Unit
        }
    }
    val secondsLeft = ceil(store.remainingMs / 1000.0).toInt()
    val secondsLeftText = stringResource(R.string.x_seconds_left, secondsLeft)
    var lastAnnounced by remember { mutableStateOf(Int.MAX_VALUE) }
    LaunchedEffect(store.remainingMs / 1000) {
        val whole = store.remainingMs / 1000
        if (assistive && store.isTimed && whole < lastAnnounced && whole in ANNOUNCED_SECONDS) view.announceForAccessibility(secondsLeftText)
        lastAnnounced = whole
    }

    BackHandler { confirmsExit = true }

    Column(modifier.fillMaxSize().padding(horizontal = Spacing.screenGutter).padding(bottom = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        // ✕ at the top right; the timer takes the other side.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            Timer()
            val matchedLabel = stringResource(R.string.matched)
            val matchedValue = stringResource(R.string.x_of_x, matched, goal)
            ProgressTrack(
                fraction = if (goal > 0) matched.toFloat() / goal else 0f,
                modifier = Modifier.weight(1f).clearAndSetSemantics {
                    contentDescription = matchedLabel
                    stateDescription = matchedValue
                },
            )
            CloseRunButton(
                stringResource(if (store.isSandbox) R.string.leave_practice else R.string.exit_challenge),
                onClick = { confirmsExit = true },
            )
        }

        Column(Modifier.fillMaxWidth().padding(top = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(stringResource(R.string.match_the_pairs), style = Typography.screenTitle, color = SyllColors.ink, modifier = Modifier.semantics { heading() })
            Stats(game)
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = Spacing.xs)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                BoardColumn(
                    slots = wordSlots.ifEmpty { game?.wordOrder.orEmpty() },
                    isWord = true,
                    selected = game?.selectedWordTileId,
                    correctTiles = correctTiles,
                    tileHeight = tileHeight,
                    onMeasured = { if (it > tileHeightPx) tileHeightPx = it },
                    modifier = Modifier.weight(1f),
                )
                BoardColumn(
                    slots = meaningSlots.ifEmpty { game?.meaningOrder.orEmpty() },
                    isWord = false,
                    selected = game?.selectedMeaningTileId,
                    correctTiles = correctTiles,
                    tileHeight = tileHeight,
                    onMeasured = { if (it > tileHeightPx) tileHeightPx = it },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (confirmsExit) {
        ConfirmSheet(
            style = ConfirmSheetStyle.LeavePractice,
            title = stringResource(if (store.isSandbox) R.string.leave_practice_2 else R.string.exit_challenge_2),
            message = stringResource(if (store.isSandbox) R.string.this_practice_round_isn_t_saved else R.string.this_run_won_t_be_saved_as_a_win),
            cancelTitle = stringResource(R.string.keep_playing),
            confirmTitle = stringResource(R.string.exit),
            onDismiss = { confirmsExit = false },
            onConfirm = {
                confirmsExit = false
                appScope.launch { store.quit() }
            },
        )
    }
}

@Composable
private fun Timer() {
    val store = LocalChallengeRunStore.current
    if (store.isTimed) {
        val remaining = store.remainingMs
        val urgent = remaining <= 10_000
        val label = stringResource(R.string.time_left)
        val value = stringResource(R.string.x_seconds, ceil(remaining / 1000.0).toInt())
        Box(
            Modifier.defaultMinSize(minWidth = Spacing.minTouchTarget, minHeight = Spacing.minTouchTarget).clearAndSetSemantics {
                contentDescription = label
                stateDescription = value
            },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                ChallengeTimeFormat.countdown(remaining),
                style = Typography.emphasis.copy(fontFeatureSettings = "tnum"),
                color = if (urgent) SyllColors.errorInk else SyllColors.ink,
                modifier = Modifier
                    .background(if (urgent) SyllColors.errorSurface else SyllColors.neutralSurface, CircleShape)
                    .padding(horizontal = Spacing.s, vertical = Spacing.xxs + 2.dp),
            )
        }
    } else {
        Text(
            stringResource(R.string.untimed),
            style = Typography.micro.copy(fontWeight = FontWeight.Bold),
            color = SyllColors.mutedInk,
            modifier = Modifier
                .background(SyllColors.neutralSurface, CircleShape)
                .padding(horizontal = Spacing.s, vertical = Spacing.xxs + 2.dp),
        )
    }
}

@Composable
private fun Stats(game: MatchMadnessState?) {
    val combo = game?.combo ?: 0
    Row(Modifier.semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.combo_x, combo),
            style = Typography.secondary.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
            color = if (combo > 1) SyllColors.selectedInk else SyllColors.mutedInk,
        )
        game?.provisionalScore?.let { score ->
            Text(
                stringResource(R.string.score_x, maxOf(score, 0)),
                style = Typography.secondary.copy(fontFeatureSettings = "tnum"),
                color = SyllColors.mutedInk,
            )
        }
    }
}

@Composable
private fun BoardColumn(
    slots: List<String?>,
    isWord: Boolean,
    selected: String?,
    correctTiles: Set<String>,
    tileHeight: androidx.compose.ui.unit.Dp,
    onMeasured: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val store = LocalChallengeRunStore.current
    val motion = LocalMotion.current
    val depthPx = with(LocalDensity.current) { Spacing.tileDepth.roundToPx() }
    // A taller or shorter meaning resizes its slot smoothly instead of snapping.
    Column(
        modifier.animateContentSize(motion.animation(MotionTokens.matchFadeOut)),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        slots.forEachIndexed { index, tileId ->
            Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = tileHeight + Spacing.tileDepth)) {
                // Each slot keeps its identity; the tile in it fades out and the next one fades in.
                androidx.compose.runtime.key(index) {
                    BoardSlot(tileId, isWord, selected, correctTiles, tileHeight, onMeasured)
                }
            }
        }
    }
}

@Composable
private fun BoardSlot(
    tileId: String?,
    isWord: Boolean,
    selected: String?,
    correctTiles: Set<String>,
    tileHeight: androidx.compose.ui.unit.Dp,
    onMeasured: (Int) -> Unit,
) {
    val store = LocalChallengeRunStore.current
    val motion = LocalMotion.current
    val depthPx = with(LocalDensity.current) { Spacing.tileDepth.roundToPx() }
    var shown by remember { mutableStateOf(tileId) }
    if (tileId != null) shown = tileId
    AnimatedVisibility(
        visible = tileId != null,
        enter = fadeIn(motion.animation(MotionTokens.matchFadeIn)),
        exit = fadeOut(motion.animation(MotionTokens.matchFadeOut)),
    ) {
        val id = shown ?: return@AnimatedVisibility
        androidx.compose.runtime.key(id) {
            val pair = if (isWord) store.pairForWordTile(id) else store.pairForMeaningTile(id)
            MatchMadnessTile(
                text = (if (isWord) pair?.word else pair?.meaning).orEmpty(),
                isWord = isWord,
                isSelected = selected == id,
                isWrong = id in store.wrongTileIds,
                isCorrect = id in correctTiles,
                shakes = store.shakeCounts[id] ?: 0,
                minHeight = tileHeight,
                onClick = { if (isWord) store.tapWord(id) else store.tapMeaning(id) },
                modifier = Modifier.onSizeChanged { onMeasured(it.height - depthPx) },
            )
        }
    }
}

// endregion

/** iOS `BrandBar`: the app icon and the "syll" wordmark. */
@Composable
fun BrandBar(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "Syll" },
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val shape = RoundedCornerShape(Radii.chip)
        Image(
            painterResource(R.drawable.brand_icon),
            null,
            Modifier.size(34.dp).clip(shape).border(1.dp, SyllColors.stroke, shape),
        )
        Text("syll", style = Typography.wordmark.copy(letterSpacing = (-0.6).sp), color = SyllColors.ink)
        Spacer(Modifier.width(0.dp))
    }
}
