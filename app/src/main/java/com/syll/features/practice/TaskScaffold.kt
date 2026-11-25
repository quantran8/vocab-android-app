package com.syll.features.practice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syll.R
import com.syll.app.LocalPracticeSessionStore
import com.syll.designsystem.components.ActionButtonKind
import com.syll.designsystem.components.AudioPlayButton
import com.syll.designsystem.components.PrimaryButton
import com.syll.designsystem.components.SyllieMood
import com.syll.designsystem.components.SyllieView
import com.syll.designsystem.components.pressScaleClickable
import com.syll.designsystem.motion.LocalMotion
import com.syll.designsystem.motion.MotionTokens
import com.syll.designsystem.tokens.Radii
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography
import com.syll.domain.practice.TaskAttempt
import com.syll.domain.practice.TaskPrompt
import com.syll.domain.practice.TaskTemplate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/** iOS `FeedbackKind` / `FeedbackCopy`. */
enum class FeedbackKind { SUCCESS, ERROR, INFO }

data class FeedbackCopy(val kind: FeedbackKind, val title: String, val message: String)

/** iOS `TaskFeedbackCopy`: feedback never names the answer until the task is finished. */
@Composable
fun taskFeedbackCopy(attempt: TaskAttempt): FeedbackCopy? {
    val answer = attempt.solutionText
    return when (attempt.status) {
        TaskAttempt.Status.ANSWERING, TaskAttempt.Status.RECORDED -> null
        TaskAttempt.Status.SOLVED -> FeedbackCopy(
            FeedbackKind.SUCCESS, stringResource(R.string.correct_2), answer ?: stringResource(R.string.all_pairs_matched_2),
        )
        TaskAttempt.Status.WRONG -> FeedbackCopy(
            FeedbackKind.ERROR, stringResource(R.string.not_quite), stringResource(R.string.try_again_or_show_the_answer),
        )
        TaskAttempt.Status.REVEALED -> FeedbackCopy(
            FeedbackKind.INFO, stringResource(R.string.here_s_the_answer), answer ?: attempt.allPairsText.orEmpty(),
        )
        // The exercise keeps the learner's work; this panel is the only place the answer appears.
        TaskAttempt.Status.EXHAUSTED -> FeedbackCopy(
            FeedbackKind.INFO, stringResource(R.string.the_correct_answer), answer ?: attempt.missedPairsText.orEmpty(),
        )
    }
}

/**
 * iOS `TaskScaffold`: question → prompt → exercise → feedback → pinned action, shared by every template.
 * The action panel goes to the flow's pinned bar (see [StepActionBar]), so it stays put while steps slide.
 */
@Composable
fun TaskScaffold(
    attempt: TaskAttempt,
    feedback: FeedbackCopy? = null,
    onTapOutside: (() -> Unit)? = null,
    /** Recording scored by the backend: the word sweeps while the take is scored, then shows its syllables' colours. */
    promptStyle: RecordingPromptStyle? = null,
    /** Off when the result is shown above the panel (scored Recording); [feedbackDetail] takes the title's place. */
    feedbackShowsHeading: Boolean = true,
    feedbackDetail: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
    action: @Composable ColumnScope.() -> Unit,
) {
    // The reactions for the next right and wrong answer, picked ahead so they're ready before the answer renders.
    var happyMood by remember { mutableStateOf(SyllieMood.nextHappyReaction()) }
    var oopsMood by remember { mutableStateOf(SyllieMood.nextOopsReaction()) }
    var thinkingMood by remember { mutableStateOf(SyllieMood.nextThinking()) }
    var previousStatus by remember { mutableStateOf(attempt.status) }
    LaunchedEffect(attempt.status) {
        val old = previousStatus
        val new = attempt.status
        if (old == TaskAttempt.Status.SOLVED) happyMood = SyllieMood.nextHappyReaction()
        if (old.isMiss() && !new.isMiss()) oopsMood = SyllieMood.nextOopsReaction()
        if (old != TaskAttempt.Status.ANSWERING && new == TaskAttempt.Status.ANSWERING) thinkingMood = SyllieMood.nextThinking()
        previousStatus = new
    }
    // Recording: each take's result comes through `feedback` while the status stays `answering`.
    var previousFeedbackKind by remember { mutableStateOf(feedback?.kind) }
    LaunchedEffect(feedback?.kind) {
        if (previousFeedbackKind == FeedbackKind.SUCCESS) happyMood = SyllieMood.nextHappyReaction()
        if (previousFeedbackKind == FeedbackKind.ERROR) oopsMood = SyllieMood.nextOopsReaction()
        previousFeedbackKind = feedback?.kind
    }
    val shownFeedback = feedback ?: taskFeedbackCopy(attempt)
    val mascot = when {
        feedback != null && attempt.status == TaskAttempt.Status.ANSWERING -> when (feedback.kind) {
            FeedbackKind.SUCCESS -> happyMood
            FeedbackKind.ERROR -> oopsMood
            FeedbackKind.INFO -> SyllieMood.IDLE
        }
        attempt.status == TaskAttempt.Status.SOLVED -> happyMood
        attempt.status.isMiss() -> oopsMood
        attempt.status == TaskAttempt.Status.ANSWERING -> thinkingMood
        else -> SyllieMood.IDLE
    }

    Column(
        Modifier
            .fillMaxSize()
            .then(
                if (onTapOutside != null) {
                    Modifier.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onTapOutside)
                } else {
                    Modifier
                },
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.practiceGutter)
            .padding(top = Spacing.m, bottom = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        attempt.task.question?.let { ExerciseHeading(it) }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            TaskPromptView(attempt, promptStyle)
            // Recording: which of the word's sounds to read.
            attempt.task.ipa?.let { ipa ->
                val label = stringResource(R.string.ipa_x, ipa)
                Text(
                    ipa,
                    style = Typography.body,
                    color = SyllColors.mutedInk,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { contentDescription = label },
                )
            }
        }
        // While a wrong answer is shown, the exercise waits for "Try again" or "Continue".
        val locked = attempt.status == TaskAttempt.Status.WRONG || attempt.status == TaskAttempt.Status.EXHAUSTED
        Column(
            Modifier.fillMaxWidth().then(if (locked) Modifier.blockTouches() else Modifier),
            content = content,
        )
    }

    StepActionBar {
        FeedbackActionPanel(shownFeedback, mascot, showsHeading = feedbackShowsHeading, detail = feedbackDetail, action = action)
    }
}

