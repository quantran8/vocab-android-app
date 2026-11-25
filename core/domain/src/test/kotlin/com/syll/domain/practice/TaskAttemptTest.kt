package com.syll.domain.practice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.syll.domain.practice.TaskEvent.Action as A

/** Port of iOS `TaskAttemptTests`. */
class TaskAttemptTest {
    @Test
    fun recordingCountsAsRightFirstTimeOnlyWhenTheFirstTakePasses() {
        val first = TaskAttempt(PracticeFixtures.recording(maxRetries = 1), TaskPhase.WEAK_WORDS_PRACTICE)
        first.recordTranscript("clarify", passed = true)
        first.finishTranscribed()
        assertTrue(first.isRightFirstTime)

        val second = TaskAttempt(PracticeFixtures.recording(maxRetries = 1), TaskPhase.WEAK_WORDS_PRACTICE)
        second.recordTranscript("clarity", passed = false)
        second.recordTranscript("clarify", passed = true)
        second.finishTranscribed()
        assertFalse(second.isRightFirstTime)

        val unavailable = TaskAttempt(PracticeFixtures.recording(), TaskPhase.WEAK_WORDS_PRACTICE)
        unavailable.markRecordingUnavailable("mic_denied")
        assertFalse(unavailable.isRightFirstTime)
    }

    @Test
    fun eventsCarryTheTimeSinceTheTaskWasShown() {
        val attempt = TaskAttempt(PracticeFixtures.typing(), TaskPhase.DUE_REVIEW)
        attempt.elapsedMs = 1_200
        attempt.useHint()
        // A clock that steps back never makes the times decrease, and nothing goes past the backend's cap.
        attempt.elapsedMs = 900
        attempt.submitText("clarify")
        assertEquals(listOf(1_200, 1_200), attempt.events.map { it.elapsedMs })
        attempt.elapsedMs = 99 * 60 * 1000
        assertEquals(TaskAttempt.MAX_ELAPSED_MS, attempt.elapsedMs)

        assertEquals("""{"seq":1,"elapsedMs":1200,"action":"hint"}""", attempt.events[0].toJson().toString())
        assertEquals("""{"seq":1,"action":"hint"}""", TaskEvent(1, A.Hint).toJson().toString())
    }

    @Test
    fun normalizeMatchesTheBackend() {
        assertEquals("new york", PracticeAnswer.normalize("  New \t\n  York "))
        assertEquals("café", PracticeAnswer.normalize("café"))
        assertNotEquals(PracticeAnswer.normalize("cafe"), PracticeAnswer.normalize("café"))
        assertEquals("it's!", PracticeAnswer.normalize("it's!"))
        assertEquals("a b", PracticeAnswer.normalize("a ﻿b"))
    }

    @Test
    fun graphemesCountClustersOfTheNfcString() {
        assertEquals(5, PracticeAnswer.graphemes("cafés").size)
        assertEquals(listOf("a", "👍🏽", "b"), PracticeAnswer.graphemes("a👍🏽b"))
    }

    @Test
    fun seqIncreasesAcrossEveryEventKind() {
        val attempt = TaskAttempt(PracticeFixtures.reorder(), TaskPhase.RANDOM_PRACTICE)
        attempt.tapTile(2)
        attempt.tapTile(1)
        attempt.removeTile(1)
        attempt.useHint()
        assertEquals(listOf(1, 2, 3, 4), attempt.events.map { it.seq })
    }

    @Test
    fun noEventsAfterACorrectSubmit() {
        val attempt = TaskAttempt(PracticeFixtures.choice(), TaskPhase.DUE_REVIEW)
        assertEquals(false, attempt.submitOption(7))
        attempt.tryAgain()
        assertEquals(true, attempt.submitOption(3))
        val count = attempt.events.size
        attempt.reveal()
        assertNull(attempt.submitOption(3))
        assertEquals(count, attempt.events.size)
        assertEquals(TaskAttempt.Status.SOLVED, attempt.status)
    }

    @Test
    fun unknownOptionIsNotRecorded() {
        val attempt = TaskAttempt(PracticeFixtures.choice(), TaskPhase.DUE_REVIEW)
        assertNull(attempt.submitOption(99))
        assertTrue(attempt.events.isEmpty())
    }

