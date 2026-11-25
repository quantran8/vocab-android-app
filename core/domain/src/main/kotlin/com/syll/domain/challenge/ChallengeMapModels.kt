package com.syll.domain.challenge

import com.syll.domain.LenientEnumSerializer
import com.syll.domain.NewWordReason
import kotlinx.serialization.Serializable

// Wire models for `GET /v1/me/challenge-map` (backend `docs/FRONTEND_FLOW.md` §12.1).
// Unknown enum values decode to UNKNOWN instead of failing the whole map, so a newer server state renders as a
// neutral, non-playable node rather than an error screen.

/**
 * iOS `ChallengeMap`. The backend alone decides every state: the client never unlocks anything by counting words
 * itself.
 */
@Serializable
data class ChallengeMap(
    val configVersion: String? = null,
    val gameMode: String? = null,
    /** Materialized chapters, oldest first. */
    val chapters: List<ChallengeChapter>,
    /** The chapter after the last materialized one (`chapterNo` 1 when there is none yet). */
    val nextChapter: ChallengeChapterPreview? = null,
    /** Where the map scrolls to first; null when nothing is playable. */
    val currentNode: ChallengeNodeRef? = null,
    /** `scope_exhausted` when Daily has no new words left to grow the next chapter; null otherwise or from an older backend. */
    @Serializable(with = NewWordReason.Serializer::class) val newWordsBlockedReason: NewWordReason? = null,
) {
    val totalStars: Int get() = chapters.sumOf { it.starsEarned }

    /** The earliest chapter not cleared yet: only it can be played forward. */
    val openChapter: ChallengeChapter? get() = chapters.firstOrNull { it.state != ChallengeChapter.State.COMPLETED }

    companion object
}

@Serializable
data class ChallengeNodeRef(val chapterNo: Int, val levelNo: Int)

@Serializable
data class ChallengeChapter(
    /** Minted by the server when the chapter materializes. */
    val id: String,
    val chapterNo: Int,
    @Serializable(with = State.Serializer::class) val state: State,
    /** The meaning language the chapter's words were frozen in (e.g. "vi-VN"). */
    val explanationLocale: String? = null,
    val coreMeaningCount: Int? = null,
    val reviewMeaningCount: Int? = null,
    val starsEarned: Int,
    val maxStars: Int,
    val levels: List<ChallengeLevel>,
) {
    enum class State(val raw: String) {
        ACTIVE("active"),
        COMPLETED("completed"),
        /** Some words were removed and nothing learned can replace them: point the learner to Daily. */
        CONTENT_UNAVAILABLE("content_unavailable"),
        UNKNOWN("unknown");

        object Serializer : LenientEnumSerializer<State>("ChallengeChapter.State", entries.toTypedArray(), { it.raw }, UNKNOWN)
    }
}

@Serializable
data class ChallengeLevel(
    val levelNo: Int,
    @Serializable(with = State.Serializer::class) val state: State,
    val bestStars: Int,
    /** 0 until the level has been cleared. */
    val bestScore: Int,
    /** The level's goal under the current policy, for the sheet before a run exists. A run's plan has the final say. */
    val goalMatches: Int? = null,
    val visiblePairs: Int? = null,
    val timeLimitMs: Int? = null,
    val starCriteria: ChallengeStarCriteria? = null,
) {
    enum class State(val raw: String) {
        LOCKED("locked"),
        AVAILABLE("available"),
        CLEARED("cleared"),
        UNKNOWN("unknown");

        object Serializer : LenientEnumSerializer<State>("ChallengeLevel.State", entries.toTypedArray(), { it.raw }, UNKNOWN)
    }

    val id: Int get() = levelNo
    val isPlayable: Boolean get() = state == State.AVAILABLE || state == State.CLEARED

    companion object {
        const val MAX_STARS = 3
    }
}

@Serializable
data class ChallengeChapterPreview(
    val chapterNo: Int,
    @Serializable(with = State.Serializer::class) val state: State,
    /** Eligible, unallocated meanings counted towards this chapter. Not the My Words count. */
    val eligibleNewCount: Int,
    val requiredNewCount: Int,
) {
    enum class State(val raw: String) {
        /** The current chapter isn't cleared yet. */
        LOCKED("locked"),
        NEEDS_VOCABULARY("needs_vocabulary"),
        /** Playing its Level 1 materializes it. */
        READY("ready"),
        UNKNOWN("unknown");

        object Serializer : LenientEnumSerializer<State>("ChallengeChapterPreview.State", entries.toTypedArray(), { it.raw }, UNKNOWN)
    }

    val remainingNewCount: Int get() = maxOf(requiredNewCount - eligibleNewCount, 0)
}

/** iOS `ChallengeHomeSummary`: what the Home card shows, derived from the server map only. */
sealed interface ChallengeHomeSummary {
    /** Not enough eligible words for the first chapter. */
    data class NotYetEligible(val eligible: Int, val required: Int) : ChallengeHomeSummary

    /** A chapter can be entered; nothing played in it yet. */
    data class ChapterReady(val chapterNo: Int) : ChallengeHomeSummary

    data class InProgress(val chapterNo: Int, val levelNo: Int, val earnedStars: Int, val maxStars: Int) : ChallengeHomeSummary

    /** Every chapter is cleared and the next needs more words. */
    data class ChapterComplete(
        val chapterNo: Int,
        val earnedStars: Int,
        val maxStars: Int,
        val nextEligible: Int,
        val nextRequired: Int,
    ) : ChallengeHomeSummary

    /** The open chapter lost words that can't be replaced yet. Stars are kept. */
    data class ContentUnavailable(val chapterNo: Int, val earnedStars: Int, val maxStars: Int) : ChallengeHomeSummary

    companion object {
        fun from(map: ChallengeMap): ChallengeHomeSummary? {
            map.openChapter?.let { open ->
                if (open.state == ChallengeChapter.State.CONTENT_UNAVAILABLE) {
                    return ContentUnavailable(open.chapterNo, open.starsEarned, open.maxStars)
                }
                val level = map.currentNode?.takeIf { it.chapterNo == open.chapterNo }?.levelNo
                    ?: open.levels.firstOrNull { it.state == ChallengeLevel.State.AVAILABLE }?.levelNo
                    ?: 1
                return InProgress(open.chapterNo, level, open.starsEarned, open.maxStars)
            }
            val next = map.nextChapter
            if (next != null && next.state == ChallengeChapterPreview.State.READY) return ChapterReady(next.chapterNo)
            val last = map.chapters.lastOrNull()
            return when {
                last != null -> ChapterComplete(
                    chapterNo = last.chapterNo,
                    earnedStars = last.starsEarned,
                    maxStars = last.maxStars,
                    nextEligible = next?.eligibleNewCount ?: 0,
                    nextRequired = next?.requiredNewCount ?: 0,
                )
                next != null -> NotYetEligible(next.eligibleNewCount, next.requiredNewCount)
                else -> null
            }
        }
    }
}