private fun TaskAttempt.Status.isMiss() = this == TaskAttempt.Status.WRONG || this == TaskAttempt.Status.EXHAUSTED

/** Swallows touches before the children see them, so a locked exercise can't be changed. */
private fun Modifier.blockTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
}

/** iOS `ExerciseHeading`: the left-aligned task question. */
@Composable
fun ExerciseHeading(title: String) {
    Text(
        title,
        style = Typography.screenTitle.copy(letterSpacing = (-0.4).sp),
        color = SyllColors.ink,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The prompt's area is as tall as the audio button, so text and audio prompts start the content at one height. */
private val PROMPT_MIN_HEIGHT = 96.dp

/** iOS `TaskPromptView`: a task has at most one prompt kind. Audio stays audio: showing the text would give the answer away. */
@Composable
fun TaskPromptView(attempt: TaskAttempt, style: RecordingPromptStyle? = null) {
    val store = LocalPracticeSessionStore.current
    val scope = rememberCoroutineScope()
    when (val prompt = attempt.task.prompt) {
        null -> Unit
        is TaskPrompt.Text -> {
            val parts = prompt.highlight?.split(prompt.text)
            val text = buildAnnotatedString {
                if (parts == null) {
                    append(prompt.text)
                } else {
                    append(parts.before)
                    withStyle(SpanStyle(color = SyllColors.interactive, textDecoration = TextDecoration.Underline)) { append(parts.word) }
                    append(parts.after)
                }
            }
            if (attempt.task.template == TaskTemplate.CLOZE_SELECT) {
                // Cloze: the sentence's translation, a quiet line above the sentence itself.
                Text(text, style = Typography.body, color = SyllColors.bodyInk, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            } else {
                // The word or meaning stands alone: 36 sp for a word, 30 sp for a meaning to type or spell.
                val fontStyle = if (attempt.task.template == TaskTemplate.TYPING || attempt.task.template == TaskTemplate.FLY_REORDER) {
                    Typography.stat
                } else {
                    Typography.challengeTerm
                }
                val textStyle = fontStyle.copy(letterSpacing = Typography.displayTracking.sp)
                Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = PROMPT_MIN_HEIGHT), contentAlignment = Alignment.Center) {
                    if (style != null && prompt.highlight == null) {
                        RecordingPromptWord(prompt.text, style, textStyle)
                    } else {
                        Text(text, style = textStyle, color = SyllColors.ink, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        is TaskPrompt.Audio -> Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            AudioPlayButton(
                isSpeaking = store.audio.isPlaying,
                isAvailable = true,
                isLarge = true,
                onClick = { scope.launch { store.playPromptAudio() } },
            )
            if (store.audioFailed) {
                Text(
                    stringResource(R.string.sound_didn_t_play_try_again_or_answer_without_it),
                    style = Typography.micro,
                    color = SyllColors.errorInk,
                    textAlign = TextAlign.Center,
                )
            }
            // Heard once as the task appears, after the step transition; leaving the task cancels it.
            LaunchedEffect(attempt.task.id) {
                delay(AUTOPLAY_DELAY_MS)
                store.playPromptAudio()
            }
        }
    }
}

const val AUTOPLAY_DELAY_MS = 350L

/**
 * iOS `FeedbackActionPanel`: the pinned bottom panel where explanation and primary action share one surface. With
 * feedback present, the whole panel takes the status colour. Syllie sits beside the action, reacting with [mascot].
 */
@Composable
fun FeedbackActionPanel(
    feedback: FeedbackCopy?,
    mascot: SyllieMood?,
    /**
     * Off when the result is shown above the panel (a scored read-aloud): the panel keeps the feedback's colour and
     * announcement, and shows [detail] (if any) instead of the icon and title.
     */
    showsHeading: Boolean = true,
    detail: (@Composable () -> Unit)? = null,
    action: @Composable ColumnScope.() -> Unit,
) {
    val motion = LocalMotion.current
    val surface by animateColorAsState(
        when (feedback?.kind) {
            FeedbackKind.SUCCESS -> SyllColors.successSurface
            FeedbackKind.ERROR -> SyllColors.errorSurface
            FeedbackKind.INFO -> SyllColors.infoSurface
            null -> SyllColors.canvas
        },
        motion.animation(MotionTokens.feedback),
        label = "panelSurface",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .background(surface, RoundedCornerShape(topStart = Radii.hero, topEnd = Radii.hero))
            .navigationBarsPadding()
            .padding(horizontal = Spacing.practiceGutter)
            .padding(top = if (feedback == null || (!showsHeading && detail == null)) Spacing.s else Spacing.l - 4.dp, bottom = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        AnimatedVisibility(
            visible = feedback != null && !showsHeading && detail != null,
            enter = if (motion.reduceMotion) fadeIn() else slideInVertically { it / 2 } + fadeIn(),
            exit = fadeOut(),
        ) {
            val shownDetail = remember(feedback) { detail } ?: return@AnimatedVisibility
            val announcement = feedback?.let { "${it.title}. ${it.message}" }.orEmpty()
            Box(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite; stateDescription = announcement }) {
                shownDetail()
            }
        }
        AnimatedVisibility(
            visible = feedback != null && showsHeading,
            enter = if (motion.reduceMotion) fadeIn() else slideInVertically { it / 2 } + fadeIn(),
            exit = fadeOut(),
        ) {
            val shown = remember(feedback) { feedback } ?: return@AnimatedVisibility
            val ink = when (shown.kind) {
                FeedbackKind.SUCCESS -> SyllColors.successInk
                FeedbackKind.ERROR -> SyllColors.errorInk
                FeedbackKind.INFO -> SyllColors.infoInk
            }
            Column(
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(Spacing.xs - 2.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s - 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .background(
                                when (shown.kind) {
                                    FeedbackKind.SUCCESS -> SyllColors.success
                                    FeedbackKind.ERROR -> SyllColors.error
                                    FeedbackKind.INFO -> SyllColors.interactive
                                },
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            when (shown.kind) {
                                FeedbackKind.SUCCESS -> Icons.Filled.Check
                                FeedbackKind.ERROR -> Icons.Filled.Close
                                FeedbackKind.INFO -> Icons.Filled.PriorityHigh
                            },
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(shown.title, style = Typography.feedbackTitle, color = ink)
                }
                if (shown.message.isNotEmpty()) Text(shown.message, style = Typography.secondary, color = ink)
            }
        }
        if (mascot != null) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                // The artboard leaves room around his body; overlap it into the gutter and the button.
                SyllieReactionView(mascot, Modifier.size(100.dp, 83.dp).offset(x = (-7).dp))
                Column(Modifier.weight(1f).offset(x = (-12).dp), content = action)
            }
        } else {
            Column(content = action)
        }
    }
}

/**
 * iOS `SyllieReactionView`: plays [reaction] once, then settles into its held loop or idle. Looping moods stay until
 * the next reaction. Decorative only.
 */
@Composable
fun SyllieReactionView(reaction: SyllieMood, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(SyllieMood.IDLE) }
    LaunchedEffect(reaction) {
        shown = reaction
        val duration = reaction.reactionDurationMillis ?: return@LaunchedEffect
        delay(duration)
        shown = reaction.settledMood
    }
    SyllieView(shown, modifier)
}

/**
 * iOS `TaskActionBar`: Check while answering, Try again after a miss, Continue once finished.
 * [requirement] replaces Check where there is none (Match submits pair by pair).
 */
@Composable
fun TaskActionBar(
    attempt: TaskAttempt,
    checkTitle: String = stringResource(R.string.check),
    canCheck: Boolean = false,
    requirement: String? = null,
    onCheck: (() -> Unit)? = null,
    /** Offers "Show answer" here. Off where [HintControl] already does. */
    showsReveal: Boolean = true,
) {
    val store = LocalPracticeSessionStore.current
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        when {
            attempt.isFinished -> PrimaryButton(
                stringResource(R.string.s_continue),
                onClick = store::advance,
                kind = if (attempt.status == TaskAttempt.Status.SOLVED) ActionButtonKind.SUCCESS else ActionButtonKind.APP,
            )
            attempt.status == TaskAttempt.Status.WRONG -> {
                RevealButton(attempt)
                PrimaryButton(stringResource(R.string.try_again_2), onClick = store::tryAgain, kind = ActionButtonKind.RETRY)
            }
            else -> {
                if (showsReveal) RevealButton(attempt)
                when {
                    onCheck != null -> PrimaryButton(checkTitle, onClick = onCheck, enabled = canCheck)
                    requirement != null -> Text(
                        requirement,
                        style = Typography.secondary.copy(fontWeight = FontWeight.SemiBold),
                        color = SyllColors.mutedInk,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = Spacing.minTouchTarget).padding(top = Spacing.s),
                    )
                }
            }
        }
    }
}

@Composable
private fun RevealButton(attempt: TaskAttempt) {
    val store = LocalPracticeSessionStore.current
    if (!attempt.canReveal) return
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Text(
            stringResource(R.string.show_answer),
            style = Typography.secondary.copy(fontWeight = FontWeight.Bold),
            color = if (attempt.status == TaskAttempt.Status.WRONG) SyllColors.destructiveInk else SyllColors.mutedInk,
            modifier = Modifier
                .defaultMinSize(minHeight = Spacing.minTouchTarget)
                .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = store::reveal)
                .padding(vertical = Spacing.s),
        )
    }
}

