package com.syll.domain.ranking

import com.syll.domain.Iso8601InstantSerializer
import com.syll.domain.LenientEnumSerializer
import kotlinx.serialization.Serializable
import java.time.Instant

// Wire models for LP, lifetime Tier and the weekly ranking: backend docs/FRONTEND_FLOW.md §14.
// Every field an older backend lacks is optional, so a server without ranking still decodes.

/** iOS `TierKey`: stable server ids; names, stories and art live on the client (spec §3.2). */
@Serializable(with = TierKey.Serializer::class)
enum class TierKey(val raw: String) {
    FIRST_STEPS("first_steps"),
    IN_MOTION("in_motion"),
    BUILDING_UP("building_up"),
    STEADY_EFFORT("steady_effort"),
    COMMITTED("committed"),
    DEDICATED("dedicated"),
    LASTING_MARK("lasting_mark"),
    UNKNOWN("unknown");

    object Serializer : LenientEnumSerializer<TierKey>("TierKey", entries.toTypedArray(), { it.raw }, UNKNOWN)
}

@Serializable
data class TierBadge(
    val level: Int,
    /** Null before the first Tier: never shown as "Tier 0". */
    val key: TierKey? = null,
)

@Serializable
data class NextTier(
    val level: Int,
    val key: TierKey? = null,
    val minLifetimeLP: Int,
    val minLifetimeActiveDays: Int,
    /** Only in `GET /me/tier` and `/me/ranking`; the commit response leaves them out. */
    val remainingLP: Int? = null,
    val remainingActiveDays: Int? = null,
)

/** The light Tier figures `GET /me` carries for Profile. */
@Serializable
data class TierSummary(val level: Int, val key: TierKey? = null, val lifetimeLP: Int, val lifetimeActiveDays: Int)

@Serializable
data class TierMilestone(
    val level: Int,
    val key: TierKey? = null,
    @Serializable(with = Iso8601InstantSerializer::class) val unlockedAt: Instant,
)

/** `GET /me/tier`. */
@Serializable
data class TierDetail(
    val level: Int,
    val key: TierKey? = null,
    val lifetimeLP: Int,
    val lifetimeActiveDays: Int,
    val nextTier: NextTier? = null,
    val minQualifiedDayLP: Int? = null,
    val milestones: List<TierMilestone> = emptyList(),
) {
    val summary: TierSummary get() = TierSummary(level, key, lifetimeLP, lifetimeActiveDays)
}

// Commit

@Serializable
data class LPBreakdown(val newMeaningLP: Int = 0, val dueReviewLP: Int = 0, val extraPracticeLP: Int = 0) {
    operator fun plus(other: LPBreakdown) = LPBreakdown(
        newMeaningLP + other.newMeaningLP,
        dueReviewLP + other.dueReviewLP,
        extraPracticeLP + other.extraPracticeLP,
    )
}

@Serializable
data class CommitLP(val earned: Int, val breakdown: LPBreakdown? = null, val weeklyLP: Int, val lifetimeLP: Int)

@Serializable
data class TierUp(
    val level: Int,
    val key: TierKey? = null,
    @Serializable(with = Iso8601InstantSerializer::class) val unlockedAt: Instant,
)

@Serializable
data class CommitTier(
    val level: Int,
    val key: TierKey? = null,
    val lifetimeActiveDays: Int,
    val tierUps: List<TierUp> = emptyList(),
    val nextTier: NextTier? = null,
)

@Serializable
data class CommitRanking(val weeklyLP: Int, val listed: Boolean)

/**
 * iOS `SessionEffort`: LP and Tier across one session screen. A daily lesson can commit twice (review, then new
 * words), so the results add both commits up and keep the latest totals.
 */
data class SessionEffort(
    val earned: Int = 0,
    val breakdown: LPBreakdown = LPBreakdown(),
    val lifetimeLP: Int = 0,
    val tier: CommitTier? = null,
    val tierUps: List<TierUp> = emptyList(),
) {
    fun adding(lp: CommitLP, tier: CommitTier?): SessionEffort = copy(
        earned = earned + lp.earned,
        breakdown = breakdown + (lp.breakdown ?: LPBreakdown()),
        lifetimeLP = lp.lifetimeLP,
        tier = tier ?: this.tier,
        tierUps = tierUps + (tier?.tierUps ?: emptyList()),
    )

    /** The highest Tier this session unlocked: the one to celebrate. */
    val celebratedTierUp: TierUp? get() = tierUps.maxByOrNull { it.level }
}

