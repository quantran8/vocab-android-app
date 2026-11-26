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
        /** Sessions the learner aims for each week; null when the server has none. */
        val weeklySessionGoal: Int? = null,
        val days: List<Day>,
    ) {
        /** The last day that isn't ahead: today in the learner's time zone. */
        val today: Day? get() = days.lastOrNull { !it.isFuture }
        val goalReached: Boolean get() = weeklySessionGoal?.let { completedSessions >= it } ?: false

        /** New words learned so far this week, one running total per elapsed day. */
        val cumulativeNewWords: List<Int>
            get() = days.filter { !it.isFuture }.runningFold(0) { total, day -> total + (day.newWords ?: 0) }.drop(1)
    }

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
