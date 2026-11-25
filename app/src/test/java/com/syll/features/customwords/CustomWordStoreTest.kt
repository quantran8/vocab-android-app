package com.syll.features.customwords

import com.syll.domain.customwords.CustomWord
import com.syll.domain.customwords.CustomWordAdded
import com.syll.domain.customwords.CustomWordAddition
import com.syll.domain.customwords.CustomWordLookup
import com.syll.domain.customwords.CustomWordQuery
import com.syll.domain.customwords.ExistingWord
import com.syll.domain.customwords.WordDraft
import com.syll.features.auth.StubVocabApi
import com.syll.network.ApiError
import com.syll.platform.SilentAudioClips
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port of iOS `CustomWordStoreTests`. */
class CustomWordStoreTest {
    private val api = StubVocabApi()
    private val draft = CustomWordLookup.Draft(WordDraft("d", "l", emptyList()))

    private fun CoroutineScope.store() = CustomWordStore(api, SilentAudioClips, this)

    private fun error(status: Int, code: String, retryable: Boolean = false) =
        ApiError.Server(status, code, "m", retryable = retryable, requestId = null)

    private fun word(id: String = "m") = CustomWord(
        meaningId = id, wordId = "w", lemma = "bank", partOfSpeech = "noun", meaningText = "bờ", definitionText = "định nghĩa",
        overridden = false, translationPending = false, recalled = false,
    )

    @Test
    fun lookupIsTrimmedAndShowsTheResult() = runBlocking<Unit> {
        val store = store()
        api.lookupResults += Result.success(CustomWordLookup.Invalid)
        store.lookup("  word ")
        assertEquals(listOf(CustomWordQuery("word")), api.lookups)
        assertEquals(CustomWordStore.LookupPhase.Result(CustomWordLookup.Invalid), store.phase)
    }

    @Test
    fun providerOutageOffersRetryAndLimitDoesNot() = runBlocking<Unit> {
        val store = store()
        api.lookupResults += Result.failure(error(503, "LEXICAL_PROVIDER_UNAVAILABLE", retryable = true))
        api.lookupResults += Result.failure(error(429, "CUSTOM_WORD_LIMIT_REACHED"))
        store.lookup("word")
        assertEquals(true, (store.phase as? CustomWordStore.LookupPhase.Failed)?.canRetry)
        store.retryLookup()
        assertEquals(false, (store.phase as? CustomWordStore.LookupPhase.Failed)?.canRetry)
        assertEquals(2, api.lookups.size)
    }

    @Test
    fun addRefreshesHomeAndAcceptsExistingWord() = runBlocking<Unit> {
        val store = store()
        var refreshes = 0
        store.onRoadmapChanged = { refreshes += 1 }
        api.addResults += Result.success(CustomWordAdded("m", added = false))
        store.add(CustomWordAddition.meaning("m"), lemma = "bank")
        assertEquals(CustomWordStore.LookupPhase.Added("bank", isNew = false), store.phase)
        assertEquals(1, refreshes)
    }

    @Test
    fun multiAddKeepsSuccessesAndRetriesOnlyFailures() = runBlocking<Unit> {
        val store = store()
        api.lookupResults += Result.success(draft)
        store.lookup("bank")
        api.addResults += Result.success(CustomWordAdded("a", added = true))
        api.addResults += Result.failure(error(503, "UNAVAILABLE", retryable = true))
        val selections = listOf(
            CustomWordStore.Selection("a", CustomWordAddition.meaning("a"), "river bank"),
            CustomWordStore.Selection("b", CustomWordAddition.meaning("b"), "financial bank"),
        )
        store.addSelected(selections, "bank")
        assertEquals(CustomWordStore.LookupPhase.Result(draft), store.phase)
        assertEquals(setOf("a"), store.addedSenseIds)
        assertNotNull(store.notice)
        api.addResults += Result.success(CustomWordAdded("b", added = true))
        store.addSelected(selections, "bank")
        assertEquals(listOf("river bank", "financial bank"), store.addedMeanings)
        assertEquals(setOf("a", "b"), store.addedSenseIds)
        assertNull(store.notice)
        assertTrue(store.phase is CustomWordStore.LookupPhase.Added)
        assertTrue(api.addResults.isEmpty())
    }

