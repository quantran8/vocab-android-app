package com.syll.features.premium

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.syll.domain.premium.PremiumOffer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

/**
 * A Play purchase in the purchased state (iOS `VerifiedTransaction`). The backend verifies [purchaseToken] with
 * Google; [acknowledge] runs only after it answers (Play refunds a purchase left unacknowledged for three days).
 */
class VerifiedPurchase(
    val purchaseToken: String,
    val productId: String,
    val isAcknowledged: Boolean,
    val acknowledge: suspend () -> Unit,
)

/** iOS `PurchaseOutcome`. */
sealed interface PurchaseOutcome {
    data class Purchased(val purchase: VerifiedPurchase) : PurchaseOutcome
    /** A payment still to be completed (cash, a family approval): it arrives later through [PlayBillingClient.purchaseUpdates]. */
    data object Pending : PurchaseOutcome
    data object Cancelled : PurchaseOutcome
}

/** A Play Billing failure the paywall reports; [isServiceProblem] when Play itself couldn't be reached. */
class BillingException(val responseCode: Int) : Exception("Play Billing response $responseCode") {
    val isServiceProblem: Boolean
        get() = responseCode == BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE ||
            responseCode == BillingClient.BillingResponseCode.SERVICE_DISCONNECTED ||
            responseCode == BillingClient.BillingResponseCode.BILLING_UNAVAILABLE ||
            responseCode == BillingClient.BillingResponseCode.NETWORK_ERROR
}

/** iOS `StoreKitClient`: Play Billing behind an interface, so stores, tests and previews never touch Google Play. */
interface PlayBillingClient {
    suspend fun offers(ids: List<String>): List<PremiumOffer>
    /** [accountId] becomes the purchase's `obfuscatedAccountId` (the user id). */
    suspend fun purchase(offer: PremiumOffer, accountId: String): PurchaseOutcome
    /** Purchases made or completed outside a [purchase] call: pending payments completing, other devices. */
    val purchaseUpdates: Flow<VerifiedPurchase>
    /** The subscriptions this Google account holds now (Play answers from its cache, refreshed in the background). */
    suspend fun currentPurchases(): List<VerifiedPurchase>
}

/** Previews and tests: no products, nothing to restore. */
object NoPlayBilling : PlayBillingClient {
    override suspend fun offers(ids: List<String>): List<PremiumOffer> = emptyList()
    override suspend fun purchase(offer: PremiumOffer, accountId: String): PurchaseOutcome = PurchaseOutcome.Cancelled
    override val purchaseUpdates: Flow<VerifiedPurchase> = MutableSharedFlow()
    override suspend fun currentPurchases(): List<VerifiedPurchase> = emptyList()
}

/**
 * Play Billing Library. The billing flow needs an activity, which `MainActivity` attaches while it is alive. A
 * purchase's result comes back through the [PurchasesUpdatedListener]: it answers the [purchase] call waiting for
 * it, anything else goes to [purchaseUpdates].
 */
class LivePlayBilling(context: Context) : PlayBillingClient {
    private var activity: Activity? = null
    private val updates = MutableSharedFlow<VerifiedPurchase>(extraBufferCapacity = 16)
    private var waiting: CompletableDeferred<Pair<BillingResult, List<Purchase>?>>? = null
    private val details = mutableMapOf<String, ProductDetails>()
    private val connection = Mutex()

