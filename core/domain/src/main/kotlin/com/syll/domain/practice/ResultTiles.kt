package com.syll.domain.practice

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.random.Random

/** iOS `PracticeResult`: everything the results screen shows. Built from the last commit, with a daily chain's review merged in. */
data class PracticeResult(val kind: Kind, val summary: CommitSummary) {
    enum class Kind { DAILY, RANDOM_PRACTICE, WEAK_PRACTICE, SPEAKING_PRACTICE, CHECKPOINT }

    /**
     * The tiles with something to show (an empty one is left out), or null when this commit has no result groups
     * (checkpoint, or an older backend). LP is not a tile.
     */
    val tiles: List<ResultTile>? get() = ResultTiles.tiles(this)

    companion object {
        fun of(mode: SessionMode, summary: CommitSummary) = PracticeResult(
            when (mode) {
                SessionMode.DAILY -> Kind.DAILY
                SessionMode.RANDOM_PRACTICE -> Kind.RANDOM_PRACTICE
                SessionMode.WEAK_WORDS_PRACTICE -> Kind.WEAK_PRACTICE
                SessionMode.SPEAKING_PRACTICE -> Kind.SPEAKING_PRACTICE
                SessionMode.CHECKPOINT_CHALLENGE -> Kind.CHECKPOINT
            },
            summary,
        )
    }
}

/** A part of the detail sheet; tapping a tile scrolls to it. */
enum class ResultSection { REVIEW, NEW_WORDS, PRACTICE, WEAK, SPEAKING }

/** iOS `ResultTile`: what a tile shows, as data, so the rules can be tested without UI. */
data class ResultTile(
    val kind: Kind,
    val number: Number,
    val caption: Caption,
    /** The weaker parts first (zeros left out), then any other note. */
    val lines: List<Line> = emptyList(),
    /** Nothing to report: the screen leaves the tile out. */
    val isEmpty: Boolean = false,
    /** Weak practice cleared every weak word. */
    val celebrates: Boolean = false,
) {
    enum class Kind { REVIEW, NEW_WORDS, PRACTICE, WEAK_REMAINING, SPEAKING }

    sealed interface Number {
        /** "—" */
        data object None : Number
        data class Fraction(val part: Int, val total: Int) : Number
        /** "+3" */
        data class Plus(val value: Int) : Number
        data class Count(val value: Int) : Number
    }

    enum class Caption {
        REMEMBERED, NO_REVIEWS, PICKED_UP, NO_NEW_WORDS, PRACTICE_CLEAN, NO_SKILL_MEASURED,
        WEAK_REMAINING, NO_WEAK_WORDS_LEFT, SAID_RIGHT,
    }

    sealed interface Line {
        data class Relearn(val count: Int) : Line
        data class Shaky(val count: Int) : Line
        data class NeededRetry(val count: Int) : Line
        data class SawAnswer(val count: Int) : Line
        data class AlreadyKnew(val count: Int) : Line
        data class Missed(val count: Int) : Line
        data class Helped(val count: Int) : Line
        /** Signed change in weak words since before the session. */
        data class WeakDelta(val delta: Int) : Line
        data class BecameFluent(val count: Int) : Line
        data class Slipped(val count: Int) : Line

        companion object {
            /** "−3" / "+2", with a real minus sign. */
            fun signed(value: Int): String = if (value < 0) "−${-value}" else "+$value"
        }
    }

    val section: ResultSection
        get() = when (kind) {
            Kind.REVIEW -> ResultSection.REVIEW
            Kind.NEW_WORDS -> ResultSection.NEW_WORDS
            Kind.PRACTICE -> ResultSection.PRACTICE
            Kind.WEAK_REMAINING -> ResultSection.WEAK
            Kind.SPEAKING -> ResultSection.SPEAKING
        }
}

