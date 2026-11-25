package com.syll.features.challenge

import com.syll.data.InMemoryKeyValueStore
import com.syll.domain.challenge.ChallengeChapter
import com.syll.domain.challenge.ChallengeChapterPreview
import com.syll.domain.challenge.ChallengeCommitResponse
import com.syll.domain.challenge.ChallengeErrorCode
import com.syll.domain.challenge.ChallengeHomeSummary
import com.syll.domain.challenge.ChallengeLevel
import com.syll.domain.challenge.ChallengeMap
import com.syll.domain.challenge.ChallengeNodeRef
import com.syll.domain.challenge.ChallengeRunPlan
import com.syll.domain.challenge.ChallengeRunStatus
import com.syll.domain.challenge.preview
import com.syll.domain.challenge.previewNotEligible
import com.syll.features.auth.StubVocabApi
import com.syll.network.ApiError
import com.syll.platform.NoOpFeedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Port of iOS `ChallengeRunStoreTests`. */
class ChallengeRunStoreTest {
    private val api = StubVocabApi()
    private var offset = 0L
    private val storage = InMemoryKeyValueStore()
    private val node = ChallengeNodeRef(chapterNo = 1, levelNo = 2)

    private fun CoroutineScope.makeStore() =
        ChallengeRunStore(api, NoOpFeedback, this, now = { 1_000_000L + offset }, tickIntervalMs = null, storage = storage)

    private fun plan(goal: Int = 2, visible: Int = 2): ChallengeRunPlan {
        val sandbox = ChallengeRunPlan.sandbox(visiblePairs = visible, goalMatches = goal)
        return sandbox.copy(runId = "run-1", chapterNo = node.chapterNo, levelNo = node.levelNo, policyVersion = "v1", score = null)
    }

    private fun server(status: Int, code: String, details: Map<String, String>? = null) =
        ApiError.Server(status, code, "", retryable = false, requestId = null, details = details)

    /** A commit response as the backend sends it (`FRONTEND_FLOW.md` §12.4). */
    private fun result(stars: Int = 2, outcome: String = "cleared", next: ChallengeNodeRef? = null): ChallengeCommitResponse {
        val unlock = next?.let { """{"kind":"level","chapterNo":${it.chapterNo},"levelNo":${it.levelNo}}""" } ?: """{"kind":"none"}"""
        val json = """{"runId":"run-1","status":"committed",
            "result":{"outcome":"$outcome","starsThisRun":$stars,"bestStars":$stars,"correctMatches":2,"mistakes":0,
                      "longestCombo":2,"elapsedMs":4000,"score":210,"bestScore":210},
            "unlock":$unlock,"nextAction":"${if (next == null) "open_map" else "play_next_level"}"}"""
        return decoder.decodeFromString(ChallengeCommitResponse.serializer(), json)
    }

    /** One wrong pair, then both pairs, at 1, 2 and 3 seconds after [offset]'s current value. */
    private fun playToGoal(store: ChallengeRunStore) {
        val start = offset
        offset = start + 1000
        store.tapWord("w1")
        store.tapMeaning("m2") // wrong
        offset = start + 2000
        store.tapMeaning("m1")
        store.tapWord("w1")
        offset = start + 3000
        store.tapWord("w2")
        store.tapMeaning("m2")
    }

    private fun body(index: Int = 0) = Json.parseToJsonElement(api.challengeCommitBodies[index].decodeToString()).jsonObject

    // Issue → start → play → commit

