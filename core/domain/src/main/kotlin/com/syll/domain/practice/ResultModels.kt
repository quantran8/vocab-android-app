package com.syll.domain.practice

import com.syll.domain.Iso8601InstantSerializer
import com.syll.domain.LenientEnumSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import java.time.Instant

// Results-screen blocks of a commit summary (backend FRONTEND_FLOW §15). Every field is optional: sessions committed
// by an older backend replay without them.

/** A pronunciation's speaking progress. `NOT_TRIED`: no recording of it has been scored yet. */
@Serializable(with = SpeakingStatus.Serializer::class)
enum class SpeakingStatus(val raw: String) {
    NOT_TRIED("not_tried"), PRACTICING("practicing"), FLUENT("fluent"), UNKNOWN("unknown");

    object Serializer : LenientEnumSerializer<SpeakingStatus>("SpeakingStatus", entries.toTypedArray(), { it.raw }, UNKNOWN)
}

/** The skill a result item is about. An unknown value hides the line that would name it. */
@Serializable(with = ResultSkill.Serializer::class)
enum class ResultSkill(val raw: String) {
    SPELLING("spelling"), LISTENING("listening"), SPEAKING("speaking"), UNKNOWN("unknown");

    /** Sessions saved by an older backend say `writing` (now spelling). */
    object Serializer : LenientEnumSerializer<ResultSkill>(
        "ResultSkill", entries.toTypedArray(), { it.raw }, UNKNOWN,
    ) {
        override fun deserialize(decoder: Decoder): ResultSkill {
            val raw = decoder.decodeString()
            return if (raw == "writing") SPELLING else entries.firstOrNull { it.raw == raw && it != UNKNOWN } ?: UNKNOWN
        }
    }
}

/** Why a word is in a weaker group: the answer that decided it. Unknown values show no reason. */
@Serializable(with = ResultCause.Serializer::class)
enum class ResultCause(val raw: String) {
    HINTED("hinted"), RETRIED("retried"), PRIMED("primed"),
    /** Sent by an older backend only: a correct pick now counts as remembered. */
    RECOGNIZED_ONLY("recognized_only"),
    REVEALED("revealed"), OUT_OF_TRIES("out_of_tries"), WRONG_CHOICE("wrong_choice"), NOT_PASSED("not_passed"),
    UNKNOWN("unknown");

    object Serializer : LenientEnumSerializer<ResultCause>("ResultCause", entries.toTypedArray(), { it.raw }, UNKNOWN)
}

/** The band of a scored recording's overall score. The server picks it. */
@Serializable(with = PronunciationBand.Serializer::class)
enum class PronunciationBand(val raw: String) {
    EXCELLENT("excellent"), GOOD("good"), FAIR("fair"), POOR("poor"), UNKNOWN("unknown");

    object Serializer : LenientEnumSerializer<PronunciationBand>("PronunciationBand", entries.toTypedArray(), { it.raw }, UNKNOWN)

    companion object {
        /** Only for a backend that sends a score without a band. `GOOD` starts at the backend's pass mark (80). */
        fun fallback(score: Double): PronunciationBand = when {
            score >= 90 -> EXCELLENT
            score >= 80 -> GOOD
            score >= 60 -> FAIR
            else -> POOR
        }
    }
}

/** One word in a group. Which fields are set depends on the block. */
@Serializable
data class ResultItem(
    val meaningId: String,
    val wordId: String? = null,
    val lemma: String = "",
    val gloss: String = "",
    @Serializable(with = Iso8601InstantSerializer::class) val nextReviewAt: Instant? = null,
    /** Review items only. */
    @Serializable(with = Iso8601InstantSerializer::class) val learnedAt: Instant? = null,
    /** Weaker review and new-word items: what happened, and in which skill. */
    val cause: ResultCause? = null,
    val struggledAt: ResultSkill? = null,
    /** Practice answers: the kind of task, as a label. */
    val skill: ResultSkill? = null,
    /** Speaking changes: the status after the session, and the score (null when scored on the device). */
    val status: SpeakingStatus? = null,
    val score: Double? = null,
    val band: PronunciationBand? = null,
)

enum class ReviewGroup(val raw: String) { SOLID("solid"), SHAKY("shaky"), RELEARN("relearn") }
enum class NewWordGroup(val raw: String) { SMOOTH("smooth"), RETRIED("retried"), REVEALED("revealed"), KNOWN("known") }
enum class PracticeGroup(val raw: String) { CLEAN("clean"), HELPED("helped"), MISSED("missed") }
enum class SpeakingGroup(val raw: String) { PASSED("passed"), NOT_PASSED("notPassed") }

/** The groups of one block. Reads only the keys the app knows: an unknown group is ignored, a missing one is empty. */
data class ResultGroups<G : Enum<G>>(private val items: Map<G, List<ResultItem>> = emptyMap()) {
    operator fun get(group: G): List<ResultItem> = items[group] ?: emptyList()
    fun count(group: G): Int = get(group).size

    open class Serializer<G : Enum<G>>(private val groups: Array<G>, private val raw: (G) -> String) : KSerializer<ResultGroups<G>> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ResultGroups")
        override fun serialize(encoder: Encoder, value: ResultGroups<G>) = throw SerializationException("Decoded only")
        override fun deserialize(decoder: Decoder): ResultGroups<G> {
            val json = decoder as? JsonDecoder ?: throw SerializationException("JSON only")
            val root = json.decodeJsonElement() as? JsonObject ?: throw SerializationException("Expected groups")
            return ResultGroups(
                groups.associateWith { group ->
                    (root[raw(group)] as? JsonArray)?.map { json.json.decodeFromJsonElement(ResultItem.serializer(), it) } ?: emptyList()
                },
            )
        }
    }
}

