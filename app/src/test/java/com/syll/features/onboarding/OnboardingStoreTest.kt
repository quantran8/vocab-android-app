package com.syll.features.onboarding

import com.syll.domain.CefrLevel
import com.syll.domain.LearningPreferences
import com.syll.domain.TopicCatalog
import com.syll.features.auth.StubVocabApi
import com.syll.features.plan.InMemoryPreferencesStore
import com.syll.features.plan.LearningPlanStore
import com.syll.features.plan.NoReminders
import com.syll.network.CatalogCollection
import com.syll.network.PlacementResult
import com.syll.platform.NoOpFeedback
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Port of iOS `OnboardingStoreTests` (store part; the `LearningPreferences` part is in core/domain). */
class OnboardingStoreTest {
    private val placed = PlacementResult(CefrLevel.B1, correctCount = 9, totalCount = 18)

    @Before
    fun bundledCatalog() {
        TopicCatalog.resetToBundled()
    }

    private fun makeStore(
        storage: InMemoryPreferencesStore = InMemoryPreferencesStore(),
        api: StubVocabApi = StubVocabApi(),
    ): Pair<OnboardingStore, LearningPlanStore> {
        val plan = LearningPlanStore(storage, NoReminders)
        return OnboardingStore(plan, api, NoOpFeedback) to plan
    }

    /** iOS `makeStore(api:)`: a one-collection catalog. */
    private fun storeWithSmallCatalog(api: StubVocabApi): OnboardingStore {
        api.catalogCollections = listOf(CatalogCollection("c", "c", "C", null, emptyList()))
        return makeStore(api = api).first
    }

    @Test
    fun walksForwardAndBack() = runBlocking {
        val (store, _) = makeStore()
        assertEquals(OnboardingStore.Step.WELCOME, store.step)
        store.begin()
        assertEquals(OnboardingStore.Step.LEVEL, store.step)
        store.selectLevel(CefrLevel.B1)
        store.continueToCollection()
        assertEquals(OnboardingStore.Step.COLLECTION, store.step)
        store.continueToTopics()
        assertEquals(OnboardingStore.Step.TOPICS, store.step)
        store.continueToRoutine()
        assertEquals(OnboardingStore.Step.ROUTINE, store.step)
        store.back()
        assertEquals(OnboardingStore.Step.TOPICS, store.step)
        store.back()
        assertEquals(OnboardingStore.Step.COLLECTION, store.step)
        store.back()
        assertEquals(OnboardingStore.Step.LEVEL, store.step)
        store.back()
        assertEquals(OnboardingStore.Step.WELCOME, store.step)
    }

    @Test
    fun acceptingAPlacementDoesNotSaveTheLevelAgain() = runBlocking {
        val api = StubVocabApi()
        val store = storeWithSmallCatalog(api)
        store.begin()
        store.openPlacementTest()
        assertEquals(OnboardingStore.Step.PLACEMENT, store.step)
        store.acceptPlacement(placed)
        assertEquals(OnboardingStore.Step.COLLECTION, store.step)
        assertEquals(CefrLevel.B1, store.level)
        assertEquals(emptyList<CefrLevel>(), api.levelUpdates)
    }

    @Test
    fun backFromCollectionSkipsThePlacementTest() = runBlocking {
        val store = storeWithSmallCatalog(StubVocabApi())
        store.begin()
        store.openPlacementTest()
        store.acceptPlacement(placed)
        store.back()
        assertEquals(OnboardingStore.Step.LEVEL, store.step)
    }

    @Test
    fun stepChangesRememberTheirDirection() {
        val store = storeWithSmallCatalog(StubVocabApi())
        store.begin()
        assertFalse(store.isMovingBack)
        store.openPlacementTest()
        assertFalse(store.isMovingBack)
        store.back()
        assertTrue(store.isMovingBack)
        store.openPlacementTest()
        assertFalse(store.isMovingBack)
    }

    @Test
    fun leavingThePlacementTestReturnsToLevel() {
        val store = storeWithSmallCatalog(StubVocabApi())
        store.begin()
        store.openPlacementTest()
        store.back()
        assertEquals(OnboardingStore.Step.LEVEL, store.step)
    }

    @Test
    fun disagreeingWithAPlacementSavesOnlyTheNewLevel() = runBlocking {
        val api = StubVocabApi()
        val store = storeWithSmallCatalog(api)
        store.begin()
        store.openPlacementTest()
        store.chooseLevelByHand(after = placed)
        assertEquals(OnboardingStore.Step.LEVEL, store.step)
        assertEquals(CefrLevel.B1, store.level)
        store.selectLevel(CefrLevel.C1)
        store.continueToCollection()
        assertEquals(listOf(CefrLevel.C1), api.levelUpdates)
    }

