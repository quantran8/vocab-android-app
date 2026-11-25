package com.syll.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLanguageTest {
    @Test
    fun regionQualifiedTagsMatchTheirLanguage() {
        assertEquals(AppLanguage.VIETNAMESE, AppLanguage.fromLanguageTag("vi-VN"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLanguageTag("en_GB"))
    }

    @Test
    fun portugueseAlwaysMapsToBrazil() {
        assertEquals(AppLanguage.PORTUGUESE_BRAZIL, AppLanguage.fromLanguageTag("pt-PT"))
    }

    @Test
    fun onlyTraditionalChineseMatches() {
        assertEquals(AppLanguage.CHINESE_TRADITIONAL, AppLanguage.fromLanguageTag("zh-Hant-TW"))
        assertEquals(AppLanguage.CHINESE_TRADITIONAL, AppLanguage.fromLanguageTag("zh-HK"))
        assertNull(AppLanguage.fromLanguageTag("zh-Hans-CN"))
        assertNull(AppLanguage.fromLanguageTag("zh-CN"))
    }

    @Test
    fun legacyIndonesianCodeIsAccepted() {
        assertEquals(AppLanguage.INDONESIAN, AppLanguage.fromLanguageTag("in-ID"))
    }

    @Test
    fun deviceFallsBackToFirstKnownThenEnglish() {
        assertEquals(AppLanguage.JAPANESE, AppLanguage.fromPreferred(listOf("fr-FR", "ja-JP")))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromPreferred(listOf("fr-FR", "zh-CN")))
    }
}