object ReviewGroupsSerializer : ResultGroups.Serializer<ReviewGroup>(ReviewGroup.entries.toTypedArray(), { it.raw })
object NewWordGroupsSerializer : ResultGroups.Serializer<NewWordGroup>(NewWordGroup.entries.toTypedArray(), { it.raw })
object PracticeGroupsSerializer : ResultGroups.Serializer<PracticeGroup>(PracticeGroup.entries.toTypedArray(), { it.raw })
object SpeakingGroupsSerializer : ResultGroups.Serializer<SpeakingGroup>(SpeakingGroup.entries.toTypedArray(), { it.raw })

@Serializable
data class ReviewSummary(
    val total: Int? = null,
    @Serializable(with = ReviewGroupsSerializer::class) val groups: ResultGroups<ReviewGroup> = ResultGroups(),
)

@Serializable
data class NewWordSummary(
    val total: Int? = null,
    @Serializable(with = NewWordGroupsSerializer::class) val groups: ResultGroups<NewWordGroup> = ResultGroups(),
)

@Serializable
data class PracticeSummary(
    val total: Int? = null,
    val excluded: Excluded? = null,
    @Serializable(with = PracticeGroupsSerializer::class) val groups: ResultGroups<PracticeGroup> = ResultGroups(),
) {
    @Serializable
    data class Excluded(val count: Int = 0, val reasons: List<String> = emptyList())
}

/** `needPracticeWords` of the word stats, before and after the commit. */
@Serializable
data class NeedPracticeCount(val before: Int? = null, val after: Int? = null)

/** Recordings scored this session: one per pronunciation, and the words whose speaking status changed. */
@Serializable
data class SpeakingSummary(
    val total: Int = 0,
    val passed: Int = 0,
    /** Every scored pronunciation; null from an older backend, which sent only the counts. */
    @Serializable(with = SpeakingGroupsSerializer::class) val groups: ResultGroups<SpeakingGroup>? = null,
    val becameFluent: List<ResultItem> = emptyList(),
    val slipped: List<ResultItem> = emptyList(),
)

@Serializable
data class CommitSummary(
    // Legacy counts: only the fallback results grid reads them.
    val newWordGoalSlots: Int? = null,
    val reviewedMeanings: Int? = null,
    val practiceTasks: Int? = null,
    /** Words passed with "I know this"; null from an older backend. */
    val knownMeanings: Int? = null,
    val review: ReviewSummary? = null,
    val newWords: NewWordSummary? = null,
    val practice: PracticeSummary? = null,
    /** Any mode that scored a recording; null otherwise. */
    val speaking: SpeakingSummary? = null,
    val needPractice: NeedPracticeCount? = null,
    val countedAsReview: List<ResultItem>? = null,
)

/** Set only by the commit that used up a topic, the scope or the whole collection; a retry repeats it. */
@Serializable
data class ScopeEvents(val completedTopicIds: List<String>, val scopeExhausted: Boolean, val collectionCompleted: Boolean) {
    val isEmpty: Boolean get() = completedTopicIds.isEmpty() && !scopeExhausted && !collectionCompleted
}

/** `POST /sessions/:id/commit`. LP, Tier and ranking blocks arrive with Ranking (Phase 2); unknown keys are ignored. */
@Serializable
data class CommitResponse(val sessionId: String, val summary: CommitSummary, val scopeEvents: ScopeEvents? = null)

@Serializable
data class CommitStatus(val status: String, val response: CommitResponse? = null)

// Recording

data class RecordedAudio(val data: ByteArray, val contentType: String = WAV_CONTENT_TYPE) {
    override fun equals(other: Any?) = other is RecordedAudio && data.contentEquals(other.data) && contentType == other.contentType
    override fun hashCode() = data.contentHashCode() * 31 + contentType.hashCode()

    companion object {
        /** Passed through to Azure Speech by the backend. */
        const val WAV_CONTENT_TYPE = "audio/wav; codecs=audio/pcm; samplerate=16000"
    }
}

@Serializable(with = SyllableBand.Serializer::class)
enum class SyllableBand(val raw: String) {
    GOOD("good"), POOR("poor"), UNKNOWN("unknown");

    object Serializer : LenientEnumSerializer<SyllableBand>("SyllableBand", entries.toTypedArray(), { it.raw }, UNKNOWN)
}

/** One syllable of a server-scored recording: its letters in the word, its sounds and its score. */
@Serializable
data class PronunciationSyllable(val text: String, val ipa: String? = null, val score: Double? = null, val band: SyllableBand)

@Serializable
data class RecordingAssessment(
    val assessmentReceiptId: String,
    val validity: String,
    val passed: Boolean? = null,
    val accuracyScore: Double? = null,
    /** On-device scoring only: the normalized transcript that was scored. */
    val heard: String? = null,
    /** Backend scoring: the overall score's band. Optional for an older backend. */
    val band: PronunciationBand? = null,
    /** Backend scoring: the word's syllables in reading order, each with its band. */
    val syllables: List<PronunciationSyllable>? = null,
) {
    /** False for silence or noise: not a fail, the learner should just read again. */
    val isValid: Boolean get() = validity == "valid"

    /** The server's band, or one derived from the score. Null without a score (on-device takes). */
    val scoreBand: PronunciationBand?
        get() {
            val score = accuracyScore
            if (!isValid || score == null) return null
            return band?.takeIf { it != PronunciationBand.UNKNOWN } ?: PronunciationBand.fallback(score)
        }
}
