package com.syll.domain.customwords

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.text.BreakIterator
import java.util.Locale

// region Lookup

/**
 * iOS `TargetLocator`: the word to highlight in a sentence. Offsets count UTF-16 code units, as the backend does (and
 * as Kotlin string indices do); [end] is exclusive.
 */
@Serializable
data class TargetLocator(val start: Int, val end: Int) {
    /** Null when the locator falls outside the sentence. */
    fun range(sentence: String): IntRange? =
        if (start >= 0 && start < end && end <= sentence.length) start until end else null

    fun substring(sentence: String): String? = range(sentence)?.let { sentence.substring(it) }

    fun contains(other: TargetLocator): Boolean = start <= other.start && other.end <= end

    companion object {
        /** First case-insensitive match, which is how the backend places a target. */
        fun locating(target: String, sentence: String): TargetLocator? {
            val needle = target.trim()
            if (needle.isEmpty()) return null
            val index = sentence.indexOf(needle, ignoreCase = true)
            if (index < 0) return null
            return TargetLocator(index, index + needle.length)
        }

        /** One locator per word, in reading order, without the punctuation between them. */
        fun words(sentence: String): List<TargetLocator> {
            val iterator = BreakIterator.getWordInstance(Locale.ENGLISH)
            iterator.setText(sentence)
            val words = mutableListOf<TargetLocator>()
            var start = iterator.first()
            var end = iterator.next()
            while (end != BreakIterator.DONE) {
                if (sentence.substring(start, end).any { it.isLetterOrDigit() }) words += TargetLocator(start, end)
                start = end
                end = iterator.next()
            }
            return words
        }
    }
}

@Serializable
data class CustomWordExample(val text: String, val translation: String, val targetLocator: TargetLocator)

/** `POST /custom-words/lookup` body; a null context is omitted. */
data class CustomWordQuery(val text: String, val context: String? = null) {
    fun toJson(): JsonObject = buildJsonObject {
        put("text", text)
        context?.let { put("context", it) }
    }
}

@Serializable
data class ExistingMeaning(
    val meaningId: String,
    @Serializable(with = Status.Serializer::class) val status: Status,
    val partOfSpeech: String,
    val inRoadmap: Boolean,
    /** Only on `translationDraft`. */
    val draftId: String? = null,
    /** Absent on `unavailable`. */
    val ipa: String? = null,
    /** Absent on `unavailable`. */
    val meaningText: String? = null,
    val definitionText: String? = null,
    val examples: List<CustomWordExample>? = null,
    /** The learner already studied this meaning, from their own words or a shared topic. Absent from older servers. */
    val learned: Boolean? = null,
) {
    enum class Status(val raw: String) {
        AVAILABLE("available"),
        UNAVAILABLE("unavailable"),
        TRANSLATION_DRAFT("translation_draft");

        /** Strict like iOS: an unknown status fails the lookup. */
        object Serializer : KSerializer<Status> {
            override val descriptor: SerialDescriptor =
                kotlinx.serialization.descriptors.PrimitiveSerialDescriptor("ExistingMeaning.Status", kotlinx.serialization.descriptors.PrimitiveKind.STRING)
            override fun deserialize(decoder: Decoder): Status {
                val raw = decoder.decodeString()
                return entries.firstOrNull { it.raw == raw } ?: throw SerializationException("Unknown meaning status: $raw")
            }
            override fun serialize(encoder: Encoder, value: Status) = encoder.encodeString(value.raw)
        }
    }

    enum class Standing { LEARNED, ON_PATH }

    val id: String get() = meaningId

    /** Why this meaning can't be added again; null when it can. */
    val standing: Standing?
        get() = when {
            learned == true -> Standing.LEARNED
            inRoadmap -> Standing.ON_PATH
            else -> null
        }

    /** Null when this meaning can't be added. */
    val addition: CustomWordAddition?
        get() = when (status) {
            Status.AVAILABLE -> CustomWordAddition.meaning(meaningId)
            Status.TRANSLATION_DRAFT -> draftId?.let { CustomWordAddition.translation(it) }
            Status.UNAVAILABLE -> null
        }

    val canBeAdded: Boolean get() = standing == null && addition != null
}

@Serializable
data class ExistingWord(val wordId: String, val lemma: String, val meanings: List<ExistingMeaning>)

@Serializable
data class DraftSense(
    val partOfSpeech: String,
    val ipa: String? = null,
    val cefrLevel: String? = null,
    val meaningText: String,
    val definitionText: String,
    val examples: List<CustomWordExample>,
)

@Serializable
data class WordDraft(val draftId: String, val lemma: String, val senses: List<DraftSense>)

