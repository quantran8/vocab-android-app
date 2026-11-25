package com.syll.domain.challenge

import com.syll.domain.challenge.MatchMadnessAction.TapMeaning
import com.syll.domain.challenge.MatchMadnessAction.TapWord
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `ChallengeBoardOrderingTests`. */
class ChallengeBoardOrderingTest {
    /** The shared vectors from backend `match-madness.spec.ts` / `FRONTEND_FLOW.md` §12.3. */
    @Test
    fun backendSharedVectors() {
        val seed = "00112233445566778899aabbccddeeff"
        val ids = (1..3).map { "018f0000-0000-7000-8000-00000000000$it" }
        assertEquals(
            "a1eedcdd4a2b5f8ffd4f2133437cc340a0fcb76214ccc2dd789dbc2aac1a8158",
            ChallengeBoardOrdering.sortKey(seed, 0, ChallengeBoardOrdering.Side.WORD, ids[0]),
        )
        assertEquals(listOf(ids[0], ids[2], ids[1]), ChallengeBoardOrdering.order(ids, seed, 0, ChallengeBoardOrdering.Side.WORD))
        assertEquals(listOf(ids[1], ids[0], ids[2]), ChallengeBoardOrdering.order(ids, seed, 0, ChallengeBoardOrdering.Side.MEANING))
        assertEquals(listOf(ids[0], ids[1], ids[2]), ChallengeBoardOrdering.order(ids, seed, 1, ChallengeBoardOrdering.Side.WORD))
        assertEquals(listOf(ids[0], ids[2], ids[1]), ChallengeBoardOrdering.order(ids, seed, 1, ChallengeBoardOrdering.Side.MEANING))
    }

    // Reference values computed independently (Python hashlib).
    @Test
    fun sortKeyIsLowercaseHexSha256OfTheJoinedInput() {
        assertEquals(
            "de72e234e69c3853cfe915c978720dca170567a0e9f9132b3ef7f030c882d22c",
            ChallengeBoardOrdering.sortKey("seed-1", 0, ChallengeBoardOrdering.Side.WORD, "w1"),
        )
    }

    @Test
    fun columnsAreOrderedIndependentlyPerSideAndCorrectCount() {
        val words = listOf("w1", "w2", "w3", "w4")
        val meanings = listOf("m1", "m2", "m3", "m4")
        assertEquals(listOf("w3", "w2", "w4", "w1"), ChallengeBoardOrdering.order(words, "seed-1", 0, ChallengeBoardOrdering.Side.WORD))
        assertEquals(listOf("m1", "m4", "m3", "m2"), ChallengeBoardOrdering.order(meanings, "seed-1", 0, ChallengeBoardOrdering.Side.MEANING))
        assertEquals(listOf("w4", "w2", "w1", "w3"), ChallengeBoardOrdering.order(words, "seed-1", 1, ChallengeBoardOrdering.Side.WORD))
        assertEquals(listOf("m4", "m3", "m1", "m2"), ChallengeBoardOrdering.order(meanings, "seed-1", 2, ChallengeBoardOrdering.Side.MEANING))
    }

    @Test
    fun orderDoesNotDependOnInputOrder() {
        val ids = listOf("w1", "w2", "w3", "w4")
        assertEquals(
            ChallengeBoardOrdering.order(ids, "seed-1", 0, ChallengeBoardOrdering.Side.WORD),
            ChallengeBoardOrdering.order(ids.reversed(), "seed-1", 0, ChallengeBoardOrdering.Side.WORD),
        )
    }
}

/** iOS `ChallengeBoardSlotsTests`. */
class ChallengeBoardSlotsTest {
    @Test
    fun firstBoardTakesEngineOrder() {
        assertEquals(listOf("w2", "w1"), ChallengeBoardSlots.reconcile(emptyList(), listOf("w2", "w1")))
    }