    @Test
    fun firstRecallCannotBeRevealedBeforeASubmit() {
        val attempt = TaskAttempt(PracticeFixtures.typing(), TaskPhase.NEW_PRACTICE)
        assertTrue(attempt.requiresSubmission)
        assertFalse(attempt.canReveal)
        attempt.reveal()
        assertTrue(attempt.events.isEmpty())

        attempt.submitText("wrong")
        assertTrue(attempt.canReveal)
        attempt.reveal()
        assertEquals(TaskAttempt.Status.REVEALED, attempt.status)
    }

    @Test
    fun reviewRecallCanBeRevealedStraightAway() {
        val attempt = TaskAttempt(PracticeFixtures.typing(), TaskPhase.DUE_REVIEW)
        assertTrue(attempt.canReveal)
        attempt.reveal()
        assertEquals(listOf<A>(A.Reveal), attempt.events.map { it.action })
    }

    @Test
    fun typedValueIsCappedAtTheBackendLimit() {
        val attempt = TaskAttempt(PracticeFixtures.typing(), TaskPhase.DUE_REVIEW)
        attempt.submitText("x".repeat(500))
        val value = (attempt.events.first().action as A.SubmitValue).value
        assertEquals(TaskAttempt.MAX_VALUE_LENGTH, value.length)
    }

    @Test
    fun blankTextIsNotAnAttempt() {
        val attempt = TaskAttempt(PracticeFixtures.typing(), TaskPhase.DUE_REVIEW)
        assertNull(attempt.submitText("   "))
        assertTrue(attempt.events.isEmpty())
    }

    @Test
    fun tileSubmitMatchesTheReplayedRow() {
        val attempt = TaskAttempt(PracticeFixtures.reorder(), TaskPhase.RANDOM_PRACTICE)
        attempt.tapTile(1)
        attempt.tapTile(2)
        attempt.moveTile(2, 0)
        assertEquals(true, attempt.submitTiles())
        assertEquals(A.SubmitTiles(listOf(2, 1)), attempt.events.last().action)
    }

    @Test
    fun tileTapRules() {
        val attempt = TaskAttempt(PracticeFixtures.reorder(), TaskPhase.RANDOM_PRACTICE)
        attempt.tapTile(1)
        attempt.tapTile(1)
        attempt.removeTile(2)
        attempt.moveTile(1, 3)
        assertEquals(listOf<A>(A.TapTile(1)), attempt.events.map { it.action })
    }

    @Test
    fun matchEndsWhenEveryPairIsRight() {
        val attempt = TaskAttempt(PracticeFixtures.match(), TaskPhase.RANDOM_PRACTICE)
        assertEquals(false, attempt.submitPair(1, 6))
        assertEquals(true, attempt.submitPair(1, 5))
        assertNull("A solved pair can't be sent again", attempt.submitPair(1, 5))
        assertFalse(attempt.isFinished)
        assertEquals(true, attempt.submitPair(2, 6))
        assertEquals(TaskAttempt.Status.SOLVED, attempt.status)
    }

    @Test
    fun surgeryScoresTheRepairedWord() {
        val attempt = TaskAttempt(PracticeFixtures.surgery(), TaskPhase.DUE_REVIEW)
        assertNull("Nothing fixed yet", attempt.submitRepair(emptyMap()))
        assertNull("Out of range", attempt.submitRepair(mapOf(8 to "")))
        assertEquals(false, attempt.submitRepair(mapOf(4 to "x")))
        attempt.tryAgain()
        assertEquals("An extra change spoils it", false, attempt.submitRepair(mapOf(4 to "", 0 to "k")))
        attempt.tryAgain()
        assertEquals("Blank means delete; either r spells it", true, attempt.submitRepair(mapOf(3 to "  ")))
        assertEquals("clarify", attempt.solutionText)
        assertEquals(
            listOf<A>(
                A.SubmitRepair(listOf(RepairEdit(4, "x"))),
                A.SubmitRepair(listOf(RepairEdit(0, "k"), RepairEdit(4, ""))),
                A.SubmitRepair(listOf(RepairEdit(3, ""))),
            ),
            attempt.events.map { it.action },
        )
    }

    @Test
    fun recordingHasExactlyOneEvent() {
        val attempt = TaskAttempt(PracticeFixtures.recording(), TaskPhase.NEW_PRACTICE)
        attempt.finishRecording("r1")
        attempt.markRecordingUnavailable("x")
        assertEquals(listOf(TaskEvent(1, A.Assessed("r1"))), attempt.events)
    }

    @Test
    fun recordingCanNotBeRevealed() {
        assertFalse(TaskAttempt(PracticeFixtures.recording(), TaskPhase.NEW_PRACTICE).canReveal)
    }

