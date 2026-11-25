package com.syll.features.ranking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SentimentVerySatisfied
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syll.R
import com.syll.designsystem.components.CardDivider
import com.syll.designsystem.components.IconBadge
import com.syll.designsystem.components.IconBadgeTone
import com.syll.designsystem.components.ProgressTrack
import com.syll.designsystem.components.SyllieMood
import com.syll.designsystem.components.cardSurface
import com.syll.designsystem.motion.LocalMotion
import com.syll.designsystem.motion.MotionTokens
import com.syll.designsystem.motion.symbolBounceOnce
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography
import com.syll.domain.ranking.NextTier
import com.syll.domain.ranking.SessionEffort
import com.syll.domain.ranking.TierBadge
import com.syll.domain.ranking.TierKey
import com.syll.domain.ranking.TierSummary
import com.syll.features.practice.SyllieReactionView

// region Tier presentation

/** iOS `TierKey.name`. */
val TierKey.nameRes: Int
    get() = when (this) {
        TierKey.FIRST_STEPS -> R.string.first_steps
        TierKey.IN_MOTION -> R.string.in_motion
        TierKey.BUILDING_UP -> R.string.building_up
        TierKey.STEADY_EFFORT -> R.string.steady_effort
        TierKey.COMMITTED -> R.string.committed
        TierKey.DEDICATED -> R.string.dedicated
        TierKey.LASTING_MARK -> R.string.lasting_mark
        TierKey.UNKNOWN -> R.string.new_milestone
    }

/** iOS `TierKey.story`: recognises the effort; never claims a level of English or words remembered. */
val TierKey.storyRes: Int
    get() = when (this) {
        TierKey.FIRST_STEPS -> R.string.you_ve_started_putting_effort_into_your_learning
        TierKey.IN_MOTION -> R.string.your_sessions_are_adding_up_into_your_first_stre
        TierKey.BUILDING_UP -> R.string.small_sessions_have_added_up_to_a_clear_achievem
        TierKey.STEADY_EFFORT -> R.string.you_ve_learned_on_many_different_days_at_your_ow
        TierKey.COMMITTED -> R.string.you_ve_put_in_real_effort_and_keep_building_this
        TierKey.DEDICATED -> R.string.a_milestone_for_the_long_stretch_you_ve_given_to
        TierKey.LASTING_MARK -> R.string.your_effort_has_become_a_lasting_mark_on_your_jo
        TierKey.UNKNOWN -> R.string.keep_going_at_your_own_pace
    }

/** iOS `TierKey.systemImage`: stand-in art until the Tier visuals are drawn. */
val TierKey.icon: ImageVector
    get() = when (this) {
        TierKey.FIRST_STEPS -> Icons.AutoMirrored.Filled.DirectionsWalk
        TierKey.IN_MOTION -> Icons.Filled.Air
        TierKey.BUILDING_UP -> Icons.Filled.Layers
        TierKey.STEADY_EFFORT -> Icons.Filled.CalendarMonth
        TierKey.COMMITTED -> Icons.Filled.LocalFireDepartment
        TierKey.DEDICATED -> Icons.Filled.Landscape
        TierKey.LASTING_MARK -> Icons.Filled.Verified
        TierKey.UNKNOWN -> Icons.Filled.AutoAwesome
    }

val TierKey.mood: SyllieMood
    get() = when (this) {
        TierKey.FIRST_STEPS, TierKey.IN_MOTION, TierKey.UNKNOWN -> SyllieMood.HAPPY
        TierKey.BUILDING_UP, TierKey.STEADY_EFFORT -> SyllieMood.HAPPY_PUFF
        TierKey.COMMITTED, TierKey.DEDICATED, TierKey.LASTING_MARK -> SyllieMood.PROUD
    }

// endregion

// region Avatar

