package com.syll.features.report

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syll.R
import com.syll.app.UserMessage
import com.syll.domain.report.ContentReportReason
import com.syll.domain.report.ContentReportRequest
import com.syll.domain.report.ContentReportTarget
import com.syll.network.ApiError
import com.syll.network.VocabApi
import com.syll.network.asApiError
import kotlinx.coroutines.CancellationException
import java.util.UUID

/**
 * iOS `ContentReportStore`: sends content reports (backend FRONTEND_FLOW §18). Reports are feedback for reviewers:
 * they never touch the session, its attempts or its commit. A failed send keeps the draft; resending the same report
 * reuses its key, so the server stores it once.
 */
class ContentReportStore(
    private val api: VocabApi,
    private val makeId: () -> String = { UUID.randomUUID().toString() },
) {
    sealed interface Phase {
        data object Editing : Phase
        data object Submitting : Phase
        data class Failed(val message: UserMessage) : Phase
    }

    var phase by mutableStateOf<Phase>(Phase.Editing)
        private set
    val isSubmitting: Boolean get() = phase == Phase.Submitting

    /** The last request that didn't get an answer; the same report resends it unchanged. */
    private var unanswered: ContentReportRequest? = null
    private var generation = 0

    /** Before each new sheet. */
    fun begin() {
        phase = Phase.Editing
        unanswered = null
    }

    /** Whether the server took the report (a duplicate of an open one counts). */
    suspend fun submit(target: ContentReportTarget, reason: ContentReportReason, note: String): Boolean {
        val trimmed = note.trim()
        val draft = ContentReportRequest(
            clientReportId = makeId(),
            reason = reason,
            context = target.context,
            note = trimmed.takeIf { it.isNotEmpty() }?.takeCodePoints(ContentReportRequest.MAX_NOTE_LENGTH),
        )
        val request = unanswered?.takeIf { it.isSameReport(draft) } ?: draft
        val started = generation
        phase = Phase.Submitting
        return try {
            api.submitContentReport(request)
            if (started != generation) return false
            unanswered = null
            phase = Phase.Editing
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (started != generation) return false
            val error = e.asApiError
            // Only an unanswered request may have been stored; any answer means the key is spent.
            unanswered = if (error.isRetryable) request else null
            phase = Phase.Failed(message(error))
            false
        }
    }

    fun reset() {
        generation += 1
        phase = Phase.Editing
        unanswered = null
    }

    companion object {
        fun message(error: ApiError): UserMessage = when (error.code) {
            "REPORT_TARGET_OVERRIDDEN" -> UserMessage.Resource(R.string.this_meaning_shows_your_own_edit_so_it_can_t_be)
            "REPORT_CONTEXT_UNAVAILABLE" -> UserMessage.Resource(R.string.this_content_is_no_longer_available_to_report)
            "REPORT_LIMIT_REACHED" -> UserMessage.Resource(R.string.you_ve_sent_the_most_reports_for_today)
            "REPORT_IDEMPOTENCY_CONFLICT", "VALIDATION_FAILED" -> UserMessage.Resource(R.string.this_report_couldn_t_be_sent_please_try_again)
            else -> UserMessage.Api(error)
        }
    }
}

/** The note limit counts Unicode scalars, as the server does. */
internal fun String.scalarCount(): Int = codePointCount(0, length)

private fun String.takeCodePoints(max: Int): String = if (scalarCount() <= max) this else substring(0, offsetByCodePoints(0, max))
