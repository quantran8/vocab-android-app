package com.syll.features.practice

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.composed
import kotlin.random.Random
import androidx.compose.runtime.mutableStateMapOf
import com.syll.designsystem.motion.MotionTokens
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.togetherWith
import android.icu.text.ListFormatter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.ConfigurationCompat
import com.syll.R
import com.syll.app.LocalLearningPlanStore
import com.syll.app.LocalPracticeSessionStore
import com.syll.app.text
import com.syll.designsystem.components.ActionButtonKind
import com.syll.designsystem.components.CardDivider
import com.syll.designsystem.components.CardRow
import com.syll.designsystem.components.ConfirmSheet
import com.syll.designsystem.components.ConfirmSheetStyle
import com.syll.designsystem.components.FlowHeader
import com.syll.designsystem.components.PinnedActionBar
import com.syll.designsystem.components.PrimaryButton
import com.syll.designsystem.components.SecondaryButton
import com.syll.designsystem.components.StatusScreen
import com.syll.designsystem.components.SyllieMood
import com.syll.designsystem.components.SyllieView
import com.syll.designsystem.components.cardSurface
import com.syll.designsystem.motion.LocalMotion
import com.syll.designsystem.tokens.Borders
import com.syll.designsystem.tokens.Radii
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography
import com.syll.domain.NewWordReason
import com.syll.domain.practice.LearnCard
import com.syll.domain.practice.PlanStep
import com.syll.domain.practice.TaskPhase
import com.syll.domain.practice.TaskTemplate
import com.syll.features.plan.CatalogState
import com.syll.features.plan.displayName
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** iOS `PracticeSessionView`: everything between tapping a practice button and returning to the tab it was started from. */
@Composable
fun PracticeSessionView() {
    val store = LocalPracticeSessionStore.current
    val scope = rememberCoroutineScope()
    val close = { store.close() }

    val motion = LocalMotion.current
    // Stages cross-fade with the `transition` curve (iOS `motion.perform(transition)` around stage changes).
    AnimatedContent(
        store.stage,
        Modifier.fillMaxSize().background(SyllColors.canvas),
        transitionSpec = { fadeIn(motion.animation(MotionTokens.transition)) togetherWith fadeOut(motion.animation(MotionTokens.transition)) },
        contentKey = { it::class },
        label = "sessionStage",
    ) { stage ->
    Box(Modifier.fillMaxSize().background(SyllColors.canvas)) {
        when (stage) {
            PracticeSessionStore.Stage.Idle -> Unit
            PracticeSessionStore.Stage.Planning -> StatusScreen(
                stringResource(R.string.preparing_your_session), Modifier.safeDrawingPadding(), isBusy = true, showsFlyingSyllie = true,
            )
            PracticeSessionStore.Stage.Committing -> StatusScreen(
                stringResource(R.string.saving_your_progress), Modifier.safeDrawingPadding(), isBusy = true, showsFlyingSyllie = true,
            )
            PracticeSessionStore.Stage.Empty -> EmptyScreen()
            is PracticeSessionStore.Stage.ReviewPreview -> ReviewPreviewView(stage.words)
            PracticeSessionStore.Stage.ReviewFinished -> ReviewFinishedView(store.upcomingCards)
            PracticeSessionStore.Stage.Running -> PracticeFlowView()
            // The celebration and results screens arrive with step 6.
            is PracticeSessionStore.Stage.Finished -> Column(
                Modifier.fillMaxSize().safeDrawingPadding().padding(Spacing.screenGutter),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(1f))
                SyllieView(SyllieMood.PROUD, Modifier.size(187.dp, 154.dp))
                Spacer(Modifier.weight(1f))
                PrimaryButton(stringResource(R.string.done), onClick = close)
            }
            is PracticeSessionStore.Stage.Failed -> when (val failure = stage.failure) {
                is PracticeSessionStore.Failure.Planning -> StatusScreen(
                    stringResource(R.string.couldn_t_start_the_session), Modifier.safeDrawingPadding(), message = failure.message.text(),
                ) {
                    PrimaryButton(stringResource(R.string.try_again_2), onClick = { scope.launch { store.retry() } })
                    SecondaryButton(stringResource(R.string.close), onClick = close)
                }
                is PracticeSessionStore.Failure.Commit -> StatusScreen(
                    stringResource(R.string.your_progress_isn_t_saved_yet), Modifier.safeDrawingPadding(), message = failure.message.text(),
                ) {
                    PrimaryButton(stringResource(R.string.try_saving_again), onClick = { scope.launch { store.retry() } })
                }
                is PracticeSessionStore.Failure.SessionLost -> StatusScreen(
                    stringResource(R.string.session_ended), Modifier.safeDrawingPadding(), message = failure.message.text(),
                ) {
                    SecondaryButton(stringResource(R.string.close), onClick = close)
                }
            }
        }
    }
    }

    // Back leaves through the same confirmation as ✕ while running; elsewhere it closes.
    BackHandler(enabled = store.stage != PracticeSessionStore.Stage.Running && store.stage != PracticeSessionStore.Stage.Committing) {
        store.close()
    }
}

