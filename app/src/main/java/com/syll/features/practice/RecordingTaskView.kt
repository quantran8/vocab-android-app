package com.syll.features.practice

import androidx.compose.runtime.mutableStateOf
import com.syll.features.premium.PaywallReason
import com.syll.features.premium.PaywallView
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.syll.R
import com.syll.app.LocalAppScope
import com.syll.app.LocalLearnerStore
import com.syll.app.LocalPracticeSessionStore
import com.syll.designsystem.components.ActionButtonKind
import com.syll.designsystem.components.PrimaryButton
import com.syll.designsystem.components.SecondaryButton
import com.syll.designsystem.components.TextLink
import com.syll.designsystem.components.soundControlClickable
import com.syll.designsystem.motion.LocalMotion
import com.syll.designsystem.motion.MotionTokens
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography
import com.syll.domain.practice.PronunciationBand
import com.syll.domain.practice.PronunciationColoring
import com.syll.domain.practice.RecordingAssessment
import com.syll.domain.practice.TaskAttempt
import com.syll.domain.practice.TaskPrompt
import com.syll.features.practice.PracticeSessionStore.RecordingStage
import com.syll.features.practice.PracticeSessionStore.UnavailableReason
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * iOS `RecordingTaskView`: read aloud, get scored, optionally read again. The last assessment is what gets committed.
 * `meaningCue` is decoded but not shown: a learned word's recording goes straight to the read-aloud.
 * A take scored by the backend shows its score in the mic's place, the word coloured per syllable, and the
 * syllables' detail in the panel; an on-device take (right or wrong, no score) keeps the plain result.
 */
@Composable
fun RecordingTaskView(attempt: TaskAttempt) {
    val store = LocalPracticeSessionStore.current
    val learner = LocalLearnerStore.current
    // Stopping uploads the take; it must not be cancelled if this view recomposes away mid-request.
    val appScope = LocalAppScope.current
    val stage = store.recording
    val isRecording = stage == RecordingStage.Recording
    val assessment = (stage as? RecordingStage.Assessed)?.assessment
    val scored = assessment?.let { ScoredTake.of(it) }
    var paywallReason by remember { mutableStateOf<PaywallReason?>(null) }
    paywallReason?.let { PaywallView(it, onDismiss = { paywallReason = null }) }

    val promptStyle: RecordingPromptStyle? = when {
        stage == RecordingStage.Uploading && !store.scoresOnDevice -> RecordingPromptStyle.Scoring
        scored != null -> (attempt.task.prompt as? TaskPrompt.Text)?.takeIf { it.highlight == null }?.let { prompt ->
            PronunciationColoring.segments(prompt.text, scored.assessment.syllables.orEmpty())?.let(RecordingPromptStyle::Scored)
        }
        else -> null
    }

    // Without syllables from the backend the panel holds only Syllie and the buttons. One syllable is the whole
    // word, already coloured in the prompt, so it gets no detail either.
    val syllables = scored?.assessment?.syllables?.takeIf { it.size > 1 }
    val feedbackDetail: (@Composable () -> Unit)? = syllables?.let {
        {
            androidx.compose.runtime.key(scored.assessment.assessmentReceiptId) {
                PronunciationDetail(it, passed = scored.assessment.passed == true)
            }
        }
    }

    TaskScaffold(
        attempt,
        feedback = recordingFeedback(attempt, stage, scored),
        promptStyle = promptStyle,
        feedbackShowsHeading = scored == null,
        feedbackDetail = feedbackDetail,
        content = {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                if (scored != null) {
                    PronunciationScoreRing(scored.score, scored.band)
                    PronunciationBandTitle(scored.band)
                } else {
                    val canUseMic = when (stage) {
                        // A take's result waits for "Try again"; a failed upload resends the same take.
                        RecordingStage.Uploading, RecordingStage.UploadFailed, is RecordingStage.Blocked, is RecordingStage.Assessed -> false
                        RecordingStage.Recording -> true
                        RecordingStage.Idle -> attempt.canRecordAgain
                    }
                    RecordingPulseButton(isRecording, canUseMic, level = store::inputLevel) {
                        appScope.launch { if (isRecording) store.stopRecording() else store.startRecording() }
                    }
                    RecordingStatus(
                        stage,
                        showsPremium = store.isFreeSpeaking && learner.offersPremium,
                        onPremium = { paywallReason = PaywallReason.Speaking },
                    )
                }
            }
        },
        action = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                RecordingActions(attempt, stage)
            }
        },
    )
}

