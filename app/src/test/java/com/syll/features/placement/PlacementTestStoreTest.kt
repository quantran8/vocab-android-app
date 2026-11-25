package com.syll.features.placement

import com.syll.app.UserMessage
import com.syll.domain.CefrLevel
import com.syll.features.auth.StubVocabApi
import com.syll.network.ApiError
import com.syll.network.PlacementAnswer
import com.syll.network.PlacementQuestion
import com.syll.network.PlacementResult
import com.syll.platform.NoOpFeedback
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port of iOS `PlacementTestStoreTests` (decoding and the explicit null are in core/network). */
class PlacementTestStoreTest {
    private val api = StubVocabApi()
    private val result = PlacementResult(CefrLevel.B1, correctCount = 1, totalCount = 2)

    private fun question(id: String) = PlacementQuestion(
        id = id,
        prompt = PlacementQuestion.Prompt("word-$id"),
        question = "?",
        options = listOf(PlacementQuestion.Option("$id-a", "a"), PlacementQuestion.Option("$id-b", "b")),
    )

    private fun CoroutineScope.makeStore() = PlacementTestStore(api, NoOpFeedback, this)

    private suspend fun answerAll(store: PlacementTestStore) {
        store.select("q1-b")
        store.confirmAnswer()
        store.skipQuestion()
    }

    private suspend fun CoroutineScope.startedStore(): PlacementTestStore {
        api.placementQuestions = Result.success(listOf(question("q1"), question("q2")))
        val store = makeStore()
        store.start().join()
        return store
    }

    @Test
    fun startShowsLoadingRightAway() = runBlocking {
        val store = makeStore()
        store.start()
        assertEquals(PlacementTestStore.Stage.Loading, store.stage)
    }

    @Test
    fun keepsQuestionOrder() = runBlocking {
        api.placementQuestions = Result.success(listOf(question("q2"), question("q1")))
        val store = makeStore()
        store.start().join()
        assertEquals(PlacementTestStore.Stage.Answering, store.stage)
        assertEquals(listOf("q2", "q1"), store.questions.map { it.id })
        assertEquals("q2", store.currentQuestion?.id)
    }

    @Test
    fun noQuestionsMeansNoContent() = runBlocking {
        val store = makeStore()
        store.start().join()
        assertEquals(PlacementTestStore.Stage.NoContent, store.stage)
    }

    @Test
    fun loadFailureIsReported() = runBlocking {
        api.placementQuestions = Result.failure(ApiError.Transport)
        val store = makeStore()
        store.start().join()
        assertEquals(PlacementTestStore.Stage.LoadFailed(UserMessage.Api(ApiError.Transport)), store.stage)
    }

    @Test
    fun cannotConfirmWithoutAChoice() = runBlocking {
        val store = startedStore()
        store.confirmAnswer()
        assertEquals(0, store.index)
        assertEquals(PlacementTestStore.Stage.Answering, store.stage)
    }

    @Test
    fun aSecondConfirmDoesNotAnswerTheNextQuestion() = runBlocking {
        val store = startedStore()
        store.select("q1-a")
        store.confirmAnswer()
        store.confirmAnswer()
        assertEquals(1, store.index)
        assertNull(store.selectedOptionId)
    }

    @Test
    fun skippingDiscardsTheSelection() = runBlocking {
        api.placementResults += Result.success(result)
        val store = startedStore()
        store.select("q1-a")
        store.skipQuestion()
        assertEquals(1, store.index)
        assertNull(store.selectedOptionId)
        store.skipQuestion()
        assertEquals(listOf(PlacementAnswer("q1", null), PlacementAnswer("q2", null)), api.placementSubmissions.first())
    }

    @Test
    fun submitsEveryQuestionInOrderOnce() = runBlocking {
        api.placementResults += Result.success(result)
        val store = startedStore()
        answerAll(store)
        assertEquals(listOf(listOf(PlacementAnswer("q1", "q1-b"), PlacementAnswer("q2", null))), api.placementSubmissions)
        assertEquals(PlacementTestStore.Stage.Finished(result), store.stage)
    }

    @Test
    fun finishingTellsTheAppALevelWasSaved() = runBlocking {
        api.placementResults += Result.success(result)
        val store = startedStore()
        var notified = false
        store.onPlaced = { notified = true }
        answerAll(store)
        assertTrue(notified)
    }

    @Test
    fun aRetriedSubmitResendsTheSameAnswers() = runBlocking {
        api.placementResults += Result.failure(ApiError.Transport)
        api.placementResults += Result.success(result)
        val store = startedStore()
        answerAll(store)
        assertEquals(PlacementTestStore.Stage.SubmitFailed(UserMessage.Api(ApiError.Transport), canRetry = true), store.stage)
        store.retrySubmit()
        assertEquals(PlacementTestStore.Stage.Finished(result), store.stage)
        assertEquals(2, api.placementSubmissions.size)
        assertEquals(api.placementSubmissions[0], api.placementSubmissions[1])
    }

    @Test
    fun aRejectedSubmitCannotBeRetried() = runBlocking {
        val rejected = ApiError.Server(400, "VALIDATION_FAILED", "No valid question", retryable = false, requestId = null)
        api.placementResults += Result.failure(rejected)
        val store = startedStore()
        answerAll(store)
        assertEquals(PlacementTestStore.Stage.SubmitFailed(UserMessage.Api(rejected), canRetry = false), store.stage)
        store.retrySubmit()
        assertEquals(1, api.placementSubmissions.size)
    }

    @Test
    fun retakingStartsFresh() = runBlocking {
        api.placementResults += Result.success(result)
        api.placementResults += Result.success(result)
        val store = startedStore()
        answerAll(store)
        store.start().join()
        assertEquals(PlacementTestStore.Stage.Answering, store.stage)
        assertEquals(0, store.index)
        answerAll(store)
        assertEquals("The first run's answers are gone", 2, api.placementSubmissions[1].size)
    }

    @Test
    fun anAnswerForACancelledTestIsIgnored() = runBlocking {
        api.placementQuestions = Result.success(listOf(question("q1")))
        val release = CompletableDeferred<Unit>()
        api.onPlacementTest = { release.await() }
        val store = makeStore()
        val load = store.start()
        store.cancel()
        release.complete(Unit)
        load.join()
        assertEquals(PlacementTestStore.Stage.Loading, store.stage)
        assertTrue(store.questions.isEmpty())
    }
}
