package com.syll.domain.practice

import com.syll.domain.NewWordReason
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

enum class SessionMode(val raw: String) {
    DAILY("daily"),
    CHECKPOINT_CHALLENGE("checkpoint_challenge"),
    RANDOM_PRACTICE("random_practice"),
    WEAK_WORDS_PRACTICE("weak_words_practice"),
    /** One recording per learned word, weakest speaking first. Never moves the review schedule. */
    SPEAKING_PRACTICE("speaking_practice"),
    ;

    companion object {
        fun of(raw: String): SessionMode = entries.firstOrNull { it.raw == raw } ?: throw SerializationException("Unknown mode $raw")
    }
}

/** The source of a random practice. Encodes as the one key the backend expects. */
sealed interface PracticeFilter {
    data class Collection(val id: String) : PracticeFilter
    data class Topics(val ids: List<String>) : PracticeFilter
    data object CustomWords : PracticeFilter

    fun toJson(): JsonObject = buildJsonObject {
        when (this@PracticeFilter) {
            is Collection -> put("collectionId", id)
            is Topics -> putJsonArray("topicIds") { ids.forEach { add(JsonPrimitive(it)) } }
            CustomWords -> put("customOnly", true)
        }
    }
}

/** `POST /sessions/plan`. */
data class PlanRequest(
    val mode: SessionMode,
    val capabilities: Capabilities,
    val challengeId: String? = null,
    /** Daily only: due reviews and no new words. */
    val reviewOnly: Boolean? = null,
    /** Daily only: an optional "I know this" recall before each new word. */
    val knownCheck: Boolean? = null,
    /** Random practice only: where the words come from. Null is every learned word. */
    val filter: PracticeFilter? = null,
) {
    data class Capabilities(val audioPlayback: Boolean, val microphone: Boolean)

    fun toJson(): JsonObject = buildJsonObject {
        put("mode", mode.raw)
        putJsonObject("capabilities") {
            put("audioPlayback", capabilities.audioPlayback)
            put("microphone", capabilities.microphone)
        }
        challengeId?.let { put("challengeId", it) }
        reviewOnly?.let { put("reviewOnly", it) }
        knownCheck?.let { put("knownCheck", it) }
        filter?.let { put("filter", it.toJson()) }
    }
}

/** Daily only: how many new words the plan gave and, when fewer than the goal, why. */
data class NewWordsIssue(val requested: Int, val issued: Int, val withheldReason: NewWordReason?)

@Serializable
data class SourceTopic(val id: String, val name: String)

/** One word the daily plan reviews, as the preview screen shows it. */
@Serializable
data class PlanReviewWord(val wordId: String, val meaningId: String, val lemma: String, val sourceTopic: SourceTopic? = null)

/** The optional recall before a Learn card. Passed first try, the card and [skippableTaskIds] are skipped. */
data class KnownCheck(val learnStepId: String, val skippableTaskIds: Set<String>)

@Serializable
data class AudioClip(val url: String, val locale: String)

@Serializable
data class LearnCard(
    val word: String,
    val meaning: String,
    val definition: String? = null,
    val ipa: String? = null,
    val audio: AudioClip? = null,
    val examples: List<Example>? = null,
    /** Optional: a plan started before the backend sent it has no value here. */
    val partOfSpeech: String? = null,
    /** Open the word's other meanings; absent from plans made before the backend sent them. */
    val wordId: String? = null,
    val meaningId: String? = null,
) {
    @Serializable
    data class Example(val text: String, val translation: String? = null)
}

/** Display-only on the backend, so a new phase must not break the session. */
enum class TaskPhase(val raw: String) {
    DUE_REVIEW("due_review"),
    NEW_PRACTICE("new_practice"),
    KNOWN_CHECK("known_check"),
    CHECKPOINT_CHALLENGE("checkpoint_challenge"),
    RANDOM_PRACTICE("random_practice"),
    WEAK_WORDS_PRACTICE("weak_words_practice"),
    SPEAKING_PRACTICE("speaking_practice"),
    OTHER("other"),
    ;

    companion object {
        fun of(raw: String): TaskPhase = entries.firstOrNull { it.raw == raw && it != OTHER } ?: OTHER
    }
}

sealed interface PlanStep {
    data class Learn(val id: String, val card: LearnCard) : PlanStep
    data class Practice(val phase: TaskPhase, val task: PracticeTask) : PlanStep

