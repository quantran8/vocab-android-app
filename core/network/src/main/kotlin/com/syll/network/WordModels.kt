package com.syll.network

import com.syll.domain.Iso8601InstantSerializer
import com.syll.domain.practice.AudioClip
import com.syll.domain.practice.LearnCard
import com.syll.domain.practice.SpeakingStatus
import kotlinx.serialization.Serializable
import java.time.Instant

// My Words (backend FRONTEND_FLOW: `GET /me/words`, `GET /me/words/stats`, `GET /words/:id`).

/** iOS `WordMeaning`: one learned meaning in a library row. */
@Serializable
data class WordMeaning(
    val meaningId: String,
    val meaning: Sense,
    @Serializable(with = Iso8601InstantSerializer::class) val learnedAt: Instant? = null,
    @Serializable(with = Iso8601InstantSerializer::class) val nextReviewAt: Instant? = null,
) {
    @Serializable
    data class Sense(
        val partOfSpeech: String,
        val text: String,
        val ipa: String? = null,
        val definition: String? = null,
        val audio: AudioClip? = null,
        val examples: List<LearnCard.Example>? = null,
    )
}

/** iOS `WordProgress`: one library row. Memory and speaking load with [WordDetail] when a word opens. */
@Serializable
data class WordProgress(
    val wordId: String,
    val lemma: String,
    /** Learned meanings only; [meaningCount] counts every meaning the word has. Absent from older servers. */
    val meanings: List<WordMeaning> = emptyList(),
    val meaningCount: Int? = null,
    /** Across every meaning, learned or not. Absent from older servers. */
    val ipaVaries: Boolean? = null,
) {
    val id: String get() = wordId

    /** Null when the meanings are pronounced differently (e.g. "read" /riːd/ vs /rɛd/). */
    val ipa: String?
        get() {
            if (ipaVaries == true) return null
            val pronunciations = meanings.map { it.meaning.ipa.orEmpty() }.toSet()
            return pronunciations.singleOrNull()?.takeIf { it.isNotEmpty() }
        }
}

/** iOS `WordStats`: library-wide figures, independent of paging and search. */
@Serializable
data class WordStats(
    val learnedWords: Int,
    val needPracticeWords: Int,
    /** Words whose default pronunciation is fluent; null from an older backend. */
    val fluentWords: Int? = null,
)

/** iOS `MeaningSpeaking`. */
@Serializable
data class MeaningSpeaking(
    val status: SpeakingStatus,
    /** Azure scores only; null when every take was scored on the device. */
    val lastScore: Double? = null,
    val bestScore: Double? = null,
    @Serializable(with = Iso8601InstantSerializer::class) val lastAssessedAt: Instant? = null,
)

/** iOS `MeaningMemory`: how well a learned meaning holds now (FSRS). */
@Serializable
data class MeaningMemory(
    /** Days until recall drops to 90%. */
    val stabilityDays: Double,
    /** Chance of recalling the meaning right now, 0…1. */
    val retrievability: Double,
    @Serializable(with = Iso8601InstantSerializer::class) val nextReviewAt: Instant? = null,
)

/** iOS `WordDetail`: every meaning of the word, including the ones not learned yet (`learnedAt == null`). */
@Serializable
data class WordDetail(val wordId: String, val lemma: String, val meanings: List<Meaning>) {
    @Serializable
    data class Meaning(
        val meaningId: String,
        val meaning: WordMeaning.Sense,
        @Serializable(with = Iso8601InstantSerializer::class) val learnedAt: Instant? = null,
        /** Null until learned, and from an older backend. */
        val memory: MeaningMemory? = null,
        /** Null when the meaning has no pronunciation to record, and from an older backend. */
        val speaking: MeaningSpeaking? = null,
        /** Missed recently, the latest answer included (the Weak Words definition). */
        val needsPractice: Boolean? = null,
        /** Shows the learner's own edit, which can't be reported. Null from an older backend. */
        val edited: Boolean? = null,
    )
}

@Serializable
internal data class WordsResponse(val words: List<WordProgress>)
