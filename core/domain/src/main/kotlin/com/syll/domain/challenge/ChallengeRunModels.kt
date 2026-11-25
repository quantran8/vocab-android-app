package com.syll.domain.challenge

import com.syll.domain.Iso8601InstantSerializer
import com.syll.domain.LenientEnumSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.Instant

// Wire models for a challenge run (backend `docs/FRONTEND_FLOW.md` §12.2–12.4): issue → start → commit, plus
// status / quit for recovery. The client never sends stars, score or accuracy; the server replays `events` and
// answers with the result.

/**
 * iOS `ChallengeRunPlan`: a frozen run plan from `POST /v1/challenge/chapters/{chapterNo}/levels/{levelNo}/runs`
 * (`status = issued`). Also returned as `run` by the status route while a run is still open.
 */
@Serializable
data class ChallengeRunPlan(
    val runId: String,
    val status: String? = null,
    val gameMode: String,
    val chapterNo: Int,
    val levelNo: Int,
    val policyVersion: String,
    val contentVersion: Int? = null,
    val explanationLocale: String? = null,
    val goalMatches: Int,
    val visiblePairs: Int,
    val timeLimitMs: Int,
    /** A meaning (same `word`) matched correctly can't come back on the board for this many correct matches. */
    val minMatchesBetweenRepeats: Int,
    val starCriteria: ChallengeStarCriteria,
    val boardSeed: String,
    @Serializable(with = Iso8601InstantSerializer::class) val expiresAt: Instant? = null,
    /** The reveal queue, exactly `goalMatches` long. A meaning may appear twice, each time with new IDs. */
    val pairs: List<ChallengePairOccurrence>,
    /**
     * Display-only rule for the live score, frozen with the plan. Null only for plans issued before the backend sent
     * it, and then the live score stays hidden.
     */
    val score: ChallengeScoreRule? = null,
) {
    companion object {
        const val MATCH_MADNESS = "match_madness"

        /**
         * A local plan for previews, tests and the Practice Lab. Never committed. Values mirror the trial policy for
         * level 2 so the round feels the same; they decide nothing.
         */
        fun sandbox(timeLimitMs: Int = 90_000, visiblePairs: Int = 4, goalMatches: Int = 12): ChallengeRunPlan {
            val words = listOf(
                "reimburse" to "hoàn trả chi phí",
                "allocate" to "phân bổ",
                "postpone" to "trì hoãn",
                "clarify" to "làm rõ",
                "negotiate" to "đàm phán",
                "deadline" to "hạn chót",
                "estimate" to "ước tính",
                "approve" to "phê duyệt",
                "schedule" to "lên lịch",
                "invoice" to "hoá đơn",
                "colleague" to "đồng nghiệp",
                "review" to "xem xét lại",
            )
            val pairs = (0 until goalMatches).map { index ->
                val (word, meaning) = words[index % words.size]
                ChallengePairOccurrence(
                    pairId = "p${index + 1}",
                    wordTileId = "w${index + 1}",
                    meaningTileId = "m${index + 1}",
                    word = word,
                    meaning = meaning,
                )
            }
            return ChallengeRunPlan(
                runId = "sandbox",
                gameMode = MATCH_MADNESS,
                chapterNo = 1,
                levelNo = 1,
                policyVersion = "sandbox",
                explanationLocale = "vi-VN",
                goalMatches = goalMatches,
                visiblePairs = visiblePairs,
                timeLimitMs = timeLimitMs,
                minMatchesBetweenRepeats = 3,
                starCriteria = ChallengeStarCriteria(
                    two = ChallengeStarCriteria.Tier(maxElapsedMs = 80_000, maxMistakes = 3),
                    three = ChallengeStarCriteria.Tier(maxElapsedMs = 60_000, maxMistakes = 1),
                ),
                boardSeed = "sandbox-seed",
                pairs = pairs,
                score = ChallengeScoreRule(match = 100, streakStep = 10, maxStreakSteps = 5, mistake = 20, perSecondLeft = 5),
            )
        }
    }
}

/** One star is any clear. Two and three need both their time and their mistake bound. */
@Serializable
data class ChallengeStarCriteria(val two: Tier, val three: Tier) {
    @Serializable
    data class Tier(val maxElapsedMs: Int, val maxMistakes: Int)
}

/** One pair occurrence in the queue. */
@Serializable
data class ChallengePairOccurrence(
    val pairId: String,
    val wordTileId: String,
    val meaningTileId: String,
    val word: String,
    val meaning: String,
) {
    val id: String get() = pairId
}

/**
 * The plan's `score`: each correct pair `match + streakStep × min(combo, maxStreakSteps)` (combo counting the pair
 * just matched), each mistake `−mistake`, a clear adds `perSecondLeft × whole seconds left`; only the total is clamped
 * at 0.
 */
@Serializable
data class ChallengeScoreRule(
    val match: Int,
    val streakStep: Int,
    val maxStreakSteps: Int,
    val mistake: Int,
    val perSecondLeft: Int? = null,
) {
    fun points(forCombo: Int): Int = match + streakStep * minOf(maxOf(forCombo, 0), maxStreakSteps)

    fun clearBonus(timeLimitMs: Int, elapsedMs: Int): Int = (perSecondLeft ?: 0) * (maxOf(timeLimitMs - elapsedMs, 0) / 1000)
}