@Composable
private fun EmptyScreen() {
    val store = LocalPracticeSessionStore.current
    val plan = LocalLearningPlanStore.current
    val withheld = store.newWords?.withheldReason
    val modifier = Modifier.safeDrawingPadding()
    val closeButton: @Composable () -> Unit = { SecondaryButton(stringResource(R.string.close), onClick = { store.close() }) }
    when {
        store.isDaily && withheld == NewWordReason.SCOPE_EXHAUSTED -> StatusScreen(
            stringResource(R.string.you_ve_learned_every_new_word_here),
            modifier,
            message = stringResource(
                if (plan.current.hasAllTopics) R.string.you_can_still_review_and_practise_the_words_you else R.string.you_can_still_review_and_practise_the_words_you_2,
            ),
        ) {
            PrimaryButton(
                stringResource(if (plan.current.hasAllTopics) R.string.change_collection else R.string.choose_topics),
                onClick = { store.close(PracticeSessionStore.FollowUp.CHOOSE_TOPICS) },
            )
            SecondaryButton(stringResource(R.string.practise), onClick = { store.close(PracticeSessionStore.FollowUp.RANDOM_PRACTICE) })
            closeButton()
        }
        // Android offers no Premium until Phase 4: the free-words case keeps Practise and Close.
        store.isDaily && withheld == NewWordReason.FREE_LIMIT -> StatusScreen(
            stringResource(R.string.you_ve_learned_all_your_free_words),
            modifier,
            message = stringResource(R.string.upgrade_to_premium_to_keep_learning_new_words_yo),
        ) {
            SecondaryButton(stringResource(R.string.practise), onClick = { store.close(PracticeSessionStore.FollowUp.RANDOM_PRACTICE) })
            closeButton()
        }
        else -> StatusScreen(stringResource(R.string.nothing_to_practise_right_now), modifier, message = emptyMessage()) {
            closeButton()
        }
    }
}

@Composable
private fun emptyMessage(): String {
    val store = LocalPracticeSessionStore.current
    return stringResource(
        when {
            store.isWeakPractice -> R.string.words_you_keep_missing_will_show_up_here
            store.isSpeakingPractice -> if (store.canRecord) {
                R.string.learned_words_with_a_pronunciation_will_show_up
            } else {
                R.string.allow_microphone_access_in_settings_to_practice
            }
            store.isReviewOnly -> R.string.no_words_are_waiting_for_review_right_now
            store.practiceFilter != null -> R.string.you_haven_t_learned_any_words_from_this_source_y
            else -> R.string.check_back_after_learning_more_words
        },
    )
}

// Review preview

