package com.syll.features.practice

import com.syll.domain.NewWordReason
import com.syll.domain.practice.CommitResponse
import com.syll.domain.practice.CommitStatus
import com.syll.domain.practice.CommitSummary
import com.syll.domain.practice.KnownCheck
import com.syll.domain.practice.LearnCard
import com.syll.domain.practice.NewWordGroup
import com.syll.domain.practice.NewWordSummary
import com.syll.domain.practice.NewWordsIssue
import com.syll.domain.practice.PairKey
import com.syll.domain.practice.PlanReviewWord
import com.syll.domain.practice.PlanStep
import com.syll.domain.practice.PracticeFilter
import com.syll.domain.practice.PracticeTask
import com.syll.domain.practice.RecordedAudio
import com.syll.domain.practice.RecordingAssessment
import com.syll.domain.practice.RecordingScoring
import com.syll.domain.practice.ResultGroups
import com.syll.domain.practice.ResultItem
import com.syll.domain.practice.ReviewGroup
import com.syll.domain.practice.ReviewSummary
import com.syll.domain.practice.SessionMode
import com.syll.domain.practice.SessionPlan
import com.syll.domain.practice.TaskBody
import com.syll.domain.practice.TaskEvent
import com.syll.domain.practice.TaskOption
import com.syll.domain.practice.TaskPhase
import com.syll.domain.practice.TaskPrompt
import com.syll.domain.practice.TaskTemplate
import com.syll.features.auth.StubVocabApi
import com.syll.domain.ranking.CommitLP
import com.syll.domain.ranking.CommitTier
import com.syll.domain.ranking.LPBreakdown
import com.syll.domain.ranking.TierKey
import com.syll.domain.ranking.TierUp
import com.syll.network.ApiError
import com.syll.platform.NoOpFeedback
import com.syll.platform.SilentAudioClips
import com.syll.platform.SpeechTranscribing
import com.syll.platform.VoiceRecording
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port of iOS `PracticeSessionStoreTests` and the session part of `ScopeReviewLoadTests`. */
class PracticeSessionStoreTest {
    private val summary = CommitSummary(newWordGoalSlots = 1, reviewedMeanings = 0, practiceTasks = 2)
    private val committed = CommitResponse("s1", summary)
    private val api = StubVocabApi()
    private val recorder = StubRecorder()
    private val transcriber = StubTranscriber()

    private fun choice(id: String = "t-choice") = PracticeTask(
        id, TaskTemplate.SELECT, "gloss", TaskPrompt.Text("clarify"), "?",
        TaskBody.Choice(listOf(TaskOption(7, "a"), TaskOption(3, "b")), null, 3),
    )

    private fun typing(id: String) = PracticeTask(
        id, TaskTemplate.TYPING, "meaning_recall", TaskPrompt.Text("làm rõ"), null, TaskBody.Typing(listOf("clarify")),
    )

    private val match = PracticeTask(
        "t-match", TaskTemplate.MATCH, "meaning_pairs", TaskPrompt.Text("Match"), null,
        TaskBody.Match(listOf(TaskOption(1, "a"), TaskOption(2, "b")), listOf(TaskOption(5, "x"), TaskOption(6, "y")), listOf(PairKey(1, 5), PairKey(2, 6))),
    )

    private fun recordingTask(maxRetries: Int? = null) = PracticeTask(
        "t-recording", TaskTemplate.RECORDING, "scripted_pronunciation", TaskPrompt.Text("clarify"), null,
        TaskBody.Recording(listOf("clarify")), maxRetries = maxRetries,
    )

    private val card = LearnCard("clarify", "làm rõ")
    private val learnAndChoice = listOf(PlanStep.Learn("l1", card), PlanStep.Practice(TaskPhase.NEW_PRACTICE, choice()))
    private val review = listOf(PlanStep.Practice(TaskPhase.DUE_REVIEW, choice()))

    private fun CoroutineScope.makeStore(steps: List<PlanStep>, scoring: RecordingScoring = RecordingScoring.BACKEND): PracticeSessionStore {
        api.plan = Result.success(SessionPlan("s1", SessionMode.DAILY, steps))
        return PracticeSessionStore(api, SilentAudioClips, recorder, NoOpFeedback, this, recordingScoring = scoring, transcriber = transcriber)
    }

