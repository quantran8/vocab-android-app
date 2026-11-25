package com.lamls.vocabflow.domain

/**
 * The languages the app's interface is translated into (iOS `AppLanguage`). Learning content is not affected:
 * meanings and explanations follow the backend's `explanationLocale`.
 *
 * [tag] is the BCP 47 tag the Android resources and per-app language use; iOS uses the same codes.
 */
enum class AppLanguage(
    val tag: String,
    /** The value `PUT /me/settings` accepts. The backend localizes the catalog with it. */
    val uiLocale: String,
    /** Each language's own name, deliberately not translated so people can always find theirs. */
    val nativeName: String,
) {
    VIETNAMESE("vi", "vi-VN", "Tiếng Việt"),
    ENGLISH("en", "en-US", "English"),
    HINDI("hi", "hi-IN", "हिन्दी"),
    SPANISH("es", "es-MX", "Español"),
    JAPANESE("ja", "ja-JP", "日本語"),
    PORTUGUESE_BRAZIL("pt-BR", "pt-BR", "Português (Brasil)"),
    KOREAN("ko", "ko-KR", "한국어"),
    CHINESE_TRADITIONAL("zh-Hant", "zh-TW", "繁體中文"),
    THAI("th", "th-TH", "ไทย"),
    ARABIC("ar", "ar-SA", "العربية"),
    INDONESIAN("id", "id-ID", "Bahasa Indonesia"),
    ;

    val isRightToLeft: Boolean get() = this == ARABIC

    companion object {
        private val traditionalRegions = setOf("TW", "HK", "MO")

        /**
         * Accepts region-qualified tags such as `vi-VN`. Portuguese maps to the Brazilian translation and Chinese
         * only matches the traditional script or its regions. Android's legacy `in` is Indonesian.
         */
        fun fromLanguageTag(languageTag: String): AppLanguage? {
            val parts = languageTag.replace('_', '-').split('-').filter { it.isNotEmpty() }
            val code = parts.firstOrNull()?.lowercase() ?: return null
            return when (code) {
                "pt" -> PORTUGUESE_BRAZIL
                "zh" -> {
                    val rest = parts.drop(1)
                    val isTraditional = rest.any { it.equals("Hant", ignoreCase = true) } ||
                        rest.any { it.length == 2 && it.uppercase() in traditionalRegions }
                    if (isTraditional) CHINESE_TRADITIONAL else null
                }
                "in" -> INDONESIAN
                else -> entries.firstOrNull { it.tag == code }
            }
        }

        /** The first of the device's languages the app has, else English (iOS `AppLanguage.device`). */
        fun fromPreferred(languageTags: List<String>): AppLanguage =
            languageTags.firstNotNullOfOrNull(::fromLanguageTag) ?: ENGLISH
    }
}