    @Test
    fun deliveryModeIsTruthful() {
        assertNull(TaskAttempt(PracticeFixtures.typing(), TaskPhase.DUE_REVIEW).deliveryMode)

        val attempt = TaskAttempt(PracticeFixtures.typing(prompt = TaskPrompt.Audio(PracticeFixtures.clip)), TaskPhase.DUE_REVIEW)
        assertEquals("Never played", DeliveryMode.NONE, attempt.deliveryMode)
        attempt.recordAudioPlayback(succeeded = false)
        assertEquals(DeliveryMode.FALLBACK, attempt.deliveryMode)
        attempt.recordAudioPlayback(succeeded = true)
        assertEquals(DeliveryMode.AUDIO, attempt.deliveryMode)
        attempt.recordAudioPlayback(succeeded = false)
        assertEquals("Sound that reached the learner stays delivered", DeliveryMode.AUDIO, attempt.deliveryMode)
    }

    @Test
    fun eventLimitEndsATaskValidly() {
        val attempt = TaskAttempt(PracticeFixtures.reorder(), TaskPhase.NEW_PRACTICE)
        while (!attempt.isFinished) {
            attempt.tapTile(1)
            if (!attempt.isFinished) attempt.removeTile(1)
        }
        assertEquals(TaskAttempt.MAX_EVENTS, attempt.events.size)
        assertEquals("First recall still ends on a submit", 1, attempt.submitCount)
    }

    @Test
    fun eventLimitRevealsAnUnfinishedMatch() {
        val attempt = TaskAttempt(PracticeFixtures.match(), TaskPhase.RANDOM_PRACTICE)
        while (!attempt.isFinished) attempt.submitPair(1, 6)
        assertEquals(TaskAttempt.Status.REVEALED, attempt.status)
        assertEquals(TaskAttempt.MAX_EVENTS, attempt.events.size)
    }

    @Test
    fun lastTryEndsTheTaskWithNoExtraEvent() {
        val attempt = TaskAttempt(PracticeFixtures.choice(maxRetries = 1), TaskPhase.DUE_REVIEW)
        attempt.submitOption(7)
        assertEquals(TaskAttempt.Status.WRONG, attempt.status)
        attempt.tryAgain()
        attempt.submitOption(7)
        assertEquals(TaskAttempt.Status.EXHAUSTED, attempt.status)
        assertEquals(listOf<A>(A.SubmitOption(7), A.SubmitOption(7)), attempt.events.map { it.action })

        attempt.tryAgain()
        assertNull("A submit past the limit rejects the commit", attempt.submitOption(3))
        assertEquals(2, attempt.submitCount)
    }

    @Test
    fun lastTryCanStillBeRight() {
        val attempt = TaskAttempt(PracticeFixtures.choice(maxRetries = 1), TaskPhase.DUE_REVIEW)
        attempt.submitOption(7)
        attempt.tryAgain()
        assertEquals(true, attempt.submitOption(3))
        assertEquals(TaskAttempt.Status.SOLVED, attempt.status)
    }

    @Test
    fun zeroRetriesAllowsOnlyTheFirstTry() {
        val attempt = TaskAttempt(PracticeFixtures.choice(maxRetries = 0), TaskPhase.DUE_REVIEW)
        attempt.submitOption(7)
        assertEquals(TaskAttempt.Status.EXHAUSTED, attempt.status)
    }

    @Test
    fun noLimitWhenThePlanHasNone() {
        val attempt = TaskAttempt(PracticeFixtures.choice(), TaskPhase.DUE_REVIEW)
        repeat(5) {
            attempt.submitOption(7)
            attempt.tryAgain()
        }
        assertEquals(5, attempt.submitCount)
        assertEquals(TaskAttempt.Status.ANSWERING, attempt.status)
    }

    @Test
    fun matchCountsTriesPerLeftItem() {
        val attempt = TaskAttempt(PracticeFixtures.match(maxRetries = 1), TaskPhase.RANDOM_PRACTICE)
        attempt.submitPair(1, 6)
        attempt.submitPair(2, 5)
        assertTrue("One miss each, not two on the same item", attempt.exhaustedLeftIds.isEmpty())

        attempt.submitPair(1, 6)
        assertEquals(setOf(1), attempt.exhaustedLeftIds)
        assertNull("An item out of tries can't be sent again", attempt.submitPair(1, 5))
        assertFalse(attempt.isFinished)
    }

