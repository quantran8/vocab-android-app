package com.syll.domain.practice

import java.text.Normalizer

/** iOS `PronunciationSegment`: a run of the prompt word, a syllable with its band or the text between syllables (`band == null`). */
data class PronunciationSegment(val text: String, val band: SyllableBand?)

/** iOS `PronunciationColoring`. */
object PronunciationColoring {
    /**
     * Splits [word] along [syllables] in order, keeping the word's own spelling and case.
     * Null when the syllables don't spell the word (letters left over or out of order): the word stays plain.
     */
    fun segments(word: String, syllables: List<PronunciationSyllable>): List<PronunciationSegment>? {
        if (syllables.isEmpty()) return null
        val folded = FoldedText(word)
        val segments = mutableListOf<PronunciationSegment>()
        var cursor = 0
        for (syllable in syllables) {
            if (syllable.text.isEmpty()) return null
            val range = folded.find(syllable.text, from = cursor) ?: return null
            if (!appendGap(word.substring(cursor, range.first), segments)) return null
            segments += PronunciationSegment(word.substring(range.first, range.last + 1), syllable.band)
            cursor = range.last + 1
        }
        if (!appendGap(word.substring(cursor), segments)) return null
        return segments
    }

    /** Spaces, hyphens and apostrophes between syllables stay uncoloured; a skipped letter means a mismatch. */
    private fun appendGap(gap: String, segments: MutableList<PronunciationSegment>): Boolean {
        if (gap.isEmpty()) return true
        if (gap.any { it.isLetter() }) return false
        segments += PronunciationSegment(gap, null)
        return true
    }

    /**
     * Case- and diacritic-insensitive search (Swift `.caseInsensitive, .diacriticInsensitive`) that answers in the
     * original string's indices, so the found run keeps the word's own spelling.
     */
    private class FoldedText(text: String) {
        private val folded = StringBuilder()
        /** For each folded char, the original char it came from. */
        private val origin = mutableListOf<Int>()
        /** For each original char, where its folding starts. */
        private val start = IntArray(text.length + 1)

        init {
            text.forEachIndexed { index, char ->
                start[index] = folded.length
                fold(char.toString()).forEach {
                    folded.append(it)
                    origin += index
                }
            }
            start[text.length] = folded.length
        }

        /** The original index range of the first match at or after [from], aligned to whole original chars. */
        fun find(needle: String, from: Int): IntRange? {
            val target = fold(needle)
            if (target.isEmpty()) return null
            var at = folded.indexOf(target, start[from])
            while (at >= 0) {
                val end = at + target.length
                val first = origin[at]
                val last = origin[end - 1]
                if (start[first] == at && start[last + 1] == end) return first..last
                at = folded.indexOf(target, at + 1)
            }
            return null
        }

        private fun fold(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFD)
                .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
                .lowercase()
    }
}