/** A valid take with a score from the backend. On-device and Lab takes carry no score. */
private data class ScoredTake(val assessment: RecordingAssessment, val score: Double, val band: PronunciationBand) {
    companion object {
        fun of(assessment: RecordingAssessment): ScoredTake? {
            val band = assessment.scoreBand ?: return null
            val score = assessment.accuracyScore ?: return null
            return ScoredTake(assessment, score, band)
        }
    }
}

/** While a take's result is shown, the panel holds it and the mic waits for "Try again". */
@Composable
private fun recordingFeedback(attempt: TaskAttempt, stage: RecordingStage, scored: ScoredTake?): FeedbackCopy? {
    if (stage == RecordingStage.UploadFailed) {
        // Not the learner's miss: the info panel, and the take is kept for "Try again".
        return FeedbackCopy(
            FeedbackKind.INFO,
            stringResource(R.string.couldn_t_connect),
            stringResource(R.string.your_recording_is_still_here_try_sending_it_agai),
        )
    }
    val assessment = (stage as? RecordingStage.Assessed)?.assessment ?: return null
    if (!assessment.isValid) {
        val title = if (attempt.canRecordAgain) {
            stringResource(R.string.we_couldn_t_hear_you_clearly_try_reading_again)
        } else {
            stringResource(R.string.we_couldn_t_hear_you_clearly)
        }
        return FeedbackCopy(FeedbackKind.INFO, title, "")
    }
    val passed = assessment.passed == true
    val kind = if (passed) FeedbackKind.SUCCESS else FeedbackKind.ERROR
    if (scored != null) {
        // The title and score are drawn above the panel; these feed the panel's colour and announcement.
        return FeedbackCopy(kind, scored.band.title(), PronunciationSummary.scoreText(scored.score))
    }
    val title = when {
        attempt.hasTranscripts -> stringResource(if (passed) R.string.correct_2 else R.string.not_quite)
        passed -> stringResource(R.string.great_pronunciation)
        attempt.canRecordAgain -> stringResource(R.string.close_try_once_more)
        else -> stringResource(R.string.close_keep_practicing)
    }
    val message = when {
        assessment.accuracyScore != null -> "${assessment.accuracyScore!!.roundToInt()}"
        assessment.heard != null && !passed -> stringResource(R.string.we_heard_x, assessment.heard!!)
        else -> ""
    }
    return FeedbackCopy(kind, title, message)
}

@Composable
private fun RecordingStatus(stage: RecordingStage, showsPremium: Boolean, onPremium: () -> Unit) {
    val store = LocalPracticeSessionStore.current
    when (stage) {
        RecordingStage.Idle -> StatusText(stringResource(R.string.tap_the_microphone_and_read_aloud))
        RecordingStage.Recording -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            ListeningBars(level = store::inputLevel)
            StatusText(stringResource(R.string.listening_tap_to_stop))
        }
        RecordingStage.Uploading -> if (store.scoresOnDevice) {
            CircularProgressIndicator(Modifier.size(24.dp), color = SyllColors.mutedInk, strokeWidth = 2.5.dp)
        } else {
            StatusText(stringResource(R.string.scoring))
        }
        // A free take is right or wrong only; Premium scores it.
        is RecordingStage.Assessed -> if (showsPremium) {
            TextLink(
                stringResource(R.string.get_a_pronunciation_score_with_premium),
                onClick = onPremium,
                color = SyllColors.grape600,
                icon = Icons.Outlined.WorkspacePremium,
            )
        }
        RecordingStage.UploadFailed -> StatusText(stringResource(R.string.couldn_t_check_your_recording))
        is RecordingStage.Blocked -> StatusText(
            stringResource(
                when (stage.reason) {
                    UnavailableReason.MIC_DENIED -> R.string.microphone_access_is_off_you_can_turn_it_on_in_s
                    UnavailableReason.ON_DEVICE_UNAVAILABLE -> R.string.speech_checking_isn_t_available_on_this_device_t
                    else -> R.string.recording_isn_t_available_right_now
                },
            ),
        )
    }
}

