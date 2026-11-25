package com.syll.features.home

import com.syll.network.LearnHome
import com.syll.network.NewWordOverrideMode
import com.syll.network.NewWordReason
import com.syll.network.ScopeStatus
import java.time.Instant

/** iOS `DailyLessonStatus`: what the Daily Lesson card says, from `GET /me/learn-home`. */
data class DailyLessonStatus(
    val newWords: Int,
    val backlog: Int,
    /** Reviews the next daily session holds: the backlog up to the planner's cap. */
    val sessionReviews: Int,
    val scope: Scope,
    val reason: Reason?,
) {
    sealed interface Scope {
        data object Ok : Scope
        /** Less than one week of new words left. */
        data class Low(val remaining: Int, val sessions: Int) : Scope
        data class ExhaustedWithCustom(val custom: Int) : Scope
        data object ExhaustedNoCustom : Scope
    }

    sealed interface Reason {
        data class UserOverride(val until: Instant?) : Reason
        data object AutoBalanced : Reason
        /** Fewer new words than chosen because that's all the scope holds. */
        data object ScopeLimited : Reason
        /** The free plan allows only [remaining] more new words (of [max]). */
        data class FreeLimit(val remaining: Int, val max: Int) : Reason
    }

    /** The free plan's limit when its new words are all used: the card offers reviews and Premium instead. */
    val freeWordsUsedUp: Int?
        get() = (reason as? Reason.FreeLimit)?.takeIf { newWords == 0 }?.max

    companion object {
        /** Without the newer blocks (an older backend) it shows the [goal] as before. */
        fun of(home: LearnHome?, goal: Int): DailyLessonStatus {
            val load = home?.reviewLoad
            val newWords = load?.effectiveNewPerSession ?: goal
            val backlog = load?.backlog ?: home?.dueCount ?: 0
            val due = home?.dueCount ?: backlog
            val sessionReviews = load?.let { minOf(due, it.dueCap) } ?: due
            val scopeState = home?.scope
            val scope = when (scopeState?.status) {
                ScopeStatus.EXHAUSTED -> if (scopeState.pendingCustomMeanings > 0) {
                    Scope.ExhaustedWithCustom(scopeState.pendingCustomMeanings)
                } else {
                    Scope.ExhaustedNoCustom
                }
                ScopeStatus.LOW -> Scope.Low(scopeState.remainingNewMeanings, scopeState.sessionsLeftEstimate ?: 1)
                else -> Scope.Ok
            }
            val reason = when (load?.newWordReason) {
                NewWordReason.USER_OVERRIDE -> Reason.UserOverride(
                    load.override?.takeIf { it.mode == NewWordOverrideMode.UNTIL_DATE }?.until,
                )
                NewWordReason.AUTO_BALANCED -> Reason.AutoBalanced
                NewWordReason.SCOPE_LIMITED -> Reason.ScopeLimited
                NewWordReason.FREE_LIMIT -> Reason.FreeLimit(
                    remaining = home.freeAllowance?.remaining ?: newWords,
                    max = home.freeAllowance?.max ?: 0,
                )
                else -> null
            }
            return DailyLessonStatus(newWords, backlog, sessionReviews, scope, reason)
        }
    }
}
