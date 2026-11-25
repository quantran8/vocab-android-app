package com.syll.features.challenge

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syll.app.UserMessage
import com.syll.domain.challenge.ChallengeChapter
import com.syll.domain.challenge.ChallengeHomeSummary
import com.syll.domain.challenge.ChallengeLevel
import com.syll.domain.challenge.ChallengeMap
import com.syll.domain.challenge.ChallengeNodeRef
import com.syll.network.VocabApi
import com.syll.network.asApiError
import kotlinx.coroutines.CancellationException

/**
 * iOS `ChallengeMapStore`: the server's Challenge Map. Refreshed when the shell appears (so after every Daily lesson
 * too), after every committed run and on pull-to-refresh. What's on screen is only a cache: nothing unlocks from it.
 */
class ChallengeMapStore(private val api: VocabApi) {
    var map by mutableStateOf<ChallengeMap?>(null)
        private set
    /** Set when the map couldn't load; whatever was on screen stays. */
    var error by mutableStateOf<UserMessage?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    /** Nodes the last refresh opened that weren't open before: they animate in once. */
    var newlyUnlocked by mutableStateOf<Set<ChallengeNodeRef>>(emptySet())
        private set

    /** Bumped by [reset], so an answer for the previous account is dropped. */
    private var generation = 0

    val summary: ChallengeHomeSummary? get() = map?.let(ChallengeHomeSummary::from)

    fun chapter(chapterNo: Int): ChallengeChapter? = map?.chapters?.firstOrNull { it.chapterNo == chapterNo }

    fun level(node: ChallengeNodeRef): ChallengeLevel? = chapter(node.chapterNo)?.levels?.firstOrNull { it.levelNo == node.levelNo }

    suspend fun refresh() {
        val started = generation
        isLoading = true
        try {
            val fresh = api.challengeMap()
            if (started != generation) return
            map?.let { old -> newlyUnlocked = playableNodes(fresh) - playableNodes(old) }
            map = fresh
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (started == generation) error = UserMessage.Api(e.asApiError)
        } finally {
            if (started == generation) isLoading = false
        }
    }

    /** Called once the unlock animation has played. */
    fun consumeUnlock(node: ChallengeNodeRef) {
        newlyUnlocked = newlyUnlocked - node
    }

    fun reset() {
        generation += 1
        map = null
        error = null
        isLoading = false
        newlyUnlocked = emptySet()
    }

    private fun playableNodes(map: ChallengeMap): Set<ChallengeNodeRef> =
        map.chapters.flatMap { chapter ->
            chapter.levels.filter { it.isPlayable }.map { ChallengeNodeRef(chapter.chapterNo, it.levelNo) }
        }.toSet()
}
