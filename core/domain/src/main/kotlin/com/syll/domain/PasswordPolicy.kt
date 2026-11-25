package com.syll.domain

import java.text.BreakIterator

/**
 * iOS `PasswordPolicy`: rules for a password the learner creates (sign up, reset, change). Logging in only checks
 * [length], so accounts made before these rules keep working. The backend enforces the same rules.
 *
 * Lengths count user-perceived characters, like Swift's `String.count`.
 */
object PasswordPolicy {
    /** Allowed password length. Logging in checks only this. */
    val length = 8..72

    enum class Rule {
        LENGTH, LETTER, DIGIT;

        fun isMetBy(password: String): Boolean = when (this) {
            LENGTH -> characterCount(password) >= length.first
            LETTER -> password.codePoints().anyMatch { Character.isLetter(it) || Character.getType(it).isMark() }
            DIGIT -> password.codePoints().anyMatch { Character.getType(it) == Character.DECIMAL_DIGIT_NUMBER.toInt() }
        }
    }

    fun isTooLong(password: String): Boolean = characterCount(password) > length.last

    fun accepts(password: String): Boolean = !isTooLong(password) && Rule.entries.all { it.isMetBy(password) }

    /** Whether a log-in password has an allowed length. */
    fun hasAllowedLength(password: String): Boolean = characterCount(password) in length

    /** Grapheme clusters, Swift `String.count`. */
    fun characterCount(text: String): Int {
        val iterator = BreakIterator.getCharacterInstance()
        iterator.setText(text)
        var count = 0
        while (iterator.next() != BreakIterator.DONE) count += 1
        return count
    }

    // `CharacterSet.letters` covers the L* and M* categories.
    private fun Int.isMark(): Boolean = this == Character.NON_SPACING_MARK.toInt() ||
        this == Character.ENCLOSING_MARK.toInt() || this == Character.COMBINING_SPACING_MARK.toInt()
}
