package com.syll.features.main

import com.syll.domain.AppLanguage
import com.syll.domain.CefrLevel
import com.syll.features.auth.StubVocabApi
import com.syll.features.plan.InMemoryPreferencesStore
import com.syll.features.plan.LearningPlanStore
import com.syll.features.plan.NoReminders
import com.syll.network.ApiError
import com.syll.network.WordProgress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `LearnerWordsTests`: the My Words library pages, search and refresh. */
class LearnerWordsTest {
    private val api = StubVocabApi()
    private val store = LearnerStore(api, LearningPlanStore(InMemoryPreferencesStore(), NoReminders), language = { AppLanguage.ENGLISH }, timeZone = { "UTC" })
    private val pageSize = LearnerStore.WORDS_PAGE_SIZE

    private fun word(id: String) = WordProgress(wordId = id, lemma = id)

    private fun library(count: Int) {
        api.library = (0 until count).map { word("w$it") }.toMutableList()
    }

    @Test fun refreshPicksUpALevelSavedDuringOnboarding() = runBlocking {
        library(0)
        api.profileResult = Result.success(StubVocabApi.profile(level = null))
        store.load()
        assertNull(store.profile?.proficiencyLevel)

        api.profileResult = Result.success(StubVocabApi.profile(level = CefrLevel.B1))
        store.refresh()
        assertEquals(CefrLevel.B1, store.profile?.proficiencyLevel)
    }

    @Test fun aFailedRefreshKeepsTheProfile() = runBlocking {
        library(0)
        api.profileResult = Result.success(StubVocabApi.profile(level = CefrLevel.A2))
        store.load()

        api.profileResult = Result.failure(ApiError.Transport)
        store.refresh()
        assertEquals(CefrLevel.A2, store.profile?.proficiencyLevel)
    }

    @Test fun loadsPagesUntilALibraryRunsOut() = runBlocking {
        library(pageSize + 3)
        store.loadWords("")
        assertEquals(pageSize, store.words.size)
        assertTrue(store.hasMoreWords)

        store.loadMoreWords()
        assertEquals(pageSize + 3, store.words.size)
        assertFalse(store.hasMoreWords)

        store.loadMoreWords()
        assertEquals(pageSize + 3, store.words.size)
    }

    @Test fun searchReplacesTheListAndPagesWithinTheQuery() = runBlocking {
        library(pageSize + 3)
        store.loadWords("")

        store.loadWords("w1")
        assertEquals("w1", store.wordsQuery)
        assertTrue(store.words.all { it.lemma.contains("w1") })
        assertFalse(store.hasMoreWords)
    }

    @Test fun repeatedWordAcrossPagesAppearsOnce() = runBlocking {
        library(pageSize)
        store.loadWords("")

        api.library.add(0, word("new"))
        store.loadMoreWords()
        assertEquals(store.words.size, store.words.map { it.id }.toSet().size)
        assertEquals(pageSize, store.words.size)
    }

    @Test fun failedPageKeepsTheListAndCanRetry() = runBlocking {
        library(pageSize + 1)
        store.loadWords("")

        api.wordsError = ApiError.Transport
        store.loadMoreWords()
        assertEquals(pageSize, store.words.size)
        assertNotNull(store.wordsError)
        assertTrue(store.hasMoreWords)

        api.wordsError = null
        store.loadMoreWords()
        assertEquals(pageSize + 1, store.words.size)
        assertNull(store.wordsError)
    }

    @Test fun failedNewSearchClearsTheOldRows() = runBlocking {
        library(pageSize + 1)
        store.loadWords("")

        api.wordsError = ApiError.Transport
        store.loadWords("w1")
        assertTrue(store.words.isEmpty())
        assertFalse(store.hasMoreWords)
        assertNotNull(store.wordsError)
    }

    @Test fun answerForAnOlderQueryIsIgnored() = runBlocking {
        library(3)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        api.onWords = {
            started.complete(Unit)
            release.await()
        }
        val older = async { store.loadWords("w1") }
        started.await()
        api.onWords = null
        store.loadWords("w2")
        release.complete(Unit)
        older.await()

        assertEquals("w2", store.wordsQuery)
        assertEquals(listOf("w2"), store.words.map { it.lemma })
        assertFalse(store.isSearchingWords)
    }

    @Test fun refreshLoadsStatsAndFirstPage() = runBlocking {
        library(pageSize + 1)
        store.refresh()
        assertEquals(pageSize + 1, store.wordStats?.learnedWords)
        assertEquals(pageSize, store.words.size)
    }
}