// Ranking

@Serializable(with = RankingPositionKind.Serializer::class)
enum class RankingPositionKind(val raw: String) {
    RANK("rank"), PERCENTILE("percentile"), UNRANKED("unranked");

    object Serializer : LenientEnumSerializer<RankingPositionKind>("RankingPositionKind", entries.toTypedArray(), { it.raw }, UNRANKED)
}

@Serializable
data class RankingPosition(val kind: RankingPositionKind, val rank: Int? = null, val percentile: Int? = null, val estimated: Boolean? = null)

/** The Tier block inside `/me/ranking`: like [TierDetail] without milestones. */
@Serializable
data class TierDetailSummary(
    val level: Int,
    val key: TierKey? = null,
    val lifetimeLP: Int,
    val lifetimeActiveDays: Int,
    val nextTier: NextTier? = null,
)

/** `GET /me/ranking`. */
@Serializable
data class MyRanking(
    val weekStart: String,
    @Serializable(with = Iso8601InstantSerializer::class) val weekEndsAt: Instant,
    val weeklyLP: Int,
    val listed: Boolean,
    val position: RankingPosition? = null,
    val tier: TierDetailSummary,
    /** Listed learners this week ("#42 / 120"). Not sent by the backend yet. */
    val totalListed: Int? = null,
)

@Serializable
data class RankingTierThreshold(val level: Int, val key: TierKey? = null, val minLifetimeLP: Int, val minLifetimeActiveDays: Int)

/** `GET /ranking/meta`. */
@Serializable
data class RankingMeta(
    val weekStart: String,
    @Serializable(with = Iso8601InstantSerializer::class) val weekEndsAt: Instant,
    val rankingEnabled: Boolean,
    val tiers: List<RankingTierThreshold> = emptyList(),
    val minQualifiedDayLP: Int? = null,
    val avatarKeys: List<String> = emptyList(),
    val reportReasons: List<String> = emptyList(),
    val usernameChangeDays: Int? = null,
)

enum class LeaderboardView(val raw: String) { TOP("top"), AROUND("around") }

@Serializable
data class LeaderboardRow(
    val rank: Int? = null,
    val username: String,
    val avatarKey: String,
    val weeklyLP: Int,
    val lifetimeTier: TierBadge,
    val isMe: Boolean,
) {
    val id: String get() = username
}

@Serializable
data class Leaderboard(
    val weekStart: String,
    @Serializable(with = Iso8601InstantSerializer::class) val weekEndsAt: Instant,
    val rows: List<LeaderboardRow>,
)

@Serializable
data class RankingWeek(val weekStart: String, val weeklyLP: Int, val rank: Int? = null)

@Serializable
data class RankingHistory(val currentWeek: CurrentWeek, val weeks: List<RankingWeek>) {
    @Serializable
    data class CurrentWeek(val weekStart: String, val weeklyLP: Int)
}

/** `POST /ranking/reports` reasons; the server's list in [RankingMeta.reportReasons] decides which show. */
enum class ReportReason(val raw: String) {
    OFFENSIVE_NAME("offensive_name"),
    IMPERSONATION("impersonation"),
    SUSPICIOUS_ACTIVITY("suspicious_activity"),
    OTHER("other"),
}

/** `PUT /me/profile` is strict: null fields are omitted. */
data class PublicProfileUpdate(val username: String? = null, val avatarKey: String? = null)

@Serializable
data class PublicProfile(
    val username: String,
    val avatarKey: String,
    @Serializable(with = Iso8601InstantSerializer::class) val usernameChangeAvailableAt: Instant? = null,
)

/** The server's username rule (iOS `EditPublicProfileSheet.isWellFormed`), checked only to enable Save. */
object PublicName {
    private val pattern = Regex("[a-z][a-z0-9_.]{2,19}")

    fun isWellFormed(name: String): Boolean = pattern.matches(name) && ".." !in name
}
