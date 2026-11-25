package com.syll.features.placement

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syll.app.UserMessage
import com.syll.network.PlacementAnswer
import com.syll.network.PlacementQuestion
import com.syll.network.PlacementResult
import com.syll.network.VocabApi
import com.syll.network.asApiError
import com.syll.platform.FeedbackProviding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * iOS `PlacementTestStore`: runs one placement test. Nothing is scored here and nothing is shown as right or
 * wrong: the answers go up together at the end, and the backend saves the level it works out.
 */
class PlacementTestStore(
    private val api: VocabApi,
    private val haptics: FeedbackProviding,
    /** Where [start] loads the questions (the app's main scope; a test's own scope in tests). */
    private val scope: CoroutineScope,
) {
    sealed interface Stage {
        data object Idle : Stage
        data object Loading : Stage
        /** Not enough content for a test yet; the learner picks a level by hand. */
        data object NoContent : Stage
        data class LoadFailed(val message: UserMessage) : Stage
        data object Answering : Stage
        data object Submitting : Stage
        data class SubmitFailed(val message: UserMessage, val canRetry: Boolean) : Stage
        data class Finished(val result: PlacementResult) : Stage
    }

    var stage by mutableStateOf<Stage>(Stage.Idle)
        private set
    var questions by mutableStateOf<List<PlacementQuestion>>(emptyList())
        private set
    var index by mutableIntStateOf(0)
        private set
    var selectedOptionId by mutableStateOf<String?>(null)
        private set
    /** Runs once the backend has saved a level, so anything showing the level can catch up. */
    var onPlaced: suspend () -> Unit = {}

    private val answers = mutableListOf<PlacementAnswer>()
    /** Bumped by every start and cancel, so an answer for an abandoned test is ignored. */
    private var generation = 0

    val currentQuestion: PlacementQuestion? get() = questions.getOrNull(index)
    val isOnLastQuestion: Boolean get() = index == questions.size - 1

    /** Clears the previous run before returning, so a screen opened right after never shows it. */
    fun start(): Job {
        generation += 1
        val generation = generation
        questions = emptyList()
        answers.clear()
        index = 0
        selectedOptionId = null
        stage = Stage.Loading
        return scope.launch {
            try {
                val loaded = api.placementTest()
                if (generation != this@PlacementTestStore.generation) return@launch
                questions = loaded
                stage = if (loaded.isEmpty()) Stage.NoContent else Stage.Answering
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation != this@PlacementTestStore.generation) return@launch
                stage = Stage.LoadFailed(UserMessage.Api(e.asApiError))
            }
        }
    }

    /** Leaves the on-screen stage alone: this runs while the test is animating away. */
    fun cancel() {
        generation += 1
    }

    fun select(optionId: String) {
        if (stage != Stage.Answering || optionId == selectedOptionId) return
        selectedOptionId = optionId
        haptics.selectionChanged()
    }

    /** Locks in the selected option. Each question is answered once, with no going back. */
    suspend fun confirmAnswer() {
        val selected = selectedOptionId ?: return
        record(selected)
    }

    /** Goes up as "don't know", which keeps a guess from placing the learner too high. */
    suspend fun skipQuestion() {
        haptics.selectionChanged()
        record(null)
    }

    private suspend fun record(optionId: String?) {
        val question = currentQuestion
        if (stage != Stage.Answering || question == null) return
        answers += PlacementAnswer(question.id, optionId)
        selectedOptionId = null
        if (isOnLastQuestion) submit() else index += 1
    }

    suspend fun retrySubmit() {
        if ((stage as? Stage.SubmitFailed)?.canRetry != true) return
        submit()
    }

    private suspend fun submit() {
        val generation = generation
        stage = Stage.Submitting
        try {
            val result = api.submitPlacementTest(answers.toList())
            if (generation != this.generation) return
            stage = Stage.Finished(result)
            haptics.success()
            onPlaced()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (generation != this.generation) return
            val failure = e.asApiError
            stage = Stage.SubmitFailed(UserMessage.Api(failure), failure.isRetryable)
        }
    }
}
