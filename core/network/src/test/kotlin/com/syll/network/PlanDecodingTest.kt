package com.syll.network

import com.syll.domain.practice.ClozeLine
import com.syll.domain.practice.CommitResponse
import com.syll.domain.practice.CommitSummary
import com.syll.domain.practice.DeliveryMode
import com.syll.domain.practice.KnownCheck
import com.syll.domain.practice.LearnCard
import com.syll.domain.practice.MeaningCue
import com.syll.domain.practice.NewWordGroup
import com.syll.domain.practice.NewWordsIssue
import com.syll.domain.practice.PairKey
import com.syll.domain.practice.PlanRequest
import com.syll.domain.practice.PlanStep
import com.syll.domain.practice.PracticeFilter
import com.syll.domain.practice.PracticeGroup
import com.syll.domain.practice.PracticeTask
import com.syll.domain.practice.PromptHighlight
import com.syll.domain.practice.ResultCause
import com.syll.domain.practice.ResultSkill
import com.syll.domain.practice.ReviewGroup
import com.syll.domain.practice.SentenceParts
import com.syll.domain.practice.SessionMode
import com.syll.domain.practice.SessionPlan
import com.syll.domain.practice.SpeakingStatus
import com.syll.domain.practice.TaskAttempt
import com.syll.domain.practice.TaskBody
import com.syll.domain.practice.TaskOption
import com.syll.domain.practice.TaskPhase
import com.syll.domain.practice.TaskPrompt
import com.syll.domain.NewWordReason
import com.syll.domain.practice.NeedPracticeCount
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Port of iOS `PlanDecodingTests` (plan, task and commit summary parts) and the plan part of `ScopeReviewLoadTests`. */
class PlanDecodingTest {
    private fun decode(json: String): SessionPlan = ApiJson.decodeFromString(SessionPlan.serializer(), json)

    private fun decodePlan(steps: String) =
        decode("""{"sessionId":"s1","mode":"daily","expiresAt":"2026-11-03T10:00:00.000Z","steps":[$steps]}""")

    private fun decodeTask(body: String): PracticeTask {
        val step = decodePlan("""{"type":"practice","phase":"new_practice","task":{"id":"t1",$body}}""").steps.first()
        return (step as PlanStep.Practice).task
    }

    private fun assertFails(block: () -> Unit) {
        try {
            block()
            fail("Expected the plan to fail")
        } catch (e: SerializationException) {
            // Expected: ApiClient turns it into InvalidResponse.
        } catch (e: IllegalArgumentException) {
            // kotlinx wraps some failures in its own IllegalArgumentException subclasses.
        }
    }

    private val text = """"prompt":{"type":"text","text":"x"}"""

    @Test
    fun emptyPlanHasNoSession() {
        val plan = decode("""{"sessionId":null,"mode":"daily","steps":[]}""")
        assertNull(plan.sessionId)
        assertTrue(plan.steps.isEmpty())
    }

    @Test
    fun decodesLearnStep() {
        val plan = decodePlan(
            """{"type":"learn","id":"l1","card":{"word":"clarify","meaning":"làm rõ","definition":"d",
               "audio":{"url":"https://a.example/x.mp3","locale":"en-US"},"examples":[{"text":"t","translation":"u"}]}}""",
        )
        val step = plan.steps[0] as PlanStep.Learn
        assertEquals("l1", step.id)
        assertEquals("clarify", step.card.word)
        assertNull(step.card.ipa)
        assertNull(step.card.partOfSpeech)
        assertNull(step.card.wordId)
        assertEquals("en-US", step.card.audio?.locale)
    }

