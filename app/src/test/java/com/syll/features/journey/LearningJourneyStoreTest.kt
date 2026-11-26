package com.syll.features.journey

import com.syll.domain.journey.JourneyPeriod
import com.syll.domain.journey.LearningJourney
import com.syll.features.auth.StubVocabApi
import com.syll.network.ApiError
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun word(id: String) = com.syll.domain.journey.JourneyWord(id, id, emptyList(), "2026-11-26")

    @Test
    fun moreTopicWordsAppendToTheLoadedPageAndAskForTheTopic() = runBlocking {
        api.journeyWordPages += com.syll.domain.journey.JourneyWordsPage(3, listOf(word("a"), word("b")))
        api.journeyWordPages += com.syll.domain.journey.JourneyWordsPage(3, listOf(word("c")))
        val store = LearningJourneyStore(api)
        val key = LearningJourneyStore.TopicWordsKey(JourneyPeriod.MONTH, "travel-id")
        store.loadTopicWords(key)
        assertTrue(store.canLoadMoreTopicWords(key))
        store.loadTopicWords(key, more = true)
        assertEquals(listOf("a", "b", "c"), store.topicWords[key]?.map { it.wordId })
        assertEquals(listOf(0, 2), api.journeyWordOffsets)
        assertEquals(listOf("travel-id", "travel-id"), api.journeyWordTopics)
        assertFalse(store.canLoadMoreTopicWords(key))
        store.loadTopicWords(key, more = true)
        assertEquals("nothing left to load", listOf(0, 2), api.journeyWordOffsets)
    }

    @Test
    fun ownWordsAskForNoneAndTopicsArePagedApart() = runBlocking {
        api.journeyWordPages += com.syll.domain.journey.JourneyWordsPage(1, listOf(word("mine")))
        api.journeyWordPages += com.syll.domain.journey.JourneyWordsPage(1, listOf(word("trip")))
        val store = LearningJourneyStore(api)
        val own = LearningJourney.TopicNewWords(topicId = null, count = 1)
        val ownKey = LearningJourneyStore.TopicWordsKey(JourneyPeriod.WEEK, own.wordsQueryKey)
        val travelKey = LearningJourneyStore.TopicWordsKey(JourneyPeriod.WEEK, "travel-id")
        store.loadTopicWords(ownKey)
        store.loadTopicWords(travelKey)
        assertEquals(listOf("none", "travel-id"), api.journeyWordTopics)
        assertEquals(listOf("mine"), store.topicWords[ownKey]?.map { it.wordId })
        assertEquals(listOf("trip"), store.topicWords[travelKey]?.map { it.wordId })
        assertNull("another period is its own page", store.topicWords[LearningJourneyStore.TopicWordsKey(JourneyPeriod.MONTH, "travel-id")])
        store.loadTopicWords(ownKey)
        assertEquals("a loaded topic isn't reloaded", 2, api.journeyWordTopics.size)
    }

    @Test
    fun aFailedNextPageKeepsTheLoadedRowsAndStopsAutoLoading() = runBlocking {
        api.journeyWordPages += com.syll.domain.journey.JourneyWordsPage(3, listOf(word("a"), word("b")))
        val store = LearningJourneyStore(api)
        val key = LearningJourneyStore.TopicWordsKey(JourneyPeriod.MONTH, "t")
        store.loadTopicWords(key)
        store.loadTopicWords(key, more = true) // the stub has no page left: fails offline
        assertEquals(listOf("a", "b"), store.topicWords[key]?.map { it.wordId })
        assertTrue(store.topicWordLoads[key] is LearningJourneyStore.Load.Failed)
        assertFalse("a failure waits for a retry", store.canLoadMoreTopicWords(key))
        api.journeyWordPages += com.syll.domain.journey.JourneyWordsPage(3, listOf(word("c")))
        store.loadTopicWords(key, more = true)
        assertEquals(listOf("a", "b", "c"), store.topicWords[key]?.map { it.wordId })
    }

    @Test
    fun invalidateAndResetDropTopicPages() = runBlocking {
        val key = LearningJourneyStore.TopicWordsKey(JourneyPeriod.MONTH, "t")
        api.journeyWordPages += com.syll.domain.journey.JourneyWordsPage(1, listOf(word("a")))
        val store = LearningJourneyStore(api)
        store.loadTopicWords(key)
        store.invalidate()
        assertNull("a commit may have added words", store.topicWords[key])
        api.journeyWordPages += com.syll.domain.journey.JourneyWordsPage(1, listOf(word("a")))
        store.loadTopicWords(key)
        store.reset()
        assertTrue(store.topicWords.isEmpty())
        assertTrue(store.topicWordLoads.isEmpty())
    }

    @Test
    fun theChartStartsAtTheBaselineAndReplacesIt() {
        val period = journey.period.copy(points = listOf(LearningJourney.Point("2026-11-23", 246), LearningJourney.Point("2026-11-25", 248)))
        assertEquals(listOf(246, 248), samples(period).map { it.second })
        val later = journey.period.copy(points = listOf(LearningJourney.Point("2026-11-24", 246)))
        assertEquals(listOf(241, 246), samples(later).map { it.second })
    }
}