/** iOS `CustomWordLookup`: decoded from the response's `result`; an unknown result fails the decode. */
@Serializable(with = CustomWordLookup.Serializer::class)
sealed interface CustomWordLookup {
    data class Existing(val word: ExistingWord) : CustomWordLookup
    data class Draft(val draft: WordDraft) : CustomWordLookup
    /** As [Draft], but the meaning stays private to this learner. */
    data class NewMeaningDraft(val draft: WordDraft) : CustomWordLookup
    data object Invalid : CustomWordLookup

    object Serializer : KSerializer<CustomWordLookup> {
        private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor

        override fun deserialize(decoder: Decoder): CustomWordLookup {
            val element = (decoder as? JsonDecoder)?.decodeJsonElement()?.jsonObject
                ?: throw SerializationException("CustomWordLookup needs JSON")
            return when (val result = element["result"]?.jsonPrimitive?.contentOrNull) {
                "existing" -> Existing(json.decodeFromJsonElement(ExistingWord.serializer(), element))
                "draft" -> Draft(json.decodeFromJsonElement(WordDraft.serializer(), element))
                "new_meaning_draft" -> NewMeaningDraft(json.decodeFromJsonElement(WordDraft.serializer(), element))
                "invalid" -> Invalid
                else -> throw SerializationException("Unknown lookup result: $result")
            }
        }

        override fun serialize(encoder: Encoder, value: CustomWordLookup) = throw SerializationException("Decode only")
    }
}

// endregion

// region Adding and editing

/**
 * Changes to a learner's own copy. The backend merges by field, so null fields are omitted and keep what they had;
 * [examples] replaces the whole list.
 */
data class CustomWordEdits(
    val meaningText: String? = null,
    val definitionText: String? = null,
    val examples: List<CustomWordExample>? = null,
) {
    fun toJson(): JsonObject = buildJsonObject {
        meaningText?.let { put("meaningText", it) }
        definitionText?.let { put("definitionText", it) }
        examples?.let { list ->
            putJsonArray("examples") {
                list.forEach { example ->
                    addJsonObject {
                        put("text", example.text)
                        put("translation", example.translation)
                        putJsonObject("targetLocator") {
                            put("start", example.targetLocator.start)
                            put("end", example.targetLocator.end)
                        }
                    }
                }
            }
        }
    }
}

/**
 * iOS `CustomWordAddition`: the `POST /custom-words` body. The backend rejects any other field, so the factories are
 * the only ways to build one.
 */
data class CustomWordAddition private constructor(
    val meaningId: String? = null,
    val draftId: String? = null,
    val senseIndex: Int? = null,
    val edits: CustomWordEdits? = null,
) {
    fun withEdits(edits: CustomWordEdits?) = copy(edits = edits)

    fun toJson(): JsonObject = buildJsonObject {
        meaningId?.let { put("meaningId", it) }
        draftId?.let { put("draftId", it) }
        senseIndex?.let { put("senseIndex", it) }
        edits?.let { put("edits", it.toJson()) }
    }

    companion object {
        fun meaning(id: String) = CustomWordAddition(meaningId = id)
        fun translation(draftId: String) = CustomWordAddition(draftId = draftId)
        fun draft(id: String, senseIndex: Int, edits: CustomWordEdits?) = CustomWordAddition(draftId = id, senseIndex = senseIndex, edits = edits)
    }
}

@Serializable
data class CustomWordAdded(
    val meaningId: String,
    /** False when it was already on the roadmap, which still counts as success. */
    val added: Boolean,
)

// endregion

// region The learner's list

@Serializable
data class CustomWord(
    val meaningId: String,
    val wordId: String,
    val lemma: String,
    val partOfSpeech: String,
    /** Null while [translationPending] or [recalled]. */
    val meaningText: String? = null,
    val definitionText: String? = null,
    val examples: List<CustomWordExample>? = null,
    val ipa: String? = null,
    val audioUrl: String? = null,
    val overridden: Boolean,
    val translationPending: Boolean,
    val recalled: Boolean,
) {
    val id: String get() = meaningId
    val isEditable: Boolean get() = !translationPending && !recalled && meaningText != null && definitionText != null
}

/** iOS `CustomWordGroup`: one list row, every added meaning of a word, in the order the server listed them. */
data class CustomWordGroup(val wordId: String, val lemma: String, val meanings: List<CustomWord>) {
    val id: String get() = wordId
    val meaningText: String?
        get() = meanings.filter { !it.recalled }.mapNotNull { it.meaningText }.takeIf { it.isNotEmpty() }?.joinToString("; ")
    val translationPending: Boolean get() = meanings.any { it.translationPending }
    val recalled: Boolean get() = meanings.all { it.recalled }
    val firstEditable: CustomWord? get() = meanings.firstOrNull { it.isEditable }

    companion object {
        fun grouping(words: List<CustomWord>): List<CustomWordGroup> =
            words.groupBy { it.wordId }.map { (wordId, meanings) -> CustomWordGroup(wordId, meanings.first().lemma, meanings) }
    }
}

// endregion

// region Input rules

