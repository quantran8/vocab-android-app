package com.syll.features.journey

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syll.app.UserMessage
import com.syll.domain.journey.JourneyPeriod
import com.syll.domain.journey.LearningJourney
import com.syll.network.VocabApi
import com.syll.network.asApiError
import kotlinx.coroutines.CancellationException
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * iOS `LearningJourneyStore`: Home's week and the detail's periods (backend FRONTEND_FLOW §17). History only: nothing
 * here plans, commits or changes progress. A failed load keeps what was on screen and never turns into zeros.
 */
class LearningJourneyStore(private val api: VocabApi, private val now: () -> Instant = Instant::now) {
    sealed interface Load {
        data object Idle : Load
        data object Loading : Load
        data object Loaded : Load
        data class Failed(val message: UserMessage) : Load
    }

    /** Home's summary: always the current week. */
    var summary by mutableStateOf<LearningJourney?>(null)
        private set
    var summaryLoad by mutableStateOf<Load>(Load.Idle)
        private set
    /** The detail's data per period. A stale entry stays visible while it reloads. */
    var periods by mutableStateOf<Map<JourneyPeriod, LearningJourney>>(emptyMap())
        private set
    var periodLoads by mutableStateOf<Map<JourneyPeriod, Load>>(emptyMap())
        private set

    /** Bumped by [reset] and [invalidate], so an answer for the previous account or an older state is dropped. */
    private var generation = 0
    private var summaryLoadedAt: Instant? = null
    private var periodLoadedAt = mapOf<JourneyPeriod, Instant>()

    /** Loads the week unless it is fresh. `force` after a commit, pull-to-refresh or a goal change. */
    suspend fun refreshSummary(force: Boolean = false) {
        if (!force && !isStale(summaryLoadedAt)) return
        val started = generation
        summaryLoad = Load.Loading
        try {
            val fresh = api.learningJourney(JourneyPeriod.WEEK)
            if (started != generation) return
            val at = now()
            summary = fresh
            periods = periods + (JourneyPeriod.WEEK to fresh)
            summaryLoadedAt = at
            periodLoadedAt = periodLoadedAt + (JourneyPeriod.WEEK to at)
            summaryLoad = Load.Loaded
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (started == generation) summaryLoad = Load.Failed(UserMessage.Api(e.asApiError))
        }
    }

    suspend fun load(period: JourneyPeriod, force: Boolean = false) {
        if (!force && !isStale(periodLoadedAt[period]) && periods[period] != null) return
        val started = generation
        periodLoads = periodLoads + (period to Load.Loading)
        try {
            val fresh = api.learningJourney(period)
            if (started != generation) return
            val at = now()
            periods = periods + (period to fresh)
            periodLoadedAt = periodLoadedAt + (period to at)
            periodLoads = periodLoads + (period to Load.Loaded)
            if (period == JourneyPeriod.WEEK) {
                summary = fresh
                summaryLoadedAt = at
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (started == generation) periodLoads = periodLoads + (period to Load.Failed(UserMessage.Api(e.asApiError)))
        }
    }

    /** After a committed session: everything is stale, but stays on screen until it reloads. */
    fun invalidate() {
        generation += 1
        summaryLoadedAt = null
        periodLoadedAt = emptyMap()
        if (summaryLoad == Load.Loading) summaryLoad = Load.Idle
        periodLoads = periodLoads.mapValues { (_, load) -> if (load == Load.Loading) Load.Idle else load }
    }

    /** Sign-out and account deletion: nothing carries over to the next account. */
    fun reset() {
        generation += 1
        summary = null
        summaryLoad = Load.Idle
        summaryLoadedAt = null
        periods = emptyMap()
        periodLoads = emptyMap()
        periodLoadedAt = emptyMap()
    }

    private fun isStale(loadedAt: Instant?): Boolean {
        loadedAt ?: return true
        val current = now()
        val zone = ZoneId.systemDefault()
        return Duration.between(loadedAt, current) > FRESH_FOR || loadedAt.atZone(zone).toLocalDate() != current.atZone(zone).toLocalDate()
    }

    private companion object {
        /** Reloaded after this long, or once the day changes. */
        val FRESH_FOR: Duration = Duration.ofMinutes(5)
    }
}
