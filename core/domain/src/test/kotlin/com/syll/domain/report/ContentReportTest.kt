package com.syll.domain.report

import com.syll.domain.practice.PracticeFixtures
import com.syll.domain.practice.TaskAttempt
import com.syll.domain.practice.TaskPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure part of iOS `ContentReportTests`. */
class ContentReportTest {
    @Test
    fun reasonsFollowWhatTheContentHas() {
        assertEquals(
            listOf(ContentReportReason.INCORRECT_MEANING, ContentReportReason.INCORRECT_EXAMPLE, ContentReportReason.OTHER),
            ContentReportTarget.meaningReasons(hasDefinition = false, hasExample = true, hasAudio = false),
        )
        assertFalse(ContentReportReason.ANSWER_SHOULD_BE_ACCEPTED in ContentReportTarget.taskReasons(canAppeal = false, hasAudio = true))
        assertEquals(ContentReportReason.OTHER, ContentReportTarget.taskReasons(canAppeal = true, hasAudio = true).last())
    }

    @Test
    fun aJudgedWrongAnswerCanBeAppealed() {
        val attempt = TaskAttempt(PracticeFixtures.typing(), TaskPhase.DUE_REVIEW)
        assertFalse("no appeal before an answer", attempt.canAppealAnswer)
        attempt.submitText("clarifyy")
        assertTrue(attempt.canAppealAnswer)
        assertEquals("clarifyy", attempt.reportExcerpt?.submittedText)

        val solved = TaskAttempt(PracticeFixtures.typing(), TaskPhase.DUE_REVIEW)
        solved.submitText("clarify")
        assertFalse("an accepted answer has nothing to appeal", solved.canAppealAnswer)
    }

    @Test
    fun onlyTheAppealCarriesTheAnswer() {
        val context = ContentReportTarget.Context.PracticeTask("s1", "t1", ReportAttemptExcerpt(submittedText = "resilient"))
        assertEquals("resilient", ContentReportRequest("k", ContentReportReason.ANSWER_SHOULD_BE_ACCEPTED, context, null).attemptExcerpt?.submittedText)
        assertNull(ContentReportRequest("k", ContentReportReason.INCORRECT_QUESTION, context, null).attemptExcerpt)
    }
}
