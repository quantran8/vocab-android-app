package com.syll.features.main

import com.syll.domain.AppLanguage
import com.syll.domain.CefrLevel
import com.syll.domain.LearningPreferences
import com.syll.domain.TopicCatalog
import com.syll.features.auth.StubVocabApi
import com.syll.features.plan.InMemoryPreferencesStore
import com.syll.features.plan.LearningPlanStore
import com.syll.features.plan.NoReminders
import com.syll.network.ApiError
import com.syll.network.LearningScope
import com.syll.network.UserSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** `LearnerStore.load()` decides between onboarding and Home and keeps the device's settings in step. */
class LearnerStoreTest {
    private val storage = InMemoryPreferencesStore()
    private val plan = LearningPlanStore(storage, NoReminders)
    private val api = StubVocabApi()
    private val store = LearnerStore(api, plan, language = { AppLanguage.ENGLISH }, timeZone = { "UTC" })

    @Before
    fun bundledCatalog() {
        TopicCatalog.resetToBundled()
    }

    @Test
    fun anAccountWithoutAScopeGoesToOnboarding() = runBlocking {
        storage.hasCompletedOnboarding = true // left over from another account
        api.profileResult = Result.success(StubVocabApi.profile(level = CefrLevel.B1))
        store.load()
        assertEquals(LearnerStore.Phase.Ready, store.phase)
        assertFalse(plan.hasCompletedOnboarding)
    }

    @Test
    fun anAccountWithAPlanAdoptsTheServersScopeAndGoal() = runBlocking {
        api.profileResult = Result.success(
            StubVocabApi.profile(
                level = CefrLevel.B1,
                scope = LearningScope("ielts", listOf("ielts-science")),
                settings = UserSettings("vi", "en-US", "UTC", dailyNewWordGoal = 40, reviewsPerSession = 12, dailySpeaking = true),
            ),
        )
        store.load()
        assertTrue(plan.hasCompletedOnboarding)
        val adopted = plan.current
        assertEquals("ielts", adopted.collectionId)
        assertEquals(setOf("ielts-science"), adopted.topicIds)
        assertEquals("Clamped to the app's range", 30, adopted.wordsPerDay)
        assertEquals(12, adopted.reviewsPerSession)
        assertTrue(adopted.dailySpeaking)
        assertEquals("Device settings already match", emptyList<Any>(), api.settingsUpdates)
    }

    @Test
    fun sendsTheDevicesTimeZoneLanguageAndLearningDays() = runBlocking {
        val plan = LearningPlanStore(InMemoryPreferencesStore(LearningPreferences.DEFAULT), NoReminders) // 5 learning days
        val store = LearnerStore(api, plan, language = { AppLanguage.ENGLISH }, timeZone = { "UTC" })
        api.profileResult = Result.success(
            StubVocabApi.profile(settings = UserSettings("vi", "vi-VN", "Asia/Ho_Chi_Minh", dailyNewWordGoal = 5)),
        )
        store.load()
        val update = api.settingsUpdates.single()
        assertEquals("en-US", update.uiLocale)
        assertEquals("UTC", update.timeZone)
        assertEquals(5, update.weeklySessionGoal)
    }

    @Test
    fun aFailedLoadIsReported() = runBlocking {
        api.profileResult = Result.failure(ApiError.Transport)
        store.load()
        assertTrue(store.phase is LearnerStore.Phase.Failed)
    }
}