/**
 * iOS `ReviewPreviewView`: lists the session's review words before its first step. The intro streams in, then the
 * words arrive one after another while Syllie scans them, and he nods once they've landed. Presentation only.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewPreviewView(words: List<ReviewWord>) {
    val store = LocalPracticeSessionStore.current
    val motion = LocalMotion.current
    val intro = stringResource(R.string.let_s_review_these_words_you_already_learned_bef)
    var revealed by remember { mutableStateOf(if (motion.reduceMotion) intro.length else 0) }
    var hasLanded by remember { mutableStateOf(false) }
    var reaction by remember { mutableStateOf(SyllieMood.IDLE) }
    val entrance = remember { ReviewEntrance.entries.random() }
    val trainOrigin = remember { TRAIN_ORIGINS.random() }
    val timing = remember(entrance, words.size) { entranceTiming(entrance, words.size) }
    /** Bumped per word to replay its wiggle. */
    val wiggles = remember { mutableStateMapOf<String, Int>() }

    LaunchedEffect(Unit) {
        while (revealed < intro.length) {
            revealed += 1
            delay(STREAM_CHARACTER_MILLIS)
        }
        hasLanded = true
    }
    // Syllie scans the list from the moment the words start flying in and nods the moment the last one lands.
    LaunchedEffect(hasLanded) {
        if (!hasLanded) return@LaunchedEffect
        if (!motion.reduceMotion) {
            reaction = SyllieMood.REVIEW_WATCH
            delay((timing.total * 1000).toLong())
        }
        reaction = SyllieMood.REVIEW_READY
    }
    // After the entrance, a random word stirs every so often until the screen goes away.
    LaunchedEffect(hasLanded) {
        if (!hasLanded || motion.reduceMotion) return@LaunchedEffect
        delay((timing.total * 1000).toLong())
        while (true) {
            words.randomOrNull()?.let { wiggles[it.id] = (wiggles[it.id] ?: 0) + 1 }
            val pause = MotionTokens.reviewWigglePause
            delay((Random.nextDouble(pause.start, pause.endInclusive) * 1000).toLong())
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.practiceGutter)
                .padding(top = Spacing.l, bottom = Spacing.l),
        ) {
            Text(
                stringResource(R.string.review_words_you_ve_learned),
                style = Typography.sheetTitle,
                color = SyllColors.ink,
                modifier = Modifier.semantics { heading() },
            )
            // Reveals character by character; the hidden rest keeps its space, so lines never reflow.
            Text(
                androidx.compose.ui.text.buildAnnotatedString {
                    append(intro.take(revealed))
                    pushStyle(androidx.compose.ui.text.SpanStyle(color = androidx.compose.ui.graphics.Color.Transparent))
                    append(intro.drop(revealed))
                    pop()
                },
                style = Typography.body,
                color = SyllColors.bodyInk,
                modifier = Modifier.padding(top = Spacing.xs).semantics { contentDescription = intro },
            )
            Box(Modifier.fillMaxWidth().padding(top = Spacing.l), contentAlignment = Alignment.Center) {
                SyllieReactionView(reaction, Modifier.size(150.dp, 118.dp))
            }
            FlowRow(
                Modifier.fillMaxWidth().padding(top = Spacing.l),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp),
            ) {
                words.forEachIndexed { index, word ->
                    key(word.id) {
                        ReviewTile(
                            word,
                            Modifier
                                .reviewEntrance(entrance, index, timing.lead + index * timing.stagger, hasLanded, trainOrigin)
                                .tileWiggle(wiggles[word.id] ?: 0),
                        )
                    }
                }
            }
        }
        PinnedActionBar {
            PrimaryButton(stringResource(R.string.s_continue), onClick = store::continueToPractice)
            SecondaryButton(stringResource(R.string.close), onClick = { store.close() })
        }
    }
}

private val STREAM_CHARACTER_MILLIS = (MotionTokens.streamCharacter * 1000).toLong()

/** The word, with the topic it belongs to under it: old topics stay in review after the focus changes. */
@Composable
private fun ReviewTile(word: ReviewWord, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Radii.tile)
    Column(
        modifier
            .padding(bottom = Spacing.tileDepth)
            .background(SyllColors.surface, shape)
            .border(Borders.card, SyllColors.stroke, shape)
            .defaultMinSize(minHeight = 54.dp)
            .padding(horizontal = Spacing.s + 2.dp, vertical = Spacing.xs),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(word.lemma, style = Typography.wordClass.copy(fontWeight = FontWeight.Bold), color = SyllColors.ink)
        word.topic?.let { Text(it, style = Typography.tab, color = SyllColors.mutedInk) }
    }
}

// Review finished

