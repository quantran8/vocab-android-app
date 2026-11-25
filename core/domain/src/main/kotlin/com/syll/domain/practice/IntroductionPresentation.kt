package com.syll.domain.practice

/** iOS `IntroductionTemplateID`: the six motion recipes that introduce a new word's spelling. */
enum class IntroductionTemplateID { LETTER_CASCADE, WORD_ASSEMBLE, LETTER_WAVE, SWIPE_REVEAL, FLIP_LETTERS, CHUNK_MERGE }

/** iOS `IntroductionPresentation`: which recipe a word opens with. Presentation only, never a navigation gate. */
data class IntroductionPresentation(
    val templateId: IntroductionTemplateID,
    /** Curated orthographic groups for `CHUNK_MERGE` only: visual spelling chunks, not syllables. */
    val introChunks: List<String>? = null,
) {
    /** Chunk Merge with missing or malformed chunks falls back to Swipe Reveal, so the word is still shown in full. */
    fun resolved(word: String): IntroductionPresentation {
        if (templateId != IntroductionTemplateID.CHUNK_MERGE || areValidChunks(introChunks, word)) return this
        return IntroductionPresentation(FALLBACK_TEMPLATE, null)
    }

    /** iOS `revealDuration(for:)`: the recipe's visual budget in seconds (style guide §7). */
    fun revealDuration(word: String, letterStagger: Double, chunkStagger: Double, chunkHold: Double): Double {
        val glyphs = PracticeAnswer.graphemes(word).size.toDouble()
        return when (templateId) {
            IntroductionTemplateID.LETTER_CASCADE -> glyphs * letterStagger + 0.45
            IntroductionTemplateID.WORD_ASSEMBLE -> 0.65
            IntroductionTemplateID.LETTER_WAVE -> glyphs * 0.05 + 0.45
            IntroductionTemplateID.SWIPE_REVEAL -> 0.8
            IntroductionTemplateID.FLIP_LETTERS -> glyphs * 0.06 + 0.45
            IntroductionTemplateID.CHUNK_MERGE -> (introChunks?.size ?: 1) * chunkStagger + chunkHold + 0.4
        }
    }

    companion object {
        val FALLBACK_TEMPLATE = IntroductionTemplateID.SWIPE_REVEAL

        /** Chunks must be nonempty and concatenate exactly to the displayed spelling. */
        fun areValidChunks(chunks: List<String>?, word: String): Boolean =
            !chunks.isNullOrEmpty() && chunks.none { it.isEmpty() } && chunks.joinToString("") == word

        /**
         * iOS `VocabularyItem.introPresentation` for a word without an authored recipe: one picked from the spelling,
         * so words vary but a given word always opens the same way (and the same way as on iOS).
         */
        fun forTerm(term: String): IntroductionPresentation {
            val pool = IntroductionTemplateID.entries.filter { it != IntroductionTemplateID.CHUNK_MERGE }
            // Swift: `unicodeScalars.reduce(0) { ($0 &* 31 &+ Int($1.value)) & 0x7fffffff }` on 64-bit Int.
            var pick = 0L
            term.codePoints().forEach { pick = (pick * 31 + it) and 0x7fffffff }
            return IntroductionPresentation(pool[(pick % pool.size).toInt()])
        }
    }
}