    @Test
    fun fullRunIssuesStartsPlaysAndCommits() = runBlocking<Unit> {
        api.issueResults += Result.success(plan())
        api.challengeCommitResults += Result.success(result(next = ChallengeNodeRef(1, 3)))
        val store = makeStore()
        var settled = 0
        store.onSettled = { settled += 1 }

        store.play(node, level = null)
        assertEquals(ChallengeRunStore.Stage.Ready, store.stage)
        assertEquals(listOf(node), api.issuedRuns)
        assertTrue("The clock and the run start only on Start", api.startedRuns.isEmpty())

        offset = 30_000 // time on the ready screen doesn't count
        store.begin()
        assertEquals(ChallengeRunStore.Stage.Playing, store.stage)
        assertEquals(listOf("run-1"), api.startedRuns)

        playToGoal(store)
        assertEquals(ChallengeRunStore.Stage.Committing, store.stage)
        store.settleTask?.join()

        val outcome = (store.stage as? ChallengeRunStore.Stage.Results)?.outcome ?: return@runBlocking fail("Expected results, got ${store.stage}")
        assertEquals(ChallengeNodeRef(1, 3), outcome.nextNode)
        assertTrue(outcome.isFirstClear)
        assertEquals(1, settled)
        assertFalse(store.hasPendingRun)

        val body = body()
        assertEquals("goal_reached", body["endedReason"]!!.jsonPrimitive.content)
        assertEquals(3000, body["endedElapsedMs"]!!.jsonPrimitive.int)
        val events = body["events"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf(1000, 2000, 3000), events.map { it["elapsedMs"]!!.jsonPrimitive.int })
        assertEquals(listOf(1, 2, 3), events.map { it["seq"]!!.jsonPrimitive.int })
    }

    @Test
    fun failedCommitRetriesWithIdenticalBytes() = runBlocking<Unit> {
        api.issueResults += Result.success(plan())
        api.challengeCommitResults += Result.failure(ApiError.Transport)
        api.challengeCommitResults += Result.success(result())
        val store = makeStore()
        store.play(node, null)
        store.begin()
        playToGoal(store)
        store.settleTask?.join()
        assertTrue("Expected commitFailed, got ${store.stage}", store.stage is ChallengeRunStore.Stage.CommitFailed)
        assertTrue("The frozen body survives a relaunch", store.hasPendingRun)

        offset = 80_000 // the clock moving on changes nothing in the body
        store.retryCommit()
        assertEquals(2, api.challengeCommitBodies.size)
        assertArrayEquals(api.challengeCommitBodies[0], api.challengeCommitBodies[1])
        assertTrue(store.stage is ChallengeRunStore.Stage.Results)
    }

    @Test
    fun commitConflictRendersTheStoredResult() = runBlocking<Unit> {
        api.issueResults += Result.success(plan())
        api.challengeCommitResults += Result.failure(server(409, ChallengeErrorCode.COMMIT_CONFLICT))
        api.runStatus = Result.success(ChallengeRunStatus("run-1", ChallengeRunStatus.Status.COMMITTED, response = result(stars = 3)))
        val store = makeStore()
        store.play(node, null)
        store.begin()
        playToGoal(store)
        store.settleTask?.join()
        val outcome = (store.stage as? ChallengeRunStore.Stage.Results)?.outcome ?: return@runBlocking fail("Expected results, got ${store.stage}")
        assertEquals(3, outcome.result.starsThisRun)
        assertEquals(listOf("run-1"), api.statusRequests)
    }

    @Test
    fun timeUpEndsTheRunEvenAfterTheBackground() = runBlocking<Unit> {
        api.issueResults += Result.success(plan())
        api.challengeCommitResults += Result.success(result(stars = 0, outcome = "failed"))
        val store = makeStore()
        store.play(node, null)
        store.begin()
        store.tapWord("w1")
        store.tapMeaning("m1")

        // Backgrounded for two minutes: nothing ticked, the clock kept running.
        offset = 120_000
        store.tapWord("w2")
        assertEquals("A tap after time is up ends the run instead of counting", ChallengeRunStore.Stage.Committing, store.stage)
        store.settleTask?.join()

        val body = body()
        assertEquals("time_up", body["endedReason"]!!.jsonPrimitive.content)
        assertEquals(90_000, body["endedElapsedMs"]!!.jsonPrimitive.int)
        assertEquals(1, body["events"]!!.jsonArray.size)
        val outcome = (store.stage as? ChallengeRunStore.Stage.Results)?.outcome ?: return@runBlocking fail("Expected results")
        assertFalse(outcome.isCleared)
    }

