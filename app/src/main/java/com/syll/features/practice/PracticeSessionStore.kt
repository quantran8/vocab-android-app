package com.syll.features.practice

import com.syll.domain.practice.PracticeResult
import com.syll.domain.ranking.SessionEffort
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syll.R
import com.syll.app.UserMessage
import com.syll.domain.practice.AudioClip
import com.syll.domain.practice.CommitBody
import com.syll.domain.practice.CommitResponse
import com.syll.domain.practice.CommitSummary
import com.syll.domain.practice.KnownCheck
import com.syll.domain.practice.LearnCard
import com.syll.domain.practice.TaskTemplate
import com.syll.domain.report.ContentReportTarget
import com.syll.domain.report.canAppealAnswer
import com.syll.domain.report.reportExcerpt
import com.syll.domain.practice.NewWordsIssue
import com.syll.domain.practice.PlanRequest
import com.syll.domain.practice.PlanStep
import com.syll.domain.practice.PracticeTask
import com.syll.domain.practice.PracticeFilter
import com.syll.domain.practice.RecordedAudio
import com.syll.domain.practice.RecordingAssessment
import com.syll.domain.practice.RecordingScoring
import com.syll.domain.practice.ReviewSummary
import com.syll.domain.practice.ScopeEvents
import com.syll.domain.practice.SessionMode
import com.syll.domain.practice.SessionPlan
import com.syll.domain.practice.TaskAttempt
import com.syll.domain.practice.TaskBody
import com.syll.domain.practice.TaskPhase
import com.syll.domain.practice.TaskPrompt
import com.syll.domain.practice.TranscriptMatcher
import com.syll.network.ApiError
import com.syll.network.VocabApi
import com.syll.network.asApiError
import com.syll.platform.AudioClipPlaying
import com.syll.platform.FeedbackProviding
import com.syll.platform.NoSpeechTranscriber
import com.syll.platform.SilentSoundEffects
import com.syll.platform.SoundEffectsPlaying
import com.syll.platform.SpeechTranscribing
import com.syll.platform.VoiceRecording
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/** iOS `SessionPartProgress`: one part of a session, with its own progress. */
data class SessionPartProgress(val part: Part, val current: Int, val total: Int) {
    enum class Part { REVIEW, NEW_WORDS, PRACTICE }
}

/** iOS `ReviewWord`: one word of the review preview. */
data class ReviewWord(val id: String, val lemma: String, val topic: String? = null)

/**
 * iOS `PracticeSessionStore`: runs one planned session: renders steps in order, scores instantly with each task's
 * answer key, and commits the recorded events at the end. Nothing counts until the commit succeeds.
 */
