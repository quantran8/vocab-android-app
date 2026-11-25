package com.syll.features.challenge

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syll.R
import com.syll.app.LocalAppScope
import com.syll.app.LocalChallengeMapStore
import com.syll.app.LocalChallengeRunStore
import com.syll.app.LocalPracticeSessionStore
import com.syll.designsystem.components.PinnedActionBar
import com.syll.designsystem.components.PrimaryButton
import com.syll.designsystem.components.SecondaryButton
import com.syll.designsystem.components.cardSurface
import com.syll.designsystem.motion.symbolBounceOnce
import com.syll.designsystem.motion.LocalMotion
import com.syll.designsystem.motion.MotionTokens
import com.syll.designsystem.tokens.Radii
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography
import com.syll.domain.challenge.ChallengeNodeRef
import com.syll.domain.challenge.ChallengeRunResult
import com.syll.domain.practice.SessionMode
import com.syll.features.practice.results.CelebrationBadge
import com.syll.features.practice.results.entrance
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * iOS `ChallengeResultsView`: the server's verdict on a committed run. Shown only after the commit answered: never a
 * speculative win. The celebration is short and never holds up "Next Level".
 */
@Composable
fun ChallengeResultsView(outcome: ChallengeRunOutcome) {
    val run = LocalChallengeRunStore.current
    val map = LocalChallengeMapStore.current
    val practice = LocalPracticeSessionStore.current
    val appScope = LocalAppScope.current
    val motion = LocalMotion.current
    val locale = LocalConfiguration.current.locales[0]
    val result = outcome.result
    val celebrationEarned = outcome.isFirstClear || outcome.isNewBest
    /** The next chapter is short of words: offer the Daily Lesson instead of a next level. */
    val wordsNeeded = outcome.wordsNeeded
    var celebrates by remember { mutableStateOf(false) }
    var showsConfetti by remember { mutableStateOf(false) }
    var hasAppeared by remember { mutableStateOf(motion.reduceMotion) }

    LaunchedEffect(Unit) { hasAppeared = true }
    LaunchedEffect(Unit) {
        if (!celebrationEarned || motion.reduceMotion) return@LaunchedEffect
        celebrates = true
        delay(CELEBRATION_CONFETTI_CUE_MS)
        showsConfetti = true
        delay((MotionTokens.challengeCelebrationHold * 1000).toLong())
        celebrates = false
        showsConfetti = false
    }

    fun play(node: ChallengeNodeRef) {
        appScope.launch { run.play(node, map.level(node)) }
    }
    fun backToMap() = run.close()
    fun continueDaily() {
        run.close()
        appScope.launch { practice.start(SessionMode.DAILY) }
    }

    val isCleared = result.outcome == ChallengeRunResult.Outcome.CLEARED
    val chip = when {
        !isCleared -> R.string.not_cleared
        outcome.chapterCleared -> R.string.chapter_cleared
        outcome.isNewBest -> R.string.new_best
        outcome.isFirstClear -> R.string.level_cleared
        else -> R.string.level_complete
    }
    val headline = when {
        !isCleared -> stringResource(R.string.time_s_up)
        outcome.chapterCleared -> stringResource(R.string.chapter_x_done, outcome.node.chapterNo)
        outcome.isNewBest -> stringResource(R.string.new_best_2)
        else -> stringResource(R.string.nice_run)
    }
    val goal = run.plan?.goalMatches ?: result.correctMatches
    val next = outcome.nextNode
    val note: String? = when {
        !isCleared -> stringResource(R.string.x_of_x_matched_challenges_are_optional_and_your, result.correctMatches, goal)
        wordsNeeded != null -> stringResource(R.string.learn_x_more_words_to_unlock_chapter_x, wordsNeeded.remaining, wordsNeeded.chapterNo)
        outcome.isNewBest -> stringResource(R.string.best_x_x_stars, outcome.previousBestStars, result.bestStars)
        !outcome.isFirstClear && result.starsThisRun < result.bestStars -> stringResource(R.string.your_best_stays_at_x_stars, result.bestStars)
        next != null && next.chapterNo == outcome.node.chapterNo -> stringResource(R.string.level_x_is_open, next.levelNo)
        else -> null
    }

    val stats = buildList {
        add(R.string.matched to "${result.correctMatches}")
        add(R.string.mistakes to "${result.mistakes}")
        if (isCleared) add(R.string.time_s to ChallengeTimeFormat.elapsed(result.elapsedMs, locale))
        add(R.string.longest_combo to "${result.longestCombo}")
        add(R.string.score to ChallengeTimeFormat.whole(result.score, locale))
        add(R.string.best_score to ChallengeTimeFormat.whole(result.bestScore, locale))
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screenGutter + 4.dp)
                    .padding(top = Spacing.s, bottom = Spacing.l),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BrandBar()
                Spacer(Modifier.height(Spacing.xl))
                if (outcome.chapterCleared) {
                    Icon(
                        Icons.Filled.EmojiEvents, null, tint = SyllColors.reward,
                        modifier = Modifier
                            .padding(bottom = Spacing.m)
                            .size(56.dp)
                            .symbolBounceOnce(MotionTokens.resultSymbolBounce)
                            .clearAndSetSemantics { },
                    )
                } else if (motion.reduceMotion && celebrationEarned) {
                    Box(Modifier.padding(bottom = Spacing.l)) { CelebrationBadge() }
                }
                ChallengeChip(stringResource(chip), fill = if (isCleared) SyllColors.reward else SyllColors.neutralSurface)
                Text(
                    headline,
                    style = Typography.display.copy(letterSpacing = (-1.4).sp),
                    color = SyllColors.ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Spacing.s).semantics { heading() },
                )
                val chapter = if (outcome.chapterCleared) map.chapter(outcome.node.chapterNo) else null
                Column(Modifier.padding(top = Spacing.m), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    StarRow(result.starsThisRun, size = 34.dp)
                    if (chapter != null) {
                        Text(
                            stringResource(R.string.x_x_stars_in_this_chapter, chapter.starsEarned, chapter.maxStars),
                            style = Typography.micro.copy(fontWeight = FontWeight.SemiBold),
                            color = SyllColors.mutedInk,
                        )
                    }
                }
                if (note != null) {
                    Text(note, style = Typography.secondary, color = SyllColors.mutedInk, textAlign = TextAlign.Center, modifier = Modifier.padding(top = Spacing.m))
                }
                Column(Modifier.padding(top = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    stats.chunked(2).forEachIndexed { row, pair ->
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            pair.forEachIndexed { column, (label, value) ->
                                StatTile(
                                    label = stringResource(label),
                                    value = value,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .entrance(hasAppeared, MotionTokens.celebrationStatsDelay + (row * 2 + column) * MotionTokens.celebrationStagger),
                                )
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            PinnedActionBar {
                when {
                    !isCleared -> {
                        PrimaryButton(stringResource(R.string.try_again), onClick = { play(outcome.node) })
                        SecondaryButton(stringResource(R.string.map), onClick = ::backToMap)
                    }
                    next != null -> {
                        PrimaryButton(stringResource(R.string.next_level), onClick = { play(next) }, shimmers = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            SecondaryButton(stringResource(R.string.replay_level), onClick = { play(outcome.node) }, modifier = Modifier.weight(1f))
                            SecondaryButton(stringResource(R.string.map), onClick = ::backToMap, modifier = Modifier.weight(1f))
                        }
                    }
                    wordsNeeded != null -> {
                        PrimaryButton(stringResource(R.string.continue_daily), onClick = ::continueDaily)
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            SecondaryButton(stringResource(R.string.replay_level), onClick = { play(outcome.node) }, modifier = Modifier.weight(1f))
                            SecondaryButton(stringResource(R.string.map), onClick = ::backToMap, modifier = Modifier.weight(1f))
                        }
                    }
                    else -> {
                        PrimaryButton(stringResource(R.string.replay_level), onClick = { play(outcome.node) })
                        SecondaryButton(stringResource(R.string.map), onClick = ::backToMap)
                    }
                }
            }
        }
        AnimatedVisibility(celebrates, enter = fadeIn(), exit = fadeOut(motion.animation(MotionTokens.transition))) {
            SyllieCelebrationView()
        }
        if (showsConfetti) ConfettiBurst()
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.cardSurface(radius = Radii.tile).padding(Spacing.m).semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Text(value, style = Typography.screenTitle.copy(fontFeatureSettings = "tnum"), color = SyllColors.ink)
        Text(label, style = Typography.micro, color = SyllColors.mutedInk)
    }
}

