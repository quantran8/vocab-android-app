package com.syll.domain.report

import com.syll.domain.practice.TaskAttempt
import com.syll.domain.practice.TaskEvent
import com.syll.domain.practice.TaskTemplate
import java.util.UUID

// iOS `ContentReport.swift`. Contract: backend docs/FRONTEND_FLOW.md §18 and docs/content-reports.md.

enum class ContentReportSource(val raw: String) {
    LEARN_CARD("learn_card"),
    PRACTICE_TASK("practice_task"),
    WORD_DETAIL("word_detail"),
}

enum class ContentReportReason(val raw: String) {
    INCORRECT_MEANING("incorrect_meaning"),
    INCORRECT_DEFINITION("incorrect_definition"),
    INCORRECT_EXAMPLE("incorrect_example"),
    ANSWER_SHOULD_BE_ACCEPTED("answer_should_be_accepted"),
    INCORRECT_QUESTION("incorrect_question"),
    AUDIO_ISSUE("audio_issue"),
    OTHER("other"),
}

/** The learner's answer, as a hint for the reviewer. Never audio, never the whole event stream. */
data class ReportAttemptExcerpt(
    val submittedText: String? = null,
    val optionId: String? = null,
    val tileIds: List<String>? = null,
    val receiptId: String? = null,
) {
    companion object {
        const val MAX_TEXT_LENGTH = 256
    }
}

/** What the report sheet is about, captured when it opens so a later step change can't retarget it. */
class ContentReportTarget(
    val context: Context,
    /** The word or question, shown under the sheet title. */
    val subtitle: String?,
    /** The reasons this content can have, in display order; `OTHER` is always last. */
    val reasons: List<ContentReportReason>,
    /** A recording task: the answer reason reads "My pronunciation was marked wrong". */
    val isRecording: Boolean = false,
) {
    /** Each opening is its own sheet. */
    val id: String = UUID.randomUUID().toString()

    sealed interface Context {
        val source: ContentReportSource

        data class LearnCard(val sessionId: String, val learnStepId: String) : Context {
            override val source get() = ContentReportSource.LEARN_CARD
        }

        data class PracticeTask(val sessionId: String, val taskId: String, val excerpt: ReportAttemptExcerpt?) : Context {
            override val source get() = ContentReportSource.PRACTICE_TASK
        }

        data class WordDetail(val meaningId: String) : Context {
            override val source get() = ContentReportSource.WORD_DETAIL
        }
    }

    companion object {
        /** Learn card or word detail: the reasons depend on what the meaning shows. */
        fun meaningReasons(hasDefinition: Boolean, hasExample: Boolean, hasAudio: Boolean): List<ContentReportReason> = buildList {
            add(ContentReportReason.INCORRECT_MEANING)
            if (hasDefinition) add(ContentReportReason.INCORRECT_DEFINITION)
            if (hasExample) add(ContentReportReason.INCORRECT_EXAMPLE)
            if (hasAudio) add(ContentReportReason.AUDIO_ISSUE)
            add(ContentReportReason.OTHER)
        }

        /** A task: the answer appeal only once an answer was judged (never before feedback, never when it was accepted). */
        fun taskReasons(canAppeal: Boolean, hasAudio: Boolean): List<ContentReportReason> = buildList {
            add(ContentReportReason.INCORRECT_QUESTION)
            if (canAppeal) add(ContentReportReason.ANSWER_SHOULD_BE_ACCEPTED)
            add(ContentReportReason.INCORRECT_MEANING)
            if (hasAudio) add(ContentReportReason.AUDIO_ISSUE)
            add(ContentReportReason.OTHER)
        }
    }
}

/** iOS `ContentReportRequest`. Encoded by the network layer. */
data class ContentReportRequest(
    val clientReportId: String,
    val reason: ContentReportReason,
    val context: ContentReportTarget.Context,
    val note: String?,
) {
    val source: ContentReportSource get() = context.source

    /** Same report, ignoring the idempotency key: a retry must reuse the key. */
    fun isSameReport(other: ContentReportRequest): Boolean = reason == other.reason && context == other.context && note == other.note

    /** Only the appeal carries the answer. */
    val attemptExcerpt: ReportAttemptExcerpt?
        get() = (context as? ContentReportTarget.Context.PracticeTask)?.excerpt?.takeIf { reason == ContentReportReason.ANSWER_SHOULD_BE_ACCEPTED }

    companion object {
        const val MAX_NOTE_LENGTH = 500
    }
}

/** The latest judged answer, for an "accept my answer" report; null before any answer or for answers with no text. */
val TaskAttempt.reportExcerpt: ReportAttemptExcerpt?
    get() {
        for (event in events.asReversed()) {
            when (val action = event.action) {
                is TaskEvent.Action.SubmitValue -> return ReportAttemptExcerpt(submittedText = action.value.take(ReportAttemptExcerpt.MAX_TEXT_LENGTH))
                is TaskEvent.Action.Transcribed -> return ReportAttemptExcerpt(submittedText = action.transcript.take(ReportAttemptExcerpt.MAX_TEXT_LENGTH))
                is TaskEvent.Action.SubmitOption -> return ReportAttemptExcerpt(optionId = action.id.toString())
                is TaskEvent.Action.SubmitTiles -> return ReportAttemptExcerpt(tileIds = action.ids.take(ReportAttemptExcerpt.MAX_TEXT_LENGTH).map(Int::toString))
                is TaskEvent.Action.Assessed -> return ReportAttemptExcerpt(receiptId = action.receiptId)
                else -> Unit
            }
        }
        return null
    }

/**
 * The answer was judged and not accepted: the only time an appeal makes sense. A recording is judged per take, so a
 * failed take can be appealed before the task ends.
 */
val TaskAttempt.canAppealAnswer: Boolean
    get() {
        if (reportExcerpt == null) return false
        if (task.template == TaskTemplate.RECORDING) return !isRightFirstTime
        return when (status) {
            TaskAttempt.Status.WRONG, TaskAttempt.Status.EXHAUSTED, TaskAttempt.Status.REVEALED -> true
            TaskAttempt.Status.ANSWERING, TaskAttempt.Status.SOLVED, TaskAttempt.Status.RECORDED -> false
        }
    }