    private suspend fun waitForStage(store: PracticeSessionStore, matches: (PracticeSessionStore.Stage) -> Boolean) {
        repeat(1000) {
            if (matches(store.stage)) return
            yield()
        }
    }

    private suspend fun settled(store: PracticeSessionStore) = waitForStage(store) {
        it != PracticeSessionStore.Stage.Running && it != PracticeSessionStore.Stage.Committing
    }

    private suspend fun runToCommit(store: PracticeSessionStore) {
        store.start(SessionMode.DAILY)
        store.advance()
        store.submitOption(3)
        store.advance()
        settled(store)
    }

    private fun lastBody(): JsonObject = Json.parseToJsonElement(api.commitBodies.last().decodeToString()).jsonObject

    private fun actions(store: PracticeSessionStore) = store.attempts["t-recording"]?.events?.map { it.action }

    @Test
    fun emptyPlanHasNoSession() = runBlocking {
        val store = makeStore(emptyList())
        api.plan = Result.success(SessionPlan(null, SessionMode.DAILY, emptyList()))
        store.start(SessionMode.DAILY)
        assertEquals(PracticeSessionStore.Stage.Empty, store.stage)
    }

    @Test
    fun dueReviewListsWordsUntilTheLearnerContinues() = runBlocking {
        val store = makeStore(review)
        api.plan = Result.success(
            SessionPlan(
                "s1", SessionMode.DAILY, review,
                reviewWords = listOf(PlanReviewWord("w1", "m1", "clarify"), PlanReviewWord("w1", "m2", "clarify")),
            ),
        )
        store.start(SessionMode.DAILY)
        val preview = store.stage as PracticeSessionStore.Stage.ReviewPreview
        assertEquals("A word with two due meanings appears once", listOf("clarify"), preview.words.map { it.lemma })

        store.submitOption(3)
        assertEquals(0, store.currentAttempt?.submitCount)

        store.continueToPractice()
        assertEquals(PracticeSessionStore.Stage.Running, store.stage)
    }

    @Test
    fun reviewAndNewWordsCountProgressSeparately() = runBlocking {
        val store = makeStore(
            listOf(
                PlanStep.Practice(TaskPhase.DUE_REVIEW, choice("r1")),
                PlanStep.Practice(TaskPhase.DUE_REVIEW, choice("r2")),
                PlanStep.Learn("l1", card),
                PlanStep.Practice(TaskPhase.NEW_PRACTICE, choice("n1")),
                PlanStep.Practice(TaskPhase.NEW_PRACTICE, choice("n2")),
            ),
        )
        store.start(SessionMode.DAILY)
        assertEquals(SessionPartProgress(SessionPartProgress.Part.REVIEW, 1, 2), store.partProgress)
        store.submitOption(3)
        store.advance()
        assertEquals(SessionPartProgress(SessionPartProgress.Part.REVIEW, 2, 2), store.partProgress)
        store.submitOption(3)
        store.advance()
        assertEquals(PracticeSessionStore.Stage.ReviewFinished, store.stage)
        store.continueToPractice()
        assertEquals(SessionPartProgress(SessionPartProgress.Part.NEW_WORDS, 1, 3), store.partProgress)
        store.advance()
        assertEquals(SessionPartProgress(SessionPartProgress.Part.NEW_WORDS, 2, 3), store.partProgress)
    }

    @Test
    fun sessionWithoutReviewOrNewWordsIsOnePracticePart() = runBlocking {
        val store = makeStore(
            listOf(PlanStep.Practice(TaskPhase.RANDOM_PRACTICE, choice("p1")), PlanStep.Practice(TaskPhase.RANDOM_PRACTICE, choice("p2"))),
        )
        store.start(SessionMode.DAILY)
        assertEquals(SessionPartProgress(SessionPartProgress.Part.PRACTICE, 1, 2), store.partProgress)
    }

    @Test
    fun noReviewListWithoutReviewTasks() = runBlocking {
        val store = makeStore(learnAndChoice)
        store.start(SessionMode.DAILY)
        assertEquals(PracticeSessionStore.Stage.Running, store.stage)
    }

