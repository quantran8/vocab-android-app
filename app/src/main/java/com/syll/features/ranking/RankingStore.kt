package com.syll.features.ranking

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syll.app.UserMessage
import com.syll.domain.ranking.LeaderboardRow
import com.syll.domain.ranking.LeaderboardView
import com.syll.domain.ranking.MyRanking
import com.syll.domain.ranking.PublicProfile
import com.syll.domain.ranking.PublicProfileUpdate
import com.syll.domain.ranking.RankingMeta
import com.syll.domain.ranking.ReportReason
import com.syll.domain.ranking.TierDetail
import com.syll.network.SettingsUpdate
import com.syll.network.VocabApi
import com.syll.network.asApiError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * iOS `RankingStore`: lifetime Tier and the weekly leaderboard (FRONTEND_FLOW §14). Everything here is a cache of the
 * server: LP is only ever earned by a session commit, and Tier never goes down.
 */
class RankingStore(private val api: VocabApi) {
    var meta by mutableStateOf<RankingMeta?>(null)
        private set
    var mine by mutableStateOf<MyRanking?>(null)
        private set
    var tier by mutableStateOf<TierDetail?>(null)
        private set
    var top by mutableStateOf<List<LeaderboardRow>>(emptyList())
        private set
    var around by mutableStateOf<List<LeaderboardRow>>(emptyList())
        private set
    /** Set when the last load failed; whatever was on screen stays. */
    var error by mutableStateOf<UserMessage?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    /** The server's launch gate is closed: Tier and LP still work, the board does not. */
    var isBoardClosed by mutableStateOf(false)
        private set

    /** The profile holds the username, avatar, opt-in and the Tier summary, so it reloads after any change here. */
    var onProfileChanged: suspend () -> Unit = {}

    /** Bumped by [reset], so an answer for the previous account is dropped. */
    private var generation = 0

    /** Avatar allowlist, report reasons and the launch gate, without loading the board. */
    suspend fun loadMeta() {
        if (meta != null) return
        val started = generation
        val fetched = try {
            api.rankingMeta()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return
        }
        if (started != generation) return
        meta = fetched
        isBoardClosed = !fetched.rankingEnabled
    }

    suspend fun refreshTier() {
        val started = generation
        try {
            val detail = api.tierDetail()
            if (started == generation) tier = detail
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (started == generation) error = UserMessage.Api(e.asApiError)
        }
    }

    /** The board views only load when the gate is open, and "around me" only for a listed learner. */
    suspend fun refreshRanking() {
        val started = generation
        isLoading = true
        try {
            coroutineScope {
                val metaCall = async { api.rankingMeta() }
                val mineCall = async { api.myRanking() }
                val fetchedMeta = metaCall.await()
                val fetchedMine = mineCall.await()
                if (started != generation) return@coroutineScope
                meta = fetchedMeta
                mine = fetchedMine
                isBoardClosed = !fetchedMeta.rankingEnabled
                if (fetchedMeta.rankingEnabled) {
                    val topCall = async { api.leaderboard(LeaderboardView.TOP) }
                    val aroundCall = async { if (fetchedMine.listed) api.leaderboard(LeaderboardView.AROUND).rows else emptyList() }
                    val topRows = topCall.await().rows
                    val aroundRows = aroundCall.await()
                    if (started != generation) return@coroutineScope
                    top = topRows
                    around = aroundRows
                } else {
                    top = emptyList()
                    around = emptyList()
                }
                error = null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (started != generation) return
            val apiError = e.asApiError
            if (apiError.code == "RANKING_DISABLED") {
                isBoardClosed = true
                top = emptyList()
                around = emptyList()
            } else {
                error = UserMessage.Api(apiError)
            }
        } finally {
            if (started == generation) isLoading = false
        }
    }

    /** Opt-in or opt-out. Only an explicit choice calls this, never dismissing the intro. */
    suspend fun setVisible(visible: Boolean) {
        api.updateSettings(SettingsUpdate(leaderboardVisible = visible))
        coroutineScope {
            launch { onProfileChanged() }
            launch { refreshRanking() }
        }
    }

    /** Records that the intro was shown; it does not opt the learner in. */
    suspend fun markIntroSeen() {
        try {
            api.markRankingIntroSeen()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
        onProfileChanged()
    }

    suspend fun updateProfile(username: String?, avatarKey: String?): PublicProfile {
        val saved = api.updatePublicProfile(PublicProfileUpdate(username, avatarKey))
        onProfileChanged()
        if (mine != null) refreshRanking()
        return saved
    }

    suspend fun report(username: String, reason: ReportReason) = api.reportLearner(username, reason)

    /** Reasons the server accepts, in the client's order. */
    val reportReasons: List<ReportReason>
        get() {
            val allowed = meta?.reportReasons ?: return ReportReason.entries
            return ReportReason.entries.filter { it.raw in allowed }
        }

    val avatarKeys: List<String> get() = meta?.avatarKeys ?: emptyList()

    fun reset() {
        generation += 1
        meta = null
        mine = null
        tier = null
        top = emptyList()
        around = emptyList()
        error = null
        isLoading = false
        isBoardClosed = false
    }
}
