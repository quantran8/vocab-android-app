package com.syll.features.words

import com.syll.network.WordMeaning
import com.syll.network.WordDetail
import com.syll.designsystem.tokens.Radii
import com.syll.designsystem.motion.SymbolReplace
import com.syll.designsystem.motion.MotionTokens
import com.syll.designsystem.motion.LocalMotion
import com.syll.designsystem.components.cardSurface
import com.syll.designsystem.components.StatusPillTone
import com.syll.designsystem.components.StatusPill
import com.syll.designsystem.components.Eyebrow
import com.syll.designsystem.components.CircleIconButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syll.R
import com.syll.app.LocalSpeechPlayer
import com.syll.designsystem.components.SpeakerWaveIcon
import com.syll.designsystem.components.pressScaleClickable
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography

/** iOS `VocabularyItem.localizedPartOfSpeech`: a value added on the backend still shows, just untranslated. */
@Composable
fun localizedPartOfSpeech(value: String): String = when (value) {
    "noun" -> stringResource(R.string.noun)
    "verb" -> stringResource(R.string.verb)
    "adjective" -> stringResource(R.string.adjective)
    "adverb" -> stringResource(R.string.adverb)
    "pronoun" -> stringResource(R.string.pronoun)
    "preposition" -> stringResource(R.string.preposition)
    "conjunction" -> stringResource(R.string.conjunction)
    "interjection" -> stringResource(R.string.interjection)
    "phrasal_verb" -> stringResource(R.string.phrasal_verb)
    else -> value.replace('_', ' ')
}

/** iOS `PartOfSpeechEyebrow`: a backend part of speech styled as an eyebrow. */
@Composable
fun PartOfSpeechEyebrow(value: String, modifier: Modifier = Modifier) {
    Text(
        localizedPartOfSpeech(value).uppercase(),
        style = Typography.eyebrow,
        color = SyllColors.mutedInk,
        modifier = modifier.semantics { heading() },
    )
}

/** iOS `PartOfSpeechGroup.grouping`: groups keep the order in which their part of speech first appears. */
fun <T> groupByPartOfSpeech(items: List<T>, partOfSpeech: (T) -> String): List<Pair<String, List<T>>> =
    items.groupBy(partOfSpeech).toList()

/** iOS `ExampleTranslation`. */
@Composable
fun ExampleTranslation(text: String) {
    val label = stringResource(R.string.translation)
    Text(text, style = Typography.secondary, color = SyllColors.mutedInk, modifier = Modifier.semantics { contentDescription = "$label: $text" })
}

/**
 * iOS `WordHeroHeader`: the word, centred, over its IPA and a 44 dp pronunciation button. Plays the recorded clip
 * when there is one ([playAudio]); otherwise the device speaks the word.
 */
