package com.syll.network

import com.syll.domain.practice.CommitResponse
import com.syll.domain.ranking.CommitRanking
import com.syll.domain.ranking.LPBreakdown
import com.syll.domain.ranking.LeaderboardView
import com.syll.domain.ranking.PublicProfileUpdate
import com.syll.domain.ranking.RankingPositionKind
import com.syll.domain.ranking.ReportReason
import com.syll.domain.ranking.TierKey
import com.syll.domain.ranking.TierSummary
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** The decoding part of iOS `RankingTests`, and the ranking endpoints (§14). */
class LiveVocabApiRankingTest {
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
    fun decodesProfileRankingFieldsAndStaysCompatible() {
        val profile = ApiJson.decodeFromString(
            UserProfile.serializer(),
            """{"id":"u1","proficiencyLevel":"B1","learningLevel":null,"scope":null,
             "settings":{"explanationLocale":"vi-VN","uiLocale":"vi-VN","timeZone":"Asia/Ho_Chi_Minh","dailyNewWordGoal":5,"leaderboardVisible":false},
             "username":"brave_otter_0042","avatarKey":"syllie_brave","usernameChangeAvailableAt":null,"rankingIntroSeen":false,
             "tierSummary":{"level":0,"key":null,"lifetimeLP":15,"lifetimeActiveDays":1}}""",
        )
        assertEquals("brave_otter_0042", profile.username)
        assertEquals(false, profile.settings.leaderboardVisible)
        assertEquals(TierSummary(level = 0, key = null, lifetimeLP = 15, lifetimeActiveDays = 1), profile.tierSummary)

        val old = ApiJson.decodeFromString(
            UserProfile.serializer(),
            """{"id":"u1","proficiencyLevel":null,"learningLevel":null,"scope":null,
             "settings":{"explanationLocale":"vi-VN","uiLocale":"vi-VN","timeZone":"UTC","dailyNewWordGoal":5}}""",
        )
        assertNull(old.tierSummary)
        assertNull(old.settings.leaderboardVisible)
    }

    @Test
    fun decodesCommitLPAndAnUnknownTierKey() {
        val response = ApiJson.decodeFromString(
            CommitResponse.serializer(),
            """{"sessionId":"s1","status":"committed","committedAt":"2026-11-12T03:00:00.000Z",
             "summary":{"newWordGoalSlots":2,"reviewedMeanings":2,"practiceTasks":6},
             "lp":{"earned":18,"breakdown":{"newMeaningLP":10,"dueReviewLP":8,"extraPracticeLP":0},"lpPolicyVersion":"lp_effort_trial_v1","weeklyLP":330,"lifetimeLP":600},
             "tier":{"level":3,"key":"building_up","lifetimeActiveDays":30,
                     "tierUps":[{"level":2,"key":"in_motion","unlockedAt":"2026-11-12T03:00:00.000Z"},{"level":3,"key":"something_new","unlockedAt":"2026-11-12T03:00:00.000Z"}],
                     "nextTier":{"level":4,"key":"steady_effort","minLifetimeLP":1500,"minLifetimeActiveDays":90}},
             "ranking":{"weeklyLP":330,"listed":false}}""",
        )
        assertEquals(LPBreakdown(newMeaningLP = 10, dueReviewLP = 8, extraPracticeLP = 0), response.lp?.breakdown)
        assertEquals(listOf(TierKey.IN_MOTION, TierKey.UNKNOWN), response.tier?.tierUps?.map { it.key })
        assertEquals(CommitRanking(weeklyLP = 330, listed = false), response.ranking)

        val old = ApiJson.decodeFromString(
            CommitResponse.serializer(),
            """{"sessionId":"s1","summary":{"newWordGoalSlots":1,"reviewedMeanings":0,"practiceTasks":1}}""",
        )
        assertNull(old.lp)
    }

    @Test
    fun optInBodyCarriesOnlyTheFlag() {
        assertEquals("""{"leaderboardVisible":true}""", SettingsUpdate(leaderboardVisible = true).toJson().toString())
    }

    @Test
    fun rankingEndpoints() = runBlocking {
        enqueue(
            """{"weekStart":"2026-11-09","weekEndsAt":"2026-11-16T00:00:00.000Z","weeklyLP":30,"listed":true,
               "position":{"kind":"rank","rank":2,"percentile":null},
               "tier":{"level":1,"key":"first_steps","lifetimeLP":45,"lifetimeActiveDays":2,"nextTier":null}}""",
        )
        enqueue("""{"weekStart":"2026-11-09","weekEndsAt":"2026-11-16T00:00:00Z","rows":[{"rank":1,"username":"a","avatarKey":"syllie_calm","weeklyLP":50,"lifetimeTier":{"level":0,"key":null},"isMe":false}]}""")
        enqueue("")
        enqueue("""{"username":"alan.lee","avatarKey":"syllie_calm","usernameChangeAvailableAt":"2026-12-25T00:00:00Z"}""")
        enqueue("")

        val mine = api.myRanking()
        val board = api.leaderboard(LeaderboardView.AROUND)
        api.markRankingIntroSeen()
        val saved = api.updatePublicProfile(PublicProfileUpdate(avatarKey = "syllie_calm"))
        api.reportLearner("a", ReportReason.OFFENSIVE_NAME)

        assertEquals(RankingPositionKind.RANK, mine.position?.kind)
        assertEquals(TierKey.FIRST_STEPS, mine.tier.key)
        assertNull(board.rows[0].lifetimeTier.key)
        assertFalse(board.rows[0].isMe)
        assertEquals("alan.lee", saved.username)

        assertEquals("GET /v1/me/ranking", server.takeRequest().let { "${it.method} ${it.path}" })
        assertEquals("GET /v1/ranking/leaderboard?view=around", server.takeRequest().let { "${it.method} ${it.path}" })
        assertEquals("POST /v1/me/ranking/intro-seen", server.takeRequest().let { "${it.method} ${it.path}" })
        val profile = server.takeRequest()
        assertEquals("PUT /v1/me/profile", "${profile.method} ${profile.path}")
        assertEquals("""{"avatarKey":"syllie_calm"}""", profile.body.readUtf8())
        val report = server.takeRequest()
        assertEquals("POST /v1/ranking/reports", "${report.method} ${report.path}")
        assertEquals("""{"username":"a","reason":"offensive_name"}""", report.body.readUtf8())
    }
}