/** iOS `ResultTiles`: the tile rules from "Session Result Statistics" §4.3 and §11. Pure: the server chose every group. */
object ResultTiles {
    fun tiles(result: PracticeResult): List<ResultTile>? {
        val summary = result.summary
        val tiles = when (result.kind) {
            PracticeResult.Kind.DAILY -> {
                if (summary.review == null && summary.newWords == null) return null
                listOf(reviewTile(summary.review), newWordsTile(summary.newWords), speakingTile(summary.speaking))
            }
            PracticeResult.Kind.RANDOM_PRACTICE -> {
                if (summary.practice == null) return null
                listOf(practiceTile(summary.practice), speakingTile(summary.speaking))
            }
            PracticeResult.Kind.WEAK_PRACTICE -> {
                if (summary.practice == null) return null
                listOf(practiceTile(summary.practice), weakTile(summary.needPractice))
            }
            PracticeResult.Kind.SPEAKING_PRACTICE -> listOf(speakingTile(summary.speaking))
            PracticeResult.Kind.CHECKPOINT -> return null
        }
        return tiles.filter { !it.isEmpty }
    }

    fun reviewTile(review: ReviewSummary?): ResultTile {
        val groups = review?.groups ?: ResultGroups()
        val solid = groups.count(ReviewGroup.SOLID)
        val shaky = groups.count(ReviewGroup.SHAKY)
        val relearn = groups.count(ReviewGroup.RELEARN)
        val total = solid + shaky + relearn
        if (total == 0) return ResultTile(ResultTile.Kind.REVIEW, ResultTile.Number.None, ResultTile.Caption.NO_REVIEWS, isEmpty = true)
        return ResultTile(
            ResultTile.Kind.REVIEW, ResultTile.Number.Fraction(solid, total), ResultTile.Caption.REMEMBERED,
            lines = parts(ResultTile.Line.Shaky(shaky), shaky, ResultTile.Line.Relearn(relearn), relearn),
        )
    }

    /** Known words sit outside the fraction: they skipped learning, so they say nothing about how it went. */
    fun newWordsTile(newWords: NewWordSummary?): ResultTile {
        val groups = newWords?.groups ?: ResultGroups()
        val smooth = groups.count(NewWordGroup.SMOOTH)
        val retried = groups.count(NewWordGroup.RETRIED)
        val revealed = groups.count(NewWordGroup.REVEALED)
        val known = groups.count(NewWordGroup.KNOWN)
        val total = smooth + retried + revealed
        val knew = if (known > 0) listOf(ResultTile.Line.AlreadyKnew(known)) else emptyList()
        if (total == 0) {
            if (known == 0) return ResultTile(ResultTile.Kind.NEW_WORDS, ResultTile.Number.None, ResultTile.Caption.NO_NEW_WORDS, isEmpty = true)
            return ResultTile(ResultTile.Kind.NEW_WORDS, ResultTile.Number.None, ResultTile.Caption.PICKED_UP, lines = knew)
        }
        return ResultTile(
            ResultTile.Kind.NEW_WORDS, ResultTile.Number.Fraction(smooth, total), ResultTile.Caption.PICKED_UP,
            lines = parts(ResultTile.Line.NeededRetry(retried), retried, ResultTile.Line.SawAnswer(revealed), revealed) + knew,
        )
    }

    fun practiceTile(practice: PracticeSummary?): ResultTile {
        val groups = practice?.groups ?: ResultGroups()
        val clean = groups.count(PracticeGroup.CLEAN)
        val helped = groups.count(PracticeGroup.HELPED)
        val missed = groups.count(PracticeGroup.MISSED)
        val total = clean + helped + missed
        if (total == 0) return ResultTile(ResultTile.Kind.PRACTICE, ResultTile.Number.None, ResultTile.Caption.NO_SKILL_MEASURED, isEmpty = true)
        return ResultTile(
            ResultTile.Kind.PRACTICE, ResultTile.Number.Fraction(clean, total), ResultTile.Caption.PRACTICE_CLEAN,
            lines = parts(ResultTile.Line.Helped(helped), helped, ResultTile.Line.Missed(missed), missed),
        )
    }

