package com.syll.features.customwords

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syll.R
import com.syll.app.UserMessage
import com.syll.domain.customwords.CustomWord
import com.syll.domain.customwords.CustomWordAddition
import com.syll.domain.customwords.CustomWordEdits
import com.syll.domain.customwords.CustomWordLookup
import com.syll.domain.customwords.CustomWordQuery
import com.syll.domain.customwords.ExistingWord
import com.syll.network.ApiError
import com.syll.network.VocabApi
import com.syll.network.asApiError
import com.syll.platform.AudioClipPlaying
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID

/** iOS `CustomWordStore`: adding a word of the learner's own, and the list of words they added. */
class CustomWordStore(
    private val api: VocabApi,
    private val audio: AudioClipPlaying,
    /** Where a word's audio plays from; outlives the sheet. Main thread. */
    private val scope: CoroutineScope,
) {
    sealed interface LookupPhase {
        data object Idle : LookupPhase
        data object Loading : LookupPhase
        data class Result(val lookup: CustomWordLookup) : LookupPhase
        /**
         * [isNew] is false when the word was already on the roadmap. [meaning] is what the learner added, for the
         * confirmation.
         */
        data class Added(val lemma: String, val isNew: Boolean, val ipa: String? = null, val meaning: String? = null) : LookupPhase
        data class Failed(val message: UserMessage, val canRetry: Boolean) : LookupPhase
    }

    sealed interface ListPhase {
        data object Loading : ListPhase
        data object Ready : ListPhase
        data class Failed(val message: UserMessage) : ListPhase
    }

    data class Selection(val id: String, val addition: CustomWordAddition, val meaning: String)

    var phase by mutableStateOf<LookupPhase>(LookupPhase.Idle)
        private set
    /** Why the last add didn't go through. The result stays on screen so the learner can act on it. */
    var notice by mutableStateOf<UserMessage?>(null)
        private set
    var isAdding by mutableStateOf(false)
        private set
    var addedSenseIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var addedMeanings by mutableStateOf<List<String>>(emptyList())
        private set
    /** The word an "other meaning" search started from, which going back returns to. */
    var baseWord by mutableStateOf<ExistingWord?>(null)
        private set
    /** Set once a lookup answers: from then on, lookups and adds show as steps over the word entry. */
    var isReviewing by mutableStateOf(false)
        private set

    var listPhase by mutableStateOf<ListPhase>(ListPhase.Loading)
        private set
    var words by mutableStateOf<List<CustomWord>>(emptyList())
        private set
    /** A failed change to the list. The list itself stays usable. */
    var listError by mutableStateOf<UserMessage?>(null)
        private set
    var busyMeaningIds by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Set by the app so Home's figures follow the roadmap. */
    var onRoadmapChanged: (suspend () -> Unit)? = null

    private var lastQuery: CustomWordQuery? = null
    /** Identifies the current lookup: a slow answer for a closed sheet must not land in the next one. */
    private var lookupToken = UUID.randomUUID()

    val lastContext: String get() = lastQuery?.context.orEmpty()

    // region Lookup and add

    suspend fun lookup(text: String, context: String? = null) {
        notice = null
        val current = phase
        if (context != null && current is LookupPhase.Result && current.lookup is CustomWordLookup.Existing) {
            baseWord = current.lookup.word
        }
        run(CustomWordQuery(text.trim(), context?.trim()))
    }

    suspend fun retryLookup() {
        val query = lastQuery ?: return
        notice = null
        run(query)
    }

    private suspend fun run(query: CustomWordQuery) {
        val token = UUID.randomUUID()
        lookupToken = token
        lastQuery = query
        addedSenseIds = emptySet()
        addedMeanings = emptyList()
        phase = LookupPhase.Loading
        try {
            val result = api.lookupCustomWord(query)
            if (token != lookupToken) return
            phase = LookupPhase.Result(result)
            isReviewing = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (token != lookupToken) return
            val failure = e.asApiError
            phase = LookupPhase.Failed(UserMessage.Api(failure), failure.isRetryable)
        }
    }

    suspend fun add(addition: CustomWordAddition, lemma: String, ipa: String? = null, meaning: String? = null) {
        if (isAdding) return
        isAdding = true
        notice = null
        val token = lookupToken
        try {
            val response = api.addCustomWord(addition)
            onRoadmapChanged?.invoke()
            if (token != lookupToken) return
            phase = LookupPhase.Added(lemma, response.added, ipa, meaning)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (token != lookupToken) return
            recover(e.asApiError)
        } finally {
            isAdding = false
        }
    }

    /** Successful senses stay closed on a partial failure; retries submit only the remainder. */
    suspend fun addSelected(selections: List<Selection>, lemma: String) {
        if (isAdding || selections.isEmpty()) return
        isAdding = true
        notice = null
        val token = lookupToken
        var failed = false
        try {
            for (selection in selections) {
                if (selection.id in addedSenseIds) continue
                if (token != lookupToken) return
                try {
                    api.addCustomWord(selection.addition)
                    if (token != lookupToken) return
                    addedSenseIds = addedSenseIds + selection.id
                    addedMeanings = addedMeanings + selection.meaning
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (token != lookupToken) return
                    notice = UserMessage.Api(e.asApiError)
                    failed = true
                }
            }
            onRoadmapChanged?.invoke()
            if (token != lookupToken) return
            if (!failed) phase = LookupPhase.Added(lemma, isNew = true, meaning = addedMeanings.firstOrNull())
        } finally {
            isAdding = false
        }
    }

    /** A stale draft is the client's cue to look again, so the learner never sees it as an error. */
    private suspend fun recover(failure: ApiError) {
        when (failure.code) {
            "WORD_ALREADY_EXISTS", "CUSTOM_WORD_INVALID" -> retryLookup()
            "CUSTOM_WORD_UNAVAILABLE" -> {
                retryLookup()
                notice = UserMessage.Api(failure)
            }
            else -> notice = UserMessage.Api(failure)
        }
    }

    fun resetLookup() {
        lookupToken = UUID.randomUUID()
        lastQuery = null
        baseWord = null
        isReviewing = false
        phase = LookupPhase.Idle
        notice = null
        addedSenseIds = emptySet()
        addedMeanings = emptyList()
    }

    fun goBack() {
        val word = baseWord ?: return resetLookup()
        lookupToken = UUID.randomUUID()
        lastQuery = CustomWordQuery(word.lemma)
        baseWord = null
        phase = LookupPhase.Result(CustomWordLookup.Existing(word))
        notice = null
    }

    // endregion

    // region The learner's list

    suspend fun loadWords() {
        try {
            words = api.customWords()
            listPhase = ListPhase.Ready
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = UserMessage.Api(e.asApiError)
            if (words.isEmpty()) listPhase = ListPhase.Failed(message) else listError = message
        }
    }

    suspend fun remove(word: CustomWord) {
        change(word, treatMissingAsDone = true) { api.removeCustomWord(word.meaningId) }
        onRoadmapChanged?.invoke()
    }

    suspend fun saveOverride(edits: CustomWordEdits, word: CustomWord) {
        change(word) { api.overrideCustomWord(word.meaningId, edits) }
    }

    suspend fun restoreOriginal(word: CustomWord) {
        change(word) { api.clearCustomWordOverride(word.meaningId) }
    }

    /** A 404 means another device already took the word off, so the list is what needs fixing. */
    private suspend fun change(word: CustomWord, treatMissingAsDone: Boolean = false, request: suspend () -> Unit) {
        busyMeaningIds = busyMeaningIds + word.meaningId
        listError = null
        try {
            try {
                request()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val failure = e.asApiError
                if (failure.status == 404) {
                    if (!treatMissingAsDone) listError = UserMessage.Resource(R.string.this_word_is_no_longer_on_your_learning_path)
                } else {
                    listError = UserMessage.Api(failure)
                    return
                }
            }
            loadWords()
        } finally {
            busyMeaningIds = busyMeaningIds - word.meaningId
        }
    }

    fun play(word: CustomWord) {
        val url = word.audioUrl ?: return
        scope.launch { audio.play(url) }
    }

    /** Whether a word's recorded audio is playing, for the speaker waves. */
    val isPlayingAudio: Boolean get() = audio.isPlaying

    fun stopAudio() = audio.stop()

    fun reset() {
        resetLookup()
        audio.stop()
        listPhase = ListPhase.Loading
        words = emptyList()
        listError = null
        busyMeaningIds = emptySet()
    }

    // endregion
}
