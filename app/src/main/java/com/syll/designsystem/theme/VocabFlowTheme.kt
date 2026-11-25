package com.syll.designsystem.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography

/**
 * Light only, like iOS. Material is here for the controls that stay platform-default (Switch, NavigationBar,
 * ModalBottomSheet, top app bar back arrow); its colours map onto Syll's tokens so they don't look foreign.
 * Syll's own components (buttons, cards, chips) never take Material's styling.
 */
@Composable
fun VocabFlowTheme(content: @Composable () -> Unit) {
    val colors = lightColorScheme(
        primary = SyllColors.ink800,
        onPrimary = SyllColors.surface,
        secondary = SyllColors.blue500,
        onSecondary = SyllColors.surface,
        background = SyllColors.canvas,
        onBackground = SyllColors.ink,
        surface = SyllColors.surface,
        onSurface = SyllColors.ink,
        surfaceVariant = SyllColors.ink100,
        onSurfaceVariant = SyllColors.mutedInk,
        surfaceContainer = SyllColors.surface,
        surfaceContainerLow = SyllColors.surface,
        surfaceContainerHigh = SyllColors.surface,
        outline = SyllColors.stroke,
        outlineVariant = SyllColors.divider,
        error = SyllColors.error,
        scrim = SyllColors.ink950,
    )
    MaterialTheme(colorScheme = colors) {
        CompositionLocalProvider(
            LocalContentColor provides SyllColors.ink,
            LocalTextStyle provides Typography.body,
            content = content,
        )
    }
}