    @Test
    fun staleDraftRepeatsTheLookupSilently() = runBlocking<Unit> {
        val store = store()
        for ((status, code) in listOf(409 to "WORD_ALREADY_EXISTS", 422 to "CUSTOM_WORD_INVALID")) {
            api.lookupResults += Result.success(draft)
            api.lookupResults += Result.success(CustomWordLookup.Invalid)
            api.addResults += Result.failure(error(status, code))
            store.lookup("word", context = "river")
            store.add(CustomWordAddition.draft("d", 0, null), lemma = "word")
            assertEquals(listOf("river", "river"), api.lookups.takeLast(2).map { it.context })
            assertEquals(CustomWordStore.LookupPhase.Result(CustomWordLookup.Invalid), store.phase)
            assertNull(store.notice)
        }
    }

    @Test
    fun unavailableMeaningRefreshesStatusAndSaysSo() = runBlocking<Unit> {
        val store = store()
        api.lookupResults += Result.success(draft)
        api.lookupResults += Result.success(CustomWordLookup.Invalid)
        api.addResults += Result.failure(error(422, "CUSTOM_WORD_UNAVAILABLE"))
        store.lookup("word")
        store.add(CustomWordAddition.meaning("m"), lemma = "word")
        assertEquals(2, api.lookups.size)
        assertNotNull(store.notice)
    }

    @Test
    fun otherAddFailuresKeepTheResult() = runBlocking<Unit> {
        val store = store()
        api.lookupResults += Result.success(draft)
        api.addResults += Result.failure(error(429, "CUSTOM_WORD_LIMIT_REACHED"))
        store.lookup("word")
        store.add(CustomWordAddition.draft("d", 0, null), lemma = "word")
        assertEquals(CustomWordStore.LookupPhase.Result(draft), store.phase)
        assertEquals("The limit is never retried automatically", 1, api.lookups.size)
        assertNotNull(store.notice)
    }

    @Test
    fun answerForAClosedSheetIsDropped() = runBlocking<Unit> {
        val store = store()
        api.lookupResults += Result.success(CustomWordLookup.Invalid)
        api.onLookup = { store.resetLookup() }
        store.lookup("word")
        assertEquals(CustomWordStore.LookupPhase.Idle, store.phase)
    }

    @Test
    fun goingBackFromAnOtherMeaningSearchReturnsToTheWord() = runBlocking<Unit> {
        val store = store()
        val word = ExistingWord("w", "arrive", emptyList())
        api.lookupResults += Result.success(CustomWordLookup.Existing(word))
        api.lookupResults += Result.success(CustomWordLookup.NewMeaningDraft(WordDraft("d", "arrive", emptyList())))
        store.lookup("arrived")
        store.lookup("arrive", context = "at a decision")
        assertEquals("at a decision", store.lastContext)

        store.goBack()
        assertEquals(CustomWordStore.LookupPhase.Result(CustomWordLookup.Existing(word)), store.phase)
        assertEquals("Going back doesn't look the word up again", 2, api.lookups.size)

        store.goBack()
        assertEquals(CustomWordStore.LookupPhase.Idle, store.phase)
    }

    @Test
    fun failedOtherMeaningSearchKeepsItsContextAndWord() = runBlocking<Unit> {
        val store = store()
        val word = ExistingWord("w", "arrive", emptyList())
        api.lookupResults += Result.success(CustomWordLookup.Existing(word))
        api.lookupResults += Result.failure(error(503, "LEXICAL_PROVIDER_UNAVAILABLE", retryable = true))
        store.lookup("arrive")
        store.lookup("arrive", context = "at a decision")
        assertEquals(word, store.baseWord)
        assertEquals("at a decision", store.lastContext)
    }

    @Test
    fun changesReloadTheList() = runBlocking<Unit> {
        val store = store()
        api.customWordList = listOf(word())
        store.loadWords()
        api.customWordList = emptyList()
        store.remove(word())
        assertEquals(listOf("m"), api.removed)
        assertTrue(store.words.isEmpty())
    }

    @Test
    fun removingAWordAlreadyGoneIsNotAnError() = runBlocking<Unit> {
        val store = store()
        api.customWordList = listOf(word())
        store.loadWords()
        api.customWordList = emptyList()
        api.removeError = error(404, "NOT_FOUND")
        store.remove(word())
        assertNull(store.listError)
        assertTrue(store.words.isEmpty())
    }

    @Test
    fun failedChangeKeepsTheList() = runBlocking<Unit> {
        val store = store()
        api.customWordList = listOf(word())
        store.loadWords()
        api.removeError = ApiError.Transport
        store.remove(word())
        assertNotNull(store.listError)
        assertEquals(1, store.words.size)
    }
}
