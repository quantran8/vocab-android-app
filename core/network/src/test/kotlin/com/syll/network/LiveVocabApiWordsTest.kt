package com.syll.network

import com.syll.domain.practice.SpeakingStatus
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** My Words endpoints: paging/search query, stats, and the word page decoding with optional parts. */
class LiveVocabApiWordsTest {
    private lateinit var server: MockWebServer
    private lateinit var api: LiveVocabApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val tokens = InMemoryTokenStore(AuthTokens("access", "refresh", System.currentTimeMillis() + 3_600_000))
        api = LiveVocabApi(ApiClient(baseUrl = server.url("/").toString(), tokens = tokens, pause = {}))
    }

    @After
    fun tearDown() = server.shutdown()

    private fun enqueue(body: String) = server.enqueue(MockResponse().setResponseCode(200).setBody(body))

    @Test
    fun wordsSendsPagingAndQuery() = runBlocking {
        enqueue("""{"words":[{"wordId":"w1","lemma":"read","meaningCount":3,"ipaVaries":true,
            "meanings":[{"meaningId":"m1","meaning":{"partOfSpeech":"verb","text":"đọc","ipa":"/riːd/"},"learnedAt":"2026-11-01T00:00:00Z","nextReviewAt":null}]}]}""")
        enqueue("""{"words":[]}""")
        val page = api.words(limit = 50, offset = 0, query = "re ad")
        api.words(limit = 50, offset = 50, query = "")
        assertEquals("/v1/me/words?limit=50&offset=0&q=re%20ad", server.takeRequest().path)
        // An empty query isn't sent.
        assertEquals("/v1/me/words?limit=50&offset=50", server.takeRequest().path)
        assertEquals(3, page.single().meaningCount)
        // Meanings pronounced differently: no single IPA for the word.
        assertNull(page.single().ipa)
    }

    @Test
    fun ipaIsTheSharedPronunciation() {
        fun word(vararg ipas: String?) = WordProgress(
            "w", "x",
            ipas.mapIndexed { i, ipa -> WordMeaning("m$i", WordMeaning.Sense("noun", "t", ipa)) },
        )
        assertEquals("/a/", word("/a/", "/a/").ipa)
        assertNull(word("/a/", "/b/").ipa)
        assertNull(word(null).ipa)
    }

    @Test
    fun statsFromAnOlderBackendHaveNoFluentCount() = runBlocking {
        enqueue("""{"learnedWords":12,"needPracticeWords":3}""")
        val stats = api.wordStats()
        assertEquals("/v1/me/words/stats", server.takeRequest().path)
        assertEquals(12, stats.learnedWords)
        assertNull(stats.fluentWords)
    }

    @Test
    fun wordDetailDecodesMemoryAndSpeakingWhenPresent() = runBlocking {
        enqueue("""{"wordId":"w1","lemma":"clarify","meanings":[
            {"meaningId":"m1","meaning":{"partOfSpeech":"verb","text":"làm rõ","ipa":"/x/","audio":{"url":"https://a/b.mp3","locale":"en-US"},
              "examples":[{"text":"Clarify it.","translation":null}]},"learnedAt":"2026-11-01T00:00:00Z",
             "memory":{"stabilityDays":4.5,"retrievability":0.82,"nextReviewAt":"2026-11-30T00:00:00Z"},
             "speaking":{"status":"practicing","lastScore":71,"bestScore":88},"needsPractice":true},
            {"meaningId":"m2","meaning":{"partOfSpeech":"verb","text":"lọc"},"learnedAt":null}]}""")
        val detail = api.wordDetail("w1")
        assertEquals("/v1/words/w1", server.takeRequest().path)
        val (learned, notLearned) = detail.meanings
        assertEquals(0.82, learned.memory?.retrievability)
        assertEquals(SpeakingStatus.PRACTICING, learned.speaking?.status)
        assertEquals(88.0, learned.speaking?.bestScore)
        assertEquals(true, learned.needsPractice)
        assertNull(notLearned.learnedAt)
        assertNull(notLearned.memory)
    }
}
