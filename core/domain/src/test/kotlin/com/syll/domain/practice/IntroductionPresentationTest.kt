package com.syll.domain.practice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntroductionPresentationTest {
    @Test fun pickIsTheSwiftHashOfTheSpelling() {
        // reduce(0) { ($0 * 31 + scalar) & 0x7fffffff } % 5 over [cascade, assemble, wave, swipe, flip]:
        // clarify 853581410 → 0, arrive 738326231 → 1, luggage 514048054 → 4.
        assertEquals(IntroductionTemplateID.LETTER_CASCADE, IntroductionPresentation.forTerm("clarify").templateId)
        assertEquals(IntroductionTemplateID.WORD_ASSEMBLE, IntroductionPresentation.forTerm("arrive").templateId)
        assertEquals(IntroductionTemplateID.FLIP_LETTERS, IntroductionPresentation.forTerm("luggage").templateId)
    }

    @Test fun sameWordSameRecipeAndNeverChunkMerge() {
        val words = listOf("clarify", "arrive", "luggage", "appreciate", "a", "ice-cream", "naïve", "über")
        words.forEach { word ->
            val pick = IntroductionPresentation.forTerm(word)
            assertEquals(pick, IntroductionPresentation.forTerm(word))
            assertNotEquals(IntroductionTemplateID.CHUNK_MERGE, pick.templateId)
        }
        // Words vary.
        assertTrue(words.map { IntroductionPresentation.forTerm(it).templateId }.toSet().size > 1)
    }

    @Test fun chunkMergeNeedsChunksThatSpellTheWord() {
        val good = IntroductionPresentation(IntroductionTemplateID.CHUNK_MERGE, listOf("cla", "ri", "fy"))
        assertEquals(good, good.resolved("clarify"))
        val bad = IntroductionPresentation(IntroductionTemplateID.CHUNK_MERGE, listOf("cla", "fy"))
        assertEquals(IntroductionTemplateID.SWIPE_REVEAL, bad.resolved("clarify").templateId)
        assertFalse(IntroductionPresentation.areValidChunks(listOf("cla", "", "rify"), "clarify"))
        assertFalse(IntroductionPresentation.areValidChunks(null, "clarify"))
    }

    @Test fun revealDurationsFollowTheRecipe() {
        fun duration(id: IntroductionTemplateID, word: String = "clarify") =
            IntroductionPresentation(id, listOf("cla", "ri", "fy")).revealDuration(word, 0.055, 0.22, 0.45)
        assertEquals(7 * 0.055 + 0.45, duration(IntroductionTemplateID.LETTER_CASCADE), 1e-9)
        assertEquals(0.65, duration(IntroductionTemplateID.WORD_ASSEMBLE), 1e-9)
        assertEquals(7 * 0.05 + 0.45, duration(IntroductionTemplateID.LETTER_WAVE), 1e-9)
        assertEquals(0.8, duration(IntroductionTemplateID.SWIPE_REVEAL), 1e-9)
        assertEquals(7 * 0.06 + 0.45, duration(IntroductionTemplateID.FLIP_LETTERS), 1e-9)
        assertEquals(3 * 0.22 + 0.45 + 0.4, duration(IntroductionTemplateID.CHUNK_MERGE), 1e-9)
        // Graphemes, not UTF-16 units: "é" written as e + combining accent is one letter.
        assertEquals(0.055 + 0.45, IntroductionPresentation(IntroductionTemplateID.LETTER_CASCADE).revealDuration("é", 0.055, 0.22, 0.45), 1e-9)
    }
}