    val isDueReview: Boolean get() = this is Practice && phase == TaskPhase.DUE_REVIEW
}

/** The word a question points at, as the backend's string offsets: UTF-16 units, [end] excluded. */
@Serializable
data class PromptHighlight(val start: Int, val end: Int) {
    data class Parts(val before: String, val word: String, val after: String)

    /** Null when the offsets don't fit the text; the prompt then shows plain rather than failing the plan. */
    fun split(text: String): Parts? {
        // Kotlin strings are UTF-16, like the backend's offsets.
        if (start < 0 || start >= end || end > text.length) return null
        return Parts(text.substring(0, start), text.substring(start, end), text.substring(end))
    }
}

sealed interface TaskPrompt {
    data class Text(val text: String, val highlight: PromptHighlight? = null) : TaskPrompt
    data class Audio(val clip: AudioClip) : TaskPrompt

    val isAudio: Boolean get() = this is Audio
}

@Serializable
data class TaskOption(val id: Int, val text: String)

@Serializable
data class SentenceParts(val before: String, val after: String)

/** A cloze sentence split for layout. Text touching the blank, like a final period, stays attached to it. */
data class ClozeLine(
    val wordsBefore: List<String>,
    val attachedBefore: String,
    val attachedAfter: String,
    val wordsAfter: List<String>,
) {
    companion object {
        fun of(parts: SentenceParts): ClozeLine {
            val before = parts.before.split(Regex("\\s+")).filter { it.isNotEmpty() }.toMutableList()
            val after = parts.after.split(Regex("\\s+")).filter { it.isNotEmpty() }.toMutableList()
            val attachedBefore = if (parts.before.isNotEmpty() && !parts.before.last().isWhitespace()) before.removeAt(before.lastIndex) else ""
            val attachedAfter = if (parts.after.isNotEmpty() && !parts.after.first().isWhitespace()) after.removeAt(0) else ""
            return ClozeLine(before, attachedBefore, attachedAfter, after)
        }
    }
}

@Serializable
data class PairKey(val leftId: Int, val rightId: Int)

enum class TaskTemplate(val raw: String, val variants: Set<String>) {
    SELECT("select", setOf("gloss", "reverse_gloss", "meaning_clash", "phonetic_choice", "stress_choice")),
    CLOZE_SELECT("cloze_select", setOf("single_blank", "context_meaning")),
    TYPING("typing", setOf("meaning_recall", "transcription")),
    FLY_REORDER("fly_reorder", setOf("spelling_recall", "transcription")),
    MATCH("match", setOf("meaning_pairs", "word_pairs")),
    SURGERY("surgery", setOf("fix_spelling")),
    RECORDING("recording", setOf("scripted_pronunciation")),
}

/**
 * What the learner interacts with, including the answer key for instant feedback. The backend re-scores everything
 * at commit, so the key must never be logged or shown early.
 */
sealed interface TaskBody {
    data class Choice(val options: List<TaskOption>, val sentence: SentenceParts?, val correctOptionId: Int) : TaskBody
    data class Typing(val accepted: List<String>) : TaskBody
    data class Reorder(val tiles: List<TaskOption>, val accepted: List<String>) : TaskBody
    data class Match(val left: List<TaskOption>, val right: List<TaskOption>, val correct: List<PairKey>) : TaskBody
    data class Surgery(val word: String, val wrongGraphemeIndex: Int, val replacement: String) : TaskBody
    /** The text to read. Empty for sessions issued before the backend sent it. */
    data class Recording(val accepted: List<String>) : TaskBody
}

/**
 * A recording of a learned word first shows its meaning for an unscored try at saying the word (FRONTEND_FLOW §7.7).
 * Only the read-aloud that follows is scored and sends events.
 */
@Serializable
data class MeaningCue(val text: String, val question: String)

/**
 * iOS `PracticeTask`. An unknown template or variant fails the whole plan: commit needs a result for every task, so
 * one that can't be rendered can't be skipped.
 */
data class PracticeTask(
    val id: String,
    val template: TaskTemplate,
    val variant: String,
    /** Null only for a cloze with no clue: its sentence is the stimulus. */
    val prompt: TaskPrompt?,
    val question: String?,
    val body: TaskBody,
    /** Tries after a wrong answer, so 1 + this in total. Null means unlimited. */
    val maxRetries: Int? = null,
    /** Recording only; null for a new word's recording and from an older backend. */
    val meaningCue: MeaningCue? = null,
    /** Recording only: the sound to read. Null when the meaning has none. */
    val ipa: String? = null,
)