    @Test
    fun returningToThePlacedLevelSavesItAgain() = runBlocking {
        val api = StubVocabApi()
        val store = storeWithSmallCatalog(api)
        store.begin()
        store.chooseLevelByHand(after = placed)
        store.selectLevel(CefrLevel.C1)
        store.continueToCollection()
        store.back()
        store.selectLevel(CefrLevel.B1)
        store.continueToCollection()
        assertEquals("The server holds C1 by now", listOf(CefrLevel.C1, CefrLevel.B1), api.levelUpdates)
    }

    @Test
    fun choosingALevelWithoutATestStillSavesIt() = runBlocking {
        val api = StubVocabApi()
        val store = storeWithSmallCatalog(api)
        store.begin()
        store.selectLevel(CefrLevel.A2)
        store.continueToCollection()
        assertEquals(listOf(CefrLevel.A2), api.levelUpdates)
    }

    @Test
    fun cannotLeaveLevelWithoutAChoice() = runBlocking {
        val (store, _) = makeStore()
        store.begin()
        store.continueToCollection()
        assertEquals(OnboardingStore.Step.LEVEL, store.step)
    }

    @Test
    fun anEmptyCatalogKeepsTheLevelStepWithAnError() = runBlocking {
        val api = StubVocabApi().apply { catalogCollections = emptyList() }
        val (store, _) = makeStore(api = api)
        store.begin()
        store.selectLevel(CefrLevel.A2)
        store.continueToCollection()
        assertEquals(OnboardingStore.Step.LEVEL, store.step)
        assertTrue(store.errorMessage != null)
    }

    @Test
    fun cannotLeaveTopicsWithoutASelection() = runBlocking {
        val (store, _) = makeStore()
        store.begin()
        store.selectLevel(CefrLevel.A2)
        store.continueToCollection()
        store.continueToTopics()
        store.draft.topicIds.toList().forEach { store.toggleTopic(it) }
        assertFalse(store.canContinueFromTopics)
        store.continueToRoutine()
        assertEquals(OnboardingStore.Step.TOPICS, store.step)
    }

    @Test
    fun createFlowSavesThenFinishCompletes() = runBlocking {
        val storage = InMemoryPreferencesStore()
        val api = StubVocabApi()
        val (store, plan) = makeStore(storage, api)
        store.begin()
        store.selectLevel(CefrLevel.B2)
        store.continueToCollection()
        store.continueToTopics()
        store.continueToRoutine()
        store.toggleDay(1)
        store.createFlow()
        assertEquals(OnboardingStore.Step.READY, store.step)
        assertEquals(true, storage.preferences?.learningDays?.contains(1))
        assertFalse(plan.hasCompletedOnboarding)
        // The goal and days went up; reviews and speaking were left out (defaults).
        val settings = api.settingsUpdates.single()
        assertEquals(5, settings.dailyNewWordGoal)
        assertEquals(6, settings.weeklySessionGoal)
        assertFalse(settings.sendsReviewsPerSession)
        assertNull(settings.dailySpeaking)

        store.finish()
        assertTrue(plan.hasCompletedOnboarding)
        assertTrue(storage.hasCompletedOnboarding)
    }

    @Test
    fun createFlowRequiresALearningDay() = runBlocking {
        val storage = InMemoryPreferencesStore()
        val (store, plan) = makeStore(storage)
        store.draft = store.draft.copy(learningDays = emptySet())
        store.createFlow()
        assertNull(storage.preferences)
        assertNotEquals(OnboardingStore.Step.READY, store.step)
        store.finish()
        assertFalse("Finishing without a saved plan is ignored", plan.hasCompletedOnboarding)
    }

    @Test
    fun resumesFromSavedPreferences() {
        val saved = LearningPreferences.DEFAULT.selectCollection("ielts").toggleTopic("ielts-science")!!
        val (store, _) = makeStore(InMemoryPreferencesStore(preferences = saved))
        assertEquals("ielts", store.draft.collectionId)
        assertEquals(setOf("ielts-science"), store.draft.topicIds)
    }

    @Test
    fun planIgnoresIncompleteSaves() {
        val storage = InMemoryPreferencesStore(preferences = LearningPreferences.DEFAULT)
        val plan = LearningPlanStore(storage, NoReminders)
        plan.save(LearningPreferences.DEFAULT.copy(topicIds = emptySet()))
        assertEquals(LearningPreferences.DEFAULT, plan.current)
        assertEquals(LearningPreferences.DEFAULT, storage.preferences)
    }

    @Test
    fun switchingCollectionClearsTopics() {
        val (store, _) = makeStore()
        store.selectCollection("toeic")
        assertEquals("toeic", store.draft.collectionId)
        assertTrue(store.draft.topicIds.isEmpty())
        assertFalse(store.canContinueFromTopics)

        store.toggleTopic("toeic-office")
        store.selectCollection("toeic")
        assertEquals("Re-selecting the same collection keeps topics", setOf("toeic-office"), store.draft.topicIds)
    }
}
