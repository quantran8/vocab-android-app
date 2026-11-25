package com.syll.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/** `GET /me/learn-home` (FRONTEND_FLOW §6). */
@Serializable
data class LearnHome(
    val dueCount: Int,
    /** The number of open checkpoint challenges. */
    val checkpointAvailable: Int,
    /** Sessions counted towards the next challenge. Returns to 0 when a challenge opens. */
    val checkpointSessionsDone: Int,
    val checkpointSessionsRequired: Int,
    val learnedTotal: Int,
    /** Null from a backend that predates scope completion. */
    val scope: ScopeState? = null,
    val reviewLoad: ReviewLoad? = null,
    /** Null unless the free tier limits new words. */
    val freeAllowance: FreeAllowance? = null,
) {
    val checkpointProgress: Double
        get() = if (checkpointSessionsRequired <= 0) 0.0 else minOf(checkpointSessionsDone.toDouble() / checkpointSessionsRequired, 1.0)
}

enum class ScopeStatus(val raw: String) {
    OK("ok"), LOW("low"), EXHAUSTED("exhausted");

    object Serializer : LenientEnumSerializer<ScopeStatus>("ScopeStatus", entries.toTypedArray(), { it.raw }, OK)
}

/** How many new words the scope still holds and how many words the learner added. */
@Serializable
data class ScopeState(
    @Serializable(with = ScopeStatus.Serializer::class) val status: ScopeStatus,
    val remainingNewMeanings: Int,
    val pendingCustomMeanings: Int,
    val sessionsLeftEstimate: Int? = null,
    val topics: List<TopicRemaining> = emptyList(),
) {
    @Serializable
    data class TopicRemaining(val topicId: String, val remainingNewMeanings: Int, val totalMeanings: Int)
}

/** Why a daily session has fewer new words than the goal (`newWordReason` and `withheldReason`). */
enum class NewWordReason(val raw: String) {
    GOAL("goal"),
    USER_OVERRIDE("user_override"),
    AUTO_BALANCED("auto_balanced"),
    CHECKPOINT_GATE("checkpoint_gate"),
    SCOPE_LIMITED("scope_limited"),
    SCOPE_EXHAUSTED("scope_exhausted"),
    REVIEW_ONLY("review_only"),
    /** The free tier's new words are used up (or are the tightest limit). */
    FREE_LIMIT("free_limit"),
    UNKNOWN("unknown");

    object Serializer : LenientEnumSerializer<NewWordReason>("NewWordReason", entries.toTypedArray(), { it.raw }, UNKNOWN)
}

@Serializable
enum class NewWordOverrideMode {
    @SerialName("until_date") UNTIL_DATE,
    @SerialName("until_backlog_clear") UNTIL_BACKLOG_CLEAR,
}

/** A temporary lower number of new words the learner chose. */
@Serializable
data class NewWordOverride(
    val value: Int,
    val mode: NewWordOverrideMode,
    @Serializable(with = Iso8601InstantSerializer::class) val until: Instant? = null,
)

@Serializable
data class ReviewLoad(
    val backlog: Int,
    val dueCap: Int,
    val effectiveNewPerSession: Int,
    @Serializable(with = NewWordReason.Serializer::class) val newWordReason: NewWordReason,
    val override: NewWordOverride? = null,
    val autoBalance: Boolean,
    /** The server's "ask now" flag; the app still shows it at most once per launch and never mid-session. */
    val promptChoice: Boolean,
) {
    companion object {
        /** Above this, Home shows the backlog line and offers an extra review session. */
        const val STATUS_THRESHOLD = 10
    }
}

/** `freeAllowance`: present only while the free tier limits new words. */
@Serializable
data class FreeAllowance(val used: Int, val max: Int, val remaining: Int)

@Serializable
internal data class ReviewLoadAck(val backlog: Int)

@Serializable
internal data class OverrideRequest(val value: Int, val mode: NewWordOverrideMode, val until: String?)

@Serializable
internal data class OverrideResponse(val override: NewWordOverride)