    @Test
    fun newTileTakesTheMatchedTilesSlot() {
        assertEquals(listOf("w1", "w4", "w3"), ChallengeBoardSlots.reconcile(listOf("w1", "w2", "w3"), listOf("w4", "w3", "w1")))
    }

    @Test
    fun slotStaysEmptyWhenQueueRunsOut() {
        assertEquals(listOf("w1", null, "w3"), ChallengeBoardSlots.reconcile(listOf("w1", "w2", "w3"), listOf("w3", "w1")))
    }

    @Test
    fun freshBoardReplacesEverything() {
        assertEquals(listOf("x2", "x1"), ChallengeBoardSlots.reconcile(listOf("w1", "w2"), listOf("x2", "x1")))
    }

    @Test
    fun heldTilesKeepTheirSlotUntilReleased() {
        val held = ChallengeBoardSlots.reconcile(listOf("w1", "w2", "w3"), listOf("w4", "w3", "w1"), holding = setOf("w2"))
        assertEquals(listOf("w1", "w2", "w3"), held)
        assertEquals(listOf("w1", "w4", "w3"), ChallengeBoardSlots.reconcile(held, listOf("w4", "w3", "w1")))
    }
}

/** iOS `MatchMadnessEngineTests`. */
class MatchMadnessEngineTest {
    private val plan = ChallengeRunPlan.sandbox(visiblePairs = 4, goalMatches = 6)
    private val engine = MatchMadnessEngine(plan)

    private fun run(actions: List<MatchMadnessAction>, from: MatchMadnessState? = null): Pair<MatchMadnessState, List<MatchMadnessFeedback>> {
        var current = from ?: engine.initialState()
        val feedback = mutableListOf<MatchMadnessFeedback>()
        for (action in actions) {
            val step = engine.reduce(current, action)
            current = step.state
            feedback += step.feedback
        }
        return current to feedback
    }

    @Test
    fun initialBoardShowsTheFirstVisiblePairsInSeededOrder() {
        val state = engine.initialState()
        assertEquals(listOf("p1", "p2", "p3", "p4"), state.activePairIds)
        assertEquals(listOf("p5", "p6"), state.pendingPairIds)
        assertEquals(listOf("w4", "w3", "w2", "w1"), state.wordOrder)
        assertEquals(listOf("m2", "m4", "m1", "m3"), state.meaningOrder)
        assertEquals(0, state.provisionalScore)
    }

    @Test
    fun wordThenMeaningMatches() {
        val (state, feedback) = run(listOf(TapWord("w1", 500), TapMeaning("m1", 900)))
        assertEquals(listOf(MatchMadnessFeedback.Selected, MatchMadnessFeedback.Correct("p1", 1)), feedback)
        assertEquals(1, state.correctCount)
        assertEquals(listOf(MatchAttemptEvent(seq = 1, elapsedMs = 900, wordTileId = "w1", meaningTileId = "m1")), state.events)
    }

    @Test
    fun meaningThenWordMatches() {
        val (state, feedback) = run(listOf(TapMeaning("m2", 100), TapWord("w2", 200)))
        assertEquals(MatchMadnessFeedback.Correct("p2", 1), feedback.last())
        assertEquals("w2", state.events.first().wordTileId)
        assertEquals("m2", state.events.first().meaningTileId)
    }

    @Test
    fun selectionsAloneAreNotEvents() {
        val (state, feedback) = run(listOf(TapWord("w1", 100), TapWord("w2", 200), TapWord("w2", 300)))
        assertEquals(listOf(MatchMadnessFeedback.Selected, MatchMadnessFeedback.Selected, MatchMadnessFeedback.Deselected), feedback)
        assertNull(state.selectedWordTileId)
        assertTrue(state.events.isEmpty())
    }

    @Test
    fun changingSelectionOnOneSideThenMatching() {
        val (state, _) = run(listOf(TapWord("w1", 100), TapWord("w3", 200), TapMeaning("m3", 300)))
        assertEquals(1, state.correctCount)
        assertEquals(listOf("w3"), state.events.map { it.wordTileId })
    }

