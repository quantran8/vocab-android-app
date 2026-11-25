package com.syll.features.premium

import com.syll.domain.AppLanguage
import com.syll.domain.premium.PremiumOffer
import com.syll.domain.premium.PremiumProduct
import com.syll.features.auth.StubVocabApi
import com.syll.features.main.LearnerStore
import com.syll.features.plan.InMemoryPreferencesStore
import com.syll.features.plan.LearningPlanStore
import com.syll.features.plan.NoReminders
import com.syll.network.ApiError
import com.syll.network.AppFeatures
import com.syll.network.PremiumPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `PremiumTests` (purchases): the backend first, then Play's acknowledgement. */
class PurchaseStoreTest {
    private val api = StubVocabApi()
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private suspend fun signedInLearner(features: AppFeatures? = null): LearnerStore {
        api.profileResult = Result.success(
            StubVocabApi.profile().copy(
                id = "0193f0a0-0000-7000-8000-000000000001",
                plan = PremiumPlan(PremiumPlan.Tier.FREE, limits = PremiumPlan.Limits(3, 150, 0)),
                features = features,
            ),
        )
        val learner = LearnerStore(api, LearningPlanStore(InMemoryPreferencesStore(), NoReminders), language = { AppLanguage.ENGLISH })
        learner.reloadProfile()
        return learner
    }

    @Test
    fun aPurchaseIsAcknowledgedOnlyAfterTheServerAccepts() = runBlocking {
        val learner = signedInLearner()
        val client = FakePlayBilling()
        api.syncResults = mutableListOf(Result.failure(ApiError.Transport))
        val store = PurchaseStore(client, api, learner, scope)
        store.purchase(FakePlayBilling.offer)
        assertEquals("Kept for the next sync", emptyList<String>(), client.acknowledged)
        assertEquals(listOf(learner.accountId), client.accountIds)

        api.syncResults = mutableListOf(Result.success(PremiumPlan(PremiumPlan.Tier.PREMIUM, source = PremiumPlan.Source.PLAYSTORE)))
        store.purchase(FakePlayBilling.offer)
        assertEquals(listOf("token-1"), client.acknowledged)
        assertEquals(listOf("token-1", "token-1"), api.syncedPurchases)
        assertTrue(store.didUnlock)
    }

    @Test
    fun aPurchaseOwnedByAnotherAccountIsAcknowledged() = runBlocking {
        val learner = signedInLearner()
        val client = FakePlayBilling()
        api.syncResults = mutableListOf(Result.failure(ApiError.Server(409, "PLAYSTORE_PURCHASE_OWNED", "", false, null)))
        val store = PurchaseStore(client, api, learner, scope)
        store.purchase(FakePlayBilling.offer)
        assertEquals(listOf("token-1"), client.acknowledged)
        assertNotNull(store.notice)
        assertFalse(store.didUnlock)
    }

    @Test
    fun anInvalidPurchaseIsLeftForPlayToRefund() = runBlocking {
        val learner = signedInLearner()
        val client = FakePlayBilling()
        api.syncResults = mutableListOf(Result.failure(ApiError.Server(422, "PLAYSTORE_PURCHASE_INVALID", "", false, null)))
        val store = PurchaseStore(client, api, learner, scope)
        store.purchase(FakePlayBilling.offer)
        assertEquals(emptyList<String>(), client.acknowledged)
        assertNotNull(store.notice)
    }

    @Test
    fun syncSendsEveryHeldPurchaseQuietly() = runBlocking {
        val learner = signedInLearner()
        val client = FakePlayBilling().apply { held = listOf("token-7", "token-8") }
        api.syncResults = mutableListOf(Result.failure(ApiError.Transport))
        val store = PurchaseStore(client, api, learner, scope)
        store.syncEntitlements()
        assertEquals(listOf("token-7", "token-8"), api.syncedPurchases)
        assertNull("Quiet: failures wait for the next launch", store.notice)
    }

    @Test
    fun restoringNothingSaysSo() = runBlocking {
        val learner = signedInLearner()
        val store = PurchaseStore(FakePlayBilling(), api, learner, scope)
        store.restore()
        assertEquals(com.syll.app.UserMessage.Resource(com.syll.R.string.no_subscription_found_for_this_google_account), store.notice)
        assertEquals(PurchaseStore.Activity.Idle, store.activity)
    }

    @Test
    fun premiumIsOfferedWhileTheServerRunsFreemium() = runBlocking {
        assertFalse(signedInLearner().offersPremium)
        val learner = signedInLearner(AppFeatures(freemiumEnabled = true, redeemEnabled = true))
        assertTrue(learner.offersPremium)
        assertTrue(learner.canRedeem)
    }

    @Test
    fun aRedeemedCodeAdoptsTheNewPlan() = runBlocking {
        val learner = signedInLearner()
        val premium = PremiumPlan(PremiumPlan.Tier.PREMIUM, source = PremiumPlan.Source.REDEEM)
        api.redeemResult = Result.success(premium)
        // The reload after it answers with the server's view, which now includes the code.
        api.profileResult = Result.success(api.profileResult.getOrThrow().copy(plan = premium))
        learner.redeem("SYLL-1234")
        assertTrue(learner.isPremium)
    }
}

/** Scripted Play Billing: every purchase succeeds with token-1. */
class FakePlayBilling : PlayBillingClient {
    companion object {
        val offer = PremiumOffer(PremiumProduct.MONTHLY, "Monthly", "$6.99", PremiumOffer.Period.MONTH, 6_990_000, "USD")
    }

    var held: List<String> = emptyList()
    val acknowledged = mutableListOf<String>()
    val accountIds = mutableListOf<String?>()

    private fun purchase(token: String) = VerifiedPurchase(token, PremiumProduct.MONTHLY, isAcknowledged = false) { acknowledged += token }

    override suspend fun offers(ids: List<String>) = listOf(offer)
    override suspend fun purchase(offer: PremiumOffer, accountId: String): PurchaseOutcome {
        accountIds += accountId
        return PurchaseOutcome.Purchased(purchase("token-1"))
    }
    override val purchaseUpdates: Flow<VerifiedPurchase> = MutableSharedFlow()
    override suspend fun currentPurchases() = held.map(::purchase)
}