    @Test
    fun decodesEveryTemplate() {
        assertEquals(
            TaskBody.Choice(listOf(TaskOption(1, "a"), TaskOption(2, "b")), null, 2),
            decodeTask(""""template":"select","variant":"gloss",$text,"question":"q","options":[{"id":1,"text":"a"},{"id":2,"text":"b"}],"evaluation":{"correctOptionId":2}""").body,
        )
        assertEquals(
            TaskBody.Choice(listOf(TaskOption(1, "a")), SentenceParts("a ", " b"), 1),
            decodeTask(""""template":"cloze_select","variant":"single_blank",$text,"sentenceParts":{"before":"a ","after":" b"},"options":[{"id":1,"text":"a"}],"evaluation":{"correctOptionId":1}""").body,
        )
        assertEquals(
            TaskBody.Typing(listOf("a")),
            decodeTask(""""template":"typing","variant":"meaning_recall",$text,"evaluation":{"acceptedResponses":["a"]}""").body,
        )
        assertEquals(
            TaskBody.Reorder(listOf(TaskOption(1, "a")), listOf("a")),
            decodeTask(""""template":"fly_reorder","variant":"spelling_recall",$text,"tiles":[{"id":1,"text":"a"}],"evaluation":{"acceptedResponses":["a"]}""").body,
        )
        assertEquals(
            TaskBody.Match(listOf(TaskOption(1, "a")), listOf(TaskOption(5, "b")), listOf(PairKey(1, 5))),
            decodeTask(""""template":"match","variant":"meaning_pairs",$text,"pairs":{"left":[{"id":1,"text":"a"}],"right":[{"id":5,"text":"b"}]},"evaluation":{"correctPairs":[{"leftId":1,"rightId":5}]}""").body,
        )
        assertEquals(
            TaskBody.Surgery("ab", 1, ""),
            decodeTask(""""template":"surgery","variant":"fix_spelling",$text,"word":"ab","evaluation":{"wrongGraphemeIndex":1,"replacement":""}""").body,
        )
        val recording = decodeTask(
            """"template":"recording","variant":"scripted_pronunciation","prompt":{"type":"audio","audio":{"url":"https://a.example/x.mp3","locale":"en-US"}},"assessment":{"kind":"azurePronunciation"},"evaluation":{"acceptedResponses":["a"]}""",
        )
        assertEquals(TaskBody.Recording(listOf("a")), recording.body)
        assertEquals(true, recording.prompt?.isAudio)
        assertEquals(
            TaskBody.Recording(emptyList()),
            decodeTask(""""template":"recording","variant":"scripted_pronunciation",$text,"assessment":{"kind":"azurePronunciation"}""").body,
        )
    }

