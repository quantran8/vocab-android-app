package com.syll.network

import com.syll.domain.LenientEnumSerializer

import com.syll.domain.CatalogEntry
import com.syll.domain.CefrLevel
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class UserSettings(
    val explanationLocale: String,
    val uiLocale: String,
    val timeZone: String,
    val dailyNewWordGoal: Int,
    /** Sessions per checkpoint challenge (a Journey segment). Null from a backend that predates it. */
    val weeklySessionGoal: Int? = null,
    /** Opt-in: fewer new words while the review backlog is large. */
    val autoBalanceNewWords: Boolean? = null,
    /** Public on the weekly leaderboard; null from a backend without ranking. */
    val leaderboardVisible: Boolean? = null,
    /** Reviews per daily session; null follows the new-word goal. */
    val reviewsPerSession: Int? = null,
    /** Opt-in recordings in Daily; null from an older backend (off). */
    val dailySpeaking: Boolean? = null,
)

@Serializable
data class LearningScope(val collectionId: String, val topicIds: List<String>)

@Serializable
data class ReviewPause(val collectionId: String)

/** iOS `PremiumPlan` (FRONTEND_FLOW §16). The server decides who is Premium. */
@Serializable
data class PremiumPlan(
    @Serializable(with = TierSerializer::class) val tier: Tier,
    val productId: String? = null,
    val autoRenew: Boolean? = null,
    val limits: Limits? = null,
) {
    enum class Tier(val raw: String) { FREE("free"), PREMIUM("premium") }

    /** What the free tier still allows. Null whenever nothing limits the learner. */
    @Serializable
    data class Limits(val maxTopics: Int, val maxNewWords: Int, val newWordsUsed: Int) {
        val newWordsLeft: Int get() = maxOf(maxNewWords - newWordsUsed, 0)
    }

    val isPremium: Boolean get() = tier == Tier.PREMIUM

    object TierSerializer : LenientEnumSerializer<Tier>("Tier", Tier.entries.toTypedArray(), { it.raw }, Tier.FREE)
}

/** Server-side switches the app follows. Null from an older backend: no freemium, no redeem. */
@Serializable
data class AppFeatures(
    val freemiumEnabled: Boolean? = null,
    val redeemEnabled: Boolean? = null,
    /** Free learners see an interstitial after every this many finished sessions; 0 or null means no ads. */
    val adEverySessions: Int? = null,
)

/** `GET /me`. */
@Serializable
data class UserProfile(
    val id: String,
    val proficiencyLevel: CefrLevel? = null,
    val learningLevel: CefrLevel? = null,
    val settings: UserSettings,
    val scope: LearningScope? = null,
    /** Collections whose words the learner stopped reviewing. */
    val reviewPauses: List<ReviewPause>? = null,
    /** Public identity and lifetime Tier (FRONTEND_FLOW §14). Null from a backend without ranking. */
    val username: String? = null,
    val avatarKey: String? = null,
    /** Null means the name can change now. */
    @Serializable(with = com.syll.domain.Iso8601InstantSerializer::class) val usernameChangeAvailableAt: java.time.Instant? = null,
    val rankingIntroSeen: Boolean? = null,
    val tierSummary: com.syll.domain.ranking.TierSummary? = null,
    val plan: PremiumPlan? = null,
    val features: AppFeatures? = null,
) {
    val hasFinishedOnboarding: Boolean get() = proficiencyLevel != null && scope != null
}

/**
 * iOS `SettingsUpdate`: `PUT /me/settings` is strict, so null fields are left out of the body.
 * [reviewsPerSession] is double-optional: [sendsReviewsPerSession] with a null value sends an explicit `null`
 * (back to following the new-word goal).
 */
data class SettingsUpdate(
    val explanationLocale: String? = null,
    val uiLocale: String? = null,
    val timeZone: String? = null,
    val dailyNewWordGoal: Int? = null,
    val weeklySessionGoal: Int? = null,
    val autoBalanceNewWords: Boolean? = null,
    val leaderboardVisible: Boolean? = null,
    val sendsReviewsPerSession: Boolean = false,
    val reviewsPerSession: Int? = null,
    val dailySpeaking: Boolean? = null,
) {
    fun toJson(): JsonObject = buildJsonObject {
        explanationLocale?.let { put("explanationLocale", it) }
        uiLocale?.let { put("uiLocale", it) }
        timeZone?.let { put("timeZone", it) }
        dailyNewWordGoal?.let { put("dailyNewWordGoal", it) }
        weeklySessionGoal?.let { put("weeklySessionGoal", it) }
        autoBalanceNewWords?.let { put("autoBalanceNewWords", it) }
        leaderboardVisible?.let { put("leaderboardVisible", it) }
        if (sendsReviewsPerSession) put("reviewsPerSession", reviewsPerSession) // null → explicit null
        dailySpeaking?.let { put("dailySpeaking", it) }
    }
}

// Placement test

/**
 * Always a text `select`. Option ids are UUID strings and there is no answer key: only the backend scores it.
 * Questions and options keep the backend's order.
 */
@Serializable
data class PlacementQuestion(
    val id: String,
    val prompt: Prompt,
    val question: String? = null,
    val options: List<Option>,
) {
    @Serializable
    data class Prompt(val text: String)

    @Serializable
    data class Option(val id: String, val text: String)
}

/** [optionId] null means "don't know"; it goes up as an explicit `null`. */
data class PlacementAnswer(val questionId: String, val optionId: String?)

internal fun List<PlacementAnswer>.submissionJson(): JsonObject = buildJsonObject {
    put(
        "answers",
        buildJsonArray {
            forEach { answer ->
                add(
                    buildJsonObject {
                        put("questionId", answer.questionId)
                        put("optionId", answer.optionId)
                    },
                )
            }
        },
    )
}

/** The backend has already saved this level by the time it answers. */
@Serializable
data class PlacementResult(val proficiencyLevel: CefrLevel, val correctCount: Int, val totalCount: Int)

// Catalog

@Serializable
data class CatalogTopic(
    val id: String,
    val slug: String,
    val title: String,
    val description: String? = null,
    val learnedMeanings: Int? = null,
    val totalMeanings: Int? = null,
    /** SF Symbol name set by an admin; the app maps the ones it knows. */
    val iosIcon: String? = null,
)

@Serializable
data class CatalogCollection(
    val id: String,
    val slug: String,
    val title: String,
    val description: String? = null,
    val topics: List<CatalogTopic>,
    val learnedMeanings: Int? = null,
    val totalMeanings: Int? = null,
    /** `#RRGGBB` set by an admin. */
    val color: String? = null,
) {
    fun toEntry() = CatalogEntry(
        id = id, slug = slug, title = title, description = description,
        topics = topics.map {
            CatalogEntry.TopicEntry(it.id, it.slug, it.title, it.learnedMeanings, it.totalMeanings, it.iosIcon)
        },
        learnedMeanings = learnedMeanings, totalMeanings = totalMeanings, color = color,
    )
}

@Serializable
internal data class CatalogResponse(val collections: List<CatalogCollection>)

@Serializable
internal data class PlacementTestResponse(val questions: List<PlacementQuestion>)

@Serializable
internal data class Levels(val proficiencyLevel: CefrLevel)