@Composable
private fun StatusText(text: String) {
    Text(text, style = Typography.secondary, color = SyllColors.bodyInk, textAlign = TextAlign.Center)
}

/**
 * Buttons follow the feedback panel: Green after a pass, Coral "Try again" after a scored miss, Ink otherwise
 * (an unclear take shows the Blue info panel).
 */
@Composable
private fun RecordingActions(attempt: TaskAttempt, stage: RecordingStage) {
    val store = LocalPracticeSessionStore.current
    val appScope = LocalAppScope.current
    val continueTitle = stringResource(R.string.s_continue)
    when (stage) {
        is RecordingStage.Assessed -> {
            val passed = stage.assessment.passed == true
            if (passed || !attempt.canRecordAgain) {
                PrimaryButton(continueTitle, onClick = store::acceptRecording, kind = if (passed) ActionButtonKind.SUCCESS else ActionButtonKind.APP)
            } else {
                val valid = stage.assessment.isValid
                PrimaryButton(
                    stringResource(R.string.try_again_2),
                    onClick = store::retryRecording,
                    kind = if (valid) ActionButtonKind.RETRY else ActionButtonKind.APP,
                )
                // An invalid take is nothing to keep, so leaving it reads as skipping.
                SecondaryButton(if (valid) continueTitle else stringResource(R.string.can_t_speak_now), onClick = store::acceptRecording)
            }
        }
        RecordingStage.UploadFailed -> {
            PrimaryButton(stringResource(R.string.try_again_2), onClick = { appScope.launch { store.retryUpload() } })
            SecondaryButton(stringResource(R.string.skip), onClick = store::acceptRecording)
        }
        is RecordingStage.Blocked -> PrimaryButton(continueTitle, onClick = store::acceptRecording)
        RecordingStage.Idle -> SecondaryButton(stringResource(R.string.can_t_speak_now), onClick = store::acceptRecording)
        RecordingStage.Recording, RecordingStage.Uploading -> Unit
    }
}

// Record control

private val BUTTON_SIZE = 128.dp
/** The control's box, halo room included; its layout stops at the rest disc's bottom edge. */
private val CONTROL_SIDE = BUTTON_SIZE * 2.5f
/** The Blue 50 disc the button sits in (w-44). */
private val REST_HALO_SIZE = 176.dp
/** The button's depth edge, a step deeper than a regular button's. */
private val BUTTON_DEPTH = Spacing.buttonDepth + 1.dp
private val HALO_SCALES = listOf(1.3f, 1.65f, 2.0f)
private const val HALO_SWELL = 0.12f
private const val HALO_PULSE_DEPTH = 0.04f
private const val HALO_PULSE_PERIOD = 1.4
private const val RIPPLE_COUNT = 3
private const val RIPPLE_PERIOD = 2.1

/**
 * iOS `RecordingPulseButton`: the halo and voice ripples appear only while the mic hears a voice. Everything that
 * moves is read in the draw phase from per-frame state, so speaking redraws the rings without recomposing.
 */
