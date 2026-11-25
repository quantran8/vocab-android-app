package com.lamls.vocabflow.designsystem.tokens

import androidx.compose.ui.unit.dp

/** iOS `Spacing.swift`. iOS points map 1:1 to dp. */
object Spacing {
    val xxs = 4.dp
    val xs = 8.dp
    val s = 12.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 32.dp

    val screenGutter = 20.dp
    /** Practice screens use a wider gutter (px-6). */
    val practiceGutter = 24.dp
    val minTouchTarget = 44.dp
    /** Every button face (50 pt); small buttons are [buttonSmallHeight]. */
    val buttonHeight = 50.dp
    val buttonSmallHeight = 36.dp
    val fieldHeight = 48.dp
    val rowMinHeight = 56.dp
    /** Round icon buttons (back, close, settings). */
    val iconButton = 36.dp
    /** Rounded-square icon badges in rows and cards. */
    val iconBadge = 40.dp
    val audioButton = 52.dp
    val tileMinHeight = 60.dp
    /** Match boards (Practice match, Match Madness): taller than a choice tile, easier to hit at speed. */
    val matchTileMinHeight = 84.dp
    /** Sign in with Apple / Google buttons, the same height as `PrimaryButton`. */
    val socialButtonHeight = buttonHeight
    val tileDepth = 3.dp
    val buttonDepth = 4.dp
    val dropHitInset = 12.dp
    val slotMinWidth = 132.dp
}

/** iOS `Radii.swift`. */
object Radii {
    /** Answer tiles, word cards and text fields (rounded-2xl). */
    val tile = 16.dp
    val card = 20.dp
    /** The feedback panel's top corners. */
    val hero = 24.dp
    /** Bottom sheets' top corners. */
    val sheet = 28.dp
    /** Buttons are fully rounded; kept for the few rectangles that sit beside them. */
    val primaryAction = 22.dp
    /** Icon badges, day toggles, letter tiles (rounded-xl). */
    val chip = 12.dp
    val field = 16.dp
    /** Small code badges such as the level codes (rounded-lg). */
    val badge = 8.dp
    /** The highlighter mark behind a word in a sentence (rounded-md). */
    val highlight = 6.dp
}

/** iOS `Borders.swift`. */
object Borders {
    /** Every card's border, selected or not: option rows, panels, `CardSurface`. */
    val card = 2.dp
    /** Hairline between rows inside a card, tab bar top edge, unselected chips. */
    val hairline = 1.5.dp
    /** Focus halo around a focused field or selected letter (ring-4). */
    val focusHalo = 4.dp
}
