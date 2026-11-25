package com.syll.features.home

import com.syll.network.FreeAllowance
import com.syll.network.LearnHome
import com.syll.network.NewWordOverride
import com.syll.network.NewWordOverrideMode
import com.syll.domain.NewWordReason
import com.syll.network.ReviewLoad
import com.syll.network.ScopeState
import com.syll.network.ScopeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** The Home-card part of iOS `ScopeReviewLoadTests`. */
class DailyLessonStatusTest {
    private fun home(scope: ScopeState? = null, load: ReviewLoad? = null, free: FreeAllowance? = null) = LearnHome(
        dueCount = load?.backlog ?: 0, checkpointAvailable = 0, checkpointSessionsDone = 0, checkpointSessionsRequired = 7,
        learnedTotal = 0, scope = scope, reviewLoad = load, freeAllowance = free,
    )

    private fun load(backlog: Int = 0, effective: Int = 5, reason: NewWordReason = NewWordReason.GOAL, override: NewWordOverride? = null) =
        ReviewLoad(backlog, dueCap = 10, effectiveNewPerSession = effective, newWordReason = reason, override = override,
            autoBalance = false, promptChoice = false)

    @Test
    fun homeStatusFromAnOlderBackendShowsTheGoal() {
        val status = DailyLessonStatus.of(home(), goal = 5)
        assertEquals(5, status.newWords)
        assertEquals(DailyLessonStatus.Scope.Ok, status.scope)
        assertNull(status.reason)
    }

    @Test
    fun exhaustedScopeWithAndWithoutOwnWords() {
        fun exhausted(custom: Int) = ScopeState(ScopeStatus.EXHAUSTED, 0, custom, null, emptyList())
        assertEquals(DailyLessonStatus.Scope.ExhaustedNoCustom, DailyLessonStatus.of(home(scope = exhausted(0)), 5).scope)
        assertEquals(DailyLessonStatus.Scope.ExhaustedWithCustom(3), DailyLessonStatus.of(home(scope = exhausted(3)), 5).scope)
    }

    @Test
    fun lowScopeAndAutoBalanceReason() {
        val low = ScopeState(ScopeStatus.LOW, 12, 0, 3, emptyList())
        val status = DailyLessonStatus.of(home(scope = low, load = load(backlog = 34, effective = 2, reason = NewWordReason.AUTO_BALANCED)), 5)
        assertEquals(DailyLessonStatus.Scope.Low(12, 3), status.scope)
        assertEquals(DailyLessonStatus.Reason.AutoBalanced, status.reason)
        assertEquals(2, status.newWords)
        assertEquals(34, status.backlog)
        assertEquals("Capped at the planner's dueCap", 10, status.sessionReviews)
    }

    @Test
    fun sessionReviewsBelowTheCapAreTheWholeBacklog() {
        assertEquals(4, DailyLessonStatus.of(home(load = load(backlog = 4)), 5).sessionReviews)
    }

    @Test
    fun aLoweredGoalNamesItsEndOnlyForADate() {
        val until = Instant.parse("2026-11-15T16:59:59Z")
        val byDate = load(reason = NewWordReason.USER_OVERRIDE, override = NewWordOverride(2, NewWordOverrideMode.UNTIL_DATE, until))
        val byBacklog = load(reason = NewWordReason.USER_OVERRIDE, override = NewWordOverride(2, NewWordOverrideMode.UNTIL_BACKLOG_CLEAR, until))
        assertEquals(DailyLessonStatus.Reason.UserOverride(until), DailyLessonStatus.of(home(load = byDate), 5).reason)
        assertEquals(DailyLessonStatus.Reason.UserOverride(null), DailyLessonStatus.of(home(load = byBacklog), 5).reason)
    }

    @Test
    fun freeWordsUsedUpOnlyWhenNoNewWordsAreLeft() {
        val free = FreeAllowance(used = 20, max = 20, remaining = 0)
        val usedUp = DailyLessonStatus.of(home(load = load(effective = 0, reason = NewWordReason.FREE_LIMIT), free = free), 5)
        assertEquals(20, usedUp.freeWordsUsedUp)
        val someLeft = DailyLessonStatus.of(
            home(load = load(effective = 3, reason = NewWordReason.FREE_LIMIT), free = FreeAllowance(17, 20, 3)), 5,
        )
        assertEquals(DailyLessonStatus.Reason.FreeLimit(3, 20), someLeft.reason)
        assertNull(someLeft.freeWordsUsedUp)
    }
}