    /** Shows what is left, not what was cleared: the delta line says what this session did. */
    fun weakTile(count: NeedPracticeCount?): ResultTile {
        val after = count?.after
            ?: return ResultTile(ResultTile.Kind.WEAK_REMAINING, ResultTile.Number.None, ResultTile.Caption.WEAK_REMAINING, isEmpty = true)
        val before = count.before
        val lines = if (before != null && before != after) listOf(ResultTile.Line.WeakDelta(after - before)) else emptyList()
        return ResultTile(
            ResultTile.Kind.WEAK_REMAINING, ResultTile.Number.Count(after),
            if (after == 0) ResultTile.Caption.NO_WEAK_WORDS_LEFT else ResultTile.Caption.WEAK_REMAINING,
            lines = lines, celebrates = after == 0,
        )
    }

    /** Recordings said right (first take on the device, first score on Azure) out of those scored. */
    fun speakingTile(speaking: SpeakingSummary?): ResultTile {
        if (speaking == null || speaking.total <= 0) {
            return ResultTile(ResultTile.Kind.SPEAKING, ResultTile.Number.None, ResultTile.Caption.SAID_RIGHT, isEmpty = true)
        }
        val fluent = speaking.becameFluent.size
        val slipped = speaking.slipped.size
        return ResultTile(
            ResultTile.Kind.SPEAKING, ResultTile.Number.Fraction(speaking.passed, speaking.total), ResultTile.Caption.SAID_RIGHT,
            lines = (if (fluent > 0) listOf(ResultTile.Line.BecameFluent(fluent)) else emptyList()) +
                (if (slipped > 0) listOf(ResultTile.Line.Slipped(slipped)) else emptyList()),
        )
    }

    /** The weaker groups, by name and count; an empty one gets no line. */
    private fun parts(mid: ResultTile.Line, midCount: Int, weak: ResultTile.Line, weakCount: Int): List<ResultTile.Line> =
        (if (midCount > 0) listOf(mid) else emptyList()) + (if (weakCount > 0) listOf(weak) else emptyList())
}

/** iOS `ResultSheetBlock`: what the detail sheet lists, the groups needing attention first. */
data class ResultSheetBlock(
    val section: ResultSection,
    val title: Title,
    val groups: List<ResultSheetGroup>,
    /** Practice: answers that could not judge a skill, and why. */
    val excluded: PracticeSummary.Excluded? = null,
    /** Random: due words this practice counted as their review. */
    val countedAsReview: List<ResultItem> = emptyList(),
    /** Speaking: how many recordings were said right, out of those scored (older backend only). */
    val spoken: SpokenTally? = null,
) {
    enum class Title { REVIEW, NEW_WORDS, ANSWERS, SPEAKING }

    data class SpokenTally(val passed: Int, val total: Int)

    /** Counts for the proportion bar (strong, mid, weak), from the groups that have a level; null when none does. */
    val bar: List<Int>?
        get() {
            val leveled = groups.filter { it.kind.barLevel != null }
            if (leveled.isEmpty()) return null
            return (0 until 3).map { level -> leveled.filter { it.kind.barLevel == level }.sumOf { it.items.size } }
        }
}

data class ResultSheetGroup(val kind: Kind, val items: List<ResultItem>, val collapsedByDefault: Boolean = false) {
    enum class Kind {
        RELEARN, SHAKY, SOLID, REVEALED, RETRIED, SMOOTH, KNOWN, MISSED, HELPED, CLEAN, NOT_PASSED, SPOKE_RIGHT,
        BECAME_FLUENT, SLIPPED;

