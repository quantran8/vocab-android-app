package com.syll.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordPolicyTest {
    @Test
    fun acceptsEightCharactersWithLetterAndDigit() {
        assertTrue(PasswordPolicy.accepts("abcdefg1"))
    }

    @Test
    fun rejectsEachMissingRule() {
        assertFalse(PasswordPolicy.accepts("abc1"))
        assertFalse(PasswordPolicy.accepts("abcdefgh"))
        assertFalse(PasswordPolicy.accepts("12345678"))
    }

    @Test
    fun rejectsMoreThan72Characters() {
        val password = "a1" + "x".repeat(70)
        assertTrue(PasswordPolicy.accepts(password))
        assertTrue(PasswordPolicy.isTooLong(password + "x"))
        assertFalse(PasswordPolicy.accepts(password + "x"))
    }

    @Test
    fun countsCharactersLikeSwift() {
        // One emoji (two UTF-16 units) and a combined accent count as one character each.
        assertEquals(1, PasswordPolicy.characterCount("👍"))
        assertEquals(1, PasswordPolicy.characterCount("é"))
        assertFalse(PasswordPolicy.Rule.LENGTH.isMetBy("a1👍👍👍👍"))
    }

    @Test
    fun nonLatinLettersAndDigitsCount() {
        assertTrue(PasswordPolicy.Rule.LETTER.isMetBy("123ก"))
        assertTrue(PasswordPolicy.Rule.DIGIT.isMetBy("abc٣"))
    }

    @Test
    fun logInChecksOnlyLength() {
        assertTrue(PasswordPolicy.hasAllowedLength("abcdefgh"))
        assertFalse(PasswordPolicy.hasAllowedLength("abc"))
    }
}
