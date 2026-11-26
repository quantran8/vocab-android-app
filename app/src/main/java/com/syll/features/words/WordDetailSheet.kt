package com.syll.features.words

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.syll.R
import com.syll.app.LocalLearnerStore
import com.syll.app.LocalPracticeSessionStore
import com.syll.app.LocalSpeechPlayer
import com.syll.designsystem.components.FullScreenDialog
import com.syll.domain.report.ContentReportTarget
import com.syll.features.report.ContentReportSheet
import com.syll.features.report.ReportLink
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.syll.designsystem.components.Eyebrow
import com.syll.designsystem.components.SecondaryButton
import com.syll.designsystem.components.SectionTitle
import com.syll.designsystem.components.StatusPill
import com.syll.designsystem.components.StatusPillTone
import com.syll.designsystem.components.cardSurface
import com.syll.designsystem.components.pressScaleClickable
import com.syll.designsystem.motion.LocalMotion
import com.syll.designsystem.motion.MotionTokens
import com.syll.designsystem.motion.SymbolReplace
import com.syll.designsystem.tokens.Radii
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography
import com.syll.domain.practice.AudioClip
import com.syll.domain.practice.SpeakingStatus
import com.syll.features.practice.highlighted
import com.syll.network.MeaningMemory
import com.syll.network.MeaningSpeaking
import com.syll.network.WordDetail
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

/**
 * iOS `WordDetailSheet`: every meaning of a word grouped by part of speech; progress belongs to each meaning, not
 * the word. Each meaning is a card that opens to its definition, example, memory and speaking.
 */
