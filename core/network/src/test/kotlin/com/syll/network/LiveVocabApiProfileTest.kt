package com.syll.network

import com.syll.domain.CefrLevel
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Profile, settings, scope, catalog and placement endpoints (FRONTEND_FLOW §3–§5), and iOS `PlacementTestStoreTests` decoding. */
class LiveVocabApiProfileTest {
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

    private fun enqueue(body: String = "", status: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(status).setBody(body))
    }

    @Test
    fun decodesAProfileWithUnknownAndMissingFields() = runBlocking {
        enqueue(
            """{"id":"u1","proficiencyLevel":"B1","learningLevel":null,"futureField":1,
               "settings":{"explanationLocale":"vi","uiLocale":"vi-VN","timeZone":"Asia/Ho_Chi_Minh","dailyNewWordGoal":5},
               "scope":{"collectionId":"c","topicIds":["t"]},
               "plan":{"tier":"platinum","limits":{"maxTopics":2,"maxNewWords":20,"newWordsUsed":25}}}""",
        )
        val profile = api.profile()
        assertEquals(CefrLevel.B1, profile.proficiencyLevel)
        assertTrue(profile.hasFinishedOnboarding)
        assertNull(profile.settings.reviewsPerSession)
        assertEquals(PremiumPlan.Tier.FREE, profile.plan?.tier)
        assertEquals(0, profile.plan?.limits?.newWordsLeft)
        assertEquals("/v1/me", server.takeRequest().path)
    }

    @Test
    fun settingsLeaveOutNullsAndCanSendAnExplicitNull() = runBlocking {
        enqueue()
        enqueue()
        api.updateSettings(SettingsUpdate(dailyNewWordGoal = 7, weeklySessionGoal = 5))
        api.updateSettings(SettingsUpdate(sendsReviewsPerSession = true, reviewsPerSession = null))
        val first = server.takeRequest()
        assertEquals("PUT", first.method)
        assertEquals("/v1/me/settings", first.path)
        assertEquals("""{"dailyNewWordGoal":7,"weeklySessionGoal":5}""", first.body.readUtf8())
        assertEquals("""{"reviewsPerSession":null}""", server.takeRequest().body.readUtf8())
    }

    @Test
    fun scopeAndLevelArePuts() = runBlocking {
        enqueue()
        enqueue()
        api.updateScope(LearningScope("c", listOf("t1", "t2")))
        api.updateLevels(CefrLevel.C1)
        val scope = server.takeRequest()
        assertEquals("PUT /v1/me/scope", "${scope.method} ${scope.path}")
        assertEquals("""{"collectionId":"c","topicIds":["t1","t2"]}""", scope.body.readUtf8())
        val level = server.takeRequest()
        assertEquals("PUT /v1/me/levels", "${level.method} ${level.path}")
        assertEquals("""{"proficiencyLevel":"C1"}""", level.body.readUtf8())
    }

    @Test
    fun decodesPlacementQuestionsWithStringOptionIdsAndNoAnswerKey() = runBlocking {
        enqueue(
            """{"questions":[{"id":"q1","template":"select","variant":"gloss","prompt":{"type":"text","text":"clarify"},
               "question":"Từ này có nghĩa là gì?","options":[{"id":"0190-a","text":"làm rõ"},{"id":"0190-b","text":"x"}]}]}""",
        )
        val question = api.placementTest().single()
        assertEquals("clarify", question.prompt.text)
        assertEquals(listOf("0190-a", "0190-b"), question.options.map { it.id })
    }

    @Test
    fun aSkippedQuestionIsSentAsAnExplicitNull() = runBlocking {
        enqueue("""{"proficiencyLevel":"A2","correctCount":1,"totalCount":2}""")
        val result = api.submitPlacementTest(listOf(PlacementAnswer("q1", "a"), PlacementAnswer("q2", null)))
        assertEquals(CefrLevel.A2, result.proficiencyLevel)
        val request = server.takeRequest()
        assertEquals("POST /v1/me/placement-test", "${request.method} ${request.path}")
        assertEquals(
            """{"answers":[{"questionId":"q1","optionId":"a"},{"questionId":"q2","optionId":null}]}""",
            request.body.readUtf8(),
        )
    }

    @Test
    fun catalogUnwrapsCollections() = runBlocking {
        enqueue("""{"collections":[{"id":"c","slug":"everyday","title":"Hằng ngày","topics":[{"id":"t","slug":"travel","title":"Du lịch"}]}]}""")
        val catalog = api.catalog()
        assertEquals("/v1/catalog/collections", server.takeRequest().path)
        assertEquals("Du lịch", catalog.single().topics.single().title)
    }
}