/** iOS `SessionPlan`. */
@Serializable(with = SessionPlan.Serializer::class)
data class SessionPlan(
    /** Null when there is nothing to learn or review; such a plan has no session to commit. */
    val sessionId: String?,
    val mode: SessionMode,
    val steps: List<PlanStep>,
    /** Daily only; null from an older backend. */
    val newWords: NewWordsIssue? = null,
    val reviewWords: List<PlanReviewWord>? = null,
    /** Review task id → its topic label. */
    val sourceTopics: Map<String, SourceTopic> = emptyMap(),
    /** Known-check task id → the Learn step it can skip. */
    val knownChecks: Map<String, KnownCheck> = emptyMap(),
) {
    object Serializer : KSerializer<SessionPlan> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("SessionPlan")
        override fun serialize(encoder: Encoder, value: SessionPlan) = throw SerializationException("Plans are only decoded")
        override fun deserialize(decoder: Decoder): SessionPlan {
            val json = (decoder as? JsonDecoder) ?: throw SerializationException("JSON only")
            return PlanDecoding.plan(json.json, json.decodeJsonElement().obj())
        }
    }
}

/** Strict decoding of the plan's steps and tasks (iOS `SessionPlan` / `PlanStep` / `PracticeTask` decoders). */
internal object PlanDecoding {
    fun plan(json: Json, root: JsonObject): SessionPlan {
        val stepsJson = root.required("steps").jsonArray
        val steps = stepsJson.map { step(json, it.obj()) }
        // The extra fields a step may carry, read alongside the step itself (iOS decodes `steps` twice).
        val sourceTopics = mutableMapOf<String, SourceTopic>()
        val knownChecks = mutableMapOf<String, KnownCheck>()
        stepsJson.forEach { element ->
            val extras = element.obj()
            val taskId = (extras["task"] as? JsonObject)?.string("id")
            val topic = extras["sourceTopic"].takeUnless { it == null || it is JsonNull }
                ?.let { json.decodeFromJsonElement(SourceTopic.serializer(), it) }
            if (taskId != null && topic != null) sourceTopics[taskId] = topic
            val learnId = extras.string("id")
            val checkId = extras.string("knownCheckTaskId")
            if (learnId != null && checkId != null) {
                val skippable = (extras["skippableTaskIds"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.toSet() ?: emptySet()
                knownChecks[checkId] = KnownCheck(learnId, skippable)
            }
        }
        val newWords = (root["newWords"] as? JsonObject)?.let { issue ->
            NewWordsIssue(
                requested = issue.int("requested") ?: throw SerializationException("requested"),
                issued = issue.int("issued") ?: throw SerializationException("issued"),
                withheldReason = issue.string("withheldReason")?.let { raw ->
                    NewWordReason.entries.firstOrNull { it.raw == raw } ?: NewWordReason.UNKNOWN
                },
            )
        }
        val reviewWords = root["reviewWords"].takeUnless { it == null || it is JsonNull }?.jsonArray
            ?.map { json.decodeFromJsonElement(PlanReviewWord.serializer(), it) }
        return SessionPlan(
            sessionId = root.string("sessionId"),
            mode = SessionMode.of(root.string("mode") ?: throw SerializationException("mode")),
            steps = steps,
            newWords = newWords,
            reviewWords = reviewWords,
            sourceTopics = sourceTopics,
            knownChecks = knownChecks,
        )
    }

    private fun step(json: Json, step: JsonObject): PlanStep = when (val type = step.string("type")) {
        "learn" -> PlanStep.Learn(
            id = step.string("id") ?: throw SerializationException("learn id"),
            card = json.decodeFromJsonElement(LearnCard.serializer(), step.required("card")),
        )
        "practice" -> PlanStep.Practice(
            phase = TaskPhase.of(step.string("phase") ?: throw SerializationException("phase")),
            task = task(json, step.required("task").obj()),
        )
        else -> throw SerializationException("Unknown step $type")
    }

    fun task(json: Json, task: JsonObject): PracticeTask {
        val rawTemplate = task.string("template")
        val template = TaskTemplate.entries.firstOrNull { it.raw == rawTemplate }
            ?: throw SerializationException("Unknown template $rawTemplate")
        val variant = task.string("variant") ?: throw SerializationException("variant")
        if (variant !in template.variants) throw SerializationException("Unknown variant $variant")
        val promptJson = task["prompt"].takeUnless { it == null || it is JsonNull }
        if (promptJson == null && template != TaskTemplate.CLOZE_SELECT) throw SerializationException("prompt")
        return PracticeTask(
            id = task.string("id") ?: throw SerializationException("task id"),
            template = template,
            variant = variant,
            prompt = promptJson?.let { prompt(json, it.obj()) },
            question = task.string("question"),
            body = body(json, template, task),
            maxRetries = task.int("maxRetries"),
            meaningCue = if (template == TaskTemplate.RECORDING) {
                task["meaningCue"].takeUnless { it == null || it is JsonNull }?.let { json.decodeFromJsonElement(MeaningCue.serializer(), it) }
            } else {
                null
            },
            ipa = if (template == TaskTemplate.RECORDING) task.string("ipa") else null,
        )
    }

    private fun prompt(json: Json, prompt: JsonObject): TaskPrompt = when (val type = prompt.string("type")) {
        "text" -> TaskPrompt.Text(
            text = prompt.string("text") ?: throw SerializationException("prompt text"),
            highlight = prompt["highlight"].takeUnless { it == null || it is JsonNull }
                ?.let { json.decodeFromJsonElement(PromptHighlight.serializer(), it) },
        )
        "audio" -> TaskPrompt.Audio(json.decodeFromJsonElement(AudioClip.serializer(), prompt.required("audio")))
        else -> throw SerializationException("Unknown prompt $type")
    }

    private fun body(json: Json, template: TaskTemplate, task: JsonObject): TaskBody {
        val evaluationJson = task["evaluation"].takeUnless { it == null || it is JsonNull }?.obj()
        if (template == TaskTemplate.RECORDING) return TaskBody.Recording(evaluationJson?.strings("acceptedResponses") ?: emptyList())
        val evaluation = evaluationJson ?: throw SerializationException("evaluation")

        fun <T> required(value: T?): T = value ?: throw SerializationException("Missing answer key")
        fun options(key: String) = task.required(key).jsonArray.map { json.decodeFromJsonElement(TaskOption.serializer(), it) }

        return when (template) {
            TaskTemplate.SELECT, TaskTemplate.CLOZE_SELECT -> TaskBody.Choice(
                options = options("options"),
                sentence = if (template == TaskTemplate.CLOZE_SELECT) {
                    json.decodeFromJsonElement(SentenceParts.serializer(), task.required("sentenceParts"))
                } else {
                    null
                },
                correctOptionId = required(evaluation.int("correctOptionId")),
            )
            TaskTemplate.TYPING -> TaskBody.Typing(required(evaluation.strings("acceptedResponses")))
            TaskTemplate.FLY_REORDER -> TaskBody.Reorder(options("tiles"), required(evaluation.strings("acceptedResponses")))
            TaskTemplate.MATCH -> {
                val pairs = task.required("pairs").obj()
                TaskBody.Match(
                    left = pairs.required("left").jsonArray.map { json.decodeFromJsonElement(TaskOption.serializer(), it) },
                    right = pairs.required("right").jsonArray.map { json.decodeFromJsonElement(TaskOption.serializer(), it) },
                    correct = required(
                        evaluation["correctPairs"].takeUnless { it == null || it is JsonNull }?.jsonArray
                            ?.map { json.decodeFromJsonElement(PairKey.serializer(), it) },
                    ),
                )
            }
            TaskTemplate.SURGERY -> TaskBody.Surgery(
                word = task.string("word") ?: throw SerializationException("word"),
                wrongGraphemeIndex = required(evaluation.int("wrongGraphemeIndex")),
                replacement = required(evaluation.string("replacement")),
            )
            TaskTemplate.RECORDING -> TaskBody.Recording(evaluation.strings("acceptedResponses") ?: emptyList())
        }
    }

    private fun JsonObject.required(key: String): JsonElement =
        this[key].takeUnless { it == null || it is JsonNull } ?: throw SerializationException("Missing $key")
    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.strings(key: String): List<String>? =
        this[key].takeUnless { it == null || it is JsonNull }?.jsonArray?.map {
            (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content ?: throw SerializationException("Expected text in $key")
        }
}

private fun JsonElement.obj(): JsonObject = this as? JsonObject ?: throw SerializationException("Expected an object")