/** iOS `ReviewFinishedView`: between review and new words, lists the words coming up with their meanings. */
@Composable
private fun ReviewFinishedView(cards: List<LearnCard>) {
    val store = LocalPracticeSessionStore.current
    val plan = LocalLearningPlanStore.current
    val motion = LocalMotion.current
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    // Named only when there are one or two: a long Focus would print every topic.
    val focusNames = CatalogState.collections
        .flatMap { collection -> collection.topics.filter { it.id in plan.current.topicIds }.map { collection to it } }
        .map { (collection, topic) -> topic.displayName(collection) }
        .takeIf { it.size <= 2 }
        ?.let { ListFormatter.getInstance(locale).format(it) }
        .orEmpty()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.practiceGutter)
                .padding(top = Spacing.xl, bottom = Spacing.l),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Text(
                    stringResource(R.string.review_complete),
                    style = Typography.stat.copy(letterSpacing = Typography.displayTracking.sp),
                    color = SyllColors.ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                SyllieReactionView(if (motion.reduceMotion) SyllieMood.IDLE else SyllieMood.REVIEW_TO_LEARN, Modifier.size(150.dp, 118.dp))
            }
            if (cards.isEmpty()) {
                Text(
                    stringResource(R.string.next_up_some_new_words_to_learn),
                    style = Typography.secondary,
                    color = SyllColors.mutedInk,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.l),
                )
            } else {
                if (focusNames.isNotEmpty()) {
                    Text(
                        stringResource(R.string.now_for_new_words_from_x, focusNames),
                        style = Typography.secondary,
                        color = SyllColors.mutedInk,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.m + Spacing.xxs),
                    )
                }
                Text(
                    pluralStringResource(R.plurals.next_x_new_words, cards.size, cards.size),
                    style = Typography.wordClass.copy(fontWeight = FontWeight.Bold),
                    color = SyllColors.ink,
                    modifier = Modifier.padding(top = Spacing.l).semantics { heading() },
                )
                Column(Modifier.padding(top = Spacing.s).cardSurface()) {
                    cards.forEachIndexed { index, card ->
                        if (index > 0) CardDivider()
                        CardRow(Modifier.semantics(mergeDescendants = true) {}) {
                            Text(card.word, style = Typography.emphasis, color = SyllColors.ink, modifier = Modifier.weight(1f))
                            Text(card.meaning, style = Typography.secondary, color = SyllColors.mutedInk, textAlign = TextAlign.End)
                        }
                    }
                }
            }
        }
        PinnedActionBar {
            PrimaryButton(stringResource(R.string.learn_new_words), onClick = store::continueToPractice)
        }
    }
}

// Flow

/** iOS `PracticeFlowView`: one persistent header for every step; only the step below it transitions. */
@Composable
private fun PracticeFlowView() {
    val store = LocalPracticeSessionStore.current
    val motion = LocalMotion.current
    var isConfirmingExit by remember { mutableStateOf(false) }
    val host = remember(store.sessionToken) { StepActionBarHost() }
    val progress = store.partProgress

    BackHandler { isConfirmingExit = true }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        FlowHeader(
            stage = stringResource(
                when (progress.part) {
                    SessionPartProgress.Part.REVIEW -> R.string.review
                    SessionPartProgress.Part.NEW_WORDS -> R.string.new_words
                    SessionPartProgress.Part.PRACTICE -> R.string.practice
                },
            ),
            stageCurrent = progress.current,
            stageTotal = progress.total,
            step = progress.current,
            totalSteps = progress.total,
            closeLabel = stringResource(R.string.exit_flow),
            onClose = { isConfirmingExit = true },
            modifier = Modifier.padding(horizontal = Spacing.screenGutter).padding(top = Spacing.xxs, bottom = Spacing.s),
        )
        CompositionLocalProvider(LocalStepActionBarHost provides host) {
            AnimatedContent(
                targetState = store.stepIndex,
                transitionSpec = { motion.stepTransition(isMovingBack = false) },
                label = "practiceStep",
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { index ->
                CompositionLocalProvider(LocalStepIndex provides index) {
                    store.steps.getOrNull(index)?.let { StepView(it) }
                }
            }
        }
        // Only the step content slides; its bottom bar is drawn here and stays in place.
        host.bar?.invoke()
    }

    if (isConfirmingExit) {
        ConfirmSheet(
            style = ConfirmSheetStyle.LeavePractice,
            title = stringResource(R.string.leave_the_session),
            message = stringResource(R.string.progress_only_counts_once_the_whole_session_is_f),
            cancelTitle = stringResource(R.string.keep_practising),
            confirmTitle = stringResource(R.string.leave_without_saving),
            onDismiss = { isConfirmingExit = false },
            onConfirm = {
                isConfirmingExit = false
                store.close()
            },
        )
    }
}

