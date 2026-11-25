package com.syll.domain.practice

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * iOS `LabTemplate`: one Practice Lab entry, a backend task template with bundled example tasks in the plan wire
 * format, so the Lab renders the same views and rules as a live session.
 */
data class LabTemplate(val id: TaskTemplate, val title: String, val action: String, val systemImage: String, val tasks: List<PracticeTask>)

/** iOS `LabLibrary` (`practice_lab.json`). */
data class LabLibrary(val templates: List<LabTemplate>) {
    sealed class ValidationError(message: String) : Exception(message) {
        class EmptyTemplate(val template: TaskTemplate) : ValidationError("No tasks for $template")
        class TemplateMismatch(val taskId: String) : ValidationError("Task $taskId is in the wrong template")
        class DuplicateTaskId(val taskId: String) : ValidationError("Duplicate task $taskId")
        class MissingTemplate(val template: TaskTemplate) : ValidationError("No entry for $template")
    }

    /** Every backend template must be playable once, and each task must belong to its entry. */
    fun validate() {
        val seen = mutableSetOf<String>()
        for (template in templates) {
            if (template.tasks.isEmpty()) throw ValidationError.EmptyTemplate(template.id)
            for (task in template.tasks) {
                if (task.template != template.id) throw ValidationError.TemplateMismatch(task.id)
                if (!seen.add(task.id)) throw ValidationError.DuplicateTaskId(task.id)
            }
        }
        for (kind in TaskTemplate.entries) {
            if (templates.none { it.id == kind }) throw ValidationError.MissingTemplate(kind)
        }
    }

    companion object {
        /** Decodes the bundled file with the plan's strict task decoder; a broken file fails loudly. */
        fun decode(json: Json, text: String): LabLibrary {
            val root = json.parseToJsonElement(text).jsonObject
            val templates = root.getValue("templates").jsonArray.map { element ->
                val entry = element.jsonObject
                fun string(key: String) = entry[key]?.jsonPrimitive?.contentOrNull ?: throw SerializationException(key)
                val raw = string("id")
                LabTemplate(
                    id = TaskTemplate.entries.firstOrNull { it.raw == raw } ?: throw SerializationException("Unknown template $raw"),
                    title = string("title"),
                    action = string("action"),
                    systemImage = string("systemImage"),
                    tasks = entry.getValue("tasks").jsonArray.map { PlanDecoding.task(json, it as JsonObject) },
                )
            }
            return LabLibrary(templates)
        }
    }
}

/** iOS `ResultLabFixture`: an example results screen for the Practice Lab (`results_lab.json`). */
data class ResultLabFixture(val id: String, val title: String, val subtitle: String, val result: PracticeResult) {
    companion object {
        fun decode(json: Json, text: String): List<ResultLabFixture> =
            json.parseToJsonElement(text).jsonObject.getValue("fixtures").jsonArray.map { element ->
                val fixture = element.jsonObject
                fun string(key: String) = fixture[key]?.jsonPrimitive?.contentOrNull ?: throw SerializationException(key)
                val result = fixture.getValue("result").jsonObject
                val kind = when (val raw = result["kind"]?.jsonPrimitive?.contentOrNull) {
                    "daily" -> PracticeResult.Kind.DAILY
                    "randomPractice" -> PracticeResult.Kind.RANDOM_PRACTICE
                    "weakPractice" -> PracticeResult.Kind.WEAK_PRACTICE
                    "speakingPractice" -> PracticeResult.Kind.SPEAKING_PRACTICE
                    "checkpoint" -> PracticeResult.Kind.CHECKPOINT
                    else -> throw SerializationException("Unknown result kind $raw")
                }
                ResultLabFixture(
                    id = string("id"),
                    title = string("title"),
                    subtitle = string("subtitle"),
                    result = PracticeResult(kind, json.decodeFromJsonElement(CommitSummary.serializer(), result.getValue("summary"))),
                )
            }
    }
}