    @Test
    fun newWordsWaitUntilTheLearnerLeavesTheReviewEnd() = runBlocking {
        val store = makeStore(listOf(PlanStep.Practice(TaskPhase.DUE_REVIEW, choice()), PlanStep.Learn("l1", card)))
        store.start(SessionMode.DAILY)
        store.submitOption(3)
        store.advance()
        assertEquals(PracticeSessionStore.Stage.ReviewFinished, store.stage)
        assertEquals(listOf(card), store.upcomingCards)
        store.continueToPractice()
        assertEquals(PracticeSessionStore.Stage.Running, store.stage)
        assertEquals(1, store.stepIndex)
    }

    @Test
    fun reviewOnlyPlanCommitsWithoutTheReviewEnd() = runBlocking {
        val store = makeStore(review)
        api.commitResults += Result.success(committed)
        store.start(SessionMode.DAILY)
        store.submitOption(3)
        store.advance()
        settled(store)
        assertEquals(PracticeSessionStore.Stage.Finished(summary), store.stage)
    }

    @Test
    fun reviewOnlyPlanContinuesToNewWordsAfterSaving() = runBlocking {
        val store = makeStore(review)
        api.commitResults += Result.success(committed)
        store.start(SessionMode.DAILY)
        store.submitOption(3)
        api.plan = Result.success(SessionPlan("s2", SessionMode.DAILY, learnAndChoice))
        store.advance()
        settled(store)
        assertEquals(PracticeSessionStore.Stage.ReviewFinished, store.stage)
        assertEquals(1, api.commitBodies.size)
        store.continueToPractice()
        assertEquals(PracticeSessionStore.Stage.Running, store.stage)
        assertEquals(learnAndChoice[0], store.currentStep)
    }

    @Test
    fun failedNextPlanStillShowsTheReviewResults() = runBlocking {
        val store = makeStore(review)
        api.commitResults += Result.success(committed)
        store.start(SessionMode.DAILY)
        store.submitOption(3)
        api.plan = Result.failure(ApiError.Transport)
        store.advance()
        settled(store)
        assertEquals(PracticeSessionStore.Stage.Finished(summary), store.stage)
    }

    @Test
    fun withheldNewWordsEndTheSessionInsteadOfPlanningAgain() = runBlocking {
        val store = makeStore(review)
        api.plan = Result.success(SessionPlan("s1", SessionMode.DAILY, review, newWords = NewWordsIssue(5, 0, NewWordReason.SCOPE_EXHAUSTED)))
        api.commitResults += Result.success(committed)
        store.start(SessionMode.DAILY)
        store.submitOption(3)
        store.advance()
        settled(store)
        assertEquals(PracticeSessionStore.Stage.Finished(summary), store.stage)
        assertEquals("No second daily plan: it would answer the same", 1, api.planRequests.size)
    }

    @Test
    fun reviewOnlyAsksForNoNewWordsAndNeverContinues() = runBlocking {
        val store = makeStore(review)
        api.commitResults += Result.success(committed)
        store.startReviewOnly()
        assertEquals(true, api.planRequests.last().reviewOnly)
        assertNull(api.planRequests.last().knownCheck)
        store.continueToPractice()
        store.submitOption(3)
        store.advance()
        settled(store)
        assertEquals(PracticeSessionStore.Stage.Finished(summary), store.stage)
        assertEquals(1, api.planRequests.size)
    }

    @Test
    fun missingCheckpointChallengeIsEmptyNotAnError() = runBlocking {
        val store = makeStore(emptyList())
        api.plan = Result.failure(ApiError.Server(404, "NOT_FOUND", "", retryable = false, requestId = null))
        store.start(SessionMode.CHECKPOINT_CHALLENGE)
        assertEquals(PracticeSessionStore.Stage.Empty, store.stage)
    }

    @Test
    fun capabilitiesReflectTheDevice() = runBlocking {
        val store = makeStore(learnAndChoice)
        recorder.canRecord = false
        store.start(SessionMode.CHECKPOINT_CHALLENGE, challengeId = "c1")
        val request = api.planRequests.first()
        assertEquals(false, request.capabilities.audioPlayback)
        assertEquals(false, request.capabilities.microphone)
        assertEquals("c1", request.challengeId)
    }