/** iOS `ChallengeSandboxResultsView`: a Practice Lab round, counted locally, never sent. */
@Composable
fun ChallengeSandboxResultsView(summary: ChallengeSandboxSummary, modifier: Modifier = Modifier) {
    val run = LocalChallengeRunStore.current
    Column(
        modifier.fillMaxSize().padding(horizontal = Spacing.screenGutter).padding(bottom = Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Spacer(Modifier.weight(1f))
        ChallengeChip(stringResource(R.string.practice_round))
        Text(
            stringResource(if (summary.reachedGoal) R.string.all_matched else R.string.time_s_up),
            style = Typography.display.copy(letterSpacing = (-1.4).sp),
            color = SyllColors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.x_of_x_matched_x_mistakes_longest_combo_x, summary.correctMatches, summary.goal, summary.mistakes, summary.longestCombo),
            style = Typography.secondary,
            color = SyllColors.mutedInk,
            textAlign = TextAlign.Center,
        )
        Text(stringResource(R.string.not_scored_nothing_here_is_saved), style = Typography.micro, color = SyllColors.mutedInk)
        Spacer(Modifier.weight(1f))
        PrimaryButton(stringResource(R.string.play_again), onClick = {
            run.plan?.let { run.startSandbox(it, summary.isTimed) }
        })
        SecondaryButton(stringResource(R.string.done), onClick = { run.close() })
    }
}