@Composable
fun WordHeroHeader(
    lemma: String,
    ipa: String?,
    playAudio: (() -> Unit)?,
    isPlayingAudio: Boolean,
    showsPronunciation: Boolean = true,
) {
    val speech = LocalSpeechPlayer.current
    val canPlay = playAudio != null || speech?.isAvailable == true
    val isPlaying = if (playAudio == null) speech?.isSpeaking == true && speech.spokenText == lemma else isPlayingAudio
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            lemma,
            style = Typography.wordTitle.copy(letterSpacing = Typography.displayTracking.sp),
            color = SyllColors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        if (showsPronunciation) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                ipa?.let {
                    val label = stringResource(R.string.ipa_x, it)
                    Text(it, style = Typography.ipaHero, color = SyllColors.bodyInk, modifier = Modifier.semantics { contentDescription = label })
                }
                val label = stringResource(if (canPlay) R.string.play_pronunciation else R.string.audio_unavailable)
                val playing = stringResource(R.string.playing)
                Box(
                    Modifier
                        .size(Spacing.minTouchTarget)
                        .pressScaleClickable(enabled = canPlay) { playAudio?.invoke() ?: speech?.speak(lemma) }
                        .background(if (canPlay) SyllColors.selectedSurface else SyllColors.disabledSurface, CircleShape)
                        .semantics {
                            contentDescription = label
                            if (isPlaying) stateDescription = playing
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (canPlay) {
                        SpeakerWaveIcon(isPlaying, tint = SyllColors.link, side = 22.dp)
                    } else {
                        Icon(Icons.AutoMirrored.Outlined.VolumeOff, null, tint = SyllColors.disabledInk, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

/** iOS `OtherMeaningsLink`: under a Learn card, opens the word's other meanings for reference. */
@Composable
fun OtherMeaningsLink(count: Int, onClick: () -> Unit) {
    val label = stringResource(R.string.other_meanings_x, count)
    val hint = stringResource(R.string.for_reference_only_not_part_of_this_lesson_2)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = Spacing.xs)
            .defaultMinSize(minHeight = Spacing.minTouchTarget)
            .pressScaleClickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$label. $hint" },
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val style = Typography.secondary.copy(fontWeight = FontWeight.SemiBold)
        Icon(Icons.Outlined.Book, null, tint = SyllColors.link, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.other_meanings), style = style, color = SyllColors.link)
        StatusPill("$count", StatusPillTone.ACCENT)
        Spacer(Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = SyllColors.link, modifier = Modifier.size(18.dp))
    }
}

/** iOS `OtherMeaningsSheet`: reference sheet from a learn step, the word's other meanings outside the lesson. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtherMeaningsSheet(lemma: String, meanings: List<WordDetail.Meaning>, onDismiss: () -> Unit) {
    val motion = LocalMotion.current
    val expanded = remember { mutableStateListOf<String>().apply { meanings.firstOrNull()?.let { add(it.meaningId) } } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        shape = RoundedCornerShape(topStart = Radii.sheet, topEnd = Radii.sheet),
        containerColor = SyllColors.canvas,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Spacing.screenGutter)
                .padding(top = Spacing.s, bottom = Spacing.l),
        ) {
            Row(Modifier.padding(bottom = Spacing.m), horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Text(
                        stringResource(R.string.other_meanings_of_x, lemma),
                        style = Typography.screenTitle.copy(letterSpacing = Typography.tightTracking.sp),
                        color = SyllColors.ink,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(stringResource(R.string.for_reference_only_not_part_of_this_lesson), style = Typography.secondary, color = SyllColors.mutedInk)
                }
                CircleIconButton(Icons.Outlined.Close, stringResource(R.string.close), onClick = onDismiss)
            }
            groupByPartOfSpeech(meanings) { it.meaning.partOfSpeech }.forEach { (partOfSpeech, items) ->
                Column(Modifier.padding(bottom = Spacing.xs)) {
                    PartOfSpeechEyebrow(partOfSpeech, Modifier.padding(top = Spacing.m, bottom = Spacing.xxs))
                    items.forEach { item ->
                        val isOpen = item.meaningId in expanded
                        val state = stringResource(if (isOpen) R.string.expanded else R.string.collapsed)
                        Column(Modifier.padding(top = Spacing.s).fillMaxWidth().cardSurface().padding(horizontal = Spacing.m)) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = Spacing.minTouchTarget + Spacing.xs)
                                    .pressScaleClickable { if (isOpen) expanded.remove(item.meaningId) else expanded.add(item.meaningId) }
                                    .semantics(mergeDescendants = true) { stateDescription = state },
                                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(item.meaning.text, style = Typography.tile, color = SyllColors.ink, modifier = Modifier.weight(1f))
                                if (item.learnedAt != null) {
                                    StatusPill(stringResource(R.string.learned), StatusPillTone.SUCCESS, Icons.Filled.Check)
                                } else {
                                    StatusPill(stringResource(R.string.not_learned))
                                }
                                SymbolReplace(isOpen) { open ->
                                    Icon(if (open) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown, null, tint = SyllColors.mutedInk)
                                }
                            }
                            AnimatedVisibility(
                                isOpen,
                                enter = expandVertically(motion.animation(MotionTokens.snap), expandFrom = Alignment.Top) + fadeIn(motion.animation(MotionTokens.snap)),
                                exit = shrinkVertically(motion.animation(MotionTokens.snap), shrinkTowards = Alignment.Top) + fadeOut(motion.animation(MotionTokens.snap)),
                            ) {
                                MeaningFields(lemma, item.meaning, Modifier.padding(bottom = Spacing.s))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** iOS `MeaningFields(style: .quiet)`: definition and first example of one meaning. */
@Composable
fun MeaningFields(lemma: String, sense: WordMeaning.Sense, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        sense.definition?.takeIf { it.isNotEmpty() }?.let { definition ->
            Column(Modifier.padding(vertical = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Eyebrow(stringResource(R.string.definition), Modifier.semantics { heading() })
                Text(definition, style = Typography.body, color = SyllColors.ink)
            }
        }
        sense.examples?.firstOrNull()?.let { example ->
            Column(Modifier.padding(vertical = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Eyebrow(stringResource(R.string.in_context), Modifier.semantics { heading() })
                Text(com.syll.features.practice.highlighted(example.text, lemma), style = Typography.body, color = SyllColors.ink)
                example.translation?.takeIf { it.isNotEmpty() }?.let { ExampleTranslation(it) }
            }
        }
    }
}
