package com.syll.network

import com.syll.domain.NewWordReason
import com.syll.domain.challenge.ChallengeChapter
import com.syll.domain.challenge.ChallengeCommitResponse
import com.syll.domain.challenge.ChallengeErrorCode
import com.syll.domain.challenge.ChallengeHomeSummary
import com.syll.domain.challenge.ChallengeLevel
import com.syll.domain.challenge.ChallengeRunResult
import com.syll.domain.challenge.ChallengeRunStatus
import com.syll.domain.challenge.ChallengeUnlock
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Challenge endpoints (§12) and their lenient decoding. */
class LiveVocabApiChallengeTest {
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

    private fun enqueue(body: String = "", code: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    @Test
    fun mapDecodesAndUnknownStatesDoNotFailIt() = runBlocking {
        enqueue(
            """{"configVersion":"v1","gameMode":"match_madness","newWordsBlockedReason":"scope_exhausted",
               "chapters":[{"id":"c1","chapterNo":1,"state":"brand_new","explanationLocale":"vi-VN","starsEarned":4,"maxStars":9,
                 "levels":[{"levelNo":1,"state":"cleared","bestStars":3,"bestScore":1800},
                           {"levelNo":2,"state":"sparkly","bestStars":0,"bestScore":0,"goalMatches":12,"visiblePairs":4,"timeLimitMs":90000,
                            "starCriteria":{"two":{"maxElapsedMs":80000,"maxMistakes":3},"three":{"maxElapsedMs":60000,"maxMistakes":1}}}]}],
               "nextChapter":{"chapterNo":2,"state":"locked","eligibleNewCount":5,"requiredNewCount":12},
               "currentNode":{"chapterNo":1,"levelNo":2}}""",
        )
        val map = api.challengeMap()
        assertEquals("/v1/me/challenge-map", server.takeRequest().path)
        assertEquals(ChallengeChapter.State.UNKNOWN, map.chapters[0].state)
        assertEquals(ChallengeLevel.State.UNKNOWN, map.chapters[0].levels[1].state)
        assertEquals(false, map.chapters[0].levels[1].isPlayable)
        assertEquals(12, map.chapters[0].levels[1].goalMatches)
        assertEquals(NewWordReason.SCOPE_EXHAUSTED, map.newWordsBlockedReason)
        assertEquals(7, map.nextChapter?.remainingNewCount)
        assertEquals(4, map.totalStars)
        assertEquals(ChallengeHomeSummary.InProgress(chapterNo = 1, levelNo = 2, earnedStars = 4, maxStars = 9), ChallengeHomeSummary.from(map))
    }

    @Test
    fun homeSummaryFollowsTheServerMap() = runBlocking {
        enqueue("""{"chapters":[],"nextChapter":{"chapterNo":1,"state":"needs_vocabulary","eligibleNewCount":7,"requiredNewCount":12}}""")
        enqueue("""{"chapters":[],"nextChapter":{"chapterNo":1,"state":"ready","eligibleNewCount":12,"requiredNewCount":12}}""")
        enqueue(
            """{"chapters":[{"id":"c1","chapterNo":1,"state":"completed","starsEarned":9,"maxStars":9,"levels":[]}],
               "nextChapter":{"chapterNo":2,"state":"needs_vocabulary","eligibleNewCount":3,"requiredNewCount":12}}""",
        )
        enqueue("""{"chapters":[{"id":"c1","chapterNo":1,"state":"content_unavailable","starsEarned":2,"maxStars":9,"levels":[]}]}""")
        assertEquals(ChallengeHomeSummary.NotYetEligible(7, 12), ChallengeHomeSummary.from(api.challengeMap()))
        assertEquals(ChallengeHomeSummary.ChapterReady(1), ChallengeHomeSummary.from(api.challengeMap()))
        assertEquals(ChallengeHomeSummary.ChapterComplete(1, 9, 9, nextEligible = 3, nextRequired = 12), ChallengeHomeSummary.from(api.challengeMap()))
        assertEquals(ChallengeHomeSummary.ContentUnavailable(1, 2, 9), ChallengeHomeSummary.from(api.challengeMap()))
    }

    @Test
    fun issueStartCommitStatusQuit() = runBlocking {
        enqueue(
            """{"runId":"run-1","status":"issued","gameMode":"match_madness","chapterNo":1,"levelNo":2,"policyVersion":"v1",
               "explanationLocale":"vi-VN","goalMatches":12,"visiblePairs":4,"timeLimitMs":90000,"minMatchesBetweenRepeats":3,
               "starCriteria":{"two":{"maxElapsedMs":80000,"maxMistakes":3},"three":{"maxElapsedMs":60000,"maxMistakes":1}},
               "boardSeed":"seed","expiresAt":"2026-11-25T10:00:00.000Z",
               "pairs":[{"pairId":"p1","wordTileId":"w1","meaningTileId":"m1","word":"allocate","meaning":"phân bổ"}]}""",
        )
        enqueue("{}")
        enqueue(
            """{"runId":"run-1","status":"committed",
               "result":{"outcome":"cleared","starsThisRun":2,"bestStars":2,"correctMatches":12,"mistakes":1,"longestCombo":8,
                         "elapsedMs":40000,"score":1500,"bestScore":1500},
               "unlock":{"kind":"level","chapterNo":1,"levelNo":3},"nextAction":"play_next_level"}""",
        )
        enqueue("""{"runId":"run-1","status":"weird","response":null}""")
        enqueue("{}")

        val plan = api.issueChallengeRun(chapterNo = 1, levelNo = 2)
        api.startChallengeRun("run-1")
        val body = """{"endedElapsedMs":40000,"endedReason":"goal_reached","events":[]}""".toByteArray()
        val response = api.commitChallengeRun("run-1", body)
        val status = api.challengeRunStatus("run-1")
        api.quitChallengeRun("run-1")

        assertEquals("run-1", plan.runId)
        assertEquals(null, plan.score)
        assertEquals(java.time.Instant.parse("2026-11-25T10:00:00Z"), plan.expiresAt)
        assertEquals(ChallengeRunResult.Outcome.CLEARED, response.result.outcome)
        assertEquals(ChallengeUnlock.Kind.LEVEL, response.unlock?.kind)
        assertEquals(ChallengeCommitResponse.NextAction.PLAY_NEXT_LEVEL, response.nextAction)
        assertEquals(ChallengeRunStatus.Status.UNKNOWN, status.status)
        assertNull(status.response)

        val issue = server.takeRequest()
        assertEquals("POST /v1/challenge/chapters/1/levels/2/runs", "${issue.method} ${issue.path}")
        assertEquals("{}", issue.body.readUtf8())
        assertEquals("POST /v1/challenge/runs/run-1/start", server.takeRequest().let { "${it.method} ${it.path}" })
        val commit = server.takeRequest()
        assertEquals("POST /v1/challenge/runs/run-1/commit", "${commit.method} ${commit.path}")
        assertArrayEquals(body, commit.body.readByteArray())
        assertEquals("GET /v1/challenge/runs/run-1/status", server.takeRequest().let { "${it.method} ${it.path}" })
        assertEquals("POST /v1/challenge/runs/run-1/quit", server.takeRequest().let { "${it.method} ${it.path}" })
        Unit
    }

    @Test
    fun runActiveErrorCarriesTheOtherRunId() = runBlocking {
        enqueue(
            """{"code":"CHALLENGE_RUN_ACTIVE","message":"A run is open","retryable":false,"requestId":"r1","details":{"runId":"run-9"}}""",
            code = 409,
        )
        try {
            api.issueChallengeRun(1, 1)
            fail("expected an error")
        } catch (error: ApiError) {
            assertEquals(ChallengeErrorCode.RUN_ACTIVE, error.code)
            assertEquals("run-9", error.detail("runId"))
        }
    }
}
