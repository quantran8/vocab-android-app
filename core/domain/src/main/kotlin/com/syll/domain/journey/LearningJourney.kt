package com.syll.domain.journey

import com.syll.domain.Iso8601InstantSerializer
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate

/** iOS `JourneyPeriod`: the period the journey detail shows. Home always shows the current week. */
enum class JourneyPeriod(val raw: String) { WEEK("week"), MONTH("month"), ALL("all") }

/**
 * iOS `LearningJourney`: `GET /me/learning-journey` (backend FRONTEND_FLOW §17), history only, built from committed
 * sessions. Dates are the learner's local days as `yyyy-MM-dd`, in the server's `timezone`.
 */
@Serializable
data class LearningJourney(
    @Serializable(with = Iso8601InstantSerializer::class) val asOf: Instant,
    val timezone: String,
    /** The first day with any learning; null for a learner who hasn't finished a session yet. */
    val historyAvailableFrom: String? = null,
    val week: Week,
    val period: Period,
) {
    val hasHistory: Boolean get() = historyAvailableFrom != null

    @Serializable
    data class Day(
        val date: String,
        /** Null for days still ahead. */
        val completed: Boolean? = null,
        val completedSessions: Int? = null,
        val newWords: Int? = null,
    ) {
        val isFuture: Boolean get() = completed == null
        val localDate: LocalDate? get() = runCatching { LocalDate.parse(date) }.getOrNull()
    }

    @Serializable
    data class Week(
        val startDate: String,
        val endDateExclusive: String,
        val newWords: Int,
        val completedSessions: Int,
        val activeDays: Int,
        /** Study days the learner aims for each week (the app sets it to the days they chose); null when unset. */
        val weeklySessionGoal: Int? = null,
        /** The week's newest words, at most three. Null from an older backend. */
        val recentWords: List<RecentWord>? = null,
        val days: List<Day>,
    ) {
        /** The last day that isn't ahead: today in the learner's time zone. */
        val today: Day? get() = days.lastOrNull { !it.isFuture }
        /** The goal counts study days, never sessions: a Daily split in two still makes one day. */
        val goalReached: Boolean get() = weeklySessionGoal?.let { activeDays >= it } ?: false
        /** The days that earned a stamp, in order. */
        val studiedDays: List<Day> get() = days.filter { it.completed == true }
        /** New words beyond the ones named on Home. */
        val otherNewWords: Int get() = maxOf(0, newWords - (recentWords?.size ?: 0))
    }

    @Serializable
    data class RecentWord(val wordId: String, val lemma: String)

    @Serializable
    data class Point(
        /** The bucket's last day (today for the bucket in progress). */
        val date: String,
        val cumulativeWords: Int,
    )

    @Serializable
    data class Period(
        val kind: String,
        val startDate: String,
        val endDateExclusive: String,
        /** day | month | quarter | year */
        val bucketGranularity: String,
        val wordsAtStart: Int? = null,
        val wordsAtEnd: Int? = null,
        val newWords: Int? = null,
        val activeDays: Int? = null,
        val completedSessions: Int? = null,
        val reviewedMeanings: Int? = null,
        val points: List<Point> = emptyList(),
    )
}

/** iOS `JourneyWordsPage`: `GET /me/learning-journey/words`, the period's new words, newest first. */
@Serializable
data class JourneyWordsPage(val total: Int, val items: List<JourneyWord>)

@Serializable
data class JourneyWord(
    val wordId: String,
    val lemma: String,
    /** The meanings of the word learned in the period, in learned order. */
    val meaningTexts: List<String> = emptyList(),
    /** The local day the word was first learned. */
    val learnedOn: String,
) {
    val meaningText: String get() = meaningTexts.joinToString("; ")
}

/** iOS `JourneyWordDay`: one day of the new-words timeline. */
data class JourneyWordDay(val date: String, val words: List<JourneyWord>) {
    companion object {
        /** Pages arrive newest first, so a day can span two pages; it stays one group. */
        fun grouped(words: List<JourneyWord>): List<JourneyWordDay> {
            val days = mutableListOf<JourneyWordDay>()
            for (word in words) {
                val last = days.lastOrNull()
                if (last != null && last.date == word.learnedOn) {
                    days[days.lastIndex] = last.copy(words = last.words + word)
                } else {
                    days += JourneyWordDay(word.learnedOn, listOf(word))
                }
            }
            return days
        }
    }
}