    @Test
    fun tickAtTheLimitEndsAsTimeUp() = runBlocking<Unit> {
        api.issueResults += Result.success(plan())
        api.challengeCommitResults += Result.success(result(stars = 0, outcome = "failed"))
        val store = makeStore()
        store.play(node, null)
        store.begin()
        offset = 89_999
        store.tick()
        assertEquals(ChallengeRunStore.Stage.Playing, store.stage)
        offset = 90_000
        store.tick()
        assertEquals(ChallengeRunStore.Stage.Committing, store.stage)
        store.settleTask?.join()
    }

    @Test
    fun quitWhilePlayingCommitsQuitAndCloses() = runBlocking<Unit> {
        api.issueResults += Result.success(plan())
        api.challengeCommitResults += Result.success(result(stars = 0, outcome = "quit"))
        val store = makeStore()
        store.play(node, null)
        store.begin()
        offset = 5000
        store.quit()
        assertEquals(ChallengeRunStore.Stage.Idle, store.stage)
        assertEquals("quit", body()["endedReason"]!!.jsonPrimitive.content)
        assertEquals(5000, body()["endedElapsedMs"]!!.jsonPrimitive.int)
    }

    @Test
    fun leavingBeforeStartCancelsTheIssuedRun() = runBlocking<Unit> {
        api.issueResults += Result.success(plan())
        val store = makeStore()
        store.play(node, null)
        store.leave()
        assertEquals(ChallengeRunStore.Stage.Idle, store.stage)
        assertEquals(listOf("run-1"), api.quitRuns)
        assertFalse(store.hasPendingRun)
    }

    @Test
    fun expiredStartShowsExpired() = runBlocking<Unit> {
        api.issueResults += Result.success(plan())
        api.startResults += Result.failure(server(410, ChallengeErrorCode.RUN_EXPIRED))
        val store = makeStore()
        store.play(node, null)
        store.begin()
        assertEquals(ChallengeRunStore.Stage.Failed(ChallengeRunStore.Failure.Expired), store.stage)
    }

    @Test
    fun transportFailureOnStartStaysReady() = runBlocking<Unit> {
        api.issueResults += Result.success(plan())
        api.startResults += Result.failure(ApiError.Transport)
        val store = makeStore()
        store.play(node, null)
        store.begin()
        assertEquals(ChallengeRunStore.Stage.Ready, store.stage)
        assertNotNull(store.startError)
    }

    @Test
    fun issueErrorsMapToTheirScreens() = runBlocking<Unit> {
        val store = makeStore()
        api.issueResults += Result.failure(server(409, ChallengeErrorCode.RUN_ACTIVE))
        store.play(node, null)
        assertEquals(ChallengeRunStore.Stage.RunActive(null), store.stage)

        api.issueResults += Result.failure(server(403, ChallengeErrorCode.LEVEL_LOCKED))
        store.play(node, null)
        assertEquals(ChallengeRunStore.Stage.Failed(ChallengeRunStore.Failure.LevelLocked), store.stage)

        api.issueResults += Result.failure(server(422, ChallengeErrorCode.NOT_ENOUGH_VOCABULARY))
        store.play(node, null)
        assertEquals(ChallengeRunStore.Stage.Failed(ChallengeRunStore.Failure.NotEnoughVocabulary), store.stage)

        api.issueResults += Result.failure(server(422, ChallengeErrorCode.CONTENT_UNAVAILABLE))
        store.play(node, null)
        val failure = (store.stage as? ChallengeRunStore.Stage.Failed)?.failure
        assertTrue("Expected contentUnavailable", failure is ChallengeRunStore.Failure.ContentUnavailable)
    }