@Composable
private fun StepView(step: PlanStep) {
    val store = LocalPracticeSessionStore.current
    when (step) {
        is PlanStep.Learn -> LearnCardStep(step.card)
        is PlanStep.Practice -> {
            val attempt = store.attempts[step.task.id] ?: return
            val offer = store.knownCheckOffer
            if (step.phase == TaskPhase.KNOWN_CHECK && offer != null && store.currentStep == step) {
                LearnCardStep(offer, offersKnownCheck = true)
            } else {
                PracticeTaskView(attempt)
            }
        }
    }
}

/** iOS `PracticeTaskView`: picks the view for a task's template. Shared by live sessions and Practice Lab. */
@Composable
fun PracticeTaskView(attempt: com.syll.domain.practice.TaskAttempt) {
    when (attempt.task.template) {
        TaskTemplate.SELECT -> ChoiceTaskView(attempt)
        TaskTemplate.CLOZE_SELECT -> ClozeTaskView(attempt)
        TaskTemplate.TYPING -> TypingTaskView(attempt)
        TaskTemplate.FLY_REORDER -> ReorderTaskView(attempt)
        TaskTemplate.MATCH -> MatchTaskView(attempt)
        TaskTemplate.SURGERY -> SurgeryTaskView(attempt)
        TaskTemplate.RECORDING -> RecordingTaskView(attempt)
    }
}

/** iOS `LearnCardStep`: the word's card, or the known-check offer ("Continue" / "I know this word"). */
@Composable
private fun LearnCardStep(card: LearnCard, offersKnownCheck: Boolean = false) {
    val store = LocalPracticeSessionStore.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.practiceGutter)
            .padding(top = Spacing.m + Spacing.xxs, bottom = Spacing.l),
    ) {
        WordIntroCard(card, playAudio = card.audio?.let { clip -> { store.playClip(clip) } }, isPlayingAudio = store.audio.isPlaying)
    }
    // A new word is heard once as its card appears, after the step transition, only while the session runs.
    LaunchedEffect(card) {
        val clip = card.audio ?: return@LaunchedEffect
        delay(AUTOPLAY_DELAY_MS)
        if (store.stage == PracticeSessionStore.Stage.Running) store.playClip(clip)
    }
    StepActionBar {
        PinnedActionBar {
            if (offersKnownCheck) {
                PrimaryButton(stringResource(R.string.s_continue), onClick = store::declineKnownCheck)
                SecondaryButton(stringResource(R.string.i_know_this_word), onClick = store::markKnown, kind = ActionButtonKind.PLAIN)
            } else {
                PrimaryButton(stringResource(R.string.s_continue), onClick = store::advance)
            }
        }
    }
}

private enum class ReviewEntrance { SCATTERED, WORD_TRAIN }

/** Top, right, bottom and left (dp), each past that screen edge on any phone. */
private val TRAIN_ORIGINS = listOf(0f to -1000f, 460f to 0f, 0f to 1000f, -460f to 0f)

/** Where scattered tiles start (dp), cycled so neighbours arrive from different sides. */
private val FLY_IN_ORIGINS = listOf(
    -420f to -120f, 420f to -260f, -260f to 520f, 360f to 420f, 40f to -560f, -440f to 260f,
)

private data class EntranceTiming(val lead: Double, val stagger: Double, val total: Double)

/**
 * iOS `entranceTiming`: when each word starts flying in, after `lead`, one every `stagger`. Picked so the last word
 * comes to rest (its delay + its own landing) after a whole number of `REVIEW_WATCH` loops, at least one, so
 * Syllie's nod starts on a loop boundary. The stagger stretches or shrinks a little from its token to get there.
 */
