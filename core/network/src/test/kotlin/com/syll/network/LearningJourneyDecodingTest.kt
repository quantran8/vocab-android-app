package com.syll.network

import com.syll.domain.journey.LearningJourney
import com.syll.domain.report.ContentReportReason
import com.syll.domain.report.ContentReportRequest
import com.syll.domain.report.ContentReportTarget
import com.syll.domain.report.ReportAttemptExcerpt
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The decoding part of iOS `LearningJourneyTests`, plus the report body. */
class LearningJourneyDecodingTest {
    @Test
    fun decodesTheWeekAndTellsTodayFromTheDaysAhead() {
        val week = ApiJson.decodeFromString(LearningJourney.serializer(), JSON).week
        assertEquals("2026-11-25", week.today?.date)
        assertEquals(listOf("2026-11-23", "2026-11-25"), week.studiedDays.map { it.date })
        assertFalse("three sessions over two days is 2/3 study days", week.goalReached)
        assertEquals(listOf("resilient", "thrive"), week.recentWords?.map { it.lemma })
        assertEquals(5, week.otherNewWords)
        assertTrue(week.days[3].isFuture)
    }

    @Test
    fun aGoallessWeekIsNeverReachedAndANewLearnerHasNoHistory() {
        val json = JSON.replace("\"weeklySessionGoal\":3", "\"weeklySessionGoal\":null")
            .replace("\"historyAvailableFrom\":\"2026-08-01\"", "\"historyAvailableFrom\":null")
        val journey = ApiJson.decodeFromString(LearningJourney.serializer(), json)
        assertNull(journey.week.weeklySessionGoal)
        assertFalse(journey.week.goalReached)
        assertFalse(journey.hasHistory)
    }

    @Test
    fun theGoalCountsStudyDaysNotSessions() {
        val json = JSON.replace("\"weeklySessionGoal\":3", "\"weeklySessionGoal\":2")
        assertTrue(ApiJson.decodeFromString(LearningJourney.serializer(), json).week.goalReached)
    }

    @Test
    fun wordsOfOneDayStayOneGroup() {
        val page = ApiJson.decodeFromString(
            com.syll.domain.journey.JourneyWordsPage.serializer(),
            """{"total":3,"items":[
              {"wordId":"a","lemma":"resilient","meaningTexts":["kiên cường","đàn hồi"],"learnedOn":"2026-11-26"},
              {"wordId":"b","lemma":"thrive","meaningTexts":["phát triển"],"learnedOn":"2026-11-26"},
              {"wordId":"c","lemma":"clarify","meaningTexts":[],"learnedOn":"2026-11-24"}]}""",
        )
        assertEquals("kiên cường; đàn hồi", page.items[0].meaningText)
        val days = com.syll.domain.journey.JourneyWordDay.grouped(page.items)
        assertEquals(listOf("2026-11-26", "2026-11-24"), days.map { it.date })
        assertEquals(listOf("resilient", "thrive"), days[0].words.map { it.lemma })
    }

    @Test
    fun theReportBodyCarriesTheAnswerOnlyForAnAppeal() {
        val context = ContentReportTarget.Context.PracticeTask("s1", "t1", ReportAttemptExcerpt(submittedText = "resilient"))
        val appeal = ContentReportRequest("k", ContentReportReason.ANSWER_SHOULD_BE_ACCEPTED, context, "note").toJson()
        assertEquals("practice_task", appeal["source"]?.jsonPrimitive?.content)
        val body = appeal["context"]!!.jsonObject
        assertEquals("t1", body["taskId"]?.jsonPrimitive?.content)
        assertEquals("resilient", (body["attemptExcerpt"] as JsonObject)["submittedText"]?.jsonPrimitive?.content)

        val question = ContentReportRequest("k", ContentReportReason.INCORRECT_QUESTION, context, null).toJson()
        assertNull(question["context"]!!.jsonObject["attemptExcerpt"])
        assertNull(question["note"])
    }

    private companion object {
        // Wednesday 2026-11-25 in the learner's zone: Monday and Wednesday studied, Thursday on ahead.
        const val JSON = """
        {"asOf":"2026-11-25T04:00:00.000Z","timezone":"Asia/Ho_Chi_Minh","historyAvailableFrom":"2026-08-01","historyComplete":true,
         "week":{"startDate":"2026-11-23","endDateExclusive":"2026-11-30","newWords":7,"completedSessions":3,"activeDays":2,"weeklySessionGoal":3,"recentWords":[{"wordId":"w1","lemma":"resilient"},{"wordId":"w2","lemma":"thrive"}],
           "days":[{"date":"2026-11-23","completed":true,"completedSessions":2,"newWords":5},
                   {"date":"2026-11-24","completed":false,"completedSessions":0,"newWords":0},
                   {"date":"2026-11-25","completed":true,"completedSessions":1,"newWords":2},
                   {"date":"2026-11-26","completed":null,"completedSessions":null,"newWords":null},
                   {"date":"2026-11-27","completed":null,"completedSessions":null,"newWords":null},
                   {"date":"2026-11-28","completed":null,"completedSessions":null,"newWords":null},
                   {"date":"2026-11-29","completed":null,"completedSessions":null,"newWords":null}]},
         "period":{"kind":"week","startDate":"2026-11-23","endDateExclusive":"2026-11-30","bucketGranularity":"day",
           "wordsAtStart":241,"wordsAtEnd":248,"newWords":7,"activeDays":2,"completedSessions":3,"reviewedMeanings":12,
           "points":[{"date":"2026-11-23","cumulativeWords":246},{"date":"2026-11-25","cumulativeWords":248}]}}
        """
    }
}
