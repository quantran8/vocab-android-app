package com.syll.platform

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** When a learning day's reminder goes off next (weekdays numbered like iOS: 1 = Sunday … 7 = Saturday). */
class ReminderScheduleTest {
    private val zone: ZoneId = ZoneOffset.UTC
    // Wednesday 25 November 2026, 10:00.
    private val now = LocalDateTime.of(2026, 11, 25, 10, 0)

    private fun trigger(weekday: Int, hour: Int, minute: Int = 0) =
        LocalDateTime.ofEpochSecond(AlarmReminderScheduler.nextTrigger(weekday, hour, minute, now, zone) / 1000, 0, ZoneOffset.UTC)

    @Test fun laterTodayCounts() {
        assertEquals(LocalDateTime.of(2026, 11, 25, 20, 0), trigger(weekday = 4, hour = 20))
    }

    @Test fun aTimeAlreadyPastTodayMovesToNextWeek() {
        assertEquals(LocalDateTime.of(2026, 12, 2, 9, 30), trigger(weekday = 4, hour = 9, minute = 30))
        // Exactly now is past too: the alarm would fire at once.
        assertEquals(LocalDateTime.of(2026, 12, 2, 10, 0), trigger(weekday = 4, hour = 10))
    }

    @Test fun sundayIsDayOneAndSaturdaySeven() {
        assertEquals(LocalDateTime.of(2026, 11, 29, 8, 0), trigger(weekday = 1, hour = 8))
        assertEquals(LocalDateTime.of(2026, 11, 28, 8, 0), trigger(weekday = 7, hour = 8))
        assertEquals(LocalDateTime.of(2026, 11, 26, 8, 0), trigger(weekday = 5, hour = 8))
    }
}