/** iOS `AvatarStyle`: avatar keys come from the server's allowlist; unknown keys still draw something. */
object AvatarStyle {
    private val palette: Map<String, Pair<Color, ImageVector>> = mapOf(
        "syllie_sunny" to (SyllColors.reward to Icons.Filled.WbSunny),
        "syllie_sleepy" to (SyllColors.selectedSurface to Icons.Filled.Bedtime),
        "syllie_curious" to (SyllColors.successSurface to Icons.Filled.Search),
        "syllie_brave" to (SyllColors.errorSurface to Icons.Filled.Shield),
        "syllie_calm" to (SyllColors.selectedSurface to Icons.Filled.Eco),
        "syllie_cheerful" to (SyllColors.reward to Icons.Filled.SentimentVerySatisfied),
        "syllie_thinker" to (SyllColors.neutralSurface to Icons.Filled.Lightbulb),
        "syllie_explorer" to (SyllColors.successSurface to Icons.Filled.Map),
        "syllie_reader" to (SyllColors.selectedSurface to Icons.AutoMirrored.Filled.MenuBook),
        "syllie_artist" to (SyllColors.errorSurface to Icons.Filled.Palette),
        "syllie_sporty" to (SyllColors.successSurface to Icons.AutoMirrored.Filled.DirectionsRun),
        "syllie_starry" to (SyllColors.reward to Icons.Filled.Star),
    )

    fun background(key: String): Color = palette[key]?.first ?: SyllColors.neutralSurface
    fun symbol(key: String): ImageVector = palette[key]?.second ?: Icons.Filled.Person
}

/**
 * iOS `AvatarView`: a learner's chosen avatar from the server allowlist. Stand-in art until the Syllie set is drawn.
 * Without a key it shows [initial] (Blue 100 / Blue 700). [cornerRadius] null draws a circle.
 */
@Composable
fun AvatarView(key: String, modifier: Modifier = Modifier, size: Dp = Spacing.iconBadge, cornerRadius: Dp? = null, initial: String? = null) {
    val shape: Shape = cornerRadius?.let { RoundedCornerShape(it) } ?: CircleShape
    if (key.isEmpty() && !initial.isNullOrEmpty()) {
        Box(modifier.size(size).background(SyllColors.blue100, shape).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
            Text(
                initial.take(1).uppercase(),
                style = Typography.body.copy(fontSize = (size.value * 0.4f).sp, fontWeight = FontWeight.ExtraBold),
                color = SyllColors.blue700,
            )
        }
    } else {
        Box(modifier.size(size).background(AvatarStyle.background(key), shape).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
            Icon(AvatarStyle.symbol(key), null, tint = SyllColors.ink, modifier = Modifier.size(size * 0.45f))
        }
    }
}

// endregion

/** iOS `ProfileTierCard`: current Tier, LP toward the next one and a Sun progress bar. */
@Composable
fun ProfileTierCard(summary: TierSummary, next: NextTier?, modifier: Modifier = Modifier) {
    val key = summary.key.takeIf { summary.level > 0 }
    Column(
        modifier.cardSurface().padding(Spacing.m).semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(key?.icon ?: Icons.Outlined.EmojiEvents, if (key == null) IconBadgeTone.NEUTRAL else IconBadgeTone.REWARD)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(key?.nameRes ?: R.string.no_tier_yet), style = Typography.emphasis, color = SyllColors.ink)
                Text(
                    if (next != null) stringResource(R.string.progress_to_x, stringResource((next.key ?: TierKey.UNKNOWN).nameRes)) else stringResource(R.string.current_tier),
                    style = Typography.micro,
                    color = SyllColors.mutedInk,
                )
            }
            if (next != null) {
                Text(
                    stringResource(R.string.x_x_lp, summary.lifetimeLP, next.minLifetimeLP),
                    style = Typography.secondary.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                    color = SyllColors.ink,
                    maxLines = 1,
                )
            }
        }
        if (next != null) {
            val fraction = if (next.minLifetimeLP > 0) minOf(summary.lifetimeLP.toFloat() / next.minLifetimeLP, 1f) else 1f
            ProgressTrack(fraction, height = Spacing.s - 2.dp, color = SyllColors.reward)
        } else if (key != null) {
            Text(stringResource(R.string.you_ve_reached_the_highest_tier_your_lp_keeps_ad), style = Typography.micro, color = SyllColors.mutedInk)
        }
    }
}