@Composable
private fun RecordingPulseButton(isRecording: Boolean, isEnabled: Boolean, level: () -> Double, onClick: () -> Unit) {
    val motion = LocalMotion.current
    val isLive = isRecording && !motion.reduceMotion
    val envelope = remember { VoiceEnvelope() }
    var time by remember { mutableDoubleStateOf(0.0) }
    var presence by remember { mutableDoubleStateOf(0.0) }
    var swell by remember { mutableDoubleStateOf(0.0) }
    LaunchedEffect(isLive) {
        if (!isLive) {
            presence = 0.0
            swell = 0.0
            return@LaunchedEffect
        }
        while (true) {
            withFrameNanos { nanos ->
                val seconds = nanos / 1e9
                val output = envelope.update(level(), seconds)
                time = seconds
                presence = output.presence
                swell = output.swell
            }
        }
    }
    // Reduce motion has no live level: a static Blue 100 disc is the recording cue, faded with the transition.
    val staticHalo by animateFloatAsState(if (isRecording) 1f else 0f, motion.animation(MotionTokens.transition), label = "staticHalo")
    val haloColors = listOf(SyllColors.blue100, SyllColors.blue50, SyllColors.blue50)
    val startLabel = stringResource(R.string.start_recording)
    val stopLabel = stringResource(R.string.stop_recording)

    // Lays out only down to the rest disc's edge: the halo keeps its room above (so it never covers the word),
    // and below it draws under the status line, which then sits close to the button like the other text.
    Box(
        Modifier.fillMaxWidth().height(CONTROL_SIDE / 2 + REST_HALO_SIZE / 2),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(Modifier.wrapContentSize(unbounded = true).requiredSize(CONTROL_SIDE), contentAlignment = Alignment.Center) {
            if (motion.reduceMotion) {
                Box(Modifier.size(REST_HALO_SIZE).graphicsLayer { alpha = staticHalo }.background(SyllColors.blue100, CircleShape))
            } else {
                // The halo: rings grow out from behind the button as presence goes 0 → 1; outer rings swell more.
                Canvas(
                    Modifier
                        .requiredSize(CONTROL_SIDE)
                        .graphicsLayer {
                            alpha = min(presence * 2, 1.0).toFloat()
                            compositingStrategy = CompositingStrategy.Offscreen
                        },
                ) {
                    val growth = presence.toFloat()
                    val pulse = if (isLive) sin(time * 2 * PI / HALO_PULSE_PERIOD).toFloat() else 0f
                    val button = BUTTON_SIZE.toPx()
                    for (index in HALO_SCALES.indices.reversed()) {
                        val reach = (index + 1).toFloat()
                        val rest = 1 + (HALO_SCALES[index] - 1) * growth
                        val grown = rest + (swell.toFloat() * HALO_SWELL + pulse * HALO_PULSE_DEPTH * growth) * reach
                        drawCircle(if (isEnabled) haloColors[index] else SyllColors.disabledSurface, radius = button * grown / 2)
                    }
                }
                if (isLive) {
                    // Ripples leave the button at a steady pace and ease out as they spread, like sound waves.
                    Canvas(Modifier.requiredSize(CONTROL_SIDE)) {
                        val button = BUTTON_SIZE.toPx()
                        val line = 2.dp.toPx()
                        for (index in 0 until RIPPLE_COUNT) {
                            val phase = ((time / RIPPLE_PERIOD + index.toDouble() / RIPPLE_COUNT) % 1.0)
                            val spread = 1 - (1 - phase) * (1 - phase)
                            val fade = (1 - phase) * (1 - phase) * presence
                            val scale = 1 + spread * (0.95 + 0.25 * swell)
                            drawCircle(
                                SyllColors.interactive.copy(alpha = fade.toFloat()),
                                radius = (button / 2 - line / 2) * scale.toFloat(),
                                style = Stroke(line * scale.toFloat()),
                            )
                        }
                    }
                }
            }
            RecordButton(isRecording, isEnabled, if (isRecording) stopLabel else startLabel, onClick)
        }
    }
}

@Composable
private fun RecordButton(isRecording: Boolean, isEnabled: Boolean, label: String, onClick: () -> Unit) {
    val motion = LocalMotion.current
    Box(Modifier.size(BUTTON_SIZE + BUTTON_DEPTH), contentAlignment = Alignment.TopCenter) {
        if (isEnabled) {
            Box(Modifier.size(BUTTON_SIZE).offset(y = BUTTON_DEPTH).background(SyllColors.interactiveDepth, CircleShape))
        }
        Box(
            Modifier
                .size(BUTTON_SIZE)
                .soundControlClickable(enabled = isEnabled, isRaised = true, depth = BUTTON_DEPTH, onClick = onClick)
                .background(if (isEnabled) SyllColors.interactive else SyllColors.disabledSurface, CircleShape)
                .clearAndSetSemantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            // iOS `.contentTransition(.symbolEffect(.replace))`.
            Crossfade(isRecording, animationSpec = motion.animation(MotionTokens.symbolSwap), label = "micGlyph") { recording ->
                Icon(
                    if (recording) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = null,
                    tint = if (isEnabled) SyllColors.surface else SyllColors.disabledInk,
                    modifier = Modifier.size(56.dp),
                )
            }
        }
    }
}