        /** 0 strong, 1 mid, 2 weak; null for groups that are not a share of the block. */
        val barLevel: Int?
            get() = when (this) {
                SOLID, SMOOTH, CLEAN, SPOKE_RIGHT -> 0
                SHAKY, RETRIED, HELPED -> 1
                RELEARN, REVEALED, MISSED, NOT_PASSED -> 2
                KNOWN, BECAME_FLUENT, SLIPPED -> null
            }
    }

    /** Group headers with a "back on …" line: the items in a group come back at about the same time. */
    val showsNextReview: Boolean
        get() = kind in setOf(Kind.RELEARN, Kind.SHAKY, Kind.SOLID, Kind.REVEALED, Kind.RETRIED, Kind.SMOOTH, Kind.KNOWN)
}

/** iOS `ResultSheetModel`. */
object ResultSheetModel {
    fun blocks(result: PracticeResult): List<ResultSheetBlock> {
        val summary = result.summary
        val blocks = when (result.kind) {
            PracticeResult.Kind.DAILY -> buildList {
                summary.review?.groups?.let { g ->
                    add(
                        ResultSheetBlock(
                            ResultSection.REVIEW, ResultSheetBlock.Title.REVIEW,
                            collapsing(
                                ResultSheetGroup.Kind.SOLID,
                                listOf(
                                    ResultSheetGroup(ResultSheetGroup.Kind.RELEARN, g[ReviewGroup.RELEARN]),
                                    ResultSheetGroup(ResultSheetGroup.Kind.SHAKY, g[ReviewGroup.SHAKY]),
                                    ResultSheetGroup(ResultSheetGroup.Kind.SOLID, g[ReviewGroup.SOLID]),
                                ),
                            ),
                        ),
                    )
                }
                summary.newWords?.groups?.let { g ->
                    add(
                        ResultSheetBlock(
                            ResultSection.NEW_WORDS, ResultSheetBlock.Title.NEW_WORDS,
                            collapsing(
                                ResultSheetGroup.Kind.SMOOTH,
                                listOf(
                                    ResultSheetGroup(ResultSheetGroup.Kind.REVEALED, g[NewWordGroup.REVEALED]),
                                    ResultSheetGroup(ResultSheetGroup.Kind.RETRIED, g[NewWordGroup.RETRIED]),
                                    ResultSheetGroup(ResultSheetGroup.Kind.SMOOTH, g[NewWordGroup.SMOOTH]),
                                    ResultSheetGroup(ResultSheetGroup.Kind.KNOWN, g[NewWordGroup.KNOWN]),
                                ),
                            ),
                        ),
                    )
                }
            }.filter { it.groups.isNotEmpty() }
            PracticeResult.Kind.RANDOM_PRACTICE -> randomBlocks(summary)
            PracticeResult.Kind.WEAK_PRACTICE -> answerBlocks(summary.practice ?: PracticeSummary())
            PracticeResult.Kind.SPEAKING_PRACTICE -> emptyList()
            PracticeResult.Kind.CHECKPOINT -> return emptyList()
        }
        return blocks + listOfNotNull(speakingBlock(summary.speaking))
    }

    private fun randomBlocks(summary: CommitSummary): List<ResultSheetBlock> {
        val blocks = answerBlocks(summary.practice ?: PracticeSummary()).toMutableList()
        val counted = summary.countedAsReview.orEmpty()
        if (counted.isNotEmpty()) {
            if (blocks.isEmpty()) blocks += ResultSheetBlock(ResultSection.PRACTICE, ResultSheetBlock.Title.ANSWERS, emptyList())
            blocks[blocks.lastIndex] = blocks.last().copy(countedAsReview = counted)
        }
        return blocks
    }

