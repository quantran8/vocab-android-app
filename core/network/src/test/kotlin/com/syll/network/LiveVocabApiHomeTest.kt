package com.syll.network

import com.syll.domain.NewWordReason

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

/** Home endpoints, and the decoding part of iOS `ScopeReviewLoadTests`. */
class LiveVocabApiHomeTest {
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
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueue(body: String = "") {
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
    }

    @Test
    fun unknownReasonDoesNotFailTheHome() = runBlocking {
        enqueue(
            """{"dueCount":34,"checkpointAvailable":0,"checkpointSessionsDone":0,"checkpointSessionsRequired":7,"learnedTotal":80,
               "scope":{"status":"exhausted","remainingNewMeanings":0,"pendingCustomMeanings":3,"sessionsLeftEstimate":null,"topics":[]},
               "reviewLoad":{"backlog":34,"dueCap":10,"effectiveNewPerSession":2,"newWordReason":"something_new",
                             "override":{"value":2,"mode":"until_date","until":"2026-11-15T16:59:59.000Z"},"autoBalance":false,"promptChoice":true}}""",
        )
        val home = api.learnHome()
        assertEquals("/v1/me/learn-home", server.takeRequest().path)
        assertEquals(NewWordReason.UNKNOWN, home.reviewLoad?.newWordReason)
        assertEquals(NewWordOverrideMode.UNTIL_DATE, home.reviewLoad?.override?.mode)
        assertEquals(Instant.parse("2026-11-15T16:59:59Z"), home.reviewLoad?.override?.until)
        assertEquals(ScopeStatus.EXHAUSTED, home.scope?.status)
    }

    @Test
    fun overrideIsAPutWithTheModeAndAnOptionalDate() = runBlocking {
        enqueue("""{"override":{"value":2,"mode":"until_backlog_clear","until":null}}""")
        enqueue("""{"override":{"value":3,"mode":"until_date","until":"2026-11-15T16:59:59Z"}}""")
        val first = api.setNewWordOverride(2, NewWordOverrideMode.UNTIL_BACKLOG_CLEAR)
        api.setNewWordOverride(3, NewWordOverrideMode.UNTIL_DATE, Instant.parse("2026-11-15T16:59:59Z"))
        assertEquals(2, first.value)
        val request = server.takeRequest()
        assertEquals("PUT /v1/me/new-word-override", "${request.method} ${request.path}")
        assertEquals("""{"value":2,"mode":"until_backlog_clear"}""", request.body.readUtf8())
        assertEquals("""{"value":3,"mode":"until_date","until":"2026-11-15T16:59:59Z"}""", server.takeRequest().body.readUtf8())
    }

    @Test
    fun ackClearAndPauses() = runBlocking {
        repeat(4) { enqueue() }
        api.acknowledgeReviewLoad(34)
        api.clearNewWordOverride()
        api.setReviewPaused("c1", paused = true)
        api.setReviewPaused("c1", paused = false)
        val ack = server.takeRequest()
        assertEquals("POST /v1/me/review-load/ack", "${ack.method} ${ack.path}")
        assertEquals("""{"backlog":34}""", ack.body.readUtf8())
        assertEquals("DELETE /v1/me/new-word-override", server.takeRequest().let { "${it.method} ${it.path}" })
        assertEquals("PUT /v1/me/review-pauses/c1", server.takeRequest().let { "${it.method} ${it.path}" })
        assertEquals("DELETE /v1/me/review-pauses/c1", server.takeRequest().let { "${it.method} ${it.path}" })
    }
}