class PracticeSessionStore(
    private val api: VocabApi,
    val audio: AudioClipPlaying,
    private val recorder: VoiceRecording,
    val haptics: FeedbackProviding,
    /** Where background work runs (the commit after the last step, a limit-stopped recording). Main thread. */
    private val scope: CoroutineScope,
    private val sounds: SoundEffectsPlaying = SilentSoundEffects,
    /**
     * Backend (Azure) by default. `ON_DEVICE` falls back to the backend when a task has no reference text or
     * recognition can't run. `ON_DEVICE_ONLY` is the free tier. Re-read from [scoringForSession] on every start.
     */
    var recordingScoring: RecordingScoring = RecordingScoring.BACKEND,
    private val transcriber: SpeechTranscribing = NoSpeechTranscriber,
    /** Monotonic milliseconds; events carry the time since their step appeared. */
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    sealed interface Stage {
        data object Idle : Stage
        data object Planning : Stage
        /** Nothing to learn or review right now. */
        data object Empty : Stage
        /** The words this session reviews, shown until the learner continues. */
        data class ReviewPreview(val words: List<ReviewWord>) : Stage
        /** Between a daily session's review and its new words. */
        data object ReviewFinished : Stage
        data object Running : Stage
        data object Committing : Stage
        data class Finished(val summary: CommitSummary) : Stage
        data class Failed(val failure: Failure) : Stage
    }

    sealed interface Failure {
        val message: UserMessage
        data class Planning(override val message: UserMessage) : Failure
        /** Unknown outcome or a passing outage: retrying resends the same bytes. */
        data class Commit(override val message: UserMessage) : Failure
        /** The session can't be saved any more; only a new plan helps. */
        data class SessionLost(override val message: UserMessage) : Failure
    }

    sealed interface RecordingStage {
        data object Idle : RecordingStage
        data object Recording : RecordingStage
        data object Uploading : RecordingStage
        data class Assessed(val assessment: RecordingAssessment) : RecordingStage
        /** Can retry the same audio; a persistent failure ends in `unavailable`. */
        data object UploadFailed : RecordingStage
        /** Can't record at all. The reason goes into the `unavailable` event. */
        data class Blocked(val reason: String) : RecordingStage
    }

    /** Where the shell goes after a session screen closes, when the learner picked a next step there. */
    enum class FollowUp {
        CHOOSE_TOPICS, RANDOM_PRACTICE,
        /** The free plan's new words ran out: open Premium (Phase 4). */
        UPGRADE,
    }

    object UnavailableReason {
        const val MIC_DENIED = "micPermissionDenied"
        const val RECORDER_FAILED = "recorderFailed"
        const val ASSESSMENT_FAILED = "assessmentFailed"
        const val SPEECH_UNAVAILABLE = "speechProviderUnavailable"
        /** Free tier: on-device recognition can't run (or the task has no text to check), and uploads need Premium. */
        const val ON_DEVICE_UNAVAILABLE = "onDeviceRecognitionUnavailable"
        const val SKIPPED = "userSkipped"
    }

    private var stageState by mutableStateOf<Stage>(Stage.Idle)
    var stage: Stage
        get() = stageState
        private set(value) {
            // An interstitial (review preview, review finished) is not time spent on the task.
            if (value == Stage.Running && stageState != Stage.Running) stepShownAt = now()
            stageState = value
        }

    var steps by mutableStateOf<List<PlanStep>>(emptyList())
        private set
    private var stepIndexState by mutableIntStateOf(0)
    var stepIndex: Int
        get() = stepIndexState
        private set(value) {
            stepIndexState = value
            stepShownAt = now()
        }

    /** When the current step appeared; events carry the time since. */
    private var stepShownAt: Long? = null
    var attempts by mutableStateOf<Map<String, TaskAttempt>>(emptyMap())
        private set
    var recording by mutableStateOf<RecordingStage>(RecordingStage.Idle)
        private set
    /** New for every session; keys the route so screens never carry over. */
    var sessionToken by mutableStateOf(UUID.randomUUID())
        private set
    var audioFailed by mutableStateOf(false)
        private set
    /** Practice Lab: tasks are scored locally and nothing is planned, assessed or committed. */
    var isSandbox by mutableStateOf(false)
        private set
    var onSandboxEnd: () -> Unit = {}
    /** The last daily plan's new-word figures: why it had fewer new words than the goal, if it did. */
    var newWords by mutableStateOf<NewWordsIssue?>(null)
        private set
    /** From the last commit; set only by the commit that used up a topic, the scope or the collection. */
    var scopeEvents by mutableStateOf<ScopeEvents?>(null)
        private set
    /** LP and Tier across this session screen (both commits of a daily chain). Null from a backend without ranking. */
    var effort by mutableStateOf<SessionEffort?>(null)
        private set
    /** Read and cleared by the shell once the session screen is gone. */
    var followUp: FollowUp? = null
        private set
    /** A daily chain's first commit (the review), kept for the results screen once the new words are saved. */
    var pendingReviewSummary by mutableStateOf<ReviewSummary?>(null)
        private set
    /** Set by the app: who scores recordings in the next session (the learner's plan can change between sessions). */
    var scoringForSession: (() -> RecordingScoring)? = null
    /** A session was saved and its results are on screen (not the Lab). The ads count these. */
    var onSessionFinished: () -> Unit = {}

    private var sessionId: String? = null
    private var lastRequest: Request? = null
    private var completedLearnIds = setOf<String>()
    private var knownChecks = mapOf<String, KnownCheck>()
    /** Known-check tasks the learner declined, and the tasks a passed check made unnecessary. Never committed. */
    private var skippedTaskIds = setOf<String>()
    /** Learn steps a passed known check skipped. */
    private var knownLearnIds by mutableStateOf(setOf<String>())

    private data class Request(
        val mode: SessionMode,
        val challengeId: String? = null,
        val filter: PracticeFilter? = null,
        val reviewOnly: Boolean = false,
    )

    /** Encoded once: a retried commit must be byte-identical or the backend reports a conflict. */
    private var commitBody: ByteArray? = null
    private var pendingAudio: RecordedAudio? = null
    private var latestReceiptId: String? = null
    /** Decided on a task's first take: its events can't mix receipts and transcripts. */
    private var canTranscribe: Boolean? = null
    /** Starting waits on permissions; a second tap in that gap must not start a second recorder. */
    private var isStartingRecording = false

    init {
        recorder.onLimitReached = { scope.launch { stopRecording() } }
    }

    val isActive: Boolean get() = stage != Stage.Idle
    val currentStep: PlanStep? get() = steps.getOrNull(stepIndex)

    val currentAttempt: TaskAttempt?
        get() = (currentStep as? PlanStep.Practice)?.let { attempts[it.task.id] }

    /** The check step is never answered: it shows its word's Learn card, offering "I know this word". */
    val knownCheckOffer: LearnCard?
        get() {
            val step = currentStep as? PlanStep.Practice ?: return null
            if (step.phase != TaskPhase.KNOWN_CHECK) return null
            val learnId = knownChecks[step.task.id]?.learnStepId ?: return null
            return steps.firstNotNullOfOrNull { (it as? PlanStep.Learn)?.takeIf { learn -> learn.id == learnId }?.card }
        }

    /** Words passed with "I know this" so far in this session. */
    val knownWordCount: Int get() = knownLearnIds.size

    /**
     * Where the learner is in the current part. Review always comes before new words in a plan, and new words start
     * counting from the beginning rather than continuing review's count.
     */
    val partProgress: SessionPartProgress
        get() {
            val reviewCount = steps.takeWhile { it.isDueReview }.size
            if (stepIndex < reviewCount) return SessionPartProgress(SessionPartProgress.Part.REVIEW, stepIndex + 1, reviewCount)
            val hasNewWords = reviewCount > 0 || steps.any { it is PlanStep.Learn }
            return SessionPartProgress(
                if (hasNewWords) SessionPartProgress.Part.NEW_WORDS else SessionPartProgress.Part.PRACTICE,
                stepIndex - reviewCount + 1,
                steps.size - reviewCount,
            )
        }

    /** The words the rest of the session introduces, in plan order. */
    val upcomingCards: List<LearnCard>
        get() = steps.drop(stepIndex).mapNotNull { (it as? PlanStep.Learn)?.card }

    val firstTryCount: Int get() = attempts.values.count { it.isRightFirstTime }

    val mode: SessionMode? get() = lastRequest?.mode

    /** iOS `result(for:)`: what the results screen shows for [summary], the summary in `Stage.Finished`. */
    fun result(summary: CommitSummary): PracticeResult = PracticeResult.of(lastRequest?.mode ?: SessionMode.DAILY, summary)
    val isWeakPractice: Boolean get() = lastRequest?.mode == SessionMode.WEAK_WORDS_PRACTICE
    val isSpeakingPractice: Boolean get() = lastRequest?.mode == SessionMode.SPEAKING_PRACTICE
    val isDaily: Boolean get() = lastRequest?.mode == SessionMode.DAILY
    val isReviewOnly: Boolean get() = lastRequest?.reviewOnly == true
    val practiceFilter: PracticeFilter? get() = lastRequest?.filter
    /** Random, Weak and Speaking end with "Practice again", which plans the same request anew. */
    val canPracticeAgain: Boolean
        get() = lastRequest?.mode in setOf(SessionMode.RANDOM_PRACTICE, SessionMode.WEAK_WORDS_PRACTICE, SessionMode.SPEAKING_PRACTICE)
    /** A denied microphone empties a speaking plan, and only Settings can fix that. */
    val canRecord: Boolean get() = recorder.canRecord

    // Lifecycle

    suspend fun start(mode: SessionMode, challengeId: String? = null) {
        if (stage != Stage.Idle) return
        begin(Request(mode, challengeId = challengeId))
    }

    /** A daily session of due reviews only: no new words and no Journey slot. */
    suspend fun startReviewOnly() {
        if (stage != Stage.Idle) return
        begin(Request(SessionMode.DAILY, reviewOnly = true))
    }

    /** Random practice from [filter] (null is every learned word). */
    suspend fun startPractice(filter: PracticeFilter? = null) {
        if (stage != Stage.Idle) return
        begin(Request(SessionMode.RANDOM_PRACTICE, filter = filter))
    }

    /** From the results of a Random, Weak or Speaking session: a new session with the same source. */
    suspend fun practiceAgain() {
        val request = lastRequest
        if (stage !is Stage.Finished || !canPracticeAgain || request == null) return
        close()
        begin(request)
    }

    private suspend fun begin(request: Request) {
        lastRequest = request
        pendingReviewSummary = null
        scoringForSession?.let { recordingScoring = it() }
        plan(request)
    }

    /** The shell reads this once the session screen is gone. */
    fun consumeFollowUp(): FollowUp? = followUp.also { followUp = null }

    suspend fun retry() {
        when (val stage = stage) {
            is Stage.Failed -> when (stage.failure) {
                is Failure.Planning -> lastRequest?.let { plan(it) }
                is Failure.Commit -> commit()
                is Failure.SessionLost -> Unit
            }
            else -> Unit
        }
    }

    fun continueToPractice() {
        if (stage is Stage.ReviewPreview || stage == Stage.ReviewFinished) stage = Stage.Running
    }

    /**
     * Practice Lab (iOS `startSandbox`): plays bundled tasks with the same views and rules as a session. Nothing is
     * planned, assessed or committed; the last step calls [onSandboxEnd].
     */
    fun startSandbox(tasks: List<PracticeTask>) {
        if (stage != Stage.Idle || tasks.isEmpty()) return
        isSandbox = true
        val plan = SessionPlan("sandbox", SessionMode.RANDOM_PRACTICE, tasks.map { PlanStep.Practice(TaskPhase.RANDOM_PRACTICE, it) })
        load(plan, "sandbox")
        stage = Stage.Running
        val steps = steps
        scope.launch { preloadAudio(steps) }
    }

    /** Nothing is written: words stay unlearned until a session commits. [followUp] is where the learner asked to go next. */
    fun close(followUp: FollowUp? = null) {
        audio.stop()
        recorder.cancel()
        steps = emptyList()
        attempts = emptyMap()
        stepIndex = 0
        completedLearnIds = emptySet()
        knownChecks = emptyMap()
        skippedTaskIds = emptySet()
        knownLearnIds = emptySet()
        this.followUp = followUp
        sessionId = null
        commitBody = null
        isSandbox = false
        // A chain left halfway has no results screen; its review must not show up under the next session.
        pendingReviewSummary = null
        resetStepState()
        stage = Stage.Idle
        sessionToken = UUID.randomUUID()
    }

    fun advance() {
        val step = currentStep
        if (stage != Stage.Running || step == null) return
        when (step) {
            is PlanStep.Learn -> completedLearnIds = completedLearnIds + step.id
            is PlanStep.Practice -> if (attempts[step.task.id]?.isFinished != true) return
        }
        moveForward(step)
    }

    /** "Continue" on the offered card: the word is learned from it, and the check is never answered. */
    fun declineKnownCheck() {
        val step = currentStep as? PlanStep.Practice ?: return
        if (stage != Stage.Running || step.phase != TaskPhase.KNOWN_CHECK) return
        val learnId = knownChecks[step.task.id]?.learnStepId ?: return
        skippedTaskIds = skippedTaskIds + step.task.id
        completedLearnIds = completedLearnIds + learnId
        moveForward(step)
    }

    /** "I know this word": no recall; the word goes out in `knownLearnSteps`, its card and own tasks are skipped. */
    fun markKnown() {
        val step = currentStep as? PlanStep.Practice ?: return
        if (stage != Stage.Running || step.phase != TaskPhase.KNOWN_CHECK) return
        val check = knownChecks[step.task.id] ?: return
        knownLearnIds = knownLearnIds + check.learnStepId
        skippedTaskIds = skippedTaskIds + step.task.id + check.skippableTaskIds
        moveForward(step)
    }

    private fun isSkipped(step: PlanStep): Boolean = when (step) {
        is PlanStep.Learn -> step.id in knownLearnIds || step.id in completedLearnIds
        is PlanStep.Practice -> step.task.id in skippedTaskIds
    }

    private fun moveForward(step: PlanStep) {
        audio.stop()
        resetStepState()
        val next = steps.indices.firstOrNull { it > stepIndex && !isSkipped(steps[it]) }
        when {
            next != null -> {
                stepIndex = next
                if (step.isDueReview && currentStep?.isDueReview == false) {
                    stage = Stage.ReviewFinished
                    haptics.success()
                }
            }
            isSandbox -> onSandboxEnd()
            else -> scope.launch { commit() }
        }
    }

    // Answers

    fun submitOption(id: Int) = score { it.submitOption(id) }
    fun submitText(text: String) = score { it.submitText(text) }
    fun submitTiles() = score { it.submitTiles() }
    fun submitRepair(edits: Map<Int, String>) = score { it.submitRepair(edits) }
    fun submitPair(leftId: Int, rightId: Int) = score { it.submitPair(leftId, rightId) }
    fun tapTile(id: Int) = update(click = true) { it.tapTile(id) }
    fun removeTile(id: Int) = update(click = true) { it.removeTile(id) }
    fun moveTile(id: Int, toIndex: Int) = update(click = true) { it.moveTile(id, toIndex) }
    fun useHint() = update { it.useHint() }
    fun reveal() = update { it.reveal() }
    fun tryAgain() {
        mutate { it.tryAgain() }
    }

    // Audio

    suspend fun playPromptAudio() {
        val attempt = currentAttempt ?: return
        val clip = (attempt.task.prompt as? TaskPrompt.Audio)?.clip ?: return
        val succeeded = audio.play(clip.url)
        attempts[attempt.task.id]?.let { stored ->
            attempts = attempts + (attempt.task.id to stored.copy().apply { recordAudioPlayback(succeeded) })
        }
        if (currentAttempt?.task?.id == attempt.task.id) audioFailed = !succeeded
    }

    fun playClip(clip: AudioClip) {
        scope.launch { audio.play(clip.url) }
    }

    // Recording

    suspend fun startRecording() {
        if (recording == RecordingStage.Recording || isStartingRecording || currentAttempt?.canRecordAgain != true) return
        isStartingRecording = true
        try {
            val taskId = currentAttempt?.task?.id
            audio.stop()
            if (!recorder.requestAccess()) {
                recording = RecordingStage.Blocked(UnavailableReason.MIC_DENIED)
                return
            }
            if (canTranscribe == null) canTranscribe = if (transcriptTarget != null) transcriber.requestAccess() else false
            if (mustScoreOnDevice && canTranscribe != true) {
                // Nothing could check the take; don't ask the learner to read for nothing.
                recording = RecordingStage.Blocked(UnavailableReason.ON_DEVICE_UNAVAILABLE)
                return
            }
            try {
                recorder.start()
                // The learner may have left the task meanwhile; don't leave the mic running behind them.
                if (currentAttempt?.task?.id != taskId) {
                    recorder.cancel()
                    return
                }
                recording = RecordingStage.Recording
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                recording = RecordingStage.Blocked(UnavailableReason.RECORDER_FAILED)
            }
        } finally {
            isStartingRecording = false
        }
    }

    fun inputLevel(): Double = if (recording == RecordingStage.Recording) recorder.inputLevel() else 0.0

    suspend fun stopRecording() {
        if (recording != RecordingStage.Recording) return
        val captured = recorder.stop()
        if (captured == null) {
            recording = RecordingStage.Blocked(UnavailableReason.RECORDER_FAILED)
            return
        }
        pendingAudio = captured
        val target = transcriptTarget
        when {
            canTranscribe == true && target != null -> assessOnDevice(target)
            mustScoreOnDevice -> recording = RecordingStage.Blocked(UnavailableReason.ON_DEVICE_UNAVAILABLE)
            else -> upload()
        }
    }

    /** The free tier outside the Lab: a take is checked on the device or not at all. */
    private val mustScoreOnDevice: Boolean get() = recordingScoring == RecordingScoring.ON_DEVICE_ONLY && !isSandbox

    /** A free learner's take: checked on the device, with no pronunciation score. */
    val isFreeSpeaking: Boolean get() = mustScoreOnDevice

    suspend fun retryUpload() = upload()

    /** Leaves a take's result for the mic. The take stays the one to commit until a new one replaces it. */
    fun retryRecording() {
        if (recording !is RecordingStage.Assessed || currentAttempt?.canRecordAgain != true) return
        recording = RecordingStage.Idle
    }

    /** Ends the task with the last assessment. An invalid one still counts as this task's attempt. */
    fun acceptRecording() {
        val receipt = latestReceiptId
        when {
            receipt != null -> mutate { it.finishRecording(receipt) }
            currentAttempt?.hasTranscripts == true -> mutate { it.finishTranscribed() }
            else -> {
                val reason = when (val stage = recording) {
                    is RecordingStage.Blocked -> stage.reason
                    RecordingStage.UploadFailed -> UnavailableReason.SPEECH_UNAVAILABLE
                    else -> UnavailableReason.SKIPPED
                }
                mutate { it.markRecordingUnavailable(reason) }
            }
        }
        advance()
    }

    /** The current take is checked by the device's transcript, not scored by the backend (no score to show). */
    val scoresOnDevice: Boolean get() = canTranscribe == true && transcriptTarget != null

    private val transcriptTarget: String?
        get() {
            if (recordingScoring == RecordingScoring.BACKEND) return null
            return (currentAttempt?.task?.body as? TaskBody.Recording)?.accepted?.firstOrNull()
        }

    private suspend fun assessOnDevice(target: String) {
        val audio = pendingAudio ?: return
        val taskId = currentAttempt?.task?.id ?: return
        recording = RecordingStage.Uploading
        val candidates = transcriber.transcribe(audio)
        if (currentAttempt?.task?.id != taskId) return
        val assessment = TranscriptMatcher.assess(candidates, target)
        mutate { it.recordTranscript(assessment.heard.orEmpty(), passed = assessment.passed == true) }
        recording = RecordingStage.Assessed(assessment)
        assessmentFeedback(assessment.passed == true)
    }

    /** Every way a take gets scored ends with the same cue as an answered task. */
    private fun assessmentFeedback(passed: Boolean) {
        if (passed) {
            haptics.success()
            sounds.playCorrect()
        } else {
            haptics.error()
            sounds.playWrong()
        }
    }

    private suspend fun upload() {
        val audio = pendingAudio ?: return
        val sessionId = sessionId ?: return
        val taskId = currentAttempt?.task?.id ?: return
        recording = RecordingStage.Uploading
        if (isSandbox) {
            // No speech scoring in the Lab; any take counts as a pass so the flow can be tried end to end.
            latestReceiptId = "sandbox"
            mutate { it.countAssessment(passed = true) }
            recording = RecordingStage.Assessed(RecordingAssessment("sandbox", "valid", passed = true))
            assessmentFeedback(true)
            return
        }
        try {
            val assessment = api.assessRecording(sessionId, taskId, audio)
            if (currentAttempt?.task?.id != taskId) return
            latestReceiptId = assessment.assessmentReceiptId
            mutate { it.countAssessment(passed = assessment.passed == true) }
            recording = RecordingStage.Assessed(assessment)
            assessmentFeedback(assessment.passed == true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (currentAttempt?.task?.id != taskId) return
            val failure = e.asApiError
            when {
                endsSession(failure) -> stage = Stage.Failed(Failure.SessionLost(sessionLostMessage))
                failure.code == "PREMIUM_REQUIRED" -> {
                    // Premium lapsed mid-session: score this and later takes on the device instead.
                    recordingScoring = RecordingScoring.ON_DEVICE_ONLY
                    // A task whose earlier take has a receipt keeps it: its events can't mix receipts and transcripts.
                    val onDeviceTarget = transcriptTarget
                    if (latestReceiptId == null && onDeviceTarget != null && transcriber.requestAccess() &&
                        currentAttempt?.task?.id == taskId
                    ) {
                        canTranscribe = true
                        assessOnDevice(onDeviceTarget)
                    } else {
                        recording = RecordingStage.Blocked(UnavailableReason.ON_DEVICE_UNAVAILABLE)
                    }
                }
                failure.isRetryable || failure.code == "PAYLOAD_TOO_LARGE" -> recording = RecordingStage.UploadFailed
                else -> recording = RecordingStage.Blocked(UnavailableReason.ASSESSMENT_FAILED)
            }
        }
    }

    // Private

    /** Every clip the plan can play, fetched before the session starts; waits at most [AUDIO_PRELOAD_LIMIT_MS]. */
    private suspend fun preloadAudio(steps: List<PlanStep>) {
        val urls = steps.mapNotNull { step ->
            when (step) {
                is PlanStep.Learn -> step.card.audio?.url
                is PlanStep.Practice -> (step.task.prompt as? TaskPrompt.Audio)?.clip?.url
            }
        }
        if (urls.isEmpty()) return
        // Clips still loading after the limit keep going in the background.
        val download = scope.launch { audio.preload(urls) }
        withTimeoutOrNull(AUDIO_PRELOAD_LIMIT_MS) { download.join() }
    }

    private suspend fun plan(request: Request) {
        stage = Stage.Planning
        scopeEvents = null
        effort = null
        try {
            val plan = api.planSession(planRequest(request))
            if (stage != Stage.Planning) return
            newWords = plan.newWords
            val id = plan.sessionId
            if (id == null || plan.steps.isEmpty()) {
                stage = Stage.Empty
                return
            }
            val reviewWords = coroutineScope {
                val preloaded = async { preloadAudio(plan.steps) }
                val words = loadReviewWords(plan)
                preloaded.await()
                words
            }
            if (stage != Stage.Planning) return
            load(plan, id)
            stage = if (reviewWords.isEmpty()) Stage.Running else Stage.ReviewPreview(reviewWords)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = e.asApiError
            if (stage != Stage.Planning) return
            // `checkpointChallenge` answers 404 when no challenge is open.
            stage = if (error.code == "NOT_FOUND") Stage.Empty else Stage.Failed(Failure.Planning(UserMessage.Api(error)))
        }
    }

    private val capabilities: PlanRequest.Capabilities
        get() = PlanRequest.Capabilities(audioPlayback = audio.canPlay, microphone = recorder.canRecord)

    private fun planRequest(request: Request): PlanRequest {
        val isDaily = request.mode == SessionMode.DAILY
        return PlanRequest(
            mode = request.mode,
            capabilities = capabilities,
            challengeId = request.challengeId,
            reviewOnly = if (isDaily && request.reviewOnly) true else null,
            knownCheck = if (isDaily && !request.reviewOnly) true else null,
            filter = request.filter,
        )
    }

    private fun load(plan: SessionPlan, id: String) {
        sessionId = id
        steps = plan.steps
        stepIndex = 0
        completedLearnIds = emptySet()
        knownChecks = plan.knownChecks
        skippedTaskIds = emptySet()
        knownLearnIds = emptySet()
        commitBody = null
        attempts = plan.steps.filterIsInstance<PlanStep.Practice>().associate { it.task.id to TaskAttempt(it.task, it.phase) }
        resetStepState()
    }

    /**
     * The preview is only informational. A current backend names the reviewed words with their topic; the library
     * guess iOS keeps for an older backend arrives with the words endpoint (step 7).
     */
    private fun loadReviewWords(plan: SessionPlan): List<ReviewWord> {
        if (plan.steps.none { it.isDueReview }) return emptyList()
        return plan.reviewWords.orEmpty().distinctBy { it.wordId }.map { ReviewWord(it.wordId, it.lemma, it.sourceTopic?.name) }
    }

    private suspend fun commit() {
        val sessionId = sessionId ?: return
        try {
            val body = commitBody ?: makeCommitBody().encoded()
            commitBody = body
            stage = Stage.Committing
            val response = api.commitSession(sessionId, body)
            if (stage != Stage.Committing) return
            record(response)
            finish(response.summary)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (stage != Stage.Committing) return
            handleCommitFailure(e.asApiError, sessionId)
        }
    }

    private fun makeCommitBody(): CommitBody {
        val results = steps.mapNotNull { step ->
            val task = (step as? PlanStep.Practice)?.task ?: return@mapNotNull null
            if (task.id in skippedTaskIds) null else attempts[task.id]?.result
        }
        val learnIds = steps.mapNotNull { (it as? PlanStep.Learn)?.id }
        val known = learnIds.filter { it in knownLearnIds }
        return CommitBody(
            learnStepsCompleted = learnIds.filter { it in completedLearnIds },
            knownLearnSteps = known.ifEmpty { null },
            taskResults = results,
        )
    }

    private suspend fun handleCommitFailure(error: ApiError, sessionId: String) {
        if (error.code == "SESSION_PAYLOAD_CONFLICT") {
            // Already committed with other data: the saved result is the one that stands.
            val saved = try {
                api.commitStatus(sessionId).response
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (saved != null) {
                record(saved)
                finish(saved.summary)
                return
            }
        }
        stage = Stage.Failed(
            when {
                error.isRetryable -> Failure.Commit(UserMessage.Api(error))
                endsSession(error) || error.code == "SESSION_PAYLOAD_CONFLICT" -> Failure.SessionLost(sessionLostMessage)
                else -> Failure.SessionLost(UserMessage.Api(error))
            },
        )
    }

    private fun record(response: CommitResponse) {
        scopeEvents = response.scopeEvents
        val lp = response.lp ?: return
        effort = (effort ?: SessionEffort()).adding(lp, response.tier)
    }

    private suspend fun finish(summary: CommitSummary) {
        if (continueToNewWords()) {
            pendingReviewSummary = summary.review
            return
        }
        // A daily chain: the review came with the first commit, the new words with this one.
        val merged = pendingReviewSummary?.let { summary.copy(review = it) } ?: summary
        pendingReviewSummary = null
        stage = Stage.Finished(merged)
        haptics.success()
        if (!isSandbox) onSessionFinished()
    }

    /**
     * A daily session goes straight from review to new words, even when the plan held only review. The review is
     * already saved; if this plan fails or is more review, the session just ends. Only when the plan held back
     * nothing: any `withheldReason` means asking again would loop on the same answer.
     */
    private suspend fun continueToNewWords(): Boolean {
        val request = lastRequest ?: return false
        if (request.mode != SessionMode.DAILY || request.reviewOnly || newWords?.withheldReason != null) return false
        if (!steps.all { it.isDueReview }) return false
        val plan = try {
            api.planSession(planRequest(request))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return false
        }
        val id = plan.sessionId ?: return false
        if (plan.steps.firstOrNull()?.isDueReview != false || stage != Stage.Committing) return false
        preloadAudio(plan.steps)
        if (stage != Stage.Committing) return false
        newWords = plan.newWords
        load(plan, id)
        stage = Stage.ReviewFinished
        haptics.success()
        return true
    }

    private fun resetStepState() {
        recording = RecordingStage.Idle
        pendingAudio = null
        latestReceiptId = null
        canTranscribe = null
        audioFailed = false
    }

    /** Applies [body] to a copy of the current attempt (so Compose sees the change) and stores it. */
    private fun <T> mutate(body: (TaskAttempt) -> T): T? {
        val id = currentAttempt?.task?.id ?: return null
        if (stage != Stage.Running) return null
        val copy = attempts[id]?.copy() ?: return null
        copy.elapsedMs = stepShownAt?.let { maxOf(now() - it, 0L).toInt() }
        val result = body(copy)
        attempts = attempts + (id to copy)
        return result
    }

    /** Feedback for picking an answer in a task view. Buttons outside the answer area stay silent. */
    fun answerPicked() {
        haptics.selectionChanged()
        sounds.playClick()
    }

    private fun update(click: Boolean = false, body: (TaskAttempt) -> Unit) {
        val before = currentAttempt?.events?.size
        mutate(body)
        if (currentAttempt?.events?.size == before) return
        if (click) answerPicked() else haptics.selectionChanged()
    }

    private fun score(body: (TaskAttempt) -> Boolean?) {
        when (mutate(body)) {
            true -> {
                haptics.success()
                sounds.playCorrect()
            }
            false -> {
                haptics.error()
                sounds.playWrong()
            }
            null -> Unit
        }
    }

    private val sessionLostMessage = UserMessage.Resource(R.string.this_session_is_no_longer_available_start_a_new)

    private companion object {
        /** The longest a session waits for its audio before showing the first step. */
        const val AUDIO_PRELOAD_LIMIT_MS = 6_000L

        fun endsSession(error: ApiError): Boolean =
            error.code in setOf("SESSION_STALE", "SESSION_EXPIRED", "SESSION_NOT_OWNED", "TASK_DATA_INVALID")
    }

    // Content reports (iOS PracticeSessionStore+ContentReport)

    /**
     * What the flag in the header reports right now; null where reporting is off (Practice Lab, checkpoint challenge).
     * Captured once when the sheet opens, so a later step never changes it.
     */
    val contentReportTarget: ContentReportTarget?
        get() {
            val sessionId = sessionId ?: return null
            if (isSandbox || lastRequest?.mode == SessionMode.CHECKPOINT_CHALLENGE) return null
            return when (val step = currentStep ?: return null) {
                is PlanStep.Learn -> learnReportTarget(sessionId, step.id, step.card)
                is PlanStep.Practice -> if (step.phase == TaskPhase.KNOWN_CHECK) {
                    // The check step shows its word's Learn card, so a report is about that card.
                    val learnId = knownChecks[step.task.id]?.learnStepId ?: return null
                    learnReportTarget(sessionId, learnId, knownCheckOffer ?: return null)
                } else {
                    val attempt = attempts[step.task.id]
                    val canAppeal = attempt?.canAppealAnswer ?: false
                    val isRecording = step.task.template == TaskTemplate.RECORDING
                    ContentReportTarget(
                        context = ContentReportTarget.Context.PracticeTask(sessionId, step.task.id, if (canAppeal) attempt?.reportExcerpt else null),
                        subtitle = step.task.question,
                        reasons = ContentReportTarget.taskReasons(canAppeal, hasAudio = isRecording || step.task.prompt?.isAudio == true),
                        isRecording = isRecording,
                    )
                }
            }
        }

    /** The flag is shown but off while recording or uploading: the sheet must never open over a live mic. */
    val canOpenContentReport: Boolean
        get() = recording != RecordingStage.Recording && recording != RecordingStage.Uploading

    private fun learnReportTarget(sessionId: String, learnStepId: String, card: LearnCard) = ContentReportTarget(
        context = ContentReportTarget.Context.LearnCard(sessionId, learnStepId),
        subtitle = card.word,
        reasons = ContentReportTarget.meaningReasons(
            hasDefinition = !card.definition.isNullOrEmpty(),
            hasExample = !card.examples.isNullOrEmpty(),
            hasAudio = card.audio != null,
        ),
    )
}