    @Test
    fun recoveryCommitsAFrozenBodyLeftByAKilledApp() = runBlocking<Unit> {
        api.issueResults += Result.success(plan())
        api.challengeCommitResults += Result.failure(ApiError.Transport)
        val first = makeStore()
        first.play(node, null)
        first.begin()
        playToGoal(first)
        first.settleTask?.join()
        val frozen = api.challengeCommitBodies.first()

        // Relaunch.
        api.runStatus = Result.success(ChallengeRunStatus("run-1", ChallengeRunStatus.Status.STARTED))
        api.challengeCommitResults += Result.success(result())
        val second = makeStore()
        assertTrue(second.hasPendingRun)
        second.recoverPendingRun()
        assertArrayEquals(frozen, api.challengeCommitBodies.last())
        assertFalse(second.hasPendingRun)
        assertEquals("Recovery is silent; the map refresh shows the outcome", ChallengeRunStore.Stage.Idle, second.stage)
    }

    @Test
    fun recoverySettlesAStartedRunWithoutEventsAsQuit() = runBlocking<Unit> {
        api.issueResults += Result.success(plan())
        val first = makeStore()
        first.play(node, null)
        first.begin()
        // Killed mid-run: no body was frozen.

        api.runStatus = Result.success(ChallengeRunStatus("run-1", ChallengeRunStatus.Status.STARTED))
        val second = makeStore()
        second.recoverPendingRun()
        assertEquals(listOf("run-1"), api.quitRuns)
        assertTrue("Never commits a reconstructed run", api.challengeCommitBodies.isEmpty())
        assertFalse(second.hasPendingRun)
    }

    @Test
    fun sandboxRoundEndsLocallyAndUntimedNeverTimesOut() = runBlocking<Unit> {
        val store = makeStore()
        store.startSandbox(plan(), timed = false)
        store.begin()
        offset = 600_000
        store.tick()
        assertEquals(ChallengeRunStore.Stage.Playing, store.stage)
        playToGoal(store)
        val summary = (store.stage as? ChallengeRunStore.Stage.SandboxResults)?.summary ?: return@runBlocking fail("Expected sandbox results")
        assertTrue(summary.reachedGoal)
        assertEquals(1, summary.mistakes)
        assertTrue(api.issuedRuns.isEmpty())
        assertTrue(api.startedRuns.isEmpty())
        assertTrue(api.challengeCommitBodies.isEmpty())
    }

    @Test
    fun runActiveOverAnIssuedRunDropsItAndIssuesAgain() = runBlocking<Unit> {
        api.issueResults += Result.failure(server(409, ChallengeErrorCode.RUN_ACTIVE, mapOf("runId" to "old-run")))
        api.issueResults += Result.success(plan())
        api.runStatus = Result.success(ChallengeRunStatus("old-run", ChallengeRunStatus.Status.ISSUED))
        val store = makeStore()
        store.play(node, null)
        assertEquals(listOf("old-run"), api.quitRuns)
        assertEquals(ChallengeRunStore.Stage.Ready, store.stage)
        assertEquals("run-1", store.plan?.runId)
    }

    @Test
    fun runActiveOverAStartedRunAsksTheLearner() = runBlocking<Unit> {
        api.issueResults += Result.failure(server(409, ChallengeErrorCode.RUN_ACTIVE, mapOf("runId" to "old-run")))
        api.runStatus = Result.success(ChallengeRunStatus("old-run", ChallengeRunStatus.Status.STARTED))
        val store = makeStore()
        store.play(node, null)
        assertEquals(ChallengeRunStore.Stage.RunActive("old-run"), store.stage)
        assertTrue(api.quitRuns.isEmpty())
    }

    @Test
    fun quitAtTheDeadlineIsSentAsTimeUp() = runBlocking<Unit> {
        api.issueResults += Result.success(plan())
        api.challengeCommitResults += Result.success(result(stars = 0, outcome = "failed"))
        val store = makeStore()
        store.play(node, null)
        store.begin()
        offset = 95_000
        store.quit()
        assertEquals("time_up", body()["endedReason"]!!.jsonPrimitive.content)
        assertEquals(90_000, body()["endedElapsedMs"]!!.jsonPrimitive.int)
    }

