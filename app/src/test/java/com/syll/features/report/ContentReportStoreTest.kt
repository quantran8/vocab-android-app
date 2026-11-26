package com.syll.features.report

import com.syll.R
import com.syll.app.UserMessage
import com.syll.domain.report.ContentReportReason
import com.syll.domain.report.ContentReportTarget
import com.syll.domain.report.ReportAttemptExcerpt
import com.syll.features.auth.StubVocabApi
import com.syll.network.ApiError
import com.syll.network.ContentReportReceipt
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The store part of iOS `ContentReportTests`. */
class ContentReportStoreTest {
    private val api = StubVocabApi()
    private val target = ContentReportTarget(
        context = ContentReportTarget.Context.PracticeTask("s1", "t1", ReportAttemptExcerpt(submittedText = "resilient")),
        subtitle = null,
        reasons = ContentReportTarget.taskReasons(canAppeal = true, hasAudio = false),
    )

    @Test
    fun aRetryReusesTheKeyAndAnAnswerSpendsIt() = runBlocking {
        val ids = ArrayDeque(listOf("k1", "k2", "k3"))
        val store = ContentReportStore(api, makeId = { ids.removeFirst() })
        api.contentReportResults += Result.failure(ApiError.Transport)
        api.contentReportResults += Result.success(ContentReportReceipt("r", false))

        assertFalse(store.submit(target, ContentReportReason.INCORRECT_QUESTION, " typo "))
        assertTrue(store.submit(target, ContentReportReason.INCORRECT_QUESTION, "typo"))
        assertEquals(listOf("k1", "k1"), api.contentReports.map { it.clientReportId })
        assertEquals("typo", api.contentReports.last().note)

        store.submit(target, ContentReportReason.INCORRECT_QUESTION, "typo")
        assertEquals("a new send after success is a new report", "k3", api.contentReports.last().clientReportId)
    }

    @Test
    fun aChangedReportGetsANewKey() = runBlocking {
        val ids = ArrayDeque(listOf("k1", "k2"))
        val store = ContentReportStore(api, makeId = { ids.removeFirst() })
        api.contentReportResults += Result.failure(ApiError.Transport)
        store.submit(target, ContentReportReason.INCORRECT_QUESTION, "")
        store.submit(target, ContentReportReason.INCORRECT_MEANING, "")
        assertEquals(listOf("k1", "k2"), api.contentReports.map { it.clientReportId })
    }

    @Test
    fun anOwnEditIsExplained() = runBlocking {
        val store = ContentReportStore(api)
        api.contentReportResults += Result.failure(ApiError.Server(422, "REPORT_TARGET_OVERRIDDEN", "", false, null))
        assertFalse(store.submit(target, ContentReportReason.INCORRECT_MEANING, ""))
        assertEquals(
            ContentReportStore.Phase.Failed(UserMessage.Resource(R.string.this_meaning_shows_your_own_edit_so_it_can_t_be)),
            store.phase,
        )
    }
}
