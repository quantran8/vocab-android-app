package com.syll.features.practice.results

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.KeyboardArrowRight
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
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.syll.R
import com.syll.domain.ranking.SessionEffort
import com.syll.features.ranking.ResultEffortCard
import com.syll.app.LocalLearningPlanStore
import com.syll.designsystem.components.ActionButtonKind
import com.syll.designsystem.components.IconBadge
import com.syll.designsystem.components.IconBadgeTone
import com.syll.designsystem.components.PinnedActionBar
import com.syll.designsystem.components.PrimaryButton
import com.syll.designsystem.components.SecondaryButton
import com.syll.designsystem.components.cardSurface
import com.syll.designsystem.components.pressScaleClickable
import com.syll.designsystem.motion.LocalMotion
import com.syll.designsystem.motion.MotionTokens
import com.syll.designsystem.motion.symbolBounceOnce
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography
import com.syll.domain.practice.CommitSummary
import com.syll.domain.practice.PracticeResult
import com.syll.domain.practice.ResultSection
import com.syll.domain.practice.ResultTile
import com.syll.domain.practice.ScopeEvents
import com.syll.features.plan.displayName
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * iOS `PracticeResultsView`: beat 2 of the end of a session, after the celebration. The "Summary" title, the result
 * tiles that have something to show, the LP card and the scope card. Takes plain
 * data so a lab can show it from fixtures.
 */
@Composable
fun PracticeResultsView(
    result: PracticeResult,
    scopeEvents: ScopeEvents?,
    onChooseTopics: (() -> Unit)?,
    /** Only for the fallback grid of a session saved before the result groups existed. */
    firstTryCount: Int,
    /** Random, Weak and Speaking: plan the same practice again. */
    onPracticeAgain: (() -> Unit)?,
    onDone: () -> Unit,
    /** LP and Tier across the session; null from a backend without ranking. */
    effort: SessionEffort? = null,
) {
    val motion = LocalMotion.current
    // Only a finished collection gets the card; a finished topic is not called out.
    val hasCompletion = scopeEvents?.collectionCompleted == true
    var hasAppeared by remember { mutableStateOf(motion.reduceMotion) }
    var sheetSection by remember { mutableStateOf<ResultSection?>(null) }
    // Numbers count up and bars grow once; with reduced motion they are simply there.
    LaunchedEffect(Unit) {
        delay((MotionTokens.celebrationStatsDelay * 1000).toLong())
        hasAppeared = true
    }
    val tiles = result.tiles

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.practiceGutter)
                .padding(top = Spacing.l, bottom = Spacing.l),
        ) {
            Text(
                stringResource(R.string.summary),
                style = Typography.display,
                color = SyllColors.ink,
                modifier = Modifier.fillMaxWidth().semantics { heading() },
            )
            if (tiles != null) {
                if (tiles.isNotEmpty()) {
                    TileRow(tiles, hasAppeared, Modifier.padding(top = Spacing.l + Spacing.xxs)) { sheetSection = it }
                    // The one way in to the sheet shown as a link; it opens at the top (the first tile's block).
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.xs)
                            .defaultMinSize(minHeight = Spacing.minTouchTarget)
                            .entrance(hasAppeared, MotionTokens.celebrationStatsDelay)
                            .pressScaleClickable { sheetSection = tiles[0].section },
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.see_session_details), style = Typography.secondary.copy(fontWeight = FontWeight.SemiBold), color = SyllColors.link)
                        Icon(Icons.Filled.KeyboardArrowRight, null, tint = SyllColors.link, modifier = Modifier.size(18.dp))
                    }
                }
            } else {
                LegacyResultGrid(result.summary, firstTryCount, hasAppeared, Modifier.padding(top = Spacing.l + Spacing.xxs))
            }
            effort?.let { ResultEffortCard(it, Modifier.padding(top = Spacing.s).entrance(hasAppeared, MotionTokens.celebrationStatsDelay)) }
            if (hasCompletion) ScopeCompletionCard(Modifier.padding(top = Spacing.s))
        }
        PinnedActionBar {
            if (hasCompletion && onChooseTopics != null) {
                PrimaryButton(stringResource(R.string.choose_another_collection), onClick = onChooseTopics)
                SecondaryButton(stringResource(R.string.later), onClick = onDone, kind = ActionButtonKind.OUTLINE)
            } else {
                PrimaryButton(stringResource(R.string.done), onClick = onDone)
            }
            if (!hasCompletion && onPracticeAgain != null) {
                SecondaryButton(stringResource(R.string.practice_again), onClick = onPracticeAgain)
            }
        }
    }

    sheetSection?.let { section -> SessionResultDetailSheet(result, section, onDismiss = { sheetSection = null }) }
}

