package com.syll.designsystem.tokens

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * iOS `Typography.swift`: the system font (Roboto here, SF on iOS); identity comes from weight. Sizes follow
 * docs/app-redesign-v1.html at iOS's default Dynamic Type size, in sp, so they scale with the system font size.
 * Heavy = 800 (iOS `.heavy`), black = 900.
 */
object Typography {
    private fun style(size: Int, weight: FontWeight = FontWeight.Normal, tracking: Float = 0f) =
        TextStyle(fontSize = size.sp, fontWeight = weight, letterSpacing = tracking.sp)

    const val displayTracking = -1.2f
    const val sheetTracking = -0.8f
    const val tightTracking = -0.6f
    const val eyebrowTracking = 1.2f

    /** Screen titles: 34 extrabold. */
    val display = style(34, FontWeight.ExtraBold, displayTracking)
    /** Sheet titles: 26 extrabold. */
    val sheetTitle = style(26, FontWeight.ExtraBold, sheetTracking)
    /** Section headers inside a screen: 19 bold. */
    val sectionTitle = style(19, FontWeight.Bold)
    /** Onboarding step questions: 28 extrabold. */
    val stepTitle = style(28, FontWeight.ExtraBold, sheetTracking)
    /** Practice question line and feedback titles: 22 bold. */
    val screenTitle = style(22, FontWeight.Bold)
    val headerTitle = style(22, FontWeight.Bold)
    /** Feedback panel title: 19 extrabold. */
    val feedbackTitle = style(19, FontWeight.ExtraBold)
    /** Practice text prompt: 36 extrabold. */
    val challengeTerm = style(36, FontWeight.ExtraBold)
    /** Word intro hero: 44 extrabold, tracking -1.6. */
    val wordHero = style(44, FontWeight.ExtraBold, -1.6f)
    /** Word detail / add word hero: 38 extrabold. */
    val wordTitle = style(38, FontWeight.ExtraBold)
    val sentence = style(22, FontWeight.Bold)
    /** Base text: 16. */
    val body = style(16)
    val emphasis = style(16, FontWeight.Bold)
    /** Secondary lines, links, messages: 15. */
    val secondary = style(15)
    /** Row subtitles, captions: 13. */
    val micro = style(13)
    /** Pills and eyebrows: 12. */
    val caption = style(12)
    val eyebrow = style(12, FontWeight.Bold, eyebrowTracking)
    val tile = style(16, FontWeight.SemiBold)
    val tileLarge = style(20, FontWeight.SemiBold)
    /** IPA under the word on word detail / add word: 19. */
    val ipaHero = style(19)
    /** IPA line inside a meaning card: 17. */
    val ipaCard = style(17)
    val wordClass = style(17, FontWeight.SemiBold)
    /** The meaning on the word card: 26 extrabold. */
    val wordMeaning = style(26, FontWeight.ExtraBold)
    /** Match tiles and Match Madness: 19. */
    val choice = style(19)
    /** Select answer tiles: 22. */
    val choiceOption = style(22)
    /** Big numbers in stat cards: 30 extrabold. */
    val stat = style(30, FontWeight.ExtraBold)
    /** The overall score inside a scored read-aloud's ring: 56 extrabold. */
    val scoreHero = style(56, FontWeight.ExtraBold)
    /** Secondary figures beside a big stat: 19 extrabold. */
    val statSmall = style(19, FontWeight.ExtraBold)
    /** Button labels: 16 bold. */
    val action = style(16, FontWeight.Bold)
    val actionSmall = style(15, FontWeight.Bold)
    val tab = style(11, FontWeight.SemiBold)
    val wordmark = style(22, FontWeight.Black)
}