    @Test
    fun recordingMeaningCueAndIpaAreOptionalAndOnlyOnRecording() {
        val base = """"template":"recording","variant":"scripted_pronunciation","prompt":{"type":"text","text":"clarify"},"evaluation":{"acceptedResponses":["clarify"]}"""
        assertEquals(
            MeaningCue("làm rõ", "Which English word means this?"),
            decodeTask(base + ""","meaningCue":{"text":"làm rõ","question":"Which English word means this?"}""").meaningCue,
        )
        assertNull(decodeTask(base).meaningCue)
        assertEquals("/beɪs/", decodeTask(base + ""","ipa":"/beɪs/"""").ipa)
        assertNull(decodeTask(""""template":"typing","variant":"meaning_recall",$text,"evaluation":{"acceptedResponses":["a"]},"ipa":"/x/"""").ipa)
    }

    @Test
    fun maxRetriesIsOptional() {
        val body = """"template":"typing","variant":"meaning_recall",$text,"evaluation":{"acceptedResponses":["a"]}"""
        assertEquals(2, decodeTask(body + ""","maxRetries":2""").maxRetries)
        assertNull("Sessions issued before the limit stay unlimited", decodeTask(body).maxRetries)
    }

    @Test
    fun onlyClozeMayComeWithoutAPrompt() {
        val cloze = decodeTask(""""template":"cloze_select","variant":"single_blank","sentenceParts":{"before":"a ","after":" b"},"options":[{"id":1,"text":"a"}],"evaluation":{"correctOptionId":1}""")
        assertNull(cloze.prompt)
        assertNull(TaskAttempt(cloze, TaskPhase.DUE_REVIEW).deliveryMode)
        assertFails { decodeTask(""""template":"typing","variant":"meaning_recall","evaluation":{"acceptedResponses":["a"]}""") }
    }

    @Test
    fun promptHighlightDecodesAndSplits() {
        val task = decodeTask(""""template":"typing","variant":"meaning_recall","prompt":{"type":"text","text":"Sign the contract.","highlight":{"start":9,"end":17}},"evaluation":{"acceptedResponses":["a"]}""")
        val prompt = task.prompt as TaskPrompt.Text
        val parts = prompt.highlight!!.split(prompt.text)!!
        assertEquals("Sign the ", parts.before)
        assertEquals("contract", parts.word)
        assertEquals(".", parts.after)
    }

    @Test
    fun promptHighlightCountsUtf16UnitsAndIgnoresOffsetsOutsideTheText() {
        assertEquals("pay", PromptHighlight(3, 6).split("👍 pay")?.word)
        assertNull(PromptHighlight(2, 9).split("short"))
        assertNull(PromptHighlight(2, 2).split("short"))
    }

    @Test
    fun clozeNeedsItsSentence() {
        assertFails { decodeTask(""""template":"cloze_select","variant":"single_blank","options":[{"id":1,"text":"a"}],"evaluation":{"correctOptionId":1}""") }
    }

    @Test
    fun clozeLineKeepsPunctuationOnTheBlank() {
        val line = ClozeLine.of(SentenceParts("See the ", ". Then pay"))
        assertEquals(listOf("See", "the"), line.wordsBefore)
        assertEquals("", line.attachedBefore)
        assertEquals(".", line.attachedAfter)
        assertEquals(listOf("Then", "pay"), line.wordsAfter)
    }

    @Test
    fun clozeLineHandlesABlankAtEitherEnd() {
        val line = ClozeLine.of(SentenceParts("", " is late"))
        assertEquals(emptyList<String>(), line.wordsBefore)
        assertEquals("", line.attachedAfter)
        assertEquals(listOf("is", "late"), line.wordsAfter)
        assertEquals("the", ClozeLine.of(SentenceParts("Pay the", "")).attachedBefore)
    }

    @Test
    fun unknownTemplateVariantOrMissingKeyFailsThePlan() {
        assertFails { decodeTask(""""template":"hologram","variant":"gloss",$text""") }
        assertFails { decodeTask(""""template":"typing","variant":"gloss",$text,"evaluation":{"acceptedResponses":["a"]}""") }
        assertFails { decodeTask(""""template":"typing","variant":"meaning_recall",$text,"evaluation":{}""") }
    }

    @Test
    fun unknownPhaseIsOnlyALabel() {
        val plan = decodePlan("""{"type":"practice","phase":"somethingNew","task":{"id":"t1","template":"recording","variant":"scripted_pronunciation",$text}}""")
        assertEquals(TaskPhase.OTHER, (plan.steps[0] as PlanStep.Practice).phase)
        val known = decodePlan("""{"type":"practice","phase":"new_practice","task":{"id":"t1","template":"recording","variant":"scripted_pronunciation",$text}}""")
        assertEquals(TaskPhase.NEW_PRACTICE, (known.steps[0] as PlanStep.Practice).phase)
    }

    @Test
    fun sessionModesUseWireValues() {
        val capabilities = PlanRequest.Capabilities(audioPlayback = true, microphone = true)
        val body = PlanRequest(SessionMode.WEAK_WORDS_PRACTICE, capabilities).toJson().toString()
        assertEquals("""{"mode":"weak_words_practice","capabilities":{"audioPlayback":true,"microphone":true}}""", body)
        assertEquals(SessionMode.CHECKPOINT_CHALLENGE, decode("""{"sessionId":null,"mode":"checkpoint_challenge","steps":[]}""").mode)
        assertEquals(TaskPhase.SPEAKING_PRACTICE, TaskPhase.of("speaking_practice"))
    }

    @Test
    fun filterEncodesTheOneKeyTheBackendExpects() {
        assertEquals("""{"collectionId":"c1"}""", PracticeFilter.Collection("c1").toJson().toString())
        assertEquals("""{"topicIds":["t1"]}""", PracticeFilter.Topics(listOf("t1")).toJson().toString())
        assertEquals("""{"customOnly":true}""", PracticeFilter.CustomWords.toJson().toString())
    }

    @Test
    fun decodesLearnCardPartOfSpeech() {
        val card = ApiJson.decodeFromString(LearnCard.serializer(), """{"word":"clarify","meaning":"làm rõ","partOfSpeech":"verb","wordId":"w","meaningId":"m"}""")
        assertEquals("verb", card.partOfSpeech)
        assertEquals("w", card.wordId)
        assertEquals("m", card.meaningId)
    }

    @Test
    fun decodesPlanExtras() {
        val plan = decode(
            """{"sessionId":"s1","mode":"daily","newWords":{"requested":5,"issued":2,"withheldReason":"user_override"},
             "reviewWords":[{"wordId":"w1","meaningId":"m1","lemma":"go","sourceTopic":{"id":"t1","name":"Travel"}}],
             "steps":[
              {"type":"practice","phase":"due_review","sourceTopic":{"id":"t1","name":"Travel"},
               "task":{"id":"r1","template":"typing","variant":"meaning_recall","prompt":{"type":"text","text":"đi"},"evaluation":{"acceptedResponses":["go"]}}},
              {"type":"practice","phase":"known_check",
               "task":{"id":"k1","template":"typing","variant":"meaning_recall","prompt":{"type":"text","text":"làm rõ"},"evaluation":{"acceptedResponses":["clarify"]}}},
              {"type":"learn","id":"l1","knownCheckTaskId":"k1","skippableTaskIds":["n1"],"card":{"word":"clarify","meaning":"làm rõ","definition":null}}
             ]}""",
        )
        assertEquals(NewWordsIssue(5, 2, NewWordReason.USER_OVERRIDE), plan.newWords)
        assertEquals("Travel", plan.reviewWords?.first()?.sourceTopic?.name)
        assertEquals("Travel", plan.sourceTopics["r1"]?.name)
        assertEquals(KnownCheck("l1", setOf("n1")), plan.knownChecks["k1"])
        assertEquals(TaskPhase.KNOWN_CHECK, (plan.steps[1] as PlanStep.Practice).phase)
    }

    // Commit summary for the results screen

    private fun decodeSummary(summary: String): CommitSummary =
        ApiJson.decodeFromString(CommitResponse.serializer(), """{"sessionId":"s1","summary":$summary}""").summary

    @Test
    fun summaryFromAnOlderBackendHasNoResultBlocks() {
        val summary = decodeSummary("""{"newWordGoalSlots":2,"reviewedMeanings":1,"practiceTasks":9}""")
        assertEquals(9, summary.practiceTasks)
        assertNull(summary.review)
        assertNull(summary.newWords)
        assertNull(summary.practice)
        assertNull(decodeSummary("""{"review":null,"newWords":null}""").practiceTasks)
    }

    @Test
    fun dailySummaryDecodesGroupsAndSkipsUnknownOnes() {
        val summary = decodeSummary(
            """{"review":{"total":2,"groups":{
                "solid":[{"meaningId":"m1","wordId":"w1","lemma":"prevalence","gloss":"tỷ lệ phổ biến","nextReviewAt":"2026-11-25T13:00:00Z","learnedAt":"2026-11-09T12:10:00.000Z"}],
                "relearn":[{"meaningId":"m2","lemma":"chronic","cause":"out_of_tries","struggledAt":"writing"}],
                "shaky":[{"meaningId":"m5","lemma":"budget","cause":"something_new"}],
                "forgotten":[{"oddShape":true}]}},
             "newWords":{"total":1,"groups":{
                "retried":[{"meaningId":"m3","lemma":"fatigue","struggledAt":"grammar"}],
                "revealed":[{"meaningId":"m4","lemma":"immune","struggledAt":"speaking"}]}}}""",
        )
        val review = summary.review!!
        assertEquals("prevalence", review.groups[ReviewGroup.SOLID].first().lemma)
        assertNotNull(review.groups[ReviewGroup.SOLID].first().learnedAt)
        assertEquals("", review.groups[ReviewGroup.RELEARN].first().gloss)
        assertEquals(ResultCause.OUT_OF_TRIES, review.groups[ReviewGroup.RELEARN].first().cause)
        assertEquals("An older session's `writing` is spelling now", ResultSkill.SPELLING, review.groups[ReviewGroup.RELEARN].first().struggledAt)
        assertEquals(ResultCause.UNKNOWN, review.groups[ReviewGroup.SHAKY].first().cause)
        assertNull(review.groups[ReviewGroup.SOLID].first().cause)
        val newWords = summary.newWords!!
        assertEquals(ResultSkill.UNKNOWN, newWords.groups[NewWordGroup.RETRIED].first().struggledAt)
        assertEquals(ResultSkill.SPEAKING, newWords.groups[NewWordGroup.REVEALED].first().struggledAt)
        assertEquals(emptyList<Any>(), newWords.groups[NewWordGroup.KNOWN])
    }

    @Test
    fun practiceSummaryDecodesWithUnknownSkills() {
        val summary = decodeSummary(
            """{"review":null,"newWords":null,
             "practice":{"total":1,"excluded":{"count":2,"reasons":["invalid_recording","something_new"]},
                "groups":{"clean":[{"meaningId":"m1","lemma":"vivid","skill":"grammar"},{"meaningId":"m2","lemma":"budget","skill":null}]}},
             "speaking":null,"skillChanges":null,
             "needPractice":{"before":3,"after":null},
             "countedAsReview":[{"meaningId":"m1","lemma":"vivid","nextReviewAt":"2026-11-22T13:00:00Z"}]}""",
        )
        val practice = summary.practice!!
        assertEquals(2, practice.excluded?.count)
        assertEquals(ResultSkill.UNKNOWN, practice.groups[PracticeGroup.CLEAN].first().skill)
        assertNull(practice.groups[PracticeGroup.CLEAN].last().skill)
        assertNull(summary.speaking)
        assertEquals(NeedPracticeCount(3, null), summary.needPractice)
        assertEquals(1, summary.countedAsReview?.size)
    }

    @Test
    fun speakingSummaryDecodes() {
        val speaking = decodeSummary(
            """{"review":null,"newWords":null,
             "speaking":{"total":3,"passed":2,
                "becameFluent":[{"meaningId":"m1","wordId":"w1","lemma":"vivid","gloss":"sống động","status":"fluent","score":91}],
                "slipped":[{"meaningId":"m2","lemma":"budget","gloss":"ngân sách","status":"practicing","score":null}]}}""",
        ).speaking!!
        assertEquals(3, speaking.total)
        assertEquals(2, speaking.passed)
        assertEquals(SpeakingStatus.FLUENT, speaking.becameFluent.first().status)
        assertEquals(91.0, speaking.becameFluent.first().score)
        assertEquals(SpeakingStatus.PRACTICING, speaking.slipped.first().status)
        assertNull(speaking.slipped.first().score)
    }

    @Test
    fun anAudioPromptSendsItsDeliveryMode() {
        val task = decodeTask(""""template":"typing","variant":"transcription","prompt":{"type":"audio","audio":{"url":"https://a.example/x.mp3","locale":"en-US"}},"evaluation":{"acceptedResponses":["a"]}""")
        val attempt = TaskAttempt(task, TaskPhase.DUE_REVIEW)
        assertEquals(DeliveryMode.NONE, attempt.deliveryMode)
        assertEquals(""""deliveryMode":"none"""", attempt.result.toJson().toString().let { Regex("\"deliveryMode\":\"\\w+\"").find(it)!!.value })
    }
}