    @Test
    fun randomPracticeKeepsItsSourceAcrossARetry() = runBlocking {
        val store = makeStore(learnAndChoice)
        api.plan = Result.failure(ApiError.Transport)
        store.startPractice(PracticeFilter.Topics(listOf("t1")))
        store.retry()
        assertEquals(SessionMode.RANDOM_PRACTICE, api.planRequests.first().mode)
        assertEquals(listOf(PracticeFilter.Topics(listOf("t1")), PracticeFilter.Topics(listOf("t1"))), api.planRequests.map { it.filter })
    }

    @Test
    fun cannotAdvancePastAnUnfinishedTask() = runBlocking {
        val store = makeStore(review)
        store.start(SessionMode.DAILY)
        store.continueToPractice()
        store.advance()
        assertEquals(0, store.stepIndex)
        assertEquals(PracticeSessionStore.Stage.Running, store.stage)
    }

    @Test
    fun commitIncludesLearnStepsAndEveryTask() = runBlocking {
        val store = makeStore(learnAndChoice)
        api.commitResults += Result.success(committed)
        runToCommit(store)
        assertEquals(PracticeSessionStore.Stage.Finished(summary), store.stage)
        val body = lastBody()
        assertEquals(listOf("l1"), body["learnStepsCompleted"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf("t-choice"), body["taskResults"]!!.jsonArray.map { it.jsonObject["taskId"]!!.jsonPrimitive.content })
        assertFalse(body.containsKey("knownLearnSteps"))
    }

    @Test
    fun commitRetryResendsTheSameBytes() = runBlocking {
        val store = makeStore(learnAndChoice)
        api.commitResults += Result.failure(ApiError.Transport)
        api.commitResults += Result.success(committed)
        runToCommit(store)
        assertTrue((store.stage as PracticeSessionStore.Stage.Failed).failure is PracticeSessionStore.Failure.Commit)
        store.retry()
        assertEquals(PracticeSessionStore.Stage.Finished(summary), store.stage)
        assertEquals(2, api.commitBodies.size)
        assertArrayEquals(api.commitBodies[0], api.commitBodies[1])
    }

    @Test
    fun payloadConflictAdoptsTheSavedResult() = runBlocking {
        val store = makeStore(learnAndChoice)
        api.commitResults += Result.failure(ApiError.Server(409, "SESSION_PAYLOAD_CONFLICT", "", retryable = false, requestId = null))
        api.status = CommitStatus("committed", committed)
        runToCommit(store)
        assertEquals(PracticeSessionStore.Stage.Finished(summary), store.stage)
    }

    @Test
    fun expiredSessionCannotBeRetried() = runBlocking {
        val store = makeStore(learnAndChoice)
        api.commitResults += Result.failure(ApiError.Server(410, "SESSION_EXPIRED", "", retryable = false, requestId = null))
        runToCommit(store)
        assertTrue((store.stage as PracticeSessionStore.Stage.Failed).failure is PracticeSessionStore.Failure.SessionLost)
    }

    // "I know this"

    private val knownCheckPlan = SessionPlan(
        "s1", SessionMode.DAILY,
        listOf(
            PlanStep.Practice(TaskPhase.KNOWN_CHECK, typing("k1")),
            PlanStep.Learn("l1", card),
            PlanStep.Practice(TaskPhase.NEW_PRACTICE, choice("n1")),
            PlanStep.Practice(TaskPhase.NEW_PRACTICE, match),
        ),
        knownChecks = mapOf("k1" to KnownCheck("l1", setOf("n1"))),
    )

    @Test
    fun dailyAlwaysAsksForKnownChecks() = runBlocking {
        val store = makeStore(emptyList())
        api.plan = Result.success(knownCheckPlan)
        store.start(SessionMode.DAILY)
        assertEquals(true, api.planRequests.last().knownCheck)
        assertEquals(card.word, store.knownCheckOffer?.word)
    }

    @Test
    fun markedKnownSkipsTheCheckCardAndOwnTasks() = runBlocking {
        val store = makeStore(emptyList())
        api.plan = Result.success(knownCheckPlan)
        api.commitResults += Result.success(committed)
        store.start(SessionMode.DAILY)
        store.markKnown()
        assertEquals(1, store.knownWordCount)
        assertEquals("t-match", (store.currentStep as PlanStep.Practice).task.id)
        store.submitPair(1, 5)
        store.submitPair(2, 6)
        store.advance()
        settled(store)
        val body = lastBody()
        assertEquals(emptyList<String>(), body["learnStepsCompleted"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf("l1"), body["knownLearnSteps"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf("t-match"), body["taskResults"]!!.jsonArray.map { it.jsonObject["taskId"]!!.jsonPrimitive.content })
    }

    @Test
    fun continueOnTheOfferedCardLearnsTheWordAndSendsNoCheckResult() = runBlocking {
        val store = makeStore(emptyList())
        api.plan = Result.success(knownCheckPlan)
        api.commitResults += Result.success(committed)
        store.start(SessionMode.DAILY)
        store.declineKnownCheck()
        assertEquals("The card was already shown with the offer", "n1", (store.currentStep as PlanStep.Practice).task.id)
        store.submitOption(3)
        store.advance()
        store.submitPair(1, 5)
        store.submitPair(2, 6)
        store.advance()
        settled(store)
        val body = lastBody()
        assertEquals(listOf("l1"), body["learnStepsCompleted"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertFalse(body.containsKey("knownLearnSteps"))
        assertEquals(listOf("n1", "t-match"), body["taskResults"]!!.jsonArray.map { it.jsonObject["taskId"]!!.jsonPrimitive.content })
        assertEquals(0, store.knownWordCount)
    }

    // Recording

    @Test
    fun passingRecordingCommitsTheLastReceipt() = runBlocking {
        val store = makeStore(listOf(PlanStep.Practice(TaskPhase.NEW_PRACTICE, recordingTask())))
        api.commitResults += Result.success(committed)
        api.assessResult = Result.success(RecordingAssessment("r1", "valid", passed = false, accuracyScore = 50.0))
        store.start(SessionMode.DAILY)
        store.startRecording()
        store.stopRecording()
        api.assessResult = Result.success(RecordingAssessment("r2", "valid", passed = true, accuracyScore = 90.0))
        store.startRecording()
        store.stopRecording()
        store.acceptRecording()
        assertEquals(listOf<TaskEvent.Action>(TaskEvent.Action.Assessed("r2")), actions(store))
    }

    @Test
    fun retryRecordingReturnsToTheMicOnlyWhileTriesRemain() = runBlocking {
        val store = makeStore(listOf(PlanStep.Practice(TaskPhase.NEW_PRACTICE, recordingTask(maxRetries = 2))), RecordingScoring.ON_DEVICE)
        transcriber.candidates = listOf("apricot")
        store.start(SessionMode.DAILY)
        var tries = 0
        while (store.currentAttempt?.canRecordAgain == true) {
            store.startRecording()
            store.stopRecording()
            tries += 1
            assertTrue(store.recording is PracticeSessionStore.RecordingStage.Assessed)
            if (store.currentAttempt?.canRecordAgain == true) {
                store.retryRecording()
                assertEquals(PracticeSessionStore.RecordingStage.Idle, store.recording)
            }
        }
        assertEquals(3, tries)
        store.retryRecording()
        assertTrue("Out of tries keeps the result", store.recording is PracticeSessionStore.RecordingStage.Assessed)
    }

    @Test
    fun onDeviceScoringCommitsTheTranscriptWithoutUploading() = runBlocking {
        val store = makeStore(listOf(PlanStep.Practice(TaskPhase.NEW_PRACTICE, recordingTask())), RecordingScoring.ON_DEVICE)
        transcriber.candidates = listOf("Clarify.")
        store.start(SessionMode.DAILY)
        store.startRecording()
        store.stopRecording()
        val assessment = (store.recording as PracticeSessionStore.RecordingStage.Assessed).assessment
        assertEquals(true, assessment.passed)
        store.acceptRecording()
        assertEquals(listOf<TaskEvent.Action>(TaskEvent.Action.Transcribed("clarify")), actions(store))
    }

    @Test
    fun onDeviceScoringFallsBackToTheBackendWithoutRecognition() = runBlocking {
        val store = makeStore(listOf(PlanStep.Practice(TaskPhase.NEW_PRACTICE, recordingTask())), RecordingScoring.ON_DEVICE)
        transcriber.hasAccess = false
        api.assessResult = Result.success(RecordingAssessment("r1", "valid", passed = true, accuracyScore = 90.0))
        store.start(SessionMode.DAILY)
        store.startRecording()
        store.stopRecording()
        store.acceptRecording()
        assertEquals(listOf<TaskEvent.Action>(TaskEvent.Action.Assessed("r1")), actions(store))
    }

    @Test
    fun freePlanWithoutRecognitionNeverUploads() = runBlocking {
        val store = makeStore(listOf(PlanStep.Practice(TaskPhase.NEW_PRACTICE, recordingTask())), RecordingScoring.ON_DEVICE_ONLY)
        transcriber.hasAccess = false
        store.start(SessionMode.DAILY)
        store.startRecording()
        assertEquals(PracticeSessionStore.RecordingStage.Blocked("onDeviceRecognitionUnavailable"), store.recording)
        store.acceptRecording()
        assertEquals(listOf<TaskEvent.Action>(TaskEvent.Action.Unavailable("onDeviceRecognitionUnavailable")), actions(store))
    }

    @Test
    fun recordingStopsAtTheLimitAndKeepsTheLastReceipt() = runBlocking {
        val store = makeStore(listOf(PlanStep.Practice(TaskPhase.NEW_PRACTICE, recordingTask(maxRetries = 0))))
        api.assessResult = Result.success(RecordingAssessment("r1", "invalid"))
        store.start(SessionMode.DAILY)
        store.startRecording()
        store.stopRecording()
        store.startRecording()
        assertNotEquals("A further upload would answer 422", PracticeSessionStore.RecordingStage.Recording, store.recording)
        store.acceptRecording()
        assertEquals(listOf<TaskEvent.Action>(TaskEvent.Action.Assessed("r1")), actions(store))
    }

    @Test
    fun speechOutageDoesNotUseATryAndCanBeSkipped() = runBlocking {
        val store = makeStore(listOf(PlanStep.Practice(TaskPhase.NEW_PRACTICE, recordingTask(maxRetries = 0))))
        api.assessResult = Result.failure(ApiError.Server(503, "SPEECH_PROVIDER_UNAVAILABLE", "", retryable = true, requestId = null))
        store.start(SessionMode.DAILY)
        store.startRecording()
        store.stopRecording()
        assertEquals(true, store.currentAttempt?.canRecordAgain)
        assertEquals(PracticeSessionStore.RecordingStage.UploadFailed, store.recording)
        store.acceptRecording()
        assertEquals(listOf<TaskEvent.Action>(TaskEvent.Action.Unavailable("speechProviderUnavailable")), actions(store))
    }

    @Test
    fun backendLimitFallsBackToTheLastReceipt() = runBlocking {
        val store = makeStore(listOf(PlanStep.Practice(TaskPhase.NEW_PRACTICE, recordingTask())))
        api.assessResult = Result.success(RecordingAssessment("r1", "valid", passed = false, accuracyScore = 50.0))
        store.start(SessionMode.DAILY)
        store.startRecording()
        store.stopRecording()
        api.assessResult = Result.failure(ApiError.Server(422, "INVALID_SUBMISSION", "", retryable = false, requestId = null))
        store.startRecording()
        store.stopRecording()
        store.acceptRecording()
        assertEquals(listOf<TaskEvent.Action>(TaskEvent.Action.Assessed("r1")), actions(store))
    }

    @Test
    fun deniedMicrophoneReportsUnavailable() = runBlocking {
        val store = makeStore(listOf(PlanStep.Practice(TaskPhase.NEW_PRACTICE, recordingTask())))
        recorder.hasAccess = false
        store.start(SessionMode.DAILY)
        store.startRecording()
        assertEquals(PracticeSessionStore.RecordingStage.Blocked("micPermissionDenied"), store.recording)
        store.acceptRecording()
        assertEquals(listOf<TaskEvent.Action>(TaskEvent.Action.Unavailable("micPermissionDenied")), actions(store))
    }

    @Test
    fun assessOnAnExpiredSessionEndsIt() = runBlocking {
        val store = makeStore(listOf(PlanStep.Practice(TaskPhase.NEW_PRACTICE, recordingTask())))
        api.assessResult = Result.failure(ApiError.Server(410, "SESSION_EXPIRED", "", retryable = false, requestId = null))
        store.start(SessionMode.DAILY)
        store.startRecording()
        store.stopRecording()
        assertTrue((store.stage as PracticeSessionStore.Stage.Failed).failure is PracticeSessionStore.Failure.SessionLost)
    }

    @Test
    fun closeWritesNothing() = runBlocking {
        val store = makeStore(learnAndChoice)
        store.start(SessionMode.DAILY)
        store.close()
        assertEquals(PracticeSessionStore.Stage.Idle, store.stage)
        assertTrue(api.commitBodies.isEmpty())
    }

    @Test
    fun eventsAreTimedFromWhenTheStepAppeared() = runBlocking {
        var clock = 1_000L
        api.plan = Result.success(SessionPlan("s1", SessionMode.DAILY, learnAndChoice))
        val store = PracticeSessionStore(api, SilentAudioClips, recorder, NoOpFeedback, this, now = { clock })
        store.start(SessionMode.DAILY)
        clock += 500
        store.advance()
        clock += 1_200
        store.submitOption(3)
        assertEquals(1_200, store.currentAttempt?.events?.single()?.elapsedMs)
    }

    // Results

    private val reviewed = ReviewSummary(1, ResultGroups(mapOf(ReviewGroup.SOLID to listOf(ResultItem("m-review", lemma = "endure")))))
    private val learned = NewWordSummary(1, ResultGroups(mapOf(NewWordGroup.SMOOTH to listOf(ResultItem("m-new", lemma = "symptom")))))

    @Test
    fun dailyChainShowsTheFirstCommitsReviewWithTheNewWords() = runBlocking {
        val store = makeStore(review)
        api.commitResults += Result.success(CommitResponse("s1", CommitSummary(review = reviewed, newWords = NewWordSummary())))
        api.commitResults += Result.success(CommitResponse("s2", CommitSummary(review = ReviewSummary(), newWords = learned)))
        store.start(SessionMode.DAILY)
        store.submitOption(3)
        api.plan = Result.success(SessionPlan("s2", SessionMode.DAILY, learnAndChoice))
        store.advance()
        settled(store)
        assertEquals(PracticeSessionStore.Stage.ReviewFinished, store.stage)
        assertEquals(reviewed, store.pendingReviewSummary)

        store.continueToPractice()
        store.advance()
        store.submitOption(3)
        store.advance()
        settled(store)
        val finished = (store.stage as PracticeSessionStore.Stage.Finished).summary
        assertEquals(reviewed, finished.review)
        assertEquals(learned, finished.newWords)
        assertNull(store.pendingReviewSummary)
    }

    /** iOS `RankingTests.testSessionAddsUpReviewAndNewWordCommitsAndCelebratesTheHighestTier`. */
    @Test
    fun sessionAddsUpReviewAndNewWordCommitsAndCelebratesTheHighestTier() = runBlocking {
        val unlocked = java.time.Instant.ofEpochSecond(1_762_916_400)
        val store = makeStore(review)
        api.commitResults += Result.success(
            CommitResponse(
                "s1", summary,
                lp = CommitLP(4, LPBreakdown(dueReviewLP = 4), weeklyLP = 39, lifetimeLP = 39),
                tier = CommitTier(level = 0, key = null, lifetimeActiveDays = 1),
            ),
        )
        api.commitResults += Result.success(
            CommitResponse(
                "s2", summary,
                lp = CommitLP(5, LPBreakdown(newMeaningLP = 5), weeklyLP = 44, lifetimeLP = 44),
                tier = CommitTier(1, TierKey.FIRST_STEPS, 1, tierUps = listOf(TierUp(1, TierKey.FIRST_STEPS, unlocked))),
            ),
        )
        store.start(SessionMode.DAILY)
        store.submitOption(3)
        api.plan = Result.success(SessionPlan("s2", SessionMode.DAILY, learnAndChoice))
        store.advance()
        settled(store)
        assertEquals(PracticeSessionStore.Stage.ReviewFinished, store.stage)
        store.continueToPractice()
        store.advance()
        store.submitOption(3)
        store.advance()
        settled(store)

        assertEquals(9, store.effort?.earned)
        assertEquals(LPBreakdown(newMeaningLP = 5, dueReviewLP = 4, extraPracticeLP = 0), store.effort?.breakdown)
        assertEquals(44, store.effort?.lifetimeLP)
        assertEquals(TierKey.FIRST_STEPS, store.effort?.celebratedTierUp?.key)
    }

    @Test
    fun aChainLeftHalfwayDoesNotCarryItsReviewIntoTheNextSession() = runBlocking {
        val store = makeStore(review)
        api.commitResults += Result.success(CommitResponse("s1", CommitSummary(review = reviewed)))
        store.start(SessionMode.DAILY)
        store.submitOption(3)
        api.plan = Result.success(SessionPlan("s2", SessionMode.DAILY, learnAndChoice))
        store.advance()
        settled(store)
        assertEquals(reviewed, store.pendingReviewSummary)
        store.close()
        assertNull(store.pendingReviewSummary)

        val next = CommitSummary(review = ReviewSummary(), newWords = learned)
        api.commitResults += Result.success(CommitResponse("s2", next))
        store.start(SessionMode.DAILY)
        store.advance()
        store.submitOption(3)
        store.advance()
        settled(store)
        assertEquals(PracticeSessionStore.Stage.Finished(next), store.stage)
    }

    @Test
    fun practiceAgainPlansTheSameSource() = runBlocking {
        val store = makeStore(emptyList())
        val steps = listOf(PlanStep.Practice(TaskPhase.RANDOM_PRACTICE, choice()))
        api.plan = Result.success(SessionPlan("s1", SessionMode.RANDOM_PRACTICE, steps))
        api.commitResults += Result.success(committed)
        store.startPractice(PracticeFilter.Topics(listOf("t1")))
        store.submitOption(3)
        store.advance()
        settled(store)
        assertTrue(store.stage is PracticeSessionStore.Stage.Finished)
        assertTrue(store.canPracticeAgain)
        val firstToken = store.sessionToken
        store.practiceAgain()
        assertEquals(PracticeSessionStore.Stage.Running, store.stage)
        assertNotEquals(firstToken, store.sessionToken)
        assertEquals(2, api.planRequests.size)
        assertEquals(api.planRequests.first(), api.planRequests.last())
    }

    @Test
    fun dailyHasNoPracticeAgain() = runBlocking {
        val store = makeStore(learnAndChoice)
        api.commitResults += Result.success(committed)
        runToCommit(store)
        assertFalse(store.canPracticeAgain)
        store.practiceAgain()
        assertEquals(PracticeSessionStore.Stage.Finished(summary), store.stage)
    }

    @Test
    fun followUpIsReadOnce() = runBlocking {
        val store = makeStore(learnAndChoice)
        store.start(SessionMode.DAILY)
        store.close(PracticeSessionStore.FollowUp.CHOOSE_TOPICS)
        assertEquals(PracticeSessionStore.FollowUp.CHOOSE_TOPICS, store.consumeFollowUp())
        assertNull(store.consumeFollowUp())
    }
}

class StubRecorder : VoiceRecording {
    override var canRecord = true
    var hasAccess = true
    override var onLimitReached: (() -> Unit)? = null
    override fun inputLevel() = 0.0
    override suspend fun requestAccess() = hasAccess
    override suspend fun start() {}
    override fun stop(): RecordedAudio? = RecordedAudio(byteArrayOf(1))
    override fun cancel() {}
}

class StubTranscriber : SpeechTranscribing {
    var hasAccess = true
    var candidates = listOf<String>()
    override suspend fun requestAccess() = hasAccess
    override suspend fun transcribe(audio: RecordedAudio) = candidates
}
