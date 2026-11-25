package com.syll.features.ads

import com.syll.data.InMemoryKeyValueStore
import com.syll.domain.AppLanguage
import com.syll.features.auth.StubVocabApi
import com.syll.features.main.LearnerStore
import com.syll.features.plan.InMemoryPreferencesStore
import com.syll.features.plan.LearningPlanStore
import com.syll.features.plan.NoReminders
import com.syll.network.AppFeatures
import com.syll.network.PremiumPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `AdStoreTests`. */
class AdStoreTest {
    private val api = StubVocabApi()
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private suspend fun learner(free: Boolean, every: Int?): LearnerStore {
        api.profileResult = Result.success(
            StubVocabApi.profile().copy(
                plan = if (free) PremiumPlan(PremiumPlan.Tier.FREE, limits = PremiumPlan.Limits(3, 150, 0)) else PremiumPlan(PremiumPlan.Tier.PREMIUM),
                features = AppFeatures(freemiumEnabled = true, redeemEnabled = false, adEverySessions = every),
            ),
        )
        return LearnerStore(api, LearningPlanStore(InMemoryPreferencesStore(), NoReminders), language = { AppLanguage.ENGLISH })
            .also { it.reloadProfile() }
    }

    @Test
    fun anAdFollowsEveryThirdSessionForFreeLearners() = runBlocking {
        val provider = FakeAds()
        val ads = AdStore(provider, learner(free = true, every = 3), scope)
        ads.sessionFinished()
        ads.sessionFinished()
        ads.presentIfDue()
        assertEquals(0, provider.presented)
        ads.sessionFinished()
        ads.presentIfDue()
        assertEquals(1, provider.presented)
        assertEquals(0, ads.sessionsSinceAd)
    }

    @Test
    fun anAdThatWasNotReadyStaysDue() = runBlocking {
        val provider = FakeAds().apply { isReady = false }
        val ads = AdStore(provider, learner(free = true, every = 1), scope)
        ads.sessionFinished()
        ads.presentIfDue()
        assertTrue(ads.isDue)
        provider.isReady = true
        ads.presentIfDue()
        assertEquals(1, provider.presented)
    }

    @Test
    fun noAdsForPremiumOrWhenTurnedOffOrAtLaunch() = runBlocking {
        for ((free, every) in listOf(false to 3, true to 0)) {
            val provider = FakeAds()
            val ads = AdStore(provider, learner(free, every), scope)
            ads.sessionFinished()
            ads.presentIfDue()
            assertEquals(0, provider.presented)
            assertNull(ads.interval)
        }
        val storage = InMemoryKeyValueStore().apply { putInt("ads.sessionsSinceAd", 5) }
        val provider = FakeAds()
        val ads = AdStore(provider, learner(free = true, every = 3), scope, storage)
        ads.presentIfDue()
        assertEquals("Not when the app opens", 0, provider.presented)
    }
}

class FakeAds : InterstitialAdProviding {
    var isReady = true
    var presented = 0
        private set

    override suspend fun prepare() {}
    override suspend fun preload() {}
    override suspend fun present(): Boolean {
        if (!isReady) return false
        presented += 1
        return true
    }
}
