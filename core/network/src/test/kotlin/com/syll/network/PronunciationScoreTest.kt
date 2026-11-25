package com.syll.network

import com.syll.domain.practice.PronunciationBand
import com.syll.domain.practice.PronunciationColoring
import com.syll.domain.practice.PronunciationSegment
import com.syll.domain.practice.PronunciationSyllable
import com.syll.domain.practice.RecordingAssessment
import com.syll.domain.practice.SyllableBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** iOS `PronunciationScoreTests`. */
class PronunciationScoreTest {
    private fun syllable(text: String, band: SyllableBand) = PronunciationSyllable(text, band = band)
    private fun decode(json: String) = ApiJson.decodeFromString(RecordingAssessment.serializer(), json)

    // Decoding

    @Test fun decodesBandAndSyllables() {
        val assessment = decode(
            """{"assessmentReceiptId":"r1","validity":"valid","passed":false,"accuracyScore":64,"band":"fair",
               "syllables":[{"text":"ap","ipa":"ə","score":90,"band":"good"},{"text":"ci","ipa":"ʃi","score":42,"band":"poor"}]}""",
        )
        assertEquals(PronunciationBand.FAIR, assessment.band)
        assertEquals(listOf("ap", "ci"), assessment.syllables?.map { it.text })
        assertEquals(42.0, assessment.syllables?.last()?.score)
        assertEquals(SyllableBand.POOR, assessment.syllables?.last()?.band)
        assertEquals(PronunciationBand.FAIR, assessment.scoreBand)
    }

    @Test fun olderBackendDecodesWithoutDetail() {
        val assessment = decode("""{"assessmentReceiptId":"r1","validity":"valid","passed":true,"accuracyScore":92}""")
        assertNull(assessment.band)
        assertNull(assessment.syllables)
        assertEquals(PronunciationBand.EXCELLENT, assessment.scoreBand)
    }

    @Test fun unknownBandsDecodeLeniently() {
        val assessment = decode(
            """{"assessmentReceiptId":"r1","validity":"valid","passed":true,"accuracyScore":81,"band":"stellar",
               "syllables":[{"text":"ap","band":"great"}]}""",
        )
        assertEquals(PronunciationBand.UNKNOWN, assessment.band)
        assertEquals(SyllableBand.UNKNOWN, assessment.syllables?.first()?.band)
        // An unknown band falls back to the score's own band.
        assertEquals(PronunciationBand.GOOD, assessment.scoreBand)
    }

    // Band

    @Test fun noScoreMeansNoScoredResult() {
        assertNull(RecordingAssessment("on-device", "valid", passed = true).scoreBand)
        assertNull(RecordingAssessment("r", "invalid", accuracyScore = 70.0).scoreBand)
    }

    @Test fun fallbackGoodStartsAtThePassMark() {
        assertEquals(PronunciationBand.EXCELLENT, PronunciationBand.fallback(95.0))
        assertEquals(PronunciationBand.EXCELLENT, PronunciationBand.fallback(90.0))
        assertEquals(PronunciationBand.GOOD, PronunciationBand.fallback(80.0))
        assertEquals(PronunciationBand.FAIR, PronunciationBand.fallback(79.9))
        assertEquals(PronunciationBand.FAIR, PronunciationBand.fallback(60.0))
        assertEquals(PronunciationBand.POOR, PronunciationBand.fallback(12.0))
    }

    // Coloring

    @Test fun segmentsFollowTheSyllables() {
        val segments = PronunciationColoring.segments(
            "appreciate",
            listOf(syllable("ap", SyllableBand.GOOD), syllable("pre", SyllableBand.GOOD), syllable("ci", SyllableBand.POOR), syllable("ate", SyllableBand.POOR)),
        )
        assertEquals(
            listOf(
                PronunciationSegment("ap", SyllableBand.GOOD),
                PronunciationSegment("pre", SyllableBand.GOOD),
                PronunciationSegment("ci", SyllableBand.POOR),
                PronunciationSegment("ate", SyllableBand.POOR),
            ),
            segments,
        )
    }

    @Test fun keepsTheWordsCaseAndSeparators() {
        val segments = PronunciationColoring.segments("Ice-cream", listOf(syllable("ice", SyllableBand.GOOD), syllable("cream", SyllableBand.POOR)))
        assertEquals(
            listOf(
                PronunciationSegment("Ice", SyllableBand.GOOD),
                PronunciationSegment("-", null),
                PronunciationSegment("cream", SyllableBand.POOR),
            ),
            segments,
        )
    }

    @Test fun ignoresDiacritics() {
        val segments = PronunciationColoring.segments("café", listOf(syllable("ca", SyllableBand.GOOD), syllable("fe", SyllableBand.POOR)))
        assertEquals(listOf(PronunciationSegment("ca", SyllableBand.GOOD), PronunciationSegment("fé", SyllableBand.POOR)), segments)
    }

    @Test fun mismatchLeavesTheWordPlain() {
        // A skipped letter.
        assertNull(PronunciationColoring.segments("appreciate", listOf(syllable("ap", SyllableBand.GOOD), syllable("ci", SyllableBand.GOOD))))
        // Letters left over at the end.
        assertNull(PronunciationColoring.segments("appreciate", listOf(syllable("ap", SyllableBand.GOOD), syllable("pre", SyllableBand.GOOD))))
        // Out of order.
        assertNull(PronunciationColoring.segments("appreciate", listOf(syllable("pre", SyllableBand.GOOD), syllable("ap", SyllableBand.GOOD))))
        assertNull(PronunciationColoring.segments("appreciate", emptyList()))
    }
}
