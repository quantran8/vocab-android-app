package com.syll.domain.practice

/** iOS `PracticeFixtures`. */
object PracticeFixtures {
    fun choice(id: String = "t-choice", maxRetries: Int? = null) = PracticeTask(
        id = id, template = TaskTemplate.SELECT, variant = "gloss", prompt = TaskPrompt.Text("clarify"), question = "?",
        body = TaskBody.Choice(listOf(TaskOption(7, "a"), TaskOption(3, "b")), sentence = null, correctOptionId = 3),
        maxRetries = maxRetries,
    )

    fun typing(id: String = "t-typing", prompt: TaskPrompt = TaskPrompt.Text("làm rõ")) = PracticeTask(
        id = id, template = TaskTemplate.TYPING, variant = "meaning_recall", prompt = prompt, question = null,
        body = TaskBody.Typing(listOf("clarify")),
    )

    fun reorder(id: String = "t-reorder") = PracticeTask(
        id = id, template = TaskTemplate.FLY_REORDER, variant = "spelling_recall", prompt = TaskPrompt.Text("đi"), question = null,
        body = TaskBody.Reorder(listOf(TaskOption(1, "o"), TaskOption(2, "g")), listOf("go")),
    )

    fun match(maxRetries: Int? = null) = PracticeTask(
        id = "t-match", template = TaskTemplate.MATCH, variant = "meaning_pairs", prompt = TaskPrompt.Text("Match"), question = null,
        body = TaskBody.Match(
            left = listOf(TaskOption(1, "a"), TaskOption(2, "b")),
            right = listOf(TaskOption(5, "x"), TaskOption(6, "y")),
            correct = listOf(PairKey(1, 5), PairKey(2, 6)),
        ),
        maxRetries = maxRetries,
    )

    fun surgery() = PracticeTask(
        id = "t-surgery", template = TaskTemplate.SURGERY, variant = "fix_spelling", prompt = TaskPrompt.Text("Fix"), question = null,
        body = TaskBody.Surgery("clarrify", wrongGraphemeIndex = 4, replacement = ""),
    )

    fun recording(prompt: TaskPrompt = TaskPrompt.Text("clarify"), maxRetries: Int? = null) = PracticeTask(
        id = "t-recording", template = TaskTemplate.RECORDING, variant = "scripted_pronunciation", prompt = prompt, question = null,
        body = TaskBody.Recording(listOf("clarify")), maxRetries = maxRetries,
    )

    val clip = AudioClip("https://example.com/a.mp3", "en-US")

    val card = LearnCard(word = "clarify", meaning = "làm rõ")
}
