package com.syll.features.ads

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.syll.data.KeyValueStore
import com.syll.features.main.LearnerStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** iOS `InterstitialAdProviding`: hides the ad SDK, so stores, tests and previews never load it. */
interface InterstitialAdProviding {
    /** Consent (UMP) and SDK start-up. Safe to call more than once. */
    suspend fun prepare()
    /** Loads the next ad in the background, if none is waiting. */
    suspend fun preload()
    /** Presents a loaded ad and returns once it is dismissed. False when none was ready. */
    suspend fun present(): Boolean
}

object NoInterstitialAds : InterstitialAdProviding {
    override suspend fun prepare() {}
    override suspend fun preload() {}
    override suspend fun present() = false
}

/**
 * iOS `AdStore`: free learners (`plan.limits`) see an interstitial after every `features.adEverySessions` finished
 * sessions (backend FRONTEND_FLOW §16), never during one: the shell asks once the session screen is gone. The count
 * lives on the device and survives relaunches; an ad that wasn't ready keeps it due for next time.
 */
class AdStore(
    private val provider: InterstitialAdProviding,
    private val learner: LearnerStore,
    private val scope: CoroutineScope,
    private val storage: KeyValueStore? = null,
) {
    var sessionsSinceAd by mutableIntStateOf(storage?.getInt(COUNTER_KEY) ?: 0)
        private set
    /** Ads follow a session the learner just finished, never the app opening. */
    private var finishedSinceLaunch = false

    /** Sessions between ads for this learner; null when they see none (Premium, ads off, an older backend). */
    val interval: Int?
        get() {
            if (learner.freeLimits == null) return null
            return learner.profile?.features?.adEverySessions?.takeIf { it > 0 }
        }

    val isDue: Boolean get() = interval?.let { sessionsSinceAd >= it } ?: false

    /** Once the profile is known. Asks for consent early, so the first ad isn't held up by the forms. */
    suspend fun prepare() {
        val interval = interval ?: return
        provider.prepare()
        if (sessionsSinceAd + 1 >= interval) provider.preload()
    }

    /** A session was saved (any mode but the Lab). The ad loads one session early so it is ready on time. */
    fun sessionFinished() {
        val interval = interval ?: return
        sessionsSinceAd += 1
        finishedSinceLaunch = true
        save()
        if (sessionsSinceAd + 1 >= interval) scope.launch { provider.preload() }
    }

    /** Called when the shell reappears after a session screen closed. */
    suspend fun presentIfDue() {
        if (!isDue || !finishedSinceLaunch) return
        if (!provider.present()) {
            // Not loaded yet (no fill, offline): try again after the next session.
            scope.launch { provider.preload() }
            return
        }
        sessionsSinceAd = 0
        save()
    }

    private fun save() {
        storage?.putInt(COUNTER_KEY, sessionsSinceAd)
    }

    private companion object {
        const val COUNTER_KEY = "ads.sessionsSinceAd"
    }
}