/** One complete two-tile attempt, right or wrong. Tap selections alone are never sent. */
@Serializable
data class MatchAttemptEvent(
    val seq: Int,
    val elapsedMs: Int,
    val wordTileId: String,
    val meaningTileId: String,
    val type: String = TYPE,
) {
    /** Keys sorted like iOS `JSONEncoder.sortedKeys`. */
    fun toJson(): JsonObject = buildJsonObject {
        put("elapsedMs", elapsedMs)
        put("meaningTileId", meaningTileId)
        put("seq", seq)
        put("type", type)
        put("wordTileId", wordTileId)
    }

    companion object {
        const val TYPE = "match"
    }
}

enum class ChallengeEndedReason(val raw: String) {
    GOAL_REACHED("goal_reached"),
    TIME_UP("time_up"),
    QUIT("quit"),
}

/**
 * iOS `ChallengeCommitBody` for `POST /v1/challenge/runs/{runId}/commit`. Encoded once; a retry sends the same bytes
 * (the server is idempotent on an identical body and answers `CHALLENGE_COMMIT_CONFLICT` to a different one).
 */
data class ChallengeCommitBody(
    val events: List<MatchAttemptEvent>,
    val endedReason: ChallengeEndedReason,
    val endedElapsedMs: Int,
) {
    /** Keys sorted, as iOS encodes it. */
    fun toJson(): JsonObject = buildJsonObject {
        put("endedElapsedMs", endedElapsedMs)
        put("endedReason", endedReason.raw)
        putJsonArray("events") { events.forEach { add(it.toJson()) } }
    }

    fun encoded(): ByteArray = toJson().toString().toByteArray(Charsets.UTF_8)
}

/** iOS `ChallengeCommitResponse`: the commit response; also `response` in status and in the quit of a started run. */
@Serializable
data class ChallengeCommitResponse(
    val runId: String? = null,
    val result: ChallengeRunResult,
    val unlock: ChallengeUnlock? = null,
    @Serializable(with = NextAction.Serializer::class) val nextAction: NextAction? = null,
) {
    enum class NextAction(val raw: String) {
        PLAY_NEXT_LEVEL("play_next_level"),
        OPEN_MAP("open_map"),
        LEARN_MORE("learn_more"),
        RETRY("retry"),
        UNKNOWN("unknown");

        object Serializer : LenientEnumSerializer<NextAction>("ChallengeNextAction", entries.toTypedArray(), { it.raw }, UNKNOWN)
    }
}

/** The authoritative outcome of a committed run. */
@Serializable
data class ChallengeRunResult(
    @Serializable(with = Outcome.Serializer::class) val outcome: Outcome,
    val starsThisRun: Int,
    val bestStars: Int,
    val correctMatches: Int,
    val mistakes: Int,
    val longestCombo: Int,
    val elapsedMs: Int,
    val score: Int,
    val bestScore: Int,
) {
    enum class Outcome(val raw: String) {
        CLEARED("cleared"),
        FAILED("failed"),
        QUIT("quit"),
        UNKNOWN("unknown");

        object Serializer : LenientEnumSerializer<Outcome>("ChallengeOutcome", entries.toTypedArray(), { it.raw }, UNKNOWN)
    }
}

@Serializable
data class ChallengeUnlock(
    @Serializable(with = Kind.Serializer::class) val kind: Kind,
    val chapterNo: Int? = null,
    val levelNo: Int? = null,
    val eligibleNewCount: Int? = null,
    val requiredNewCount: Int? = null,
) {
    enum class Kind(val raw: String) {
        /** First clear of a level: the next level of the chapter opened. */
        LEVEL("level"),
        /** First clear of the last level, and the next chapter has enough words. */
        CHAPTER_READY("chapter_ready"),
        /** First clear of the last level, but the next chapter needs more words. */
        NEEDS_VOCABULARY("needs_vocabulary"),
        NONE("none"),
        UNKNOWN("unknown");

        object Serializer : LenientEnumSerializer<Kind>("ChallengeUnlockKind", entries.toTypedArray(), { it.raw }, UNKNOWN)
    }
}

/** `GET /v1/challenge/runs/{runId}/status`. */
@Serializable
data class ChallengeRunStatus(
    val runId: String,
    @Serializable(with = Status.Serializer::class) val status: Status,
    val chapterNo: Int? = null,
    val levelNo: Int? = null,
    /** The commit response, once committed. */
    val response: ChallengeCommitResponse? = null,
    /** The frozen plan while the run is still open. */
    val run: ChallengeRunPlan? = null,
) {
    enum class Status(val raw: String) {
        ISSUED("issued"),
        STARTED("started"),
        COMMITTED("committed"),
        ABANDONED("abandoned"),
        EXPIRED("expired"),
        UNKNOWN("unknown");

        object Serializer : LenientEnumSerializer<Status>("ChallengeRunStatus.Status", entries.toTypedArray(), { it.raw }, UNKNOWN)
    }
}

/** Error codes the challenge routes answer with (§12.7). */
object ChallengeErrorCode {
    const val LEVEL_LOCKED = "CHALLENGE_LEVEL_LOCKED"
    const val RUN_NOT_FOUND = "CHALLENGE_RUN_NOT_FOUND"
    const val RUN_ACTIVE = "CHALLENGE_RUN_ACTIVE"
    const val RUN_NOT_STARTED = "CHALLENGE_RUN_NOT_STARTED"
    const val COMMIT_CONFLICT = "CHALLENGE_COMMIT_CONFLICT"
    const val NOT_ENOUGH_VOCABULARY = "CHALLENGE_NOT_ENOUGH_VOCABULARY"
    const val CONTENT_UNAVAILABLE = "CHALLENGE_CONTENT_UNAVAILABLE"
    const val RUN_EXPIRED = "CHALLENGE_RUN_EXPIRED"
    const val INVALID_EVENTS = "CHALLENGE_INVALID_EVENTS"
}