/** Tiles of equal height and width; a part with nothing to report has no tile. Each rises in, staggered. */
@Composable
private fun TileRow(tiles: List<ResultTile>, hasAppeared: Boolean, modifier: Modifier, onTap: (ResultSection) -> Unit) {
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        tiles.forEachIndexed { index, tile ->
            ResultTileView(
                tile,
                revealed = hasAppeared,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .entrance(hasAppeared, MotionTokens.celebrationStatsDelay + index * MotionTokens.celebrationStagger),
                onTap = { onTap(tile.section) },
            )
        }
    }
}

/**
 * iOS's results entrance: fades and rises from `enterOffset` on the `entrance` spring after [delaySeconds].
 * Reduced motion: simply there.
 */
fun Modifier.entrance(shown: Boolean, delaySeconds: Double): Modifier = composed {
    val motion = LocalMotion.current
    val progress = remember { Animatable(if (motion.reduceMotion) 1f else 0f) }
    LaunchedEffect(shown) {
        if (!shown || motion.reduceMotion) return@LaunchedEffect
        delay((delaySeconds * 1000).toLong())
        launch { progress.animateTo(1f, MotionTokens.entrance.spec()) }
    }
    graphicsLayer {
        alpha = progress.value.coerceIn(0f, 1f)
        translationY = (1 - progress.value) * MotionTokens.enterOffset.dp.toPx()
    }
}

/** iOS `LegacyResultGrid`: the count grid for a commit without result groups (a session an older backend saved). */
@Composable
private fun LegacyResultGrid(summary: CommitSummary, firstTryCount: Int, hasAppeared: Boolean, modifier: Modifier) {
    val known = summary.knownMeanings ?: 0
    // Only the stats this session actually moved; zeros would just distract.
    val stats = listOf(
        R.string.exercises_done to (summary.practiceTasks ?: 0),
        R.string.right_first_time to firstTryCount,
        R.string.meanings_reviewed to (summary.reviewedMeanings ?: 0),
        R.string.new_words to (summary.newWordGoalSlots ?: 0) - known,
        R.string.words_you_already_knew to known,
    ).filter { it.second > 0 }
    // Two per row; a stat left on its own takes the full row.
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        stats.chunked(2).forEachIndexed { row, pair ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                pair.forEachIndexed { column, (label, value) ->
                    val index = row * 2 + column
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .entrance(hasAppeared, MotionTokens.celebrationStatsDelay + index * MotionTokens.celebrationStagger)
                            .cardSurface()
                            .padding(Spacing.m)
                            .semantics(mergeDescendants = true) {},
                        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                    ) {
                        Text("$value", style = Typography.stat.copy(fontFeatureSettings = "tnum"), color = SyllColors.ink)
                        Text(stringResource(label), style = Typography.micro, color = SyllColors.mutedInk)
                    }
                }
            }
        }
    }
}

/** iOS `ScopeCompletionCard`: shown under the results when this session finished the whole collection. */
@Composable
private fun ScopeCompletionCard(modifier: Modifier) {
    val plan = LocalLearningPlanStore.current
    val name = plan.current.collection?.displayName().orEmpty()
    Row(
        modifier.fillMaxWidth().cardSurface().padding(Spacing.m).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.symbolBounceOnce(MotionTokens.resultSymbolBounce)) {
            IconBadge(Icons.Filled.EmojiEvents, IconBadgeTone.REWARD)
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(stringResource(R.string.you_ve_learned_every_word_in_x_2, name), style = Typography.emphasis, color = SyllColors.ink)
            Text(stringResource(R.string.your_learned_words_will_still_be_reviewed_choose), style = Typography.secondary, color = SyllColors.mutedInk)
        }
    }
}
