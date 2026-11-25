package com.syll.app

import com.syll.domain.practice.KnownCheck
import com.syll.domain.practice.LearnCard
import com.syll.domain.practice.PairKey
import com.syll.domain.practice.PlanRequest
import com.syll.domain.practice.PlanReviewWord
import com.syll.domain.practice.PlanStep
import com.syll.domain.practice.PracticeTask
import com.syll.domain.practice.SentenceParts
import com.syll.domain.practice.SessionMode
import com.syll.domain.practice.SessionPlan
import com.syll.domain.practice.SourceTopic
import com.syll.domain.practice.TaskBody
import com.syll.domain.practice.TaskOption
import com.syll.domain.practice.TaskPhase
import com.syll.domain.practice.TaskPrompt
import com.syll.domain.practice.TaskTemplate

/**
 * Plans for [OfflineVocabApi]: one of every task template, so a session can be walked on a device without a backend.
 * Daily: two reviews, a known check, a new word and its practice. Review-only and practice modes reuse the reviews.
 */
object OfflinePlans {
    private val travel = SourceTopic("travel", "Travel")

    private fun text(value: String) = TaskPrompt.Text(value)

    private val reviews = listOf(
        PlanStep.Practice(
            TaskPhase.DUE_REVIEW,
            PracticeTask(
                id = "r-select", template = TaskTemplate.SELECT, variant = "gloss", prompt = text("arrive"),
                question = "What does this word mean?",
                body = TaskBody.Choice(
                    listOf(TaskOption(1, "to reach a place"), TaskOption(2, "to leave a place"), TaskOption(3, "to wait"), TaskOption(4, "to pay")),
                    sentence = null, correctOptionId = 1,
                ),
                maxRetries = 2,
            ),
        ),
        PlanStep.Practice(
            TaskPhase.DUE_REVIEW,
            PracticeTask(
                id = "r-cloze", template = TaskTemplate.CLOZE_SELECT, variant = "single_blank", prompt = null,
                question = "Choose the word that fits.",
                body = TaskBody.Choice(
                    listOf(TaskOption(1, "luggage"), TaskOption(2, "ticket"), TaskOption(3, "passport")),
                    sentence = SentenceParts("Please put your ", " in the overhead bin."), correctOptionId = 1,
                ),
                maxRetries = 2,
            ),
        ),
    )

    private val card = LearnCard(
        word = "clarify",
        meaning = "make clear",
        definition = "To make a statement or situation less confused and easier to understand.",
        ipa = "/ˈklær.ə.faɪ/",
        examples = listOf(LearnCard.Example("Could you clarify what you mean?", null)),
        partOfSpeech = "verb",
    )

    private val newWord = listOf(
        PlanStep.Practice(
            TaskPhase.KNOWN_CHECK,
            PracticeTask(
                id = "k-clarify", template = TaskTemplate.TYPING, variant = "meaning_recall", prompt = text("make clear"),
                question = "Type the English word", body = TaskBody.Typing(listOf("clarify")), maxRetries = 0,
            ),
        ),
        PlanStep.Learn("l-clarify", card),
        PlanStep.Practice(
            TaskPhase.NEW_PRACTICE,
            PracticeTask(
                id = "n-reorder", template = TaskTemplate.FLY_REORDER, variant = "spelling_recall", prompt = text("make clear"),
                question = "Spell the word",
                body = TaskBody.Reorder(
                    listOf("fy", "cla", "ri").mapIndexed { index, chunk -> TaskOption(index + 1, chunk) },
                    accepted = listOf("clarify"),
                ),
                maxRetries = 2,
            ),
        ),
        PlanStep.Practice(
            TaskPhase.NEW_PRACTICE,
            PracticeTask(
                id = "n-typing", template = TaskTemplate.TYPING, variant = "meaning_recall", prompt = text("make clear"),
                question = "Type the English word", body = TaskBody.Typing(listOf("clarify")), maxRetries = 2,
            ),
        ),
        PlanStep.Practice(
            TaskPhase.NEW_PRACTICE,
            PracticeTask(
                id = "n-surgery", template = TaskTemplate.SURGERY, variant = "fix_spelling", prompt = text("make clear"),
                question = "Fix the spelling", body = TaskBody.Surgery("clarrify", wrongGraphemeIndex = 4, replacement = ""),
                maxRetries = 2,
            ),
        ),
        PlanStep.Practice(
            TaskPhase.NEW_PRACTICE,
            PracticeTask(
                id = "n-match", template = TaskTemplate.MATCH, variant = "meaning_pairs", prompt = text("Match"),
                question = "Match the pairs",
                body = TaskBody.Match(
                    left = listOf(TaskOption(1, "clarify"), TaskOption(2, "arrive"), TaskOption(3, "luggage")),
                    right = listOf(TaskOption(5, "bags"), TaskOption(6, "make clear"), TaskOption(7, "reach a place")),
                    correct = listOf(PairKey(1, 6), PairKey(2, 7), PairKey(3, 5)),
                ),
                maxRetries = 2,
            ),
        ),
    )

    fun plan(request: PlanRequest): SessionPlan {
        val reviewWords = listOf(
            PlanReviewWord("w-arrive", "m-arrive", "arrive", travel),
            PlanReviewWord("w-luggage", "m-luggage", "luggage", travel),
        )
        return when {
            request.mode == SessionMode.DAILY && request.reviewOnly != true -> SessionPlan(
                sessionId = "offline-daily",
                mode = SessionMode.DAILY,
                steps = reviews + newWord,
                reviewWords = reviewWords,
                sourceTopics = mapOf("r-select" to travel, "r-cloze" to travel),
                knownChecks = mapOf("k-clarify" to KnownCheck("l-clarify", setOf("n-reorder", "n-typing", "n-surgery"))),
            )
            request.mode == SessionMode.DAILY -> SessionPlan("offline-review", SessionMode.DAILY, reviews, reviewWords = reviewWords)
            else -> SessionPlan("offline-practice", request.mode, reviews + newWord.drop(2))
        }
    }
}
