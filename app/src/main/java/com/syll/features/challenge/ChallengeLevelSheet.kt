package com.syll.features.challenge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.syll.R
import com.syll.app.LocalLearnerStore
import com.syll.designsystem.components.Eyebrow
import com.syll.designsystem.components.PrimaryButton
import com.syll.designsystem.components.SecondaryButton
import com.syll.designsystem.tokens.Radii
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography
import com.syll.domain.challenge.ChallengeChapter
import com.syll.domain.challenge.ChallengeLevel
import com.syll.domain.challenge.ChallengeNodeRef
import java.util.Locale

/**
 * iOS `ChallengeLevelSheet`: a level's goal and star rules (from the map; the run plan has the final say), personal
 * best, and Play. Locked levels explain how to open them. A Material bottom sheet with its drag handle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChallengeLevelSheet(
    node: ChallengeNodeRef,
    chapter: ChallengeChapter?,
    level: ChallengeLevel?,
    onPlay: () -> Unit,
    /** The untimed familiarization round (no stars, no unlocks), offered to TalkBack and switch access users. */
    onPracticeUntimed: () -> Unit,
    onDismiss: () -> Unit,
) {
    val assistive = rememberAssistiveTechEnabled()
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
                .padding(horizontal = Spacing.screenGutter)
                .padding(top = Spacing.s, bottom = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Eyebrow(stringResource(R.string.chapter_x_level_x, node.chapterNo, node.levelNo).uppercase())
                Text(
                    stringResource(R.string.match_madness),
                    style = Typography.display,
                    color = SyllColors.ink,
                    modifier = Modifier.semantics { heading() },
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(stringResource(R.string.match_each_word_to_its_meaning_before_time_runs), style = Typography.body, color = SyllColors.ink)
                val goal = level?.goalMatches
                val timeLimitMs = level?.timeLimitMs
                if (goal != null && timeLimitMs != null) {
                    Text(stringResource(R.string.match_x_pairs_in_x_seconds, goal, timeLimitMs / 1000), style = Typography.secondary, color = SyllColors.mutedInk)
                }
            }

            val criteria = level?.starCriteria
            val timeLimitMs = level?.timeLimitMs
            if (criteria != null && timeLimitMs != null) StarRules(criteria, timeLimitMs)

            if (level != null && level.state == ChallengeLevel.State.CLEARED) Best(level)

            localeNote(chapter)?.let { Text(it, style = Typography.micro, color = SyllColors.mutedInk) }

            if (level?.isPlayable == true) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    PrimaryButton(stringResource(R.string.play), onClick = onPlay, shimmers = true)
                    if (assistive) {
                        SecondaryButton(stringResource(R.string.practise_untimed), onClick = onPracticeUntimed)
                        Text(stringResource(R.string.an_untimed_practice_round_with_example_words_it), style = Typography.micro, color = SyllColors.mutedInk)
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, null, tint = SyllColors.mutedInk, modifier = Modifier.size(16.dp))
                    Text(
                        stringResource(R.string.clear_the_previous_level_to_unlock),
                        style = Typography.secondary.copy(fontWeight = FontWeight.SemiBold),
                        color = SyllColors.mutedInk,
                    )
                }
            }
        }
    }
}

@Composable
private fun Best(level: ChallengeLevel) {
    Row(
        Modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.best), style = Typography.secondary.copy(fontWeight = FontWeight.Bold), color = SyllColors.mutedInk)
        StarRow(level.bestStars, size = 18.dp)
        if (level.bestScore > 0) {
            Text(stringResource(R.string.x_points, level.bestScore), style = Typography.secondary.copy(fontFeatureSettings = "tnum"), color = SyllColors.ink)
        }
    }
}

/** "vi-VN" and "vi" are the same meaning language. */
private fun languageCode(identifier: String): String = identifier.lowercase().split('-', '_').firstOrNull().orEmpty()

/** A chapter keeps the meaning language it was frozen in, even after the setting changed. */
@Composable
private fun localeNote(chapter: ChallengeChapter?): String? {
    val learner = LocalLearnerStore.current
    val frozenLocale = chapter?.explanationLocale ?: return null
    val currentLocale = learner.profile?.settings?.explanationLocale ?: return null
    val frozen = languageCode(frozenLocale)
    if (frozen == languageCode(currentLocale)) return null
    val appLocale = LocalConfiguration.current.locales[0]
    val name = Locale.forLanguageTag(frozen).getDisplayLanguage(appLocale).ifEmpty { frozen }
    return stringResource(R.string.this_chapter_uses_x_meanings, name)
}