/**
 * iOS `VoiceEnvelope`: turns the jumpy mic level into a calm signal. The halo fades in when a voice starts, holds
 * through the short gaps between words, and fades out only after a real pause.
 */
private class VoiceEnvelope {
    data class Output(val presence: Double, val swell: Double)

    private var presence = 0.0
    private var swell = 0.0
    private var lastHeard = Double.NEGATIVE_INFINITY
    private var lastTime: Double? = null

    fun update(level: Double, time: Double): Output {
        // A gap in updates means a new recording: start from silence, not from the last one's tail.
        lastTime?.let {
            if (time - it > 0.25) {
                presence = 0.0
                swell = 0.0
                lastHeard = Double.NEGATIVE_INFINITY
            }
        }
        val elapsed = lastTime?.let { (time - it).coerceIn(0.0, 0.1) } ?: 0.0
        lastTime = time

        if (level > VOICE_GATE) lastHeard = time
        val target = if (time - lastHeard < HOLD) 1.0 else 0.0
        // A steady ramp (not an exponential, which front-loads the change into the first frames), eased at both
        // ends on output, so the halo visibly grows instead of popping in.
        val step = elapsed / (if (target > presence) GROW_IN else SHRINK_OUT)
        presence = if (target > presence) min(presence + step, target) else maxOf(presence - step, target)
        val eased = presence * presence * (3 - 2 * presence)

        val loud = ((level - VOICE_GATE) / SWELL_RANGE).coerceIn(0.0, 1.0)
        swell += (loud - swell) * (1 - exp(-elapsed / SWELL_EASE))
        return Output(eased, swell * eased)
    }

    private companion object {
        /** Mic level where room noise ends and a voice begins. */
        const val VOICE_GATE = 0.22
        /** Mic level span above the gate that maps to a full swell. */
        const val SWELL_RANGE = 0.3
        /** How long the halo stays after the voice drops, bridging gaps between words. */
        const val HOLD = 0.5
        const val GROW_IN = 0.55
        const val SHRINK_OUT = 0.7
        const val SWELL_EASE = 0.2
    }
}

private val PEAK_HEIGHTS = listOf(8f, 16f, 12f, 18f, 10f)

/** iOS `ListeningBars`: level bars under the button, driven by the microphone's loudness. */
@Composable
private fun ListeningBars(level: () -> Double) {
    val motion = LocalMotion.current
    var time by remember { mutableDoubleStateOf(0.0) }
    var loudness by remember { mutableDoubleStateOf(0.5) }
    LaunchedEffect(motion.reduceMotion) {
        if (motion.reduceMotion) {
            loudness = 0.5
            return@LaunchedEffect
        }
        while (true) {
            withFrameNanos { nanos ->
                time = nanos / 1e9
                loudness = level()
            }
        }
    }
    Row(
        Modifier.height(18.dp).clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PEAK_HEIGHTS.forEachIndexed { index, peak ->
            Canvas(Modifier.width(4.dp).height(18.dp)) {
                // A little per-bar wobble keeps the bars from moving as one block.
                val wobble = if (motion.reduceMotion) 1.0 else 0.75 + 0.25 * sin(time * 9 + index * 1.7)
                val height = maxOf(4f, peak * (loudness * wobble).toFloat()).dp.toPx()
                val width = size.width
                drawRoundRect(
                    SyllColors.interactive,
                    topLeft = Offset(0f, (size.height - height) / 2),
                    size = androidx.compose.ui.geometry.Size(width, height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(width / 2),
                )
            }
        }
    }
}