    /** Every answer in one block; a block with only uncounted answers still says why they didn't count. */
    private fun answerBlocks(practice: PracticeSummary): List<ResultSheetBlock> {
        val groups = collapsing(
            ResultSheetGroup.Kind.CLEAN,
            listOf(
                ResultSheetGroup(ResultSheetGroup.Kind.MISSED, practice.groups[PracticeGroup.MISSED]),
                ResultSheetGroup(ResultSheetGroup.Kind.HELPED, practice.groups[PracticeGroup.HELPED]),
                ResultSheetGroup(ResultSheetGroup.Kind.CLEAN, practice.groups[PracticeGroup.CLEAN]),
            ),
        )
        if (groups.isEmpty() && (practice.excluded?.count ?: 0) <= 0) return emptyList()
        return listOf(ResultSheetBlock(ResultSection.PRACTICE, ResultSheetBlock.Title.ANSWERS, groups, excluded = practice.excluded))
    }

    /**
     * Present whenever a recording was scored, so the speaking tile always has a block to open. Every scored word,
     * not passed first; the status changes follow, collapsed. An older backend sent only the counts: then the
     * "said right" line stands in for the two groups.
     */
    private fun speakingBlock(speaking: SpeakingSummary?): ResultSheetBlock? {
        if (speaking == null || speaking.total <= 0) return null
        val results = speaking.groups?.let { g ->
            listOf(
                ResultSheetGroup(ResultSheetGroup.Kind.NOT_PASSED, g[SpeakingGroup.NOT_PASSED]),
                ResultSheetGroup(ResultSheetGroup.Kind.SPOKE_RIGHT, g[SpeakingGroup.PASSED]),
            )
        }.orEmpty()
        val changes = listOf(
            ResultSheetGroup(ResultSheetGroup.Kind.SLIPPED, speaking.slipped, collapsedByDefault = speaking.groups != null),
            ResultSheetGroup(ResultSheetGroup.Kind.BECAME_FLUENT, speaking.becameFluent, collapsedByDefault = speaking.groups != null),
        )
        return ResultSheetBlock(
            ResultSection.SPEAKING, ResultSheetBlock.Title.SPEAKING, (results + changes).filter { it.items.isNotEmpty() },
            spoken = if (speaking.groups == null) ResultSheetBlock.SpokenTally(speaking.passed, speaking.total) else null,
        )
    }

    /** Drops empty groups; [kind] starts collapsed when another group in the block has words. */
    private fun collapsing(kind: ResultSheetGroup.Kind, groups: List<ResultSheetGroup>): List<ResultSheetGroup> {
        val shown = groups.filter { it.items.isNotEmpty() }
        return shown.map { it.copy(collapsedByDefault = it.kind == kind && shown.size > 1) }
    }
}

/** iOS `ResultDateText`: relative dates for the sheet, in the learner's time zone. */
object ResultDateText {
    sealed interface NextReview {
        data object Tonight : NextReview
        data object Tomorrow : NextReview
        /** Within the coming week: the weekday's name. */
        data class Weekday(val date: Instant) : NextReview
        data class InDays(val days: Long) : NextReview
    }

    fun nextReview(date: Instant, now: Instant, zone: ZoneId): NextReview {
        val days = ChronoUnit.DAYS.between(LocalDate.ofInstant(now, zone), LocalDate.ofInstant(date, zone))
        return when {
            days <= 0 -> NextReview.Tonight
            days == 1L -> NextReview.Tomorrow
            days in 2..6 -> NextReview.Weekday(date)
            else -> NextReview.InDays(days)
        }
    }

    /** The day a review word was learned, when that was within the last 7 days. */
    fun recentlyLearned(learnedAt: Instant?, now: Instant, zone: ZoneId): Instant? {
        learnedAt ?: return null
        val days = ChronoUnit.DAYS.between(LocalDate.ofInstant(learnedAt, zone), LocalDate.ofInstant(now, zone))
        return if (days in 0..6) learnedAt else null
    }

    /** The learner's zone from the profile, or the device's. */
    fun zone(identifier: String?): ZoneId = identifier?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
}