    @Test
    fun wrongPairIsRecordedResetsComboAndKeepsBothTiles() {
        val (state, feedback) = run(
            listOf(TapWord("w1", 100), TapMeaning("m1", 200), TapWord("w2", 300), TapMeaning("m3", 400)),
        )
        assertEquals(MatchMadnessFeedback.Wrong("w2", "m3"), feedback.last())
        assertEquals(1, state.wrongCount)
        assertEquals(0, state.combo)
        assertEquals(1, state.longestCombo)
        assertTrue("w2" in state.wordOrder)
        assertTrue("m3" in state.meaningOrder)
        // A wrong pair never consumes the queue.
        assertEquals(listOf("p6"), state.pendingPairIds)
        assertNull(state.selectedWordTileId)
        assertNull(state.selectedMeaningTileId)
        assertEquals(listOf(1, 2), state.events.map { it.seq })
    }

    @Test
    fun correctPairIsReplacedFromTheQueueAndColumnsReorder() {
        val (state, _) = run(listOf(TapWord("w1", 100), TapMeaning("m1", 200)))
        assertEquals(listOf("p2", "p3", "p4", "p5"), state.activePairIds)
        assertEquals(listOf("p6"), state.pendingPairIds)
        assertEquals(listOf("w3", "w4", "w5", "w2"), state.wordOrder)
        assertEquals(listOf("m4", "m3", "m5", "m2"), state.meaningOrder)
    }

    @Test
    fun tilesNotOnTheBoardAreIgnored() {
        val (state, feedback) = run(listOf(TapWord("w6", 100), TapMeaning("nope", 200)))
        assertEquals(listOf(MatchMadnessFeedback.None, MatchMadnessFeedback.None), feedback)
        assertEquals(engine.initialState(), state)
    }

    private fun repeatPlan(pairs: List<ChallengePairOccurrence>, visible: Int) = ChallengeRunPlan(
        runId = "r", gameMode = ChallengeRunPlan.MATCH_MADNESS, chapterNo = 1, levelNo = 3, policyVersion = "t",
        explanationLocale = null, goalMatches = pairs.size, visiblePairs = visible, timeLimitMs = 90_000,
        minMatchesBetweenRepeats = 3,
        starCriteria = ChallengeStarCriteria(ChallengeStarCriteria.Tier(80_000, 3), ChallengeStarCriteria.Tier(60_000, 1)),
        boardSeed = "s", pairs = pairs,
    )

    private fun occurrence(id: String, word: String) =
        ChallengePairOccurrence(pairId = id, wordTileId = "w-$id", meaningTileId = "m-$id", word = word, meaning = "gloss $word")

    @Test
    fun oneMeaningIsNeverOnTheBoardTwice() {
        // Same word twice in a row of the queue: the second waits, and nothing behind it jumps ahead of the rule.
        val engine = MatchMadnessEngine(repeatPlan(listOf(occurrence("a", "allocate"), occurrence("b", "allocate"), occurrence("c", "clarify")), visible = 2))
        val state = engine.initialState()
        assertEquals(listOf("a", "c"), state.activePairIds)
        assertEquals(listOf("b"), state.pendingPairIds)
        assertNull(state.provisionalScore)
    }

    @Test
    fun aRepeatWaitsForMinMatchesBetweenRepeats() {
        val pairs = listOf(
            occurrence("a", "one"), occurrence("b", "two"), occurrence("c", "three"), occurrence("d", "four"),
            occurrence("a2", "one"), occurrence("e", "five"),
        )
        val engine = MatchMadnessEngine(repeatPlan(pairs, visible = 2))
        var state = engine.initialState()
        assertEquals(listOf("a", "b"), state.activePairIds)
        fun match(id: String) {
            state = engine.reduce(state, TapWord("w-$id", state.events.size * 100)).state
            state = engine.reduce(state, TapMeaning("m-$id", state.events.size * 100)).state
        }
        match("a") // "one" matched at count 1
        assertEquals(listOf("b", "c"), state.activePairIds)
        match("b")
        assertEquals(listOf("c", "d"), state.activePairIds)
        match("c") // count 3: "one" still too recent, so "five" comes on first
        assertEquals(listOf("d", "e"), state.activePairIds)
        match("d") // count 4: 4 − 1 ≥ 3, the repeat may come back
        assertEquals(listOf("e", "a2"), state.activePairIds)
    }

