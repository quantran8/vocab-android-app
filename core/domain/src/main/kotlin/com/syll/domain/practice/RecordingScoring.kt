package com.syll.domain.practice

import java.text.Normalizer

/** iOS `RecordingScoring`: who scores a recording task. */
enum class RecordingScoring {
    /** Upload the audio to `recording-assess`; the receipt is committed. */
    BACKEND,
    /**
     * Transcribe on the device and compare with the task's `acceptedResponses`; each take commits as `transcribed`.
     * Falls back to the backend when the task has no reference text or recognition can't run.
     */
    ON_DEVICE,
    /** The free tier: like [ON_DEVICE] but never uploads (the backend answers `403 PREMIUM_REQUIRED`). */
    ON_DEVICE_ONLY,
}

/**
 * iOS `TranscriptMatcher`: instant feedback only; the backend re-checks the committed transcript the same way.
 * A transcript can't measure pronunciation, so a take is only right or wrong and carries no score.
 */
object TranscriptMatcher {
    fun assess(candidates: List<String>, target: String): RecordingAssessment {
        val expected = normalize(target)
        // Only the recognizer's top pick counts: lower alternatives would let a wrong reading through.
        val heard = candidates.map(::normalize).firstOrNull { it.isNotEmpty() }
        if (expected.isEmpty() || heard == null) {
            return RecordingAssessment(assessmentReceiptId = "on-device", validity = "invalid")
        }
        return RecordingAssessment(
            assessmentReceiptId = "on-device",
            validity = "valid",
            passed = heard == expected,
            heard = heard,
        )
    }

    /** Folds case, diacritics and width; keeps letters, digits and apostrophes; collapses everything else to spaces. */
    fun normalize(text: String): String {
        val folded = Normalizer.normalize(text, Normalizer.Form.NFKD)
            .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
            .lowercase()
            .replace('’', '\'')
        val kept = folded.map { if (it.isLetterOrDigit() || it == '\'') it else ' ' }.joinToString("")
        return kept.split(' ').filter { it.isNotEmpty() }.joinToString(" ")
    }
}
