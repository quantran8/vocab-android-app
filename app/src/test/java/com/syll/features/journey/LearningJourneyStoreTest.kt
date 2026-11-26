package com.syll.features.journey

import com.syll.domain.journey.JourneyPeriod
import com.syll.domain.journey.LearningJourney
import com.syll.features.auth.StubVocabApi
import com.syll.network.ApiError
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** The store part of iOS `LearningJourneyTests`. */
class LearningJourneyStoreTest {
    private val api = StubVocabApi()
    private val journey = LearningJourney(
        asOf = Instant.parse("2026-11-25T04:00:00Z"),
        timezone = "Asia/Ho_Chi_Minh",
        historyAvailableFrom = "2026-08-01",
        week = LearningJourney.Week("2026-11-23", "2026-11-30", 7, 3, 2, 3, days = emptyList()),
        period = LearningJourney.Period("week", "2026-11-23", "2026-11-30", "day", 241, 248, 7, 2, 3, 12),
    )

    @Test
    fun keepsTheLastSummaryWhenARefreshFails() = runBlocking {
        api.journeyResults[JourneyPeriod.WEEK] = Result.success(journey)
        val store = LearningJourneyStore(api)
        store.refreshSummary()
        assertNotNull(store.summary)

        api.journeyResults[JourneyPeriod.WEEK] = Result.failure(ApiError.Transport)
        store.refreshSummary(force = true)
        assertNotNull("a failed refresh must not blank the card", store.summary)
        assertTrue(store.summaryLoad is LearningJourneyStore.Load.Failed)
    }

    @Test
    fun aFreshSummaryIsNotReloaded() = runBlocking {
        api.journeyResults[JourneyPeriod.WEEK] = Result.success(journey)
        val store = LearningJourneyStore(api)
        store.refreshSummary()
        store.refreshSummary()
        assertEquals(listOf(JourneyPeriod.WEEK), api.journeyPeriodsRequested)
        store.refreshSummary(force = true)
        assertEquals(listOf(JourneyPeriod.WEEK, JourneyPeriod.WEEK), api.journeyPeriodsRequested)
    }

    @Test
    fun resetDropsEverythingForTheNextAccount() = runBlocking {
        api.journeyResults[JourneyPeriod.MONTH] = Result.success(journey)
        val store = LearningJourneyStore(api)
        store.load(JourneyPeriod.MONTH)
        assertNotNull(store.periods[JourneyPeriod.MONTH])
        store.reset()
        assertNull(store.summary)
        assertTrue(store.periods.isEmpty())
    }

    @Test
    fun moreWordsAppendToTheLoadedPage() = runBlocking {
        fun word(id: String) = com.syll.domain.journey.JourneyWord(id, id, emptyList(), "2026-11-26")
        api.journeyWordPages += com.syll.domain.journey.JourneyWordsPage(3, listOf(word("a"), word("b")))
        api.journeyWordPages += com.syll.domain.journey.JourneyWordsPage(3, listOf(word("c")))
        val store = LearningJourneyStore(api)
        store.loadWords(JourneyPeriod.MONTH)
        store.loadWords(JourneyPeriod.MONTH, more = true)
        assertEquals(listOf("a", "b", "c"), store.words[JourneyPeriod.MONTH]?.map { it.wordId })
        assertEquals(listOf(0, 2), api.journeyWordOffsets)
        store.loadWords(JourneyPeriod.MONTH, more = true)
        assertEquals("nothing left to load", listOf(0, 2), api.journeyWordOffsets)
    }

    @Test
    fun theChartStartsAtTheBaselineAndReplacesIt() {
        val period = journey.period.copy(points = listOf(LearningJourney.Point("2026-11-23", 246), LearningJourney.Point("2026-11-25", 248)))
        assertEquals(listOf(246, 248), samples(period).map { it.second })
        val later = journey.period.copy(points = listOf(LearningJourney.Point("2026-11-24", 246)))
        assertEquals(listOf(241, 246), samples(later).map { it.second })
    }
}