    @Test
    fun matchItemOutOfTriesDoesNotBlockTheBoard() {
        val attempt = TaskAttempt(PracticeFixtures.match(maxRetries = 0), TaskPhase.RANDOM_PRACTICE)
        attempt.submitPair(1, 6)
        assertEquals(true, attempt.submitPair(2, 6))
        assertEquals(TaskAttempt.Status.EXHAUSTED, attempt.status)
        assertEquals("Ending adds no reveal", 2, attempt.events.size)
        assertEquals("Only the pair that was missed", "a — x", attempt.missedPairsText)
    }

    @Test
    fun allPairsTextListsEveryPairOfAMatchAndNothingElse() {
        assertEquals("a — x\nb — y", TaskAttempt(PracticeFixtures.match(), TaskPhase.RANDOM_PRACTICE).allPairsText)
        assertNull(TaskAttempt(PracticeFixtures.surgery(), TaskPhase.RANDOM_PRACTICE).allPairsText)
    }

    @Test
    fun recordingCountsEveryAssessment() {
        val attempt = TaskAttempt(PracticeFixtures.recording(maxRetries = 1), TaskPhase.NEW_PRACTICE)
        attempt.countAssessment()
        assertTrue(attempt.canRecordAgain)
        attempt.countAssessment()
        assertFalse(attempt.canRecordAgain)
    }

    @Test
    fun eventsEncodeOnlyTheirOwnFields() {
        val cases = listOf(
            A.SubmitOption(2) to """{"seq":1,"action":"submit","optionId":2}""",
            A.SubmitValue("x") to """{"seq":1,"action":"submit","value":"x"}""",
            A.SubmitTiles(listOf(2, 3)) to """{"seq":1,"action":"submit","tileIds":[2,3]}""",
            A.SubmitRepair(listOf(RepairEdit(4, ""))) to """{"seq":1,"action":"submit","graphemeIndex":4,"replacement":""}""",
            A.SubmitRepair(listOf(RepairEdit(1, "a"), RepairEdit(4, ""))) to
                """{"seq":1,"action":"submit","edits":[{"graphemeIndex":1,"replacement":"a"},{"graphemeIndex":4,"replacement":""}]}""",
            A.Pair(1, 5) to """{"seq":1,"action":"pair","leftId":1,"rightId":5}""",
            A.TapTile(1) to """{"seq":1,"action":"tap_tile","tileId":1}""",
            A.RemoveTile(1) to """{"seq":1,"action":"remove_tile","tileId":1}""",
            A.MoveTile(1, 0) to """{"seq":1,"action":"move_tile","tileId":1,"toIndex":0}""",
            A.Hint to """{"seq":1,"action":"hint"}""",
            A.Reveal to """{"seq":1,"action":"reveal"}""",
            A.Assessed("r") to """{"seq":1,"action":"assessed","assessmentReceiptId":"r"}""",
            A.Unavailable("x") to """{"seq":1,"action":"unavailable","reason":"x"}""",
            A.Transcribed("clarify") to """{"seq":1,"action":"transcribed","transcript":"clarify"}""",
        )
        cases.forEach { (action, expected) -> assertEquals(expected, TaskEvent(1, action).toJson().toString()) }
    }

    @Test
    fun textPromptOmitsDeliveryMode() {
        val attempt = TaskAttempt(PracticeFixtures.typing(), TaskPhase.DUE_REVIEW)
        assertFalse(attempt.result.toJson().containsKey("deliveryMode"))
    }

    @Test
    fun commitBodyOmitsKnownStepsWhenThereAreNone() {
        val body = CommitBody(listOf("l1"), taskResults = emptyList())
        assertEquals("""{"learnStepsCompleted":["l1"],"taskResults":[]}""", body.encoded().decodeToString())
        val known = CommitBody(emptyList(), listOf("l1"), emptyList())
        assertEquals("""{"learnStepsCompleted":[],"knownLearnSteps":["l1"],"taskResults":[]}""", known.encoded().decodeToString())
    }

    @Test
    fun aCopyIsIndependent() {
        val attempt = TaskAttempt(PracticeFixtures.typing(), TaskPhase.NEW_PRACTICE)
        val copy = attempt.copy()
        attempt.submitText("x")
        assertTrue(copy.events.isEmpty())
        assertTrue(copy.requiresSubmission)
    }

    @Test
    fun hintsRevealTheFirstLetterThenHalf() {
        assertEquals("c _ _ _ _ _ _", HintSequence.hint("clarify", 1))
        assertEquals("c l a r _ _ _", HintSequence.hint("clarify", 2))
        assertNull(HintSequence.hint("clarify", 0))
    }
}