    // Outcome

    private fun outcome(json: String, previousBestStars: Int, previousBestScore: Int) = ChallengeRunOutcome(
        node, decoder.decodeFromString(ChallengeCommitResponse.serializer(), json), previousBestStars, previousBestScore,
    )

    @Test
    fun worseReplayKeepsTheBestAndIsNotANewBest() {
        val outcome = outcome(
            """{"runId":"r","status":"committed","result":{"outcome":"cleared","starsThisRun":1,"bestStars":3,"correctMatches":12,
             "mistakes":5,"longestCombo":3,"elapsedMs":80000,"score":900,"bestScore":2000},"unlock":{"kind":"none"},"nextAction":"open_map"}""",
            previousBestStars = 3, previousBestScore = 2000,
        )
        assertFalse(outcome.isNewBest)
        assertFalse(outcome.isFirstClear)
        assertEquals(null, outcome.nextNode)
    }

    @Test
    fun unlockKinds() {
        val level = outcome(
            """{"result":{"outcome":"cleared","starsThisRun":2,"bestStars":2,"correctMatches":12,"mistakes":1,"longestCombo":8,
             "elapsedMs":68600,"score":1685,"bestScore":1685},"unlock":{"kind":"level","chapterNo":1,"levelNo":3},"nextAction":"play_next_level"}""",
            0, 0,
        )
        assertEquals(ChallengeNodeRef(1, 3), level.nextNode)
        assertTrue(level.isFirstClear)
        assertFalse(level.chapterCleared)

        val ready = outcome(
            """{"result":{"outcome":"cleared","starsThisRun":1,"bestStars":1,"correctMatches":16,"mistakes":4,"longestCombo":5,
             "elapsedMs":88000,"score":1500,"bestScore":1500},"unlock":{"kind":"chapter_ready","chapterNo":2},"nextAction":"play_next_level"}""",
            0, 0,
        )
        assertEquals(ChallengeNodeRef(2, 1), ready.nextNode)
        assertTrue(ready.chapterCleared)

        val words = outcome(
            """{"result":{"outcome":"cleared","starsThisRun":1,"bestStars":1,"correctMatches":16,"mistakes":4,"longestCombo":5,
             "elapsedMs":88000,"score":1500,"bestScore":1500},
             "unlock":{"kind":"needs_vocabulary","chapterNo":2,"eligibleNewCount":9,"requiredNewCount":12},"nextAction":"learn_more"}""",
            0, 0,
        )
        assertEquals(null, words.nextNode)
        assertEquals(ChallengeRunOutcome.WordsNeeded(chapterNo = 2, eligible = 9, required = 12), words.wordsNeeded)
    }

    private companion object {
        val decoder = Json { ignoreUnknownKeys = true }
    }
}

/** Port of iOS `ChallengeMapTests`. */
class ChallengeMapTest {
    private val decoder = Json { ignoreUnknownKeys = true }

    @Test
    fun decodesAMapAndToleratesUnknownStates() {
        // The example from FRONTEND_FLOW.md §12.1, with one state the client doesn't know yet.
        val map = decoder.decodeFromString(
            ChallengeMap.serializer(),
            """{ "configVersion": "match-madness-trial-v1", "gameMode": "match_madness",
              "chapters": [
                { "id": "c1", "chapterNo": 1, "state": "active", "titleKey": "challenge.chapter.1", "explanationLocale": "vi-VN",
                  "coreMeaningCount": 12, "reviewMeaningCount": 0, "starsEarned": 1, "maxStars": 9,
                  "levels": [
                    { "levelNo": 1, "state": "cleared", "bestStars": 1, "bestScore": 1020 },
                    { "levelNo": 2, "state": "available", "bestStars": 0, "bestScore": 0 },
                    { "levelNo": 3, "state": "something_new", "bestStars": 0, "bestScore": 0 } ] } ],
              "nextChapter": { "chapterNo": 2, "state": "locked", "eligibleNewCount": 4, "requiredNewCount": 12 },
              "currentNode": { "chapterNo": 1, "levelNo": 2 } }""",
        )
        assertEquals(ChallengeLevel.State.UNKNOWN, map.chapters[0].levels[2].state)
        assertFalse(map.chapters[0].levels[2].isPlayable)
        assertEquals(9, map.chapters[0].maxStars)
        assertEquals(8, map.nextChapter?.remainingNewCount)
        assertEquals(ChallengeHomeSummary.InProgress(1, 2, 1, 9), ChallengeHomeSummary.from(map))
    }

