package com.syll.domain.challenge

/** iOS `MatchMadnessState`: Match Madness game state. Pure value: no clock, network or animation. */
data class MatchMadnessState(
    /** Pair IDs on the board, in the order they came on. */
    val activePairIds: List<String>,
    /** Pair IDs still queued, in queue order. A pair that can't come on yet keeps its place. */
    val pendingPairIds: List<String>,
    /** Per meaning (`word`), the `correctCount` right after it was last matched. */
    val matchedAt: Map<String, Int> = emptyMap(),
    /** Word tile IDs, top to bottom. */
    val wordOrder: List<String> = emptyList(),
    /** Meaning tile IDs, top to bottom; ordered independently of [wordOrder]. */
    val meaningOrder: List<String> = emptyList(),
    val selectedWordTileId: String? = null,
    val selectedMeaningTileId: String? = null,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val combo: Int = 0,
    val longestCombo: Int = 0,
    /** Display only (show `max(0, …)`); null when the plan has no score rule. The server's score is the one that counts. */
    val provisionalScore: Int? = null,
    /** Every complete attempt, in order: the commit body. */
    val events: List<MatchAttemptEvent> = emptyList(),
    val phase: Phase = Phase.PLAYING,
) {
    enum class Phase {
        PLAYING,
        /** `goalMatches` correct pairs: the store stops the clock and commits. */
        GOAL_REACHED,
    }
}

sealed interface MatchMadnessAction {
    data class TapWord(val tileId: String, val elapsedMs: Int) : MatchMadnessAction
    data class TapMeaning(val tileId: String, val elapsedMs: Int) : MatchMadnessAction
}

/** What the view should play for an action. Presentation only. */
sealed interface MatchMadnessFeedback {
    data object None : MatchMadnessFeedback
    data object Selected : MatchMadnessFeedback
    data object Deselected : MatchMadnessFeedback
    data class Correct(val pairId: String, val combo: Int) : MatchMadnessFeedback
    data class Wrong(val wordTileId: String, val meaningTileId: String) : MatchMadnessFeedback
    data object GoalReached : MatchMadnessFeedback
}

/**
 * iOS `MatchMadnessEngine`: runs one frozen plan with exactly the server's replay rules (`Board` in backend
 * `src/modules/challenge/domain/match-madness.ts`, §12.3 of `FRONTEND_FLOW.md`); any drift there is a 422 on commit.
 * The same engine plays a live run, a sandbox plan and the tests.
 */
class MatchMadnessEngine(val plan: ChallengeRunPlan) {
    data class Step(val state: MatchMadnessState, val feedback: MatchMadnessFeedback)

    // First occurrence wins on a duplicate key, like iOS `uniquingKeysWith: { first, _ in first }`.
    private val pairsById = LinkedHashMap<String, ChallengePairOccurrence>().apply { plan.pairs.forEach { putIfAbsent(it.pairId, it) } }
    private val pairIdByWordTile = HashMap<String, String>().apply { plan.pairs.forEach { putIfAbsent(it.wordTileId, it.pairId) } }
    private val pairIdByMeaningTile = HashMap<String, String>().apply { plan.pairs.forEach { putIfAbsent(it.meaningTileId, it.pairId) } }

    val goal: Int get() = plan.goalMatches

    fun pair(id: String): ChallengePairOccurrence? = pairsById[id]
    fun wordPair(tileId: String): ChallengePairOccurrence? = pairIdByWordTile[tileId]?.let(pairsById::get)
    fun meaningPair(tileId: String): ChallengePairOccurrence? = pairIdByMeaningTile[tileId]?.let(pairsById::get)

    fun initialState(): MatchMadnessState {
        val state = MatchMadnessState(
            activePairIds = emptyList(),
            pendingPairIds = plan.pairs.map { it.pairId },
            provisionalScore = if (plan.score == null) null else 0,
        )
        return reorder(fill(state))
    }

    fun reduce(state: MatchMadnessState, action: MatchMadnessAction): Step {
        if (state.phase != MatchMadnessState.Phase.PLAYING) return Step(state, MatchMadnessFeedback.None)
        val next: MatchMadnessState
        val elapsedMs: Int
        when (action) {
            is MatchMadnessAction.TapWord -> {
                if (action.tileId !in state.wordOrder) return Step(state, MatchMadnessFeedback.None)
                if (state.selectedWordTileId == action.tileId) {
                    return Step(state.copy(selectedWordTileId = null), MatchMadnessFeedback.Deselected)
                }
                next = state.copy(selectedWordTileId = action.tileId)
                elapsedMs = action.elapsedMs
            }
            is MatchMadnessAction.TapMeaning -> {
                if (action.tileId !in state.meaningOrder) return Step(state, MatchMadnessFeedback.None)
                if (state.selectedMeaningTileId == action.tileId) {
                    return Step(state.copy(selectedMeaningTileId = null), MatchMadnessFeedback.Deselected)
                }
                next = state.copy(selectedMeaningTileId = action.tileId)
                elapsedMs = action.elapsedMs
            }
        }
        val wordTileId = next.selectedWordTileId
        val meaningTileId = next.selectedMeaningTileId
        if (wordTileId == null || meaningTileId == null) return Step(next, MatchMadnessFeedback.Selected)
        return attempt(next, wordTileId, meaningTileId, elapsedMs)
    }