    private val listener = PurchasesUpdatedListener { result, purchases ->
        val call = waiting
        if (call != null) {
            waiting = null
            call.complete(result to purchases)
        } else if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            purchases.orEmpty().mapNotNull(::verified).forEach { updates.tryEmit(it) }
        }
    }

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(listener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    override val purchaseUpdates: Flow<VerifiedPurchase> = updates

    fun attach(activity: Activity) {
        this.activity = activity
    }

    fun detach(activity: Activity) {
        if (this.activity === activity) this.activity = null
    }

    override suspend fun offers(ids: List<String>): List<PremiumOffer> {
        ready()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                ids.map {
                    QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(BillingClient.ProductType.SUBS).build()
                },
            )
            .build()
        val list = suspendCancellableCoroutine { continuation ->
            client.queryProductDetailsAsync(params) { result, found ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    continuation.resume(Result.success(found.productDetailsList))
                } else {
                    continuation.resume(Result.failure(BillingException(result.responseCode)))
                }
            }
        }.getOrThrow()
        list.forEach { details[it.productId] = it }
        // The order the paywall lists them in: `ids`' order.
        return list.mapNotNull(::offer).sortedBy { ids.indexOf(it.id) }
    }

    override suspend fun purchase(offer: PremiumOffer, accountId: String): PurchaseOutcome {
        ready()
        val product = details[offer.id] ?: throw BillingException(BillingClient.BillingResponseCode.ITEM_UNAVAILABLE)
        val host = activity ?: throw BillingException(BillingClient.BillingResponseCode.ERROR)
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).setOfferToken(offer.offerToken).build()),
            )
            .setObfuscatedAccountId(accountId)
            .build()
        val call = CompletableDeferred<Pair<BillingResult, List<Purchase>?>>()
        waiting = call
        val launch = client.launchBillingFlow(host, params)
        if (launch.responseCode != BillingClient.BillingResponseCode.OK) {
            waiting = null
            return outcome(launch, null, offer.id)
        }
        val (result, purchases) = call.await()
        return outcome(result, purchases, offer.id)
    }

    private suspend fun outcome(result: BillingResult, purchases: List<Purchase>?, productId: String): PurchaseOutcome =
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val purchase = purchases.orEmpty().firstOrNull { productId in it.products }
                when {
                    purchase == null -> PurchaseOutcome.Cancelled
                    purchase.purchaseState == Purchase.PurchaseState.PENDING -> PurchaseOutcome.Pending
                    else -> verified(purchase)?.let { PurchaseOutcome.Purchased(it) } ?: PurchaseOutcome.Cancelled
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> PurchaseOutcome.Cancelled
            // Bought earlier (maybe on another account of this app): hand over what Play holds.
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED ->
                currentPurchases().firstOrNull { it.productId == productId }?.let { PurchaseOutcome.Purchased(it) }
                    ?: throw BillingException(result.responseCode)
            else -> throw BillingException(result.responseCode)
        }

    override suspend fun currentPurchases(): List<VerifiedPurchase> {
        ready()
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        return suspendCancellableCoroutine { continuation ->
            client.queryPurchasesAsync(params) { result, purchases ->
                continuation.resume(if (result.responseCode == BillingClient.BillingResponseCode.OK) purchases.mapNotNull(::verified) else emptyList())
            }
        }
    }

    /** Pending payments aren't sent up: the backend grants nothing for them. */
    private fun verified(purchase: Purchase): VerifiedPurchase? {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return null
        val productId = purchase.products.firstOrNull() ?: return null
        return VerifiedPurchase(purchase.purchaseToken, productId, purchase.isAcknowledged) {
            if (!purchase.isAcknowledged) acknowledge(purchase.purchaseToken)
        }
    }

    private suspend fun acknowledge(token: String) {
        ready()
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build()
        suspendCancellableCoroutine { continuation ->
            client.acknowledgePurchase(params) { continuation.resume(Unit) }
        }
    }

    /** Connects once, and again after Play drops the service connection. */
    private suspend fun ready() = connection.withLock {
        if (client.isReady) return@withLock
        val result = suspendCancellableCoroutine { continuation ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (continuation.isActive) continuation.resume(result)
                }

                override fun onBillingServiceDisconnected() {}
            })
        }
        if (result.responseCode != BillingClient.BillingResponseCode.OK) throw BillingException(result.responseCode)
    }

    /**
     * The offer the paywall sells: an introductory free trial when Play offers one to this account (Play lists only
     * offers the account is eligible for), else the base plan.
     */
    private fun offer(product: ProductDetails): PremiumOffer? {
        val options = product.subscriptionOfferDetails.orEmpty()
        val trial = options.firstOrNull { option -> option.pricingPhases.pricingPhaseList.firstOrNull()?.priceAmountMicros == 0L }
        val chosen = trial ?: options.firstOrNull { it.offerId == null } ?: options.firstOrNull() ?: return null
        val phases = chosen.pricingPhases.pricingPhaseList
        val recurring = phases.lastOrNull { it.recurrenceMode == ProductDetails.RecurrenceMode.INFINITE_RECURRING }
            ?: phases.lastOrNull() ?: return null
        return PremiumOffer(
            id = product.productId,
            displayName = product.name,
            displayPrice = recurring.formattedPrice,
            period = PremiumOffer.period(recurring.billingPeriod),
            priceMicros = recurring.priceAmountMicros,
            currencyCode = recurring.priceCurrencyCode,
            freeTrialPeriod = phases.firstOrNull()?.takeIf { trial != null && it.priceAmountMicros == 0L }?.billingPeriod,
            offerToken = chosen.offerToken,
        )
    }
}
