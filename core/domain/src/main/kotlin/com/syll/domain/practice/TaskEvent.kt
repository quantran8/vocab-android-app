package com.syll.domain.practice

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * iOS `TaskEvent`: one learner action, in the shape commit expects. The backend's schema is strict, so each action
 * encodes only its own fields.
 */
data class TaskEvent(
    val seq: Int,
    val action: Action,
    /** Ms since the task was shown, never decreasing within a task. */
    val elapsedMs: Int? = null,
) {
    sealed interface Action {
        data class SubmitOption(val id: Int) : Action
        data class SubmitValue(val value: String) : Action
        data class SubmitTiles(val ids: List<Int>) : Action
        /** Surgery: every fix, sorted by index. An empty replacement deletes the grapheme. */
        data class SubmitRepair(val edits: List<RepairEdit>) : Action
        data class Pair(val leftId: Int, val rightId: Int) : Action
        data class TapTile(val id: Int) : Action
        data class RemoveTile(val id: Int) : Action
        data class MoveTile(val id: Int, val toIndex: Int) : Action
        data object Hint : Action
        data object Reveal : Action
        data class Assessed(val receiptId: String) : Action
        data class Unavailable(val reason: String) : Action
        /** On-device take. The backend re-checks the transcript against the task's text. */
        data class Transcribed(val transcript: String) : Action

        val isSubmit: Boolean get() = this is SubmitOption || this is SubmitValue || this is SubmitTiles || this is SubmitRepair
    }

    fun toJson(): JsonObject = buildJsonObject {
        put("seq", seq)
        elapsedMs?.let { put("elapsedMs", it) }
        when (val action = action) {
            is Action.SubmitOption -> {
                put("action", "submit")
                put("optionId", action.id)
            }
            is Action.SubmitValue -> {
                put("action", "submit")
                put("value", action.value)
            }
            is Action.SubmitTiles -> {
                put("action", "submit")
                putJsonArray("tileIds") { action.ids.forEach { add(JsonPrimitive(it)) } }
            }
            is Action.SubmitRepair -> {
                put("action", "submit")
                // One fix keeps the original shape, which backends without `edits` still accept.
                if (action.edits.size == 1) {
                    put("graphemeIndex", action.edits[0].graphemeIndex)
                    put("replacement", action.edits[0].replacement)
                } else {
                    putJsonArray("edits") { action.edits.forEach { add(it.toJson()) } }
                }
            }
            is Action.Pair -> {
                put("action", "pair")
                put("leftId", action.leftId)
                put("rightId", action.rightId)
            }
            is Action.TapTile -> {
                put("action", "tap_tile")
                put("tileId", action.id)
            }
            is Action.RemoveTile -> {
                put("action", "remove_tile")
                put("tileId", action.id)
            }
            is Action.MoveTile -> {
                put("action", "move_tile")
                put("tileId", action.id)
                put("toIndex", action.toIndex)
            }
            Action.Hint -> put("action", "hint")
            Action.Reveal -> put("action", "reveal")
            is Action.Assessed -> {
                put("action", "assessed")
                put("assessmentReceiptId", action.receiptId)
            }
            is Action.Unavailable -> {
                put("action", "unavailable")
                put("reason", action.reason)
            }
            is Action.Transcribed -> {
                put("action", "transcribed")
                put("transcript", action.transcript)
            }
        }
    }
}

data class RepairEdit(val graphemeIndex: Int, val replacement: String) {
    fun toJson(): JsonObject = buildJsonObject {
        put("graphemeIndex", graphemeIndex)
        put("replacement", replacement)
    }
}

enum class DeliveryMode(val raw: String) { AUDIO("audio"), FALLBACK("fallback"), NONE("none") }

data class TaskResult(
    val taskId: String,
    /** Required for audio prompts; omitted for text prompts. */
    val deliveryMode: DeliveryMode?,
    val events: List<TaskEvent>,
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("taskId", taskId)
        deliveryMode?.let { put("deliveryMode", it.raw) }
        putJsonArray("events") { events.forEach { add(it.toJson()) } }
    }
}

/**
 * iOS `CommitBody`. [encoded] is built once and kept: a retried commit must send the first attempt's bytes.
 */
data class CommitBody(
    val learnStepsCompleted: List<String>,
    /** Learn steps marked "I know this word"; omitted when empty so older bodies stay byte-identical. */
    val knownLearnSteps: List<String>? = null,
    val taskResults: List<TaskResult>,
) {
    fun toJson(): JsonObject = buildJsonObject {
        putJsonArray("learnStepsCompleted") { learnStepsCompleted.forEach { add(JsonPrimitive(it)) } }
        knownLearnSteps?.let { steps -> putJsonArray("knownLearnSteps") { steps.forEach { add(JsonPrimitive(it)) } } }
        putJsonArray("taskResults") { taskResults.forEach { add(it.toJson()) } }
    }

    fun encoded(): ByteArray = toJson().toString().toByteArray(Charsets.UTF_8)
}