    private fun attempt(state: MatchMadnessState, wordTileId: String, meaningTileId: String, elapsedMs: Int): Step {
        val event = MatchAttemptEvent(
            seq = state.events.size + 1,
            elapsedMs = nextEventElapsed(state, elapsedMs),
            wordTileId = wordTileId,
            meaningTileId = meaningTileId,
        )
        var next = state.copy(selectedWordTileId = null, selectedMeaningTileId = null, events = state.events + event)

        // Matched by occurrence (same `pairId`), never by text.
        val pairId = pairIdByWordTile[wordTileId]
        val pair = pairId?.let(pairsById::get)
        if (pairId == null || pairIdByMeaningTile[meaningTileId] != pairId || pair == null) {
            val score = plan.score
            // Like the server, the running total may dip below 0; only the shown value is clamped.
            val provisional = next.provisionalScore?.let { current -> if (score != null) current - score.mistake else current }
            next = next.copy(wrongCount = next.wrongCount + 1, combo = 0, provisionalScore = provisional)
            return Step(next, MatchMadnessFeedback.Wrong(wordTileId, meaningTileId))
        }

        val correct = next.correctCount + 1
        val combo = next.combo + 1
        val score = plan.score
        next = next.copy(
            correctCount = correct,
            combo = combo,
            longestCombo = maxOf(next.longestCombo, combo),
            provisionalScore = if (score != null) (next.provisionalScore ?: 0) + score.points(combo) else next.provisionalScore,
            activePairIds = next.activePairIds - pairId,
            matchedAt = next.matchedAt + (pair.word to correct),
        )
        next = reorder(fill(next))

        if (next.correctCount >= goal) {
            val current = next.provisionalScore
            if (score != null && current != null) {
                val clearedAt = next.events.lastOrNull()?.elapsedMs ?: 0
                next = next.copy(provisionalScore = current + score.clearBonus(plan.timeLimitMs, clearedAt))
            }
            return Step(next.copy(phase = MatchMadnessState.Phase.GOAL_REACHED), MatchMadnessFeedback.GoalReached)
        }
        return Step(next, MatchMadnessFeedback.Correct(pairId, next.combo))
    }

    /**
     * Tops the board up to `visiblePairs` with the first queued pair whose meaning is neither on the board nor matched
     * in the last `minMatchesBetweenRepeats` correct matches. If none qualifies and the board is empty, the head of the
     * queue comes on anyway.
     */
    private fun fill(state: MatchMadnessState): MatchMadnessState {
        val active = state.activePairIds.toMutableList()
        val pending = state.pendingPairIds.toMutableList()
        while (active.size < plan.visiblePairs && pending.isNotEmpty()) {
            var index = pending.indexOfFirst { canShow(it, active, state) }
            if (index < 0 && active.isEmpty()) index = 0
            if (index < 0) break
            active.add(pending.removeAt(index))
        }
        return state.copy(activePairIds = active, pendingPairIds = pending)
    }

    /** "Same meaning" is the same `word`: a chapter never holds two meanings with one word. */
    private fun canShow(pairId: String, active: List<String>, state: MatchMadnessState): Boolean {
        val pair = pairsById[pairId] ?: return false
        if (active.any { pairsById[it]?.word == pair.word }) return false
        val last = state.matchedAt[pair.word] ?: return true
        return state.correctCount - last >= plan.minMatchesBetweenRepeats
    }

    private fun reorder(state: MatchMadnessState): MatchMadnessState {
        val active = state.activePairIds.mapNotNull(pairsById::get)
        return state.copy(
            wordOrder = ChallengeBoardOrdering.order(
                active.map { it.wordTileId }, plan.boardSeed, state.correctCount, ChallengeBoardOrdering.Side.WORD,
            ),
            meaningOrder = ChallengeBoardOrdering.order(
                active.map { it.meaningTileId }, plan.boardSeed, state.correctCount, ChallengeBoardOrdering.Side.MEANING,
            ),
        )
    }

    companion object {
        /** The `elapsedMs` the next event would carry: the server wants them strictly increasing. */
        fun nextEventElapsed(state: MatchMadnessState, elapsedMs: Int): Int {
            val last = state.events.lastOrNull()?.elapsedMs ?: return maxOf(elapsedMs, 0)
            return maxOf(elapsedMs, last + 1)
        }

        /** The commit body for a run that ends now. `goal_reached` ends exactly at the last event. */
        fun commitBody(state: MatchMadnessState, reason: ChallengeEndedReason, endedElapsedMs: Int): ChallengeCommitBody {
            val lastEvent = state.events.lastOrNull()?.elapsedMs ?: 0
            val ended = if (reason == ChallengeEndedReason.GOAL_REACHED) lastEvent else maxOf(endedElapsedMs, lastEvent)
            return ChallengeCommitBody(state.events, reason, ended)
        }
    }
}
