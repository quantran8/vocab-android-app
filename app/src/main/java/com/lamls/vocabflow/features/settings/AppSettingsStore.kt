package com.lamls.vocabflow.features.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.os.LocaleListCompat
import com.lamls.vocabflow.data.KeyValueStore
import com.lamls.vocabflow.domain.AppLanguage

/**
 * iOS `AppSettingsStore` (Phase 0 subset: language, reduce motion, haptics). Main-thread only, like the iOS
 * `@MainActor` stores; Compose reads its snapshot state directly.
 *
 * The display language is Android's per-app language: AppCompat stores it and recreates the activity, which
 * also flips the layout direction for Arabic. Until one is picked it follows the device (iOS `AppLanguage.device`).
 */
class AppSettingsStore(private val storage: KeyValueStore) {
    var reduceMotion by mutableStateOf(storage.getBoolean(KEY_REDUCE_MOTION))
        private set

    var hapticsEnabled by mutableStateOf(storage.getBoolean(KEY_HAPTICS, default = true))
        private set

    val language: AppLanguage
        get() {
            val chosen = AppCompatDelegate.getApplicationLocales()
            val tags = if (chosen.isEmpty) LocaleListCompat.getDefault().toTags() else chosen.toTags()
            return AppLanguage.fromPreferred(tags)
        }

    fun setReduceMotion(value: Boolean) {
        reduceMotion = value
        storage.putBoolean(KEY_REDUCE_MOTION, value)
    }

    fun setHapticsEnabled(value: Boolean) {
        hapticsEnabled = value
        storage.putBoolean(KEY_HAPTICS, value)
    }

    fun setLanguage(language: AppLanguage) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
    }

    private fun LocaleListCompat.toTags(): List<String> = (0 until size()).mapNotNull { get(it)?.toLanguageTag() }

    private companion object {
        const val KEY_REDUCE_MOTION = "reduceMotion"
        const val KEY_HAPTICS = "hapticsEnabled"
    }
}
