package com.syll.features.challenge

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syll.R
import com.syll.app.UserMessage
import com.syll.data.KeyValueStore
import com.syll.domain.challenge.ChallengeCommitResponse
import com.syll.domain.challenge.ChallengeEndedReason
import com.syll.domain.challenge.ChallengeErrorCode
import com.syll.domain.challenge.ChallengeLevel
import com.syll.domain.challenge.ChallengeNodeRef
import com.syll.domain.challenge.ChallengePairOccurrence
import com.syll.domain.challenge.ChallengeRunPlan
import com.syll.domain.challenge.ChallengeRunResult
import com.syll.domain.challenge.ChallengeRunStatus
import com.syll.domain.challenge.ChallengeUnlock
import com.syll.domain.challenge.MatchMadnessAction
import com.syll.domain.challenge.MatchMadnessEngine
import com.syll.domain.challenge.MatchMadnessFeedback
import com.syll.domain.challenge.MatchMadnessState
import com.syll.network.ApiError
import com.syll.network.VocabApi
import com.syll.network.asApiError
import com.syll.platform.FeedbackProviding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/** iOS `ChallengeRunOutcome`: what the results screen shows after the server committed a run. */
data class ChallengeRunOutcome(
    val node: ChallengeNodeRef,
    val response: ChallengeCommitResponse,
    /** The best before this run, from the map the run was started from (0 for a first play). */
    val previousBestStars: Int,
    val previousBestScore: Int,
) {
    /** The next chapter is short of words. */
    data class WordsNeeded(val chapterNo: Int, val eligible: Int, val required: Int) {
        val remaining: Int get() = maxOf(required - eligible, 0)
    }

    val result: ChallengeRunResult get() = response.result
    val isCleared: Boolean get() = result.outcome == ChallengeRunResult.Outcome.CLEARED
    private val unlockKind: ChallengeUnlock.Kind get() = response.unlock?.kind ?: ChallengeUnlock.Kind.NONE

    /** The server only reports an unlock on a level's first clear. */
    val isFirstClear: Boolean
        get() = isCleared && (
            previousBestStars == 0 ||
                unlockKind in setOf(ChallengeUnlock.Kind.LEVEL, ChallengeUnlock.Kind.CHAPTER_READY, ChallengeUnlock.Kind.NEEDS_VOCABULARY)
            )

    /** More stars, or a higher score than before. Display only; both numbers come from the server. */
    val isNewBest: Boolean
        get() = isCleared && !isFirstClear && (result.bestStars > previousBestStars || result.bestScore > previousBestScore)

    val chapterCleared: Boolean
        get() = unlockKind == ChallengeUnlock.Kind.CHAPTER_READY || unlockKind == ChallengeUnlock.Kind.NEEDS_VOCABULARY

    /** The level to offer next, as the server decided it. */
    val nextNode: ChallengeNodeRef?
        get() {
            val unlock = response.unlock ?: return null
            val chapterNo = unlock.chapterNo ?: return null
            return when (unlock.kind) {
                ChallengeUnlock.Kind.LEVEL -> unlock.levelNo?.let { ChallengeNodeRef(chapterNo, it) }
                ChallengeUnlock.Kind.CHAPTER_READY -> ChallengeNodeRef(chapterNo, 1)
                else -> null
            }
        }

    val wordsNeeded: WordsNeeded?
        get() {
            val unlock = response.unlock ?: return null
            if (unlock.kind != ChallengeUnlock.Kind.NEEDS_VOCABULARY) return null
            val chapterNo = unlock.chapterNo ?: return null
            return WordsNeeded(chapterNo, unlock.eligibleNewCount ?: 0, unlock.requiredNewCount ?: 0)
        }
}

/** iOS `ChallengeSandboxSummary`: a Practice Lab round, counted on the device, never sent, never scored. */
data class ChallengeSandboxSummary(
    val correctMatches: Int,
    val goal: Int,
    val mistakes: Int,
    val longestCombo: Int,
    val elapsedMs: Int,
    val isTimed: Boolean,
    val reachedGoal: Boolean,
)

/**
 * iOS `ChallengeRunStore`: runs one Match Madness run: issue → ready → start → playing → commit → results.
 * UI orchestration only: the rules live in [MatchMadnessEngine]. Nothing here touches Daily, SRS or learning sessions.
 */