@Composable
fun WordDetailSheet(
    wordId: String,
    lemma: String,
    ipa: String?,
    onDismiss: () -> Unit,
    /** Opens expanded; otherwise the first learned meaning does. */
    focusMeaningId: String? = null,
    /** Plays recorded audio when there is some. Null falls back to the loaded detail's clip, then to device speech. */
    playAudio: (() -> Unit)? = null,
) {
    val learner = LocalLearnerStore.current
    val practice = LocalPracticeSessionStore.current
    val speech = LocalSpeechPlayer.current
    var detail by remember { mutableStateOf<WordDetail?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    val expanded = remember { mutableStateListOf<String>() }
    /** The meaning opened last; the header's audio follows it. */
    var focusedId by remember { mutableStateOf<String?>(null) }
    var playingMeaningId by remember { mutableStateOf<String?>(null) }
    var reportTarget by remember { mutableStateOf<ContentReportTarget?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    val reportSent = stringResource(R.string.report_sent_thank_you)

    LaunchedEffect(wordId, attempt) {
        failed = false
        detail = null
        try {
            val loaded = learner.wordDetail(wordId)
            if (focusedId == null) {
                val ids = loaded.meanings.map { it.meaningId }
                focusedId = focusMeaningId?.takeIf { it in ids }
                    ?: loaded.meanings.firstOrNull { it.learnedAt != null }?.meaningId
                    ?: ids.firstOrNull()
                focusedId?.let { expanded.add(it) }
            }
            detail = loaded
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = true
        }
    }
    DisposableEffect(Unit) { onDispose { speech?.stop() } }

    // Senses of one spelling can sound different (read /riːd/, /rɛd/): then each meaning shows its own IPA and audio.
    val pronunciationsDiffer = detail?.let { d -> d.meanings.map { it.meaning.ipa.orEmpty() }.toSet().size > 1 } ?: false
    val loadedMeaning = detail?.let { d -> d.meanings.firstOrNull { it.meaningId == focusedId } ?: d.meanings.firstOrNull() }
    val detailClip: AudioClip? = loadedMeaning?.meaning?.audio ?: detail?.meanings?.firstNotNullOfOrNull { it.meaning.audio }
    val clipPlay = detailClip?.let { clip -> { practice.playClip(clip) } }
    val heroPlay = if (pronunciationsDiffer) clipPlay else playAudio ?: clipPlay

    // The word is the hero below, so the app bar holds only ✕.
    FullScreenDialog(title = "", onDismiss = onDismiss, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Spacing.screenGutter)
                .padding(bottom = Spacing.l),
        ) {
            WordHeroHeader(
                lemma = lemma,
                ipa = if (pronunciationsDiffer) null else ipa ?: loadedMeaning?.meaning?.ipa,
                playAudio = heroPlay,
                isPlayingAudio = practice.audio.isPlaying,
                showsPronunciation = !pronunciationsDiffer,
            )
            Column(Modifier.padding(top = Spacing.xl)) {
                SectionTitle(stringResource(R.string.meanings)) {
                    detail?.let { d ->
                        Text(
                            stringResource(R.string.x_x_learned, d.meanings.count { it.learnedAt != null }, d.meanings.size),
                            style = Typography.secondary.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                            color = SyllColors.mutedInk,
                        )
                    }
                }
                val loaded = detail
                when {
                    failed -> Column(Modifier.padding(vertical = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Text(stringResource(R.string.couldn_t_load_this_word), style = Typography.secondary, color = SyllColors.errorInk)
                        SecondaryButton(stringResource(R.string.try_again_2), onClick = { attempt++ }, icon = Icons.Filled.Refresh)
                    }
                    loaded == null -> Box(Modifier.fillMaxWidth().padding(vertical = Spacing.l), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = SyllColors.mutedInk, strokeWidth = 2.5.dp)
                    }
                    else -> groupByPartOfSpeech(loaded.meanings) { it.meaning.partOfSpeech }.forEach { (partOfSpeech, items) ->
                        Column(Modifier.padding(top = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            PartOfSpeechEyebrow(partOfSpeech)
                            items.forEachIndexed { index, item ->
                                MeaningCard(
                                    lemma, item, number = index + 1,
                                    isExpanded = item.meaningId in expanded,
                                    pronunciationsDiffer = pronunciationsDiffer,
                                    isPlaying = playingMeaningId == item.meaningId && practice.audio.isPlaying,
                                    onPlay = item.meaning.audio?.let { clip -> { playingMeaningId = item.meaningId; practice.playClip(clip) } },
                                    onReport = { reportTarget = meaningReportTarget(lemma, item) },
                                    onToggle = {
                                        if (item.meaningId in expanded) {
                                            expanded.remove(item.meaningId)
                                        } else {
                                            expanded.add(item.meaningId)
                                            focusedId = item.meaningId
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    reportTarget?.let { target ->
        ContentReportSheet(
            target,
            onDismiss = { reportTarget = null },
            onSent = {
                reportTarget = null
                snackbarScope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar(reportSent)
                }
            },
        )
    }
}

/** A meaning in word detail or other meanings: the reasons follow what it shows. */
internal fun meaningReportTarget(lemma: String, item: WordDetail.Meaning): ContentReportTarget {
    val sense = item.meaning
    return ContentReportTarget(
        context = ContentReportTarget.Context.WordDetail(item.meaningId),
        subtitle = "$lemma · ${sense.text}",
        reasons = ContentReportTarget.meaningReasons(
            hasDefinition = !sense.definition.isNullOrEmpty(),
            hasExample = !sense.examples.isNullOrEmpty(),
            hasAudio = sense.audio != null,
        ),
    )
}

@Composable
private fun MeaningCard(
    lemma: String,
    item: WordDetail.Meaning,
    number: Int,
    isExpanded: Boolean,
    pronunciationsDiffer: Boolean,
    isPlaying: Boolean,
    onPlay: (() -> Unit)?,
    onToggle: () -> Unit,
    onReport: () -> Unit,
) {
    val motion = LocalMotion.current
    val state = stringResource(if (isExpanded) R.string.expanded else R.string.collapsed)
    Column(Modifier.fillMaxWidth().cardSurface()) {
        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = Spacing.rowMinHeight)
                .pressScaleClickable(onClick = onToggle)
                .semantics(mergeDescendants = true) { stateDescription = state }
                .padding(horizontal = Spacing.m),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$number",
                style = Typography.body.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                color = SyllColors.ink400,
                modifier = Modifier.width(Spacing.m),
            )
            Text(item.meaning.text, style = Typography.emphasis, color = SyllColors.ink, modifier = Modifier.weight(1f))
            if (item.learnedAt != null) {
                StatusPill(stringResource(R.string.learned), StatusPillTone.SUCCESS, Icons.Filled.Check)
            } else {
                StatusPill(stringResource(R.string.not_learned))
            }
            SymbolReplace(isExpanded) { open ->
                Icon(
                    if (open) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    tint = SyllColors.ink400,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (pronunciationsDiffer) {
            Box(Modifier.padding(start = Spacing.xl + Spacing.s, bottom = Spacing.xs)) {
                MeaningPronunciation(lemma, item.meaning.ipa, onPlay, isPlaying)
            }
        }
        // Opens with the `snap` spring; the content fades in place.
        AnimatedVisibility(
            isExpanded,
            enter = expandVertically(motion.animation(MotionTokens.snap), expandFrom = Alignment.Top) + fadeIn(motion.animation(MotionTokens.snap)),
            exit = shrinkVertically(motion.animation(MotionTokens.snap), shrinkTowards = Alignment.Top) + fadeOut(motion.animation(MotionTokens.snap)),
        ) {
            Column(Modifier.padding(start = Spacing.xl + Spacing.s, end = Spacing.m, bottom = Spacing.s)) {
                ExpandedMeaning(lemma, item, Modifier)
                // The learner's own edit can't be reported (backend docs/content-reports.md).
                if (item.edited != true) ReportLink(onReport)
            }
        }
    }
}

/** iOS `WordPronunciation(style: .compact)`: one meaning's IPA and its own play button. */
@Composable
private fun MeaningPronunciation(lemma: String, ipa: String?, onPlay: (() -> Unit)?, isPlaying: Boolean) {
    val speech = LocalSpeechPlayer.current
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        ipa?.let { Text(it, style = Typography.secondary, color = SyllColors.bodyInk) }
        val canPlay = onPlay != null || speech?.isAvailable == true
        val label = stringResource(if (canPlay) R.string.play_pronunciation else R.string.audio_unavailable)
        Box(
            Modifier
                .size(Spacing.minTouchTarget)
                .pressScaleClickable(enabled = canPlay) { onPlay?.invoke() ?: speech?.speak(lemma) }
                .semantics { stateDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            com.syll.designsystem.components.SpeakerWaveIcon(isPlaying, tint = if (canPlay) SyllColors.link else SyllColors.disabledInk, side = 18.dp)
        }
    }
}

@Composable
private fun ExpandedMeaning(lemma: String, item: WordDetail.Meaning, modifier: Modifier) {
    val sense = item.meaning
    val locale = LocalConfiguration.current.locales[0]
    val dates = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    fun date(instant: Instant) = dates.format(instant.atZone(ZoneId.systemDefault()))
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        sense.definition?.takeIf { it.isNotEmpty() }?.let {
            Field(stringResource(R.string.definition)) { Text(it, style = Typography.secondary, color = SyllColors.ink) }
        }
        sense.examples?.firstOrNull()?.let { example ->
            Field(stringResource(R.string.example)) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs - 2.dp)) {
                    Text(highlighted(example.text, lemma), style = Typography.secondary, color = SyllColors.ink)
                    example.translation?.takeIf { it.isNotEmpty() }?.let { ExampleTranslation(it) }
                }
            }
        }
        val learnedAt = item.learnedAt
        if (learnedAt != null) {
            item.memory?.let { memory ->
                Field(stringResource(R.string.memory)) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        MemoryStrengthRow(memory, ::date)
                        if (item.needsPractice == true) StatusPill(stringResource(R.string.needs_practice), StatusPillTone.ERROR)
                    }
                }
            }
            item.speaking?.let { Field(stringResource(R.string.speaking)) { SpeakingCell(it) } }
            Text(stringResource(R.string.learned_x, date(learnedAt)), style = Typography.micro, color = SyllColors.mutedInk)
        } else {
            Text(stringResource(R.string.this_meaning_hasn_t_been_learned_yet_no_progress), style = Typography.secondary, color = SyllColors.mutedInk)
        }
    }
}

@Composable
private fun Field(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs - 2.dp)) {
        Eyebrow(title, Modifier.semantics { heading() })
        content()
    }
}

/** iOS `MemoryStrengthRow`: a bar for the chance of recall now, and the next review date. */
@Composable
private fun MemoryStrengthRow(memory: MeaningMemory, date: (Instant) -> String) {
    val percent = (memory.retrievability * 100).roundToInt()
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(stringResource(R.string.x_x_likely_to_remember_now, percent), style = Typography.secondary, color = SyllColors.ink)
        LinearProgressIndicator(
            progress = { memory.retrievability.toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = if (memory.retrievability >= 0.9) SyllColors.successInk else SyllColors.selectedInk,
            trackColor = SyllColors.track,
            drawStopIndicator = {},
        )
        memory.nextReviewAt?.let { Text(stringResource(R.string.next_review_x, date(it)), style = Typography.micro, color = SyllColors.mutedInk) }
    }
}

/** A tinted cell: the speaking status, coloured by it, over the scores when Azure scored the takes. */
@Composable
private fun SpeakingCell(speaking: MeaningSpeaking) {
    val (background, ink) = when (speaking.status) {
        SpeakingStatus.FLUENT -> SyllColors.successSurface to SyllColors.successInk
        SpeakingStatus.PRACTICING -> SyllColors.selectedSurface to SyllColors.selectedInk
        else -> SyllColors.neutralSurface to SyllColors.ink600
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(Radii.chip))
            .padding(horizontal = Spacing.s, vertical = Spacing.xs)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(
                    when (speaking.status) {
                        SpeakingStatus.FLUENT -> R.string.fluent
                        SpeakingStatus.PRACTICING -> R.string.practicing
                        else -> R.string.not_tried_yet
                    },
                ),
                style = Typography.micro.copy(fontWeight = FontWeight.Bold),
                color = ink,
            )
            if (speaking.status == SpeakingStatus.FLUENT) Icon(Icons.Filled.Check, null, tint = ink, modifier = Modifier.size(12.dp))
        }
        speaking.lastScore?.let { last ->
            val best = (speaking.bestScore ?: last).roundToInt()
            Text(stringResource(R.string.last_score_x_best_x, last.roundToInt(), best), style = Typography.caption, color = ink.copy(alpha = 0.9f))
        }
    }
}