private fun entranceTiming(entrance: ReviewEntrance, count: Int): EntranceTiming {
    val (base, settle) = when (entrance) {
        ReviewEntrance.SCATTERED -> MotionTokens.reviewTileStagger to MotionTokens.reviewTileSettle
        ReviewEntrance.WORD_TRAIN -> MotionTokens.reviewTrainStagger to MotionTokens.reviewTrainSettle
    }
    val loop = SyllieMood.REVIEW_WATCH_LOOP_MILLIS / 1000.0
    val gaps = maxOf(count - 1, 0).toDouble()
    var loops = maxOf(1.0, Math.rint((gaps * base + settle) / loop))
    while (true) {
        val total = loops * loop
        val stagger = if (gaps > 0) ((total - settle) / gaps).coerceIn(base * 0.6, base * 2) else base
        val lead = total - settle - gaps * stagger
        if (lead >= 0) return EntranceTiming(lead, stagger, total)
        loops += 1
    }
}

/**
 * iOS `ReviewPreviewView.tile`: scattered tiles fly in tilted from around the screen with a quick separate fade (so
 * a tile is solid for most of its way in); a word train has every tile follow the one before from one side.
 * Reduced motion: the tiles only fade in.
 */
private fun Modifier.reviewEntrance(
    entrance: ReviewEntrance,
    index: Int,
    delaySeconds: Double,
    hasLanded: Boolean,
    trainOrigin: Pair<Float, Float>,
): Modifier = composed {
    val motion = LocalMotion.current
    val flight = remember { Animatable(0f) }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(hasLanded) {
        if (!hasLanded) return@LaunchedEffect
        if (motion.reduceMotion) {
            flight.snapTo(1f)
            fade.animateTo(1f, MotionTokens.reducedCrossfade.spec())
            return@LaunchedEffect
        }
        delay((delaySeconds * 1000).toLong())
        when (entrance) {
            ReviewEntrance.SCATTERED -> {
                launch { fade.animateTo(1f, MotionTokens.reviewTileFade.spec()) }
                flight.animateTo(1f, MotionTokens.reviewTileLanding.spec())
            }
            ReviewEntrance.WORD_TRAIN -> {
                // The train's opacity rides the same spring as its travel.
                launch { fade.animateTo(1f, MotionTokens.reviewTrainLanding.spec()) }
                flight.animateTo(1f, MotionTokens.reviewTrainLanding.spec())
            }
        }
    }
    val origin = when {
        motion.reduceMotion -> 0f to 0f
        entrance == ReviewEntrance.SCATTERED -> FLY_IN_ORIGINS[index % FLY_IN_ORIGINS.size]
        else -> trainOrigin
    }
    val tilt = if (entrance == ReviewEntrance.SCATTERED && !motion.reduceMotion) (if (index % 2 == 0) -12f else 12f) else 0f
    graphicsLayer {
        val away = 1f - flight.value
        translationX = origin.first.dp.toPx() * away
        translationY = origin.second.dp.toPx() * away
        rotationZ = tilt * away
        alpha = fade.value.coerceIn(0f, 1f)
    }
}

/** iOS `TileWiggle`: one quick shake with a small hop, replayed whenever [trigger] changes. */
private fun Modifier.tileWiggle(trigger: Int): Modifier = composed {
    val angle = remember { Animatable(0f) }
    val lift = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        launch {
            angle.animateTo(0f, keyframes {
                durationMillis = 500
                -6f at 90 using FastOutSlowInEasing
                5f at 200 using FastOutSlowInEasing
                -4f at 300 using FastOutSlowInEasing
                2f at 390 using FastOutSlowInEasing
                0f at 500 using FastOutSlowInEasing
            })
        }
        lift.animateTo(0f, keyframes {
            durationMillis = 340
            -5f at 140 using FastOutSlowInEasing
            0f at 340 using FastOutSlowInEasing
        })
    }
    graphicsLayer {
        rotationZ = angle.value
        translationY = lift.value.dp.toPx()
    }
}
