package com.syll.features.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * iOS `AdMobInterstitials`: Google Mobile Ads interstitials. Order on first use: the UMP consent form where the law
 * asks for it, then the SDK starts. Without consent no ad is requested. The forms and the ad need an activity, which
 * `MainActivity` attaches while it is alive. The AdMob app ID is in the manifest (`-PadmobAppId`), the unit here.
 */
class AdMobInterstitials(context: Context, private val unitId: String) : InterstitialAdProviding {
    private val context = context.applicationContext
    private val consent = UserMessagingPlatform.getConsentInformation(this.context)
    private var activity: Activity? = null
    private var ad: InterstitialAd? = null
    private var isLoading = false
    private var isPrepared = false
    private var isStarted = false

    fun attach(activity: Activity) {
        this.activity = activity
    }

    fun detach(activity: Activity) {
        if (this.activity === activity) this.activity = null
    }

    override suspend fun prepare() {
        if (isPrepared) return
        val host = activity ?: return
        isPrepared = true
        suspendCancellableCoroutine { continuation ->
            consent.requestConsentInfoUpdate(
                host,
                ConsentRequestParameters.Builder().build(),
                { if (continuation.isActive) continuation.resume(Unit) },
                { if (continuation.isActive) continuation.resume(Unit) },
            )
        }
        suspendCancellableCoroutine { continuation ->
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(host) { if (continuation.isActive) continuation.resume(Unit) }
        }
        if (!consent.canRequestAds()) return
        suspendCancellableCoroutine { continuation ->
            MobileAds.initialize(context) { if (continuation.isActive) continuation.resume(Unit) }
        }
        isStarted = true
    }

    override suspend fun preload() {
        prepare()
        if (!isStarted || !consent.canRequestAds() || ad != null || isLoading) return
        isLoading = true
        try {
            ad = suspendCancellableCoroutine { continuation ->
                InterstitialAd.load(context, unitId, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(loaded: InterstitialAd) {
                        if (continuation.isActive) continuation.resume(loaded)
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                })
            }
        } finally {
            isLoading = false
        }
    }

    override suspend fun present(): Boolean {
        val loaded = ad ?: return false
        val host = activity ?: return false
        ad = null
        val dismissed = CompletableDeferred<Unit>()
        loaded.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                dismissed.complete(Unit)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                dismissed.complete(Unit)
            }
        }
        loaded.show(host)
        dismissed.await()
        return true
    }
}
