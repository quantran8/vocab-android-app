package com.syll.network

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Port of iOS `APIClientTests`. */
class ApiClientTest {
    @Serializable
    private data class LearnHomeStub(val dueCount: Int)

    private lateinit var server: MockWebServer
    private lateinit var tokens: InMemoryTokenStore

    private val homeRequest = ApiRequest(ApiRequest.Method.GET, "me/learn-home")

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        tokens = InMemoryTokenStore(AuthTokens("old", "r1", System.currentTimeMillis() + 3_600_000))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun makeClient() = ApiClient(baseUrl = server.url("/").toString(), tokens = tokens, pause = {})

    private fun enqueue(status: Int, body: String) {
        server.enqueue(MockResponse().setResponseCode(status).setBody(body))
    }

    private fun requests(): List<RecordedRequest> =
        generateSequence { server.takeRequest(10, TimeUnit.MILLISECONDS) }.toList()

    @Test
    fun sendsBearerTokenUnderV1() = runBlocking {
        enqueue(200, HOME)
        makeClient().send<LearnHomeStub>(homeRequest)
        val request = server.takeRequest()
        assertEquals("/v1/me/learn-home", request.path)
        assertEquals("Bearer old", request.getHeader("Authorization"))
    }

    @Test
    fun refreshesOnceAndRetriesAfter401() = runBlocking {
        enqueue(401, UNAUTHORIZED)
        enqueue(200, REFRESHED)
        enqueue(200, HOME)
        val home = makeClient().send<LearnHomeStub>(homeRequest)
        assertEquals(1, home.dueCount)
        assertEquals("Bearer new", requests().last().getHeader("Authorization"))
        assertEquals("Refresh tokens are single use", "r2", tokens.load()?.refreshToken)
    }

    @Test
    fun rejectedRefreshEndsTheSession() = runBlocking {
        enqueue(401, UNAUTHORIZED)
        enqueue(401, UNAUTHORIZED)
        val client = makeClient()
        var ended = false
        client.setSessionEndedHandler { ended = true }
        try {
            client.send<LearnHomeStub>(homeRequest)
            fail("Expected a failure")
        } catch (error: ApiError) {
            assertEquals(401, error.status)
        }
        assertTrue(ended)
        assertNull(tokens.load())
    }

    @Test
    fun refreshOutageKeepsTheSession() = runBlocking {
        enqueue(401, UNAUTHORIZED)
        enqueue(503, """{"code":"AUTH_PROVIDER_UNAVAILABLE","message":"x","retryable":true,"requestId":"q"}""")
        try {
            makeClient().send<LearnHomeStub>(homeRequest)
            fail("Expected a failure")
        } catch (error: ApiError) {
            assertEquals("AUTH_PROVIDER_UNAVAILABLE", error.code)
        }
        assertEquals("r1", tokens.load()?.refreshToken)
    }

    @Test
    fun expiringTokenIsRefreshedUpFront() = runBlocking {
        tokens.save(AuthTokens("old", "r1", System.currentTimeMillis() + 5_000))
        enqueue(200, REFRESHED)
        enqueue(200, HOME)
        makeClient().send<LearnHomeStub>(homeRequest)
        val recorded = requests()
        assertEquals(2, recorded.size)
        assertTrue(recorded[0].path!!.endsWith("auth/refresh"))
    }

    @Test
    fun concurrent401sShareOneRefresh() = runBlocking {
        tokens.save(AuthTokens("old", "r1", System.currentTimeMillis() + 5_000))
        enqueue(200, REFRESHED)
        enqueue(200, HOME)
        enqueue(200, HOME)
        val client = makeClient()
        listOf(async { client.send<LearnHomeStub>(homeRequest) }, async { client.send<LearnHomeStub>(homeRequest) })
            .awaitAll()
        assertEquals(1, requests().count { it.path!!.endsWith("auth/refresh") })
    }

    @Test
    fun retryableFailureBacksOffWhenAllowed() = runBlocking {
        enqueue(500, """{"code":"INTERNAL_ERROR","message":"x","retryable":true,"requestId":"q"}""")
        enqueue(200, HOME)
        makeClient().send<LearnHomeStub>(homeRequest.copy(retries = 1))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun errorDetailsAreReadAsText() = runBlocking {
        enqueue(409, """{"code":"RUN_ACTIVE","message":"x","retryable":false,"requestId":"q","details":{"runId":"r-1","n":3,"nested":{"a":1}}}""")
        try {
            makeClient().send<LearnHomeStub>(homeRequest)
            fail("Expected a failure")
        } catch (error: ApiError) {
            assertEquals("r-1", error.detail("runId"))
            assertEquals("3", error.detail("n"))
            assertEquals("", error.detail("nested"))
        }
    }

    @Test
    fun bodyWithoutTheErrorShapeIsAGenericServerError() = runBlocking {
        enqueue(502, "<html>bad gateway</html>")
        try {
            makeClient().send<LearnHomeStub>(homeRequest)
            fail("Expected a failure")
        } catch (error: ApiError) {
            assertEquals("HTTP_502", error.code)
            assertTrue(error.isRetryable)
        }
    }

    private companion object {
        const val REFRESHED = """{"accessToken":"new","refreshToken":"r2","expiresIn":3600,"user":{"id":"u","email":null}}"""
        const val HOME = """{"dueCount":1,"checkpointAvailable":0,"learnedTotal":2}"""
        const val UNAUTHORIZED = """{"code":"UNAUTHORIZED","message":"no","retryable":false,"requestId":"q"}"""
    }
}