    @Test
    fun homeSummaryStates() {
        assertEquals(ChallengeHomeSummary.NotYetEligible(7, 12), ChallengeHomeSummary.from(ChallengeMap.previewNotEligible))

        val ready = ChallengeMap(chapters = emptyList(), nextChapter = ChallengeChapterPreview(1, ChallengeChapterPreview.State.READY, 12, 12))
        assertEquals(ChallengeHomeSummary.ChapterReady(1), ChallengeHomeSummary.from(ready))

        val preview = ChallengeMap.preview
        val unavailable = ChallengeMap(
            chapters = listOf(preview.chapters[0], preview.chapters[1].copy(state = ChallengeChapter.State.CONTENT_UNAVAILABLE)),
            nextChapter = preview.nextChapter,
        )
        assertEquals(ChallengeHomeSummary.ContentUnavailable(2, 1, 9), ChallengeHomeSummary.from(unavailable))

        val done = ChallengeMap(
            chapters = listOf(preview.chapters[0]),
            nextChapter = ChallengeChapterPreview(2, ChallengeChapterPreview.State.NEEDS_VOCABULARY, 5, 12),
        )
        assertEquals(ChallengeHomeSummary.ChapterComplete(1, 7, 9, nextEligible = 5, nextRequired = 12), ChallengeHomeSummary.from(done))
    }

    @Test
    fun refreshMarksNewlyUnlockedLevelsOnce() = runBlocking<Unit> {
        val api = StubVocabApi()
        val store = ChallengeMapStore(api)
        api.challengeMapResult = Result.success(ChallengeMap.preview)
        store.refresh()
        assertTrue("Nothing animates on the first load", store.newlyUnlocked.isEmpty())

        val chapters = ChallengeMap.preview.chapters.toMutableList()
        chapters[1] = ChallengeChapter(
            id = "preview-2", chapterNo = 2, state = ChallengeChapter.State.ACTIVE, explanationLocale = "vi-VN", starsEarned = 3, maxStars = 9,
            levels = listOf(
                ChallengeLevel.preview(1, ChallengeLevel.State.CLEARED, bestStars = 1),
                ChallengeLevel.preview(2, ChallengeLevel.State.CLEARED, bestStars = 2),
                ChallengeLevel.preview(3, ChallengeLevel.State.AVAILABLE),
            ),
        )
        api.challengeMapResult = Result.success(ChallengeMap(chapters = chapters, nextChapter = ChallengeMap.preview.nextChapter, currentNode = ChallengeNodeRef(2, 3)))
        store.refresh()
        val level3 = ChallengeNodeRef(2, 3)
        assertEquals(setOf(level3), store.newlyUnlocked)
        store.consumeUnlock(level3)
        assertTrue(store.newlyUnlocked.isEmpty())
    }

    @Test
    fun failedRefreshKeepsTheMap() = runBlocking<Unit> {
        val api = StubVocabApi()
        val store = ChallengeMapStore(api)
        api.challengeMapResult = Result.success(ChallengeMap.preview)
        store.refresh()
        api.challengeMapResult = Result.failure(ApiError.Transport)
        store.refresh()
        assertEquals(ChallengeMap.preview, store.map)
        assertNotNull(store.error)
    }
}
