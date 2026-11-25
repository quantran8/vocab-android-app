package com.syll.domain.practice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** iOS `TranscriptMatcherTests`. */
class TranscriptMatcherTest {
    @Test fun exactMatchIgnoringCaseAndPunctuationPasses() {
        assertEquals(true, TranscriptMatcher.assess(listOf("Appreciate!"), "appreciate").passed)
    }

    @Test fun takesCarryNoScore() {
        assertNull(TranscriptMatcher.assess(listOf("appreciate"), "appreciate").accuracyScore)
        assertNull(TranscriptMatcher.assess(listOf("apple"), "appreciate").accuracyScore)
    }

    @Test fun onlyTopCandidateCounts() {
        val assessment = TranscriptMatcher.assess(listOf("a precious", "appreciate"), "appreciate")
        assertEquals("a precious", assessment.heard)
        assertEquals(false, assessment.passed)
    }

    @Test fun differentWordFails() {
        val assessment = TranscriptMatcher.assess(listOf("apricot"), "appreciate")
        assertEquals(true, assessment.isValid)
        assertEquals(false, assessment.passed)
    }

    @Test fun nearMatchFails() {
        assertEquals(false, TranscriptMatcher.assess(listOf("appreciates"), "appreciate").passed)
        assertEquals(false, TranscriptMatcher.assess(listOf("the cat sat on the hat"), "the cat sat on the mat").passed)
    }

    @Test fun nothingRecognizedIsInvalid() {
        val assessment = TranscriptMatcher.assess(listOf("", " . "), "appreciate")
        assertFalse(assessment.isValid)
        assertNull(assessment.passed)
    }

    @Test fun foldsDiacriticsAndCurlyApostrophes() {
        assertEquals(true, TranscriptMatcher.assess(listOf("Café"), "cafe").passed)
        assertEquals(true, TranscriptMatcher.assess(listOf("don’t"), "don't").passed)
    }
}