/** iOS `CelebrationCounts`: the counts on the celebration and whether the session had no wrong answer. */
data class CelebrationCounts(val newWords: Int, val reviewed: Int, val perfect: Boolean) {
    companion object {
        fun of(summary: CommitSummary): CelebrationCounts {
            var reviewed = 0
            var newWords = 0
            summary.review?.let { r -> reviewed += ReviewGroup.entries.sumOf { r.groups.count(it) } }
            summary.newWords?.let { n -> newWords += NewWordGroup.entries.sumOf { n.groups.count(it) } }
            summary.practice?.let { p ->
                reviewed += PracticeGroup.entries.flatMap { p.groups[it] }.map { it.meaningId }.toSet().size
            }
            val hasGroups = summary.review != null || summary.newWords != null || summary.practice != null
            if (!hasGroups) {
                // A session an older backend saved (checkpoints arrive with Challenge, Phase 2).
                return CelebrationCounts(
                    newWords = maxOf(0, (summary.newWordGoalSlots ?: 0) - (summary.knownMeanings ?: 0)),
                    reviewed = summary.reviewedMeanings ?: 0,
                    perfect = false,
                )
            }
            val weaker = (summary.review?.let { it.groups.count(ReviewGroup.SHAKY) + it.groups.count(ReviewGroup.RELEARN) } ?: 0) +
                (summary.newWords?.let { it.groups.count(NewWordGroup.RETRIED) + it.groups.count(NewWordGroup.REVEALED) } ?: 0) +
                (summary.practice?.let { it.groups.count(PracticeGroup.HELPED) + it.groups.count(PracticeGroup.MISSED) } ?: 0)
            return CelebrationCounts(newWords, reviewed, perfect = weaker == 0 && newWords + reviewed > 0)
        }
    }
}

/** iOS `CelebrationKind`: a finished collection adds the trophy and the turning rays. */
enum class CelebrationKind { SESSION, COLLECTION }

/** iOS `CelebrationVariant`, with when Syllie lands (the copy appears then) and its sound cues. */
enum class CelebrationVariant(val raw: String, val landingSeconds: Double) {
    PUFF("puff", 2.8), FIREWORKS("fireworks", 3.0), FLIP("flip", 2.3), LOOP("loop", 2.6), BOUNCE("bounce", 2.8);

    /** A sound in `res/raw` and when it starts, counted from the start of the animation. */
    data class SoundCue(val name: String, val atSeconds: Double)

    /** Matched to the variant's timeline in `syllie.riv` (60 fps): keep in sync when the timing changes there. */
    val soundCues: List<SoundCue>
        get() = when (this) {
            PUFF -> listOf(SoundCue("celebrate_puff_inhale", 0.30), SoundCue("celebrate_puff_pop", 0.95), SoundCue("celebrate_puff_land", 1.95))
            FIREWORKS -> listOf(SoundCue("celebrate_fireworks_rise", 0.0), SoundCue("celebrate_fireworks_finale", 2.08))
            FLIP -> listOf(SoundCue("celebrate_flip_jump", 0.60), SoundCue("celebrate_flip_spin", 0.85), SoundCue("celebrate_flip_land", 2.15))
            LOOP -> listOf(SoundCue("celebrate_loop_swoosh", 0.35), SoundCue("celebrate_loop_sparkle", 2.45))
            BOUNCE -> listOf(
                SoundCue("celebrate_bounce_boing", 0.78), SoundCue("celebrate_bounce_boing", 1.38),
                SoundCue("celebrate_bounce_launch", 1.55), SoundCue("celebrate_bounce_land", 2.48),
            )
        }

    companion object {
        /** A random variant, never the one shown last time. */
        fun pick(after: CelebrationVariant?, random: Random = Random.Default): CelebrationVariant =
            entries.filter { it != after }.random(random)

        fun of(raw: String?): CelebrationVariant? = entries.firstOrNull { it.raw == raw }
    }
}