    @Test
    fun repeatedWordsAreMatchedByOccurrenceNotText() {
        val engine = MatchMadnessEngine(repeatPlan(listOf(occurrence("a", "allocate"), occurrence("c", "clarify")), visible = 2))
        var state = engine.initialState()
        state = engine.reduce(state, TapWord("w-a", 10)).state
        val step = engine.reduce(state, TapMeaning("m-c", 20))
        assertEquals(MatchMadnessFeedback.Wrong("w-a", "m-c"), step.feedback)
    }

    @Test
    fun reachingTheGoalEndsTheGameAndIgnoresFurtherTaps() {
        val actions = (1..6).flatMap { listOf(TapWord("w$it", it * 1000), TapMeaning("m$it", it * 1000 + 100)) }
        val (state, feedback) = run(actions)
        assertEquals(MatchMadnessFeedback.GoalReached, feedback.last())
        assertEquals(MatchMadnessState.Phase.GOAL_REACHED, state.phase)
        assertEquals(6, state.correctCount)
        assertEquals(6, state.longestCombo)
        assertTrue(state.wordOrder.isEmpty())
        // 600 + 10 × (1+2+3+4+5+5), plus the clear bonus 5 × floor((90000 − 6100) / 1000) = 415.
        assertEquals(1215, state.provisionalScore)

        val after = engine.reduce(state, TapWord("w1", 9_000))
        assertEquals(MatchMadnessFeedback.None, after.feedback)
        assertEquals(state, after.state)
    }

    @Test
    fun eventTimesNeverGoBackwards() {
        val (state, _) = run(listOf(TapWord("w1", 500), TapMeaning("m2", 800), TapWord("w2", 700), TapMeaning("m2", 600)))
        // Strictly increasing, as the server requires.
        assertEquals(listOf(800, 801), state.events.map { it.elapsedMs })
    }

    @Test
    fun commitBodyCarriesEveryAttemptAndNoScore() {
        val (state, _) = run(listOf(TapWord("w1", 100), TapMeaning("m2", 200), TapWord("w1", 300), TapMeaning("m1", 400)))
        val body = MatchMadnessEngine.commitBody(state, ChallengeEndedReason.TIME_UP, endedElapsedMs = 90_000)
        val json = Json.parseToJsonElement(body.encoded().toString(Charsets.UTF_8)).jsonObject
        assertEquals(setOf("events", "endedReason", "endedElapsedMs"), json.keys)
        assertEquals("time_up", json["endedReason"]!!.jsonPrimitive.content)
        assertEquals(90_000, json["endedElapsedMs"]!!.jsonPrimitive.int)
        val events = json["events"]!!.jsonArray.map { it as JsonObject }
        assertEquals(2, events.size)
        assertEquals(setOf("seq", "type", "elapsedMs", "wordTileId", "meaningTileId"), events[0].keys)
        assertEquals("match", events[0]["type"]!!.jsonPrimitive.content)
        // Encoding is stable, so a retry is byte-identical.
        assertArrayEquals(body.encoded(), body.encoded())
        assertEquals(
            """{"endedElapsedMs":90000,"endedReason":"time_up","events":[""" +
                """{"elapsedMs":200,"meaningTileId":"m2","seq":1,"type":"match","wordTileId":"w1"},""" +
                """{"elapsedMs":400,"meaningTileId":"m1","seq":2,"type":"match","wordTileId":"w1"}]}""",
            body.encoded().toString(Charsets.UTF_8),
        )
    }
}
