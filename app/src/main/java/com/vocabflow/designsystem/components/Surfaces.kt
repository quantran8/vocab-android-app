package com.vocabflow.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.vocabflow.designsystem.tokens.Borders
import com.vocabflow.designsystem.tokens.Radii
import com.vocabflow.designsystem.tokens.Spacing
import com.vocabflow.designsystem.tokens.SyllColors
import com.vocabflow.designsystem.tokens.Typography

/**
 * iOS `CardSurface`: every card border is `Borders.card` (2 dp, Ink 200, radius 20, no shadow), selected or not;
 * selection changes only the colour. [accent] draws the 6 dp bar across the top.
 */
fun Modifier.cardSurface(
    radius: Dp = Radii.card,
    fill: Color = SyllColors.surface,
    accent: Color? = null,
    stroke: Color = SyllColors.stroke,
): Modifier {
    val shape = RoundedCornerShape(radius)
    return fillMaxWidth()
        .clip(shape)
        .background(fill)
        .drawWithContent {
            drawContent()
            if (accent != null) drawRect(accent, size = size.copy(height = (Spacing.xxs + Spacing.xxs / 2).toPx()))
        }
        .border(Borders.card, stroke, shape)
}

/** iOS `CardDivider`: rows inside a card are split by a 1.5 dp Ink 100 line. */
@Composable
fun CardDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(Borders.hairline).background(SyllColors.divider))
}

/** iOS `StatusScreen`: a centred title and message with optional busy spinner and pinned actions. */
@Composable
fun StatusScreen(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    isBusy: Boolean = false,
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier.fillMaxSize().padding(horizontal = Spacing.screenGutter).padding(bottom = Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Spacer(Modifier.weight(1f))
        if (isBusy) CircularProgressIndicator(color = SyllColors.mutedInk)
        Text(
            title,
            style = Typography.screenTitle.copy(letterSpacing = Typography.tightTracking.sp),
            color = SyllColors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        if (message != null) {
            Text(message, style = Typography.secondary, color = SyllColors.mutedInk, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s), content = actions)
    }
}
