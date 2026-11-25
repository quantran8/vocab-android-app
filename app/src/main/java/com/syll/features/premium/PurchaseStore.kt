package com.syll.features.premium

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syll.R
import com.syll.app.UserMessage
import com.syll.domain.premium.PremiumOffer
import com.syll.domain.premium.PremiumProduct
import com.syll.features.main.LearnerStore
import com.syll.network.VocabApi
import com.syll.network.asApiError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * iOS `PurchaseStore`: buying, restoring and syncing Premium through Google Play. The backend has the final say:
 * every purchase goes to `POST /me/subscriptions/playstore`, and a purchase is acknowledged only once it answers.
 * An unacknowledged one comes back at the next launch through [syncEntitlements], so nothing is lost offline.
 */
class PurchaseStore(
    private val client: PlayBillingClient,
    private val api: VocabApi,
    private val learner: LearnerStore,
    private val scope: CoroutineScope,
) {
    sealed interface Activity {
        data object Idle : Activity
        data object LoadingOffers : Activity
        data class Purchasing(val productId: String) : Activity
        data object Restoring : Activity
    }

    var offers by mutableStateOf<List<PremiumOffer>>(emptyList())
        private set
    var activity by mutableStateOf<Activity>(Activity.Idle)
        private set
    /** A message for the paywall: a failure, or a purchase waiting for a payment to complete. */
    var notice by mutableStateOf<UserMessage?>(null)
        private set
    /** Set after a purchase or restore made the learner Premium, so the paywall can close. */
    var didUnlock by mutableStateOf(false)
        private set

    private var updates: Job? = null

    /** Call once at launch: Play delivers pending payments that complete and purchases from elsewhere here. */
    fun listen() {
        if (updates != null) return
        updates = scope.launch {
            client.purchaseUpdates.collect { deliver(it) }
        }
    }

    /**
     * After sign-in: sends what this Google account holds, so a purchase made while signed out, on another device or
     * left unacknowledged reaches this account. Quiet: failures wait for the next launch.
     */
    suspend fun syncEntitlements() {
        val purchases = try {
            client.currentPurchases()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return
        }
        purchases.forEach { deliver(it) }
    }

    suspend fun loadOffers() {
        if (offers.isNotEmpty() || activity != Activity.Idle) return
        activity = Activity.LoadingOffers
        try {
            offers = client.offers(PremiumProduct.ids)
            if (offers.isEmpty()) notice = UserMessage.Resource(R.string.premium_isn_t_available_right_now_please_try_aga)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            notice = UserMessage.Resource(R.string.couldn_t_load_the_plans_check_your_connection_an)
        } finally {
            activity = Activity.Idle
        }
    }

    suspend fun purchase(offer: PremiumOffer) {
        val accountId = learner.accountId
        if (activity != Activity.Idle || accountId == null) return
        activity = Activity.Purchasing(offer.id)
        notice = null
        try {
            when (val outcome = client.purchase(offer, accountId)) {
                is PurchaseOutcome.Purchased -> deliver(outcome.purchase, reportsFailure = true)
                PurchaseOutcome.Pending -> notice = UserMessage.Resource(R.string.your_purchase_is_waiting_for_approval_premium_st)
                PurchaseOutcome.Cancelled -> Unit
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            notice = UserMessage.Resource(R.string.the_purchase_didn_t_go_through_you_haven_t_been)
        } finally {
            activity = Activity.Idle
        }
    }

    suspend fun restore() {
        if (activity != Activity.Idle) return
        activity = Activity.Restoring
        notice = null
        try {
            val purchases = try {
                client.currentPurchases()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice = UserMessage.Resource(R.string.couldn_t_reach_google_play_please_try_again)
                return
            }
            purchases.forEach { deliver(it, reportsFailure = true) }
            if (!learner.isPremium && notice == null) {
                notice = UserMessage.Resource(R.string.no_subscription_found_for_this_google_account)
            }
        } finally {
            activity = Activity.Idle
        }
    }

    fun clearNotice() {
        notice = null
    }

    fun acknowledgeUnlock() {
        didUnlock = false
    }

    /**
     * The backend first, then Play's acknowledgement. A purchase owned by another Syll account is acknowledged too
     * (it is paid for and stays with that account, like iOS `finish()`). One the backend calls invalid is left
     * unacknowledged, so Play refunds it; a network failure leaves it for the next launch.
     */
    private suspend fun deliver(purchase: VerifiedPurchase, reportsFailure: Boolean = false) {
        if (learner.profile == null) return
        val wasPremium = learner.isPremium
        try {
            val plan = api.syncPlayStorePurchase(purchase.purchaseToken, purchase.productId)
            acknowledge(purchase)
            learner.adoptPlan(plan)
            if (plan.isPremium && !wasPremium) didUnlock = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val failure = e.asApiError
            if (failure.code == OWNED) acknowledge(purchase)
            if (reportsFailure) notice = UserMessage.Api(failure)
        }
    }

    /** Play retries an acknowledgement it missed through the next sync, so a failure here is quiet. */
    private suspend fun acknowledge(purchase: VerifiedPurchase) {
        if (purchase.isAcknowledged) return
        try {
            purchase.acknowledge()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val OWNED = "PLAYSTORE_PURCHASE_OWNED"
    }
}
