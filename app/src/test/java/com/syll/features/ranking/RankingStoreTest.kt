package com.syll.features.ranking

import com.syll.domain.ranking.Leaderboard
import com.syll.domain.ranking.LeaderboardRow
import com.syll.domain.ranking.LeaderboardView
import com.syll.domain.ranking.MyRanking
import com.syll.domain.ranking.RankingMeta
import com.syll.domain.ranking.RankingPosition
import com.syll.domain.ranking.RankingPositionKind
import com.syll.domain.ranking.ReportReason
import com.syll.domain.ranking.TierBadge
import com.syll.domain.ranking.TierDetailSummary
import com.syll.domain.ranking.TierKey
import com.syll.features.auth.StubVocabApi
import com.syll.network.SettingsUpdate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.Locale

/** The store part of iOS `RankingTests`. */
class RankingStoreTest {
    private val api = StubVocabApi()
    private val weekEnd = Instant.ofEpochSecond(1_763_251_200)

    private fun meta(enabled: Boolean) = RankingMeta(
        weekStart = "2026-11-09", weekEndsAt = weekEnd, rankingEnabled = enabled,
        avatarKeys = listOf("syllie_calm"), reportReasons = listOf("offensive_name", "other"),
    )

    private fun mine(listed: Boolean) = MyRanking(
        weekStart = "2026-11-09", weekEndsAt = weekEnd, weeklyLP = if (listed) 30 else 0, listed = listed,
        position = if (listed) RankingPosition(RankingPositionKind.RANK, rank = 2) else null,
        tier = TierDetailSummary(level = 1, key = TierKey.FIRST_STEPS, lifetimeLP = 45, lifetimeActiveDays = 2),
    )

    private fun board(rows: List<LeaderboardRow>) = Leaderboard("2026-11-09", weekEnd, rows)

    @Test
    fun unlistedLearnerLoadsTheTopBoardOnly() = runBlocking {
        api.rankingMetaResult = Result.success(meta(enabled = true))
        api.myRankingResult = Result.success(mine(listed = false))
        api.leaderboards[LeaderboardView.TOP] = Result.success(
            board(listOf(LeaderboardRow(1, "a", "syllie_calm", 50, TierBadge(0, null), isMe = false))),
        )
        val store = RankingStore(api)
        store.refreshRanking()
        assertEquals(listOf(LeaderboardView.TOP), api.leaderboardRequests)
        assertEquals(1, store.top.size)
        assertEquals(emptyList<LeaderboardRow>(), store.around)
        assertFalse(store.isBoardClosed)
        assertEquals(listOf(ReportReason.OFFENSIVE_NAME, ReportReason.OTHER), store.reportReasons)
    }

    @Test
    fun closedGateSkipsTheBoardButKeepsTheWeek() = runBlocking {
        api.rankingMetaResult = Result.success(meta(enabled = false))
        api.myRankingResult = Result.success(mine(listed = false))
        val store = RankingStore(api)
        store.refreshRanking()
        assertTrue(store.isBoardClosed)
        assertEquals(emptyList<LeaderboardView>(), api.leaderboardRequests)
        assertEquals(1, store.mine?.tier?.level)
        assertNull(store.error)
    }

    @Test
    fun seeingTheIntroNeverOptsIn() = runBlocking {
        val store = RankingStore(api)
        var profileReloads = 0
        store.onProfileChanged = { profileReloads += 1 }
        store.markIntroSeen()
        assertEquals(1, api.introSeenCalls)
        assertEquals(emptyList<SettingsUpdate>(), api.settingsUpdates)

        api.rankingMetaResult = Result.success(meta(enabled = true))
        api.myRankingResult = Result.success(mine(listed = true))
        api.leaderboards[LeaderboardView.TOP] = Result.success(board(emptyList()))
        api.leaderboards[LeaderboardView.AROUND] = Result.success(board(emptyList()))
        store.setVisible(true)
        assertEquals(listOf(SettingsUpdate(leaderboardVisible = true)), api.settingsUpdates)
        assertEquals(setOf(LeaderboardView.AROUND, LeaderboardView.TOP), api.leaderboardRequests.toSet())
        assertEquals(2, profileReloads)
    }

    @Test
    fun failedLoadKeepsWhatWasShown() = runBlocking {
        api.rankingMetaResult = Result.success(meta(enabled = false))
        api.myRankingResult = Result.success(mine(listed = false))
        val store = RankingStore(api)
        store.refreshRanking()
        api.myRankingResult = Result.failure(com.syll.network.ApiError.Transport)
        store.refreshRanking()
        assertNotNull(store.mine)
        assertNotNull(store.error)
    }

    @Test
    fun weekLabelReadsInUtc() {
        assertEquals("09/11–15/11", WeekLabel.range("2026-11-09", Locale.UK) { "dd/MM" })
        assertNull(WeekLabel.range("not-a-date", Locale.UK) { "dd/MM" })
    }
}