/** iOS `CustomWordInput`: mirrors the backend's validation, so a bad word never costs a round trip. */
object CustomWordInput {
    enum class Issue { EMPTY, TOO_LONG, INVALID_CHARACTERS }

    const val MAX_TEXT_LENGTH = 64
    const val MAX_CONTEXT_LENGTH = 200
    const val MAX_FIELD_LENGTH = 500
    const val MAX_EXAMPLES = 3

    private val punctuation = setOf(' ', '-', '\'', '‘', '’')

    fun issue(text: String): Issue? {
        val word = text.trim()
        val first = word.firstOrNull() ?: return Issue.EMPTY
        if (word.length > MAX_TEXT_LENGTH) return Issue.TOO_LONG
        if (!first.isLetter() || !word.all { it.isLetter() || it in punctuation }) return Issue.INVALID_CHARACTERS
        return null
    }

    fun isValidField(text: String): Boolean = text.trim().length in 1..MAX_FIELD_LENGTH
}

/**
 * iOS `CustomWordEditDraft`: an editable copy of a word's text. Turns what changed into [edits], and keeps every
 * example's highlight valid while its sentence is rewritten.
 */
data class CustomWordEditDraft(
    val lemma: String,
    val meaningText: String,
    val definitionText: String,
    val examples: List<Example>,
    private val originalMeaning: String,
    private val originalDefinition: String,
) {
    data class Example(val id: Int, val text: String, val translation: String, val original: CustomWordExample) {
        val isRewritten: Boolean get() = text.trim() != original.text
        val isUnchanged: Boolean get() = !isRewritten && translation.trim() == original.translation
    }

    fun withMeaning(text: String) = copy(meaningText = text)
    fun withDefinition(text: String) = copy(definitionText = text)
    fun withExample(id: Int, text: String? = null, translation: String? = null) = copy(
        examples = examples.map { if (it.id == id) it.copy(text = text ?: it.text, translation = translation ?: it.translation) else it },
    )

    /**
     * Keep the backend's occurrence until the sentence changes. Edits automatically find the lemma or an English
     * inflection, and never permit an unrelated word as the target.
     */
    fun locator(example: Example): TargetLocator? {
        if (!example.isRewritten) return example.original.targetLocator
        val sentence = example.text.trim()
        val target = example.original.targetLocator.substring(example.original.text) ?: lemma
        val words = TargetLocator.words(sentence)
        for (candidate in listOf(lemma, target)) {
            val match = TargetLocator.locating(candidate, sentence) ?: continue
            if (words.any { it.start == match.start } && words.any { it.end == match.end }) return match
        }
        // iOS asks NLTagger for each word's lemma; here an English inflection table does the same job.
        return words.firstOrNull { EnglishInflection.isForm(it.substring(sentence).orEmpty(), lemma) }
    }

    val isValid: Boolean
        get() = CustomWordInput.isValidField(meaningText) &&
            CustomWordInput.isValidField(definitionText) &&
            examples.all { CustomWordInput.isValidField(it.text) && CustomWordInput.isValidField(it.translation) && locator(it) != null }

    /** What to send, or null when nothing changed: `edits` goes out only for a real edit. */
    val edits: CustomWordEdits?
        get() {
            val meaning = meaningText.trim()
            val definition = definitionText.trim()
            val meaningChanged = meaning != originalMeaning
            val definitionChanged = definition != originalDefinition
            val examplesChanged = examples.any { !it.isUnchanged }
            if (!meaningChanged && !definitionChanged && !examplesChanged) return null
            return CustomWordEdits(
                meaningText = meaning.takeIf { meaningChanged },
                definitionText = definition.takeIf { definitionChanged },
                examples = if (examplesChanged) examples.mapNotNull(::payload) else null,
            )
        }

    private fun payload(example: Example): CustomWordExample? =
        locator(example)?.let { CustomWordExample(example.text.trim(), example.translation.trim(), it) }

    companion object {
        fun of(lemma: String, sense: DraftSense) = make(lemma, sense.meaningText, sense.definitionText, sense.examples)
        fun of(lemma: String, meaning: ExistingMeaning) = make(lemma, meaning.meaningText, meaning.definitionText, meaning.examples)
        fun of(word: CustomWord) = make(word.lemma, word.meaningText, word.definitionText, word.examples)

        private fun make(lemma: String, meaning: String?, definition: String?, examples: List<CustomWordExample>?): CustomWordEditDraft {
            val meaningText = meaning.orEmpty()
            val definitionText = definition.orEmpty()
            return CustomWordEditDraft(
                lemma = lemma,
                meaningText = meaningText,
                definitionText = definitionText,
                examples = examples.orEmpty().take(CustomWordInput.MAX_EXAMPLES).mapIndexed { index, example ->
                    Example(index, example.text, example.translation, example)
                },
                originalMeaning = meaningText,
                originalDefinition = definitionText,
            )
        }
    }
}

// endregion
