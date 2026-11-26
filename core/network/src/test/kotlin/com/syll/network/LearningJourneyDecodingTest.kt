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
    fun decodesATopicsWordsPage() {
        val page = ApiJson.decodeFromString(
            com.syll.domain.journey.JourneyWordsPage.serializer(),
            """{"total":3,"items":[
              {"wordId":"a","lemma":"resilient","meaningTexts":["kiên cường","đàn hồi"],"learnedOn":"2026-11-26"},
              {"wordId":"c","lemma":"clarify","learnedOn":"2026-11-24"}]}""",
        )
        assertEquals(3, page.total)
        assertEquals("kiên cường; đàn hồi", page.items[0].meaningText)
        assertTrue(page.items[1].meaningTexts.isEmpty())
    }

    @Test
    fun anOlderBackendHasNoTopicsSoTheCardIsHidden() {
        val period = ApiJson.decodeFromString(LearningJourney.serializer(), JSON).period
        assertNull(period.newWordsByTopic)
        assertNull("no total either: never a zero", period.topicNewWordsTotal)
    }

    @Test
    fun decodesNewWordsByTopic() {
        val json = JSON.replace(
            "\"reviewedMeanings\":12,",
            """"reviewedMeanings":12,"newWordsByTopic":[
              {"topicId":"t1","title":"Travel","iosIcon":"airplane","color":"#3E68DB","count":5,"recentLemmas":["itinerary","layover","customs"],"finishedInPeriod":true},
              {"topicId":null,"title":null,"iosIcon":null,"color":null,"count":2,"recentLemmas":["mine","ours"],"finishedInPeriod":false},
              {"topicId":"t3","title":"Food","count":1}],""",
        )
        val period = ApiJson.decodeFromString(LearningJourney.serializer(), json).period
        val topics = period.newWordsByTopic!!
        assertEquals(listOf("t1", null, "t3"), topics.map { it.topicId })
        assertTrue(topics[0].finishedInPeriod)
        assertEquals(0x3E68DB, topics[0].markHex)
        assertEquals("itinerary, layover, customs…", topics[0].recentLine)
        assertTrue(topics[1].isOwnWords)
        assertEquals("none", topics[1].wordsQueryKey)
        assertEquals("t1", topics[0].wordsQueryKey)
        assertEquals("all of the topic's words are named: no ellipsis", "mine, ours", topics[1].recentLine)
        assertNull(topics[2].color)
        assertNull(topics[2].recentLine)
        assertFalse(topics[2].finishedInPeriod)
        assertEquals("the period's own count wins", 7, period.topicNewWordsTotal)
        assertEquals(8, period.copy(newWords = null).topicNewWordsTotal)
    }

    @Test
    fun ownWordsAndBadColoursHaveNoMark() {
        fun topic(id: String?, color: String?) = LearningJourney.TopicNewWords(topicId = id, color = color, count = 1)
        assertNull("own words are neutral even with a colour", topic(null, "#3E68DB").markHex)
        assertNull(topic("t", "3E68DB").markHex)
        assertNull(topic("t", "#3E68DBFF").markHex)
        assertNull(topic("t", "#GGGGGG").markHex)
        assertEquals(0x1FA857, topic("t", "#1fa857").markHex)
    }

    @Test
    fun fourTopicRowsThenShowMore() {
        val topics = (1..6).map { LearningJourney.TopicNewWords(topicId = "t$it", count = 10 - it) }
        assertEquals(listOf("t1", "t2", "t3", "t4"), LearningJourney.TopicNewWords.visible(topics, expanded = false).map { it.topicId })
        assertEquals(6, LearningJourney.TopicNewWords.visible(topics, expanded = true).size)
        assertEquals(2, LearningJourney.TopicNewWords.hiddenCount(topics))
        assertEquals(0, LearningJourney.TopicNewWords.hiddenCount(topics.take(4)))
    }

    @Test
    fun catalogTopicsDecodeTheirColour() {
        val collection = ApiJson.decodeFromString(
            CatalogCollection.serializer(),
            """{"id":"c","slug":"everyday","title":"Everyday","topics":[
              {"id":"t1","slug":"travel","title":"Travel","iosIcon":"airplane","color":"#3E68DB"},
              {"id":"t2","slug":"food","title":"Food"}]}""",
        )
        assertEquals("#3E68DB", collection.topics[0].color)
        assertNull(collection.topics[1].color)
        assertEquals("#3E68DB", collection.toEntry().topics[0].color)
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
