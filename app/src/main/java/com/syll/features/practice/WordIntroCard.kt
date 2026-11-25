package com.syll.features.practice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syll.R
import com.syll.designsystem.components.SpeakerWaveIcon
import com.syll.designsystem.components.CardDivider
import com.syll.designsystem.components.SpeakerWaveIcon
import com.syll.designsystem.components.Eyebrow
import com.syll.designsystem.components.SpeakerWaveIcon
import com.syll.designsystem.components.cardSurface
import com.syll.designsystem.components.SpeakerWaveIcon
import com.syll.designsystem.components.pressScaleClickable
import com.syll.designsystem.motion.LocalMotion
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography
import com.syll.domain.practice.LearnCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * iOS `WordIntroContent` (`.card` style): the word hero with its part of speech, IPA and audio, then one card holding
 * the meaning and the example; the English definition opens in place behind "See details".
 * The hero's six iOS motion recipes are a later polish; here the letters cascade in.
 */
@Composable
fun WordIntroCard(card: LearnCard, playAudio: (() -> Unit)?, isPlayingAudio: Boolean = false) {
    var showsDetails by rememberSaveable(card.word) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(bottom = Spacing.l + Spacing.xxs),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            LetterCascade(card.word)
            card.partOfSpeech?.let {
                Text(it.replace('_', ' '), style = Typography.secondary.copy(fontStyle = FontStyle.Italic), color = SyllColors.mutedInk)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                card.ipa?.let { ipa ->
                    val label = stringResource(R.string.ipa_x, ipa)
                    Text(ipa, style = Typography.body, color = SyllColors.bodyInk, modifier = Modifier.semantics { contentDescription = label })
                }
                val playLabel = stringResource(if (playAudio != null) R.string.play_pronunciation else R.string.audio_unavailable)
                Box(
                    Modifier
                        .size(Spacing.minTouchTarget)
                        .pressScaleClickable(enabled = playAudio != null) { playAudio?.invoke() }
                        .semantics { contentDescription = playLabel }
                        .background(if (playAudio != null) SyllColors.selectedSurface else SyllColors.disabledSurface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    SpeakerWaveIcon(isPlayingAudio, tint = if (playAudio != null) SyllColors.link else SyllColors.disabledInk, side = 22.dp)
                }
            }
        }

        Column(Modifier.cardSurface().padding(horizontal = Spacing.m + Spacing.xxs, vertical = Spacing.xxs)) {
            val meaningLabel = stringResource(R.string.vietnamese_meaning_x, card.meaning)
            Text(
                card.meaning,
                style = Typography.wordMeaning.copy(letterSpacing = Typography.tightTracking.sp),
                color = SyllColors.ink,
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.m).semantics { contentDescription = meaningLabel },
            )
            card.examples?.firstOrNull()?.takeIf { it.text.isNotEmpty() }?.let { example ->
                CardDivider()
                Column(Modifier.fillMaxWidth().padding(vertical = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(highlighted(example.text, card.word), style = Typography.body, color = SyllColors.ink700)
                    example.translation?.takeIf { it.isNotEmpty() }?.let {
                        Text(it, style = Typography.secondary, color = SyllColors.mutedInk)
                    }
                }
            }
            card.definition?.let { definition ->
                AnimatedVisibility(showsDetails, enter = fadeIn(), exit = fadeOut()) {
                    Column {
                        CardDivider()
                        Column(Modifier.fillMaxWidth().padding(vertical = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs - 2.dp)) {
                            Eyebrow(stringResource(R.string.definition))
                            Text(definition, style = Typography.body, color = SyllColors.ink700)
                        }
                    }
                }
                CardDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = Spacing.minTouchTarget + Spacing.xxs)
                        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { showsDetails = !showsDetails },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(if (showsDetails) R.string.show_less else R.string.see_details),
                        style = Typography.secondary.copy(fontWeight = FontWeight.SemiBold),
                        color = SyllColors.mutedInk,
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(
                        if (showsDetails) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                        contentDescription = null,
                        tint = SyllColors.mutedInk,
                    )
                }
            }
        }
    }
}

/** The word, its letters dropping in one after another (iOS `letterCascade`). Reduced motion shows it at once. */
@Composable
private fun LetterCascade(word: String) {
    val motion = LocalMotion.current
    val letters = remember(word) { word.map { it.toString() } }
    val progress = remember(word) { letters.map { Animatable(if (motion.reduceMotion) 1f else 0f) } }
    LaunchedEffect(word) {
        if (motion.reduceMotion) return@LaunchedEffect
        letters.indices.forEach { index ->
            launch { progress[index].animateTo(1f, tween(260)) }
            delay(45)
        }
    }
    Row(Modifier.semantics(mergeDescendants = true) { contentDescription = word }) {
        letters.forEachIndexed { index, letter ->
            val value = progress[index].value
            Text(
                letter,
                style = Typography.wordHero,
                color = SyllColors.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(value).graphicsLayer { translationY = (1 - value) * -24f },
            )
        }
    }
}

/** The example sentence with the word in bold Ink. */
private fun highlighted(sentence: String, term: String) = buildAnnotatedString {
    val start = sentence.indexOf(term, ignoreCase = true)
    if (start < 0) {
        append(sentence)
        return@buildAnnotatedString
    }
    append(sentence.substring(0, start))
    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = SyllColors.ink)) { append(sentence.substring(start, start + term.length)) }
    append(sentence.substring(start + term.length))
}