/** iOS `HintControl`: "Reveal hint n/2", "Show answer", and the hints given so far. */
@Composable
fun HintControl(attempt: TaskAttempt, onReveal: () -> Unit = {}) {
    val store = LocalPracticeSessionStore.current
    val canHint = attempt.hintsUsed < TaskAttempt.MAX_HINTS
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.reveal_hint_x_x, attempt.hintsUsed, TaskAttempt.MAX_HINTS),
                style = Typography.secondary.copy(fontWeight = FontWeight.ExtraBold),
                color = SyllColors.blue700.copy(alpha = if (canHint) 1f else 0.4f),
                modifier = Modifier
                    .defaultMinSize(minHeight = Spacing.minTouchTarget)
                    .clickable(remember { MutableInteractionSource() }, indication = null, enabled = canHint, role = Role.Button, onClick = store::useHint)
                    .padding(vertical = Spacing.s),
            )
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.show_answer),
                style = Typography.secondary.copy(fontWeight = FontWeight.Bold),
                color = SyllColors.mutedInk.copy(alpha = if (attempt.canReveal) 1f else 0.4f),
                modifier = Modifier
                    .defaultMinSize(minHeight = Spacing.minTouchTarget)
                    .clickable(remember { MutableInteractionSource() }, indication = null, enabled = attempt.canReveal, role = Role.Button) {
                        store.reveal()
                        onReveal()
                    }
                    .padding(vertical = Spacing.s),
            )
        }
        attempt.hints.forEach { hint ->
            val label = stringResource(R.string.hint_x, hint.replace("_", "blank"))
            Text(
                hint,
                style = Typography.body.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 3.sp),
                color = SyllColors.blue700,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SyllColors.blue50, RoundedCornerShape(8.dp))
                    .padding(horizontal = Spacing.s, vertical = 6.dp)
                    .semantics { contentDescription = label },
            )
        }
    }
}

/** iOS `ShakeEffect`: one quick horizontal shake, replayed whenever [trigger] changes. */
@Composable
fun Modifier.shake(trigger: Int): Modifier {
    val motion = LocalMotion.current
    val progress = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger == 0 || motion.reduceMotion) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(400, easing = LinearEasing))
    }
    val travel = with(LocalDensity.current) { 6.dp.toPx() }
    return graphicsLayer { translationX = travel * sin(progress.value * PI.toFloat() * 4) }
}