class ChallengeRunStore(
    private val api: VocabApi,
    private val haptics: FeedbackProviding,
    /** Where the commit and timers run; outlives the game screen. Main thread. */
    private val scope: CoroutineScope,
    /** Monotonic milliseconds. */
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
    /** Null turns the display timer off (tests call [tick]). */
    private val tickIntervalMs: Long? = 100,
    private val storage: KeyValueStore? = null,
) {
    sealed interface Failure {
        data class Issue(val message: UserMessage) : Failure
        data class Start(val message: UserMessage) : Failure
        data object NotEnoughVocabulary : Failure
        data class ContentUnavailable(val message: UserMessage) : Failure
        data object Expired : Failure
        /** The server rejected the events. A contract bug: never shown as a win. */
        data class InvalidEvents(val message: UserMessage) : Failure
        /** The level or chapter isn't open (any more): back to the map. */
        data object LevelLocked : Failure
    }

    sealed interface Stage {
        data object Idle : Stage
        data object Issuing : Stage
        /** Plan frozen, clock stopped. */
        data object Ready : Stage
        data object Starting : Stage
        data object Playing : Stage
        data object Committing : Stage
        data class Results(val outcome: ChallengeRunOutcome) : Stage
        data class SandboxResults(val summary: ChallengeSandboxSummary) : Stage
        /** The final body is frozen; retrying sends the same bytes. */
        data class CommitFailed(val message: UserMessage) : Stage
        /** Another started run is open (maybe on another device). Leaving it ends it with 0 stars. */
        data class RunActive(val runId: String?) : Stage
        data class Failed(val failure: Failure) : Stage
    }

    /** Persisted while a run is open, so a killed app can settle it on the next launch. */
    @Serializable
    data class PendingRun(
        val runId: String,
        val node: ChallengeNodeRef,
        val started: Boolean = false,
        /** The frozen commit body (UTF-8 JSON), once the run ended. */
        val body: String? = null,
    )

    /** The last action's feedback, with a counter so a repeat still changes. */
    data class FeedbackEvent(val value: MatchMadnessFeedback, val count: Int)

    var stage by mutableStateOf<Stage>(Stage.Idle)
        private set
    var plan by mutableStateOf<ChallengeRunPlan?>(null)
        private set
    var game by mutableStateOf<MatchMadnessState?>(null)
        private set
    /** Updated about 10 times a second while playing. */
    var elapsedMs by mutableIntStateOf(0)
        private set
    var isSandbox by mutableStateOf(false)
        private set
    /** False only for the untimed Practice Lab variant (accessibility / familiarization). */
    var isTimed by mutableStateOf(true)
        private set
    /** New for every run, so no view state leaks from one attempt into the next. */
    var runToken by mutableStateOf(UUID.randomUUID().toString())
        private set
    /** Tiles flashing as a wrong pair; cleared after [WRONG_FLASH_MS]. */
    var wrongTileIds by mutableStateOf<Set<String>>(emptySet())
        private set
    /** Per tile, bumped on every wrong attempt it was part of: drives the shake. */
    var shakeCounts by mutableStateOf<Map<String, Int>>(emptyMap())
        private set
    var feedback by mutableStateOf(FeedbackEvent(MatchMadnessFeedback.None, 0))
        private set
    /** A short message when [begin] failed and the run can still be started. */
    var startError by mutableStateOf<UserMessage?>(null)
        private set

    /** Runs after a run settles on the server (committed, quit or expired): refresh the map. */
    var onSettled: (suspend () -> Unit)? = null

    private var engine: MatchMadnessEngine? = null
    private var startedAt: Long? = null
    private var ticker: Job? = null
    private var issuedNode: ChallengeNodeRef? = null
    private var issuedLevel: ChallengeLevel? = null
    private var pending: PendingRun? = storage?.getString(PENDING_KEY)?.let { runCatching { json.decodeFromString(PendingRun.serializer(), it) }.getOrNull() }
    /** Set when the run being settled was quit: it closes instead of showing results. */
    private var endedReason: ChallengeEndedReason? = null
    /** One automatic retry each per run: re-issuing over a stale issued run, re-starting before a commit. */
    private var retriedIssue = false
    private var retriedStart = false
    /** The commit in flight after a run ended. Tests join it. */
    var settleTask: Job? = null
        private set

    val isActive: Boolean get() = stage != Stage.Idle
    val engineGoal: Int get() = engine?.goal ?: plan?.goalMatches ?: 0
    val remainingMs: Int get() = maxOf((plan?.timeLimitMs ?: 0) - elapsedMs, 0)
    val hasSeenTutorial: Boolean get() = storage?.getBoolean(TUTORIAL_KEY) ?: true
    val hasPendingRun: Boolean get() = pending != null

    fun pairForWordTile(id: String): ChallengePairOccurrence? = engine?.wordPair(id)
    fun pairForMeaningTile(id: String): ChallengePairOccurrence? = engine?.meaningPair(id)

    // region Issue and start

    /** Issues a run for [node]. [level] is the map's copy, for the "new best" comparison and the title. */
    suspend fun play(node: ChallengeNodeRef, level: ChallengeLevel?) {
        if (!canIssue) return
        resetRunState()
        isSandbox = false
        isTimed = true
        issuedNode = node
        issuedLevel = level
        stage = Stage.Issuing
        val token = runToken
        // A run left open by a killed app (or an offline quit) would block this one.
        if (pending != null) settlePendingRun()
        if (runToken != token || stage != Stage.Issuing) return
        try {
            val plan = api.issueChallengeRun(node.chapterNo, node.levelNo)
            if (runToken != token || stage != Stage.Issuing) {
                // Closed while issuing: don't leave an orphan behind.
                runCatching { api.quitChallengeRun(plan.runId) }
                return
            }
            load(plan)
            savePending(PendingRun(plan.runId, node))
            stage = Stage.Ready
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (runToken != token || stage != Stage.Issuing) return
            handleIssueError(e.asApiError)
        }
    }

    /** Plays a local plan with the same engine: no API, no commit, no stars. */
    fun startSandbox(plan: ChallengeRunPlan, timed: Boolean) {
        resetRunState()
        isSandbox = true
        isTimed = timed
        load(plan)
        stage = Stage.Ready
    }

    /** The clock starts only after the server acknowledged the start. */
    suspend fun begin() {
        val plan = plan
        if (stage != Stage.Ready || plan == null) return
        startError = null
        markTutorialSeen()
        if (!isSandbox) {
            stage = Stage.Starting
            val token = runToken
            try {
                api.startChallengeRun(plan.runId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (runToken != token || stage != Stage.Starting) return
                val error = e.asApiError
                if (error.code == ChallengeErrorCode.RUN_EXPIRED) {
                    clearPending()
                    stage = Stage.Failed(Failure.Expired)
                    onSettled?.invoke()
                } else {
                    startError = UserMessage.Api(error)
                    stage = Stage.Ready
                }
                return
            }
            if (runToken != token || stage != Stage.Starting) return
            savePending(pending?.copy(started = true))
        }
        startedAt = now()
        elapsedMs = 0
        stage = Stage.Playing
        startTicker()
    }

    // endregion

    // region Playing

    fun tapWord(tileId: String) = apply { MatchMadnessAction.TapWord(tileId, it) }

    fun tapMeaning(tileId: String) = apply { MatchMadnessAction.TapMeaning(tileId, it) }

    /**
     * Re-reads the clock. Runs on the display timer and when the app returns to the foreground: the run never
     * pauses, so a run whose time ran out in the background ends as `time_up`.
     */
    fun tick() {
        if (stage != Stage.Playing) return
        elapsedMs = currentElapsedMs()
        val plan = plan ?: return
        if (isTimed && elapsedMs >= plan.timeLimitMs) end(ChallengeEndedReason.TIME_UP, plan.timeLimitMs)
    }

    /** "Exit challenge?" confirmed while playing: commits the run as `quit` (no win) and closes. */
    suspend fun quit() {
        when (stage) {
            Stage.Playing -> {
                val elapsed = currentElapsedMs()
                val plan = plan
                if (isTimed && plan != null && elapsed >= plan.timeLimitMs) {
                    // The server counts a quit at the deadline as time up.
                    end(ChallengeEndedReason.TIME_UP, plan.timeLimitMs)
                } else {
                    end(ChallengeEndedReason.QUIT, elapsed)
                }
                settleTask?.join()
            }
            Stage.Ready, Stage.Starting, Stage.Issuing -> leave()
            else -> close()
        }
    }

    private fun apply(action: (Int) -> MatchMadnessAction) {
        val engine = engine
        val game = game
        val plan = plan
        if (stage != Stage.Playing || engine == null || game == null || plan == null) return
        val elapsed = currentElapsedMs()
        if (isTimed && elapsed >= plan.timeLimitMs) {
            tick()
            return
        }
        elapsedMs = elapsed
        val step = engine.reduce(game, action(elapsed))
        this.game = step.state
        feedback = FeedbackEvent(step.feedback, feedback.count + 1)
        when (val result = step.feedback) {
            MatchMadnessFeedback.None -> Unit
            MatchMadnessFeedback.Selected, MatchMadnessFeedback.Deselected -> haptics.selectionChanged()
            is MatchMadnessFeedback.Correct -> haptics.success()
            is MatchMadnessFeedback.Wrong -> {
                haptics.error()
                flashWrong(setOf(result.wordTileId, result.meaningTileId))
            }
            MatchMadnessFeedback.GoalReached -> {
                haptics.success()
                end(ChallengeEndedReason.GOAL_REACHED, elapsed)
            }
        }
    }

    private fun flashWrong(tiles: Set<String>) {
        wrongTileIds = wrongTileIds + tiles
        shakeCounts = shakeCounts.toMutableMap().apply { tiles.forEach { this[it] = (this[it] ?: 0) + 1 } }
        val token = runToken
        scope.launch {
            delay(WRONG_FLASH_MS)
            if (runToken == token) wrongTileIds = wrongTileIds - tiles
        }
    }

    // endregion

    // region Ending and commit

    /** Stops the clock and freezes the commit body. The commit itself runs in [settleTask]. */
    private fun end(reason: ChallengeEndedReason, endedElapsedMs: Int) {
        val game = game
        if (stage != Stage.Playing || game == null) return
        stopTicker()
        endedReason = reason
        elapsedMs = endedElapsedMs

        if (isSandbox) {
            if (reason == ChallengeEndedReason.QUIT) {
                close()
                return
            }
            stage = Stage.SandboxResults(
                ChallengeSandboxSummary(
                    correctMatches = game.correctCount,
                    goal = engineGoal,
                    mistakes = game.wrongCount,
                    longestCombo = game.longestCombo,
                    elapsedMs = endedElapsedMs,
                    isTimed = isTimed,
                    reachedGoal = reason == ChallengeEndedReason.GOAL_REACHED,
                ),
            )
            return
        }

        val body = MatchMadnessEngine.commitBody(game, reason, endedElapsedMs).encoded().decodeToString()
        savePending(pending?.copy(body = body))
        stage = Stage.Committing
        settleTask = scope.launch { commit() }
    }

    /** Sends the frozen body again, byte for byte. */
    suspend fun retryCommit() {
        if (stage !is Stage.CommitFailed) return
        stage = Stage.Committing
        val task = scope.launch { commit() }
        settleTask = task
        task.join()
    }

    private suspend fun commit() {
        val pending = pending ?: return
        val body = pending.body ?: return
        try {
            val response = api.commitChallengeRun(pending.runId, body.encodeToByteArray())
            settle(response)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = e.asApiError
            val code = error.code
            when {
                code == ChallengeErrorCode.COMMIT_CONFLICT -> recoverFromStatus()
                code == ChallengeErrorCode.RUN_NOT_STARTED && !retriedStart -> {
                    // The start answer was lost: start again (idempotent), then send the same body once more.
                    retriedStart = true
                    val restarted = runCatching { api.startChallengeRun(pending.runId) }.isSuccess
                    if (restarted) commit() else stage = Stage.CommitFailed(UserMessage.Api(error))
                }
                code == ChallengeErrorCode.RUN_NOT_FOUND || code == ChallengeErrorCode.RUN_EXPIRED -> {
                    clearPending()
                    if (endedReason == ChallengeEndedReason.QUIT) close() else stage = Stage.Failed(Failure.Expired)
                    onSettled?.invoke()
                }
                code == ChallengeErrorCode.INVALID_EVENTS -> {
                    clearPending()
                    stage = Stage.Failed(Failure.InvalidEvents(UserMessage.Api(error)))
                    onSettled?.invoke()
                }
                else -> if (endedReason == ChallengeEndedReason.QUIT) {
                    // Kept on disk: the next launch settles it. Quitting never waits on the network.
                    close()
                } else {
                    stage = Stage.CommitFailed(UserMessage.Api(error))
                }
            }
        }
    }

    /** After a conflict or an ambiguous failure: the server's stored answer wins. */
    private suspend fun recoverFromStatus() {
        val pending = pending ?: return
        try {
            val status = api.challengeRunStatus(pending.runId)
            when (status.status) {
                ChallengeRunStatus.Status.COMMITTED -> {
                    val response = status.response
                    if (response != null) {
                        settle(response)
                    } else {
                        clearPending()
                        close()
                        onSettled?.invoke()
                    }
                }
                ChallengeRunStatus.Status.EXPIRED, ChallengeRunStatus.Status.ABANDONED -> {
                    clearPending()
                    if (endedReason == ChallengeEndedReason.QUIT) close() else stage = Stage.Failed(Failure.Expired)
                    onSettled?.invoke()
                }
                else -> stage = Stage.CommitFailed(UserMessage.Resource(R.string.your_result_isn_t_saved_yet_2))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            stage = Stage.CommitFailed(UserMessage.Api(e.asApiError))
        }
    }

    private suspend fun settle(response: ChallengeCommitResponse) {
        val node = pending?.node ?: issuedNode ?: ChallengeNodeRef(plan?.chapterNo ?: 1, plan?.levelNo ?: 1)
        clearPending()
        if (endedReason == ChallengeEndedReason.QUIT) {
            close()
        } else {
            stage = Stage.Results(
                ChallengeRunOutcome(
                    node = node,
                    response = response,
                    previousBestStars = issuedLevel?.bestStars ?: 0,
                    previousBestScore = issuedLevel?.bestScore ?: 0,
                ),
            )
        }
        onSettled?.invoke()
    }

    // endregion

    // region Leaving

    /** Leaves before the clock started: the issued run is cancelled on the server. */
    suspend fun leave() {
        val runId = pending?.takeIf { !it.started }?.runId
        if (!isSandbox && runId != null) {
            clearPending()
            close()
            runCatching { api.quitChallengeRun(runId) }
            onSettled?.invoke()
        } else {
            close()
        }
    }

    /** From [Stage.RunActive]: ends the other run, then issues the one that was asked for. */
    suspend fun leaveActiveRunAndRetry() {
        val active = stage as? Stage.RunActive ?: return
        val node = issuedNode ?: return
        val level = issuedLevel
        active.runId?.let { runId ->
            runCatching { api.quitChallengeRun(runId) }
            clearPending()
        }
        stage = Stage.Idle
        play(node, level)
    }

    /** Back to where the run was started from. */
    fun close() {
        stopTicker()
        resetRunState()
        isSandbox = false
        isTimed = true
        stage = Stage.Idle
    }

    /** Sign-out: forget the run, including what's on disk (the server's TTL settles it). */
    fun reset() {
        close()
        clearPending()
    }

    // endregion

    // region Recovery

    /** Settles a run the app was killed in. Silent: the map refreshes afterwards. Never grants anything locally. */
    suspend fun recoverPendingRun() {
        if (stage != Stage.Idle) return
        settlePendingRun()
    }

    private suspend fun settlePendingRun() {
        val pending = pending ?: return
        try {
            val status = api.challengeRunStatus(pending.runId)
            when (status.status) {
                ChallengeRunStatus.Status.COMMITTED, ChallengeRunStatus.Status.EXPIRED, ChallengeRunStatus.Status.ABANDONED -> clearPending()
                ChallengeRunStatus.Status.ISSUED -> {
                    runCatching { api.quitChallengeRun(pending.runId) }
                    clearPending()
                }
                ChallengeRunStatus.Status.STARTED -> {
                    val body = pending.body
                    val committed = body != null && runCatching { api.commitChallengeRun(pending.runId, body.encodeToByteArray()) }.isSuccess
                    if (!committed) {
                        // The events weren't saved, so the run can't be resumed reliably: settle it with 0 stars.
                        runCatching { api.quitChallengeRun(pending.runId) }
                    }
                    clearPending()
                }
                ChallengeRunStatus.Status.UNKNOWN -> return
            }
            onSettled?.invoke()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e.asApiError.status == 404) clearPending()
        }
    }

    // endregion

    // region Helpers

    private val canIssue: Boolean
        get() = when (stage) {
            Stage.Idle, is Stage.Results, is Stage.SandboxResults, is Stage.Failed, is Stage.RunActive -> true
            else -> false
        }

    private suspend fun handleIssueError(error: ApiError) {
        when (error.code) {
            ChallengeErrorCode.RUN_ACTIVE -> handleRunActive(error.detail("runId") ?: pending?.runId)
            ChallengeErrorCode.LEVEL_LOCKED -> {
                stage = Stage.Failed(Failure.LevelLocked)
                onSettled?.invoke()
            }
            ChallengeErrorCode.NOT_ENOUGH_VOCABULARY -> {
                stage = Stage.Failed(Failure.NotEnoughVocabulary)
                onSettled?.invoke()
            }
            ChallengeErrorCode.CONTENT_UNAVAILABLE -> stage = Stage.Failed(Failure.ContentUnavailable(UserMessage.Api(error)))
            else -> stage = Stage.Failed(Failure.Issue(UserMessage.Api(error)))
        }
    }

    /**
     * An issued run holds nothing played, so it is dropped and this level issued again. A started one may be in play
     * elsewhere: the learner decides.
     */
    private suspend fun handleRunActive(runId: String?) {
        if (runId == null) {
            stage = Stage.RunActive(null)
            return
        }
        val status = runCatching { api.challengeRunStatus(runId) }.getOrNull()
        if (stage != Stage.Issuing) return
        val node = issuedNode
        if (status?.status == ChallengeRunStatus.Status.ISSUED && !retriedIssue && node != null) {
            retriedIssue = true
            runCatching { api.quitChallengeRun(runId) }
            if (pending?.runId == runId) clearPending()
            try {
                val plan = api.issueChallengeRun(node.chapterNo, node.levelNo)
                if (stage != Stage.Issuing) return
                load(plan)
                savePending(PendingRun(plan.runId, node))
                stage = Stage.Ready
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (stage != Stage.Issuing) return
                handleIssueError(e.asApiError)
            }
            return
        }
        stage = Stage.RunActive(runId)
    }

    private fun load(plan: ChallengeRunPlan) {
        val engine = MatchMadnessEngine(plan)
        this.plan = plan
        this.engine = engine
        game = engine.initialState()
    }

    private fun resetRunState() {
        stopTicker()
        plan = null
        engine = null
        game = null
        startedAt = null
        elapsedMs = 0
        wrongTileIds = emptySet()
        shakeCounts = emptyMap()
        feedback = FeedbackEvent(MatchMadnessFeedback.None, 0)
        startError = null
        endedReason = null
        issuedLevel = null
        retriedIssue = false
        retriedStart = false
        runToken = UUID.randomUUID().toString()
    }

    private fun currentElapsedMs(): Int {
        val startedAt = startedAt ?: return 0
        return maxOf(now() - startedAt, 0L).toInt()
    }

    private fun startTicker() {
        stopTicker()
        val interval = tickIntervalMs ?: return
        ticker = scope.launch {
            while (isActive) {
                delay(interval)
                tick()
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private fun markTutorialSeen() {
        storage?.putBoolean(TUTORIAL_KEY, true)
    }

    private fun savePending(run: PendingRun?) {
        pending = run
        val storage = storage ?: return
        if (run != null) storage.putString(PENDING_KEY, json.encodeToString(PendingRun.serializer(), run)) else storage.remove(PENDING_KEY)
    }

    private fun clearPending() = savePending(null)

    // endregion

    companion object {
        const val WRONG_FLASH_MS = 180L
        private const val PENDING_KEY = "challenge.pendingRun"
        private const val TUTORIAL_KEY = "challenge.tutorialSeen"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
