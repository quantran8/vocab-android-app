package com.syll.domain.ranking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** The pure parts of iOS `RankingTests`. */
class RankingModelsTest {
    @Test
    fun oldBackendLeavesNoEffort() {
        val effort = SessionEffort().adding(CommitLP(earned = 0, weeklyLP = 0, lifetimeLP = 0), tier = null)
        assertNull(effort.celebratedTierUp)
        assertEquals(0, effort.earned)
    }

    @Test
    fun effortAddsUpCommitsAndCelebratesTheHighestTier() {
        val unlocked = Instant.ofEpochSecond(1_762_916_400)
        val effort = SessionEffort()
            .adding(
                CommitLP(4, LPBreakdown(dueReviewLP = 4), weeklyLP = 39, lifetimeLP = 39),
                CommitTier(level = 0, key = null, lifetimeActiveDays = 1),
            )
            .adding(
                CommitLP(5, LPBreakdown(newMeaningLP = 5), weeklyLP = 44, lifetimeLP = 44),
                CommitTier(1, TierKey.FIRST_STEPS, 1, tierUps = listOf(TierUp(1, TierKey.FIRST_STEPS, unlocked))),
            )
        assertEquals(9, effort.earned)
        assertEquals(LPBreakdown(newMeaningLP = 5, dueReviewLP = 4), effort.breakdown)
        assertEquals(44, effort.lifetimeLP)
        assertEquals(TierKey.FIRST_STEPS, effort.celebratedTierUp?.key)
    }

    @Test
    fun usernameRuleMatchesTheServer() {
        assertTrue(PublicName.isWellFormed("alan.lee_7"))
        assertFalse(PublicName.isWellFormed("ab"))
        assertFalse(PublicName.isWellFormed("1abc"))
        assertFalse(PublicName.isWellFormed("a..b"))
        assertFalse(PublicName.isWellFormed("a".repeat(21)))
    }
}