/** iOS `TierBadgeLabel`: small lifetime-Tier marker for a leaderboard row. Level 0 shows nothing. */
@Composable
fun TierBadgeLabel(badge: TierBadge) {
    val key = badge.key ?: return
    if (badge.level <= 0) return
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
        Icon(key.icon, null, tint = SyllColors.mutedInk, modifier = Modifier.size(13.dp))
        Text(stringResource(key.nameRes), style = Typography.micro.copy(fontWeight = FontWeight.SemiBold), color = SyllColors.mutedInk)
    }
}

/**
 * iOS `ResultEffortCard`: "+N LP" with the lifetime total, one chip per source, and the Tier this session unlocked.
 * Never a rank change.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResultEffortCard(effort: SessionEffort, modifier: Modifier = Modifier) {
    val motion = LocalMotion.current
    Column(
        modifier.cardSurface().padding(Spacing.m).semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Bolt, null, tint = SyllColors.reward,
                    modifier = Modifier.size(26.dp).symbolBounceOnce(MotionTokens.resultSymbolBounce, enabled = effort.earned > 0),
                )
                Text(
                    "+${effort.earned} LP",
                    style = Typography.screenTitle.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = Typography.tightTracking.sp, fontFeatureSettings = "tnum"),
                    color = SyllColors.ink,
                    modifier = Modifier.weight(1f),
                )
                Text(stringResource(R.string.x_lp_in_total, effort.lifetimeLP), style = Typography.micro, color = SyllColors.mutedInk)
            }
            if (effort.earned == 0) {
                Text(stringResource(R.string.each_word_earns_lp_a_limited_number_of_times_a_d), style = Typography.micro, color = SyllColors.mutedInk)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    val parts = effort.breakdown
                    if (parts.newMeaningLP > 0) SourceChip(stringResource(R.string.new_words_x, parts.newMeaningLP))
                    if (parts.dueReviewLP > 0) SourceChip(stringResource(R.string.reviews_x, parts.dueReviewLP))
                    if (parts.extraPracticeLP > 0) SourceChip(stringResource(R.string.extra_practice_x, parts.extraPracticeLP))
                }
            }
        }
        val key = effort.celebratedTierUp?.key
        if (key != null) {
            CardDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.Top) {
                SyllieReactionView(if (motion.reduceMotion) SyllieMood.IDLE else key.mood, Modifier.size(64.dp, 56.dp).clearAndSetSemantics { })
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Text(stringResource(R.string.you_unlocked_x, stringResource(key.nameRes)), style = Typography.emphasis, color = SyllColors.ink)
                    Text(stringResource(key.storyRes), style = Typography.secondary, color = SyllColors.mutedInk)
                }
            }
        }
    }
}

/** A source chip: Ink 100 pill, Ink 700 bold caption. */
@Composable
private fun SourceChip(text: String) {
    Box(
        Modifier.defaultMinSize(minHeight = 24.dp).background(SyllColors.neutralSurface, CircleShape).padding(horizontal = Spacing.s - 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Typography.caption.copy(fontWeight = FontWeight.Bold), color = SyllColors.ink700, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** iOS Home `lpPill`: the learner's lifetime LP, the only number at the top of Home. */
@Composable
fun LifetimeLPPill(lp: Int, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.x_lp, lp)
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    Row(
        modifier
            .height(Spacing.l + Spacing.xs)
            .background(SyllColors.rewardSurface, CircleShape)
            .padding(horizontal = Spacing.s)
            .clearAndSetSemantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Bolt, null, tint = SyllColors.reward, modifier = Modifier.size(14.dp))
        com.syll.designsystem.motion.RollingNumber(
            value = lp,
            text = { java.text.NumberFormat.getIntegerInstance(locale).format(it) },
            style = Typography.secondary.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
            color = SyllColors.rewardInk,
        )
    }
}
