package com.syll.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.syll.R
import com.syll.designsystem.motion.LocalMotion
import com.syll.designsystem.motion.MotionTokens
import com.syll.designsystem.tokens.Borders
import com.syll.designsystem.tokens.Radii
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography

/** iOS `WordTileVisualState`. */
enum class WordTileState { IDLE, PRESSED, DRAGGING, SELECTED, CORRECT, INCORRECT, DISABLED }

/**
 * iOS `WordTile`: a tile face on a 3 dp depth edge. Pressed sinks the face into the edge; [bounceTrigger]
 * changes sink it and spring it back.
 */
@Composable
fun WordTile(
    text: String,
    state: WordTileState,
    modifier: Modifier = Modifier,
    minHeight: Dp = Spacing.tileMinHeight,
    bounceTrigger: Int = 0,
    style: TextStyle = Typography.tile,
) {
    val motion = LocalMotion.current
    val shape = RoundedCornerShape(Radii.tile)
    val spec = motion.animation<Color>(if (state == WordTileState.PRESSED) MotionTokens.press else MotionTokens.lift)
    val face by animateColorAsState(
        when (state) {
            WordTileState.SELECTED, WordTileState.DRAGGING -> SyllColors.selectedSurface
            WordTileState.CORRECT -> SyllColors.successSurface
            WordTileState.INCORRECT -> SyllColors.errorSurface
            else -> SyllColors.surface
        },
        spec, label = "tileFace",
    )
    val depth = when (state) {
        WordTileState.SELECTED -> SyllColors.selectedDepth
        WordTileState.DRAGGING -> Color.Transparent
        WordTileState.CORRECT -> SyllColors.successDepth
        WordTileState.INCORRECT -> SyllColors.errorDepth
        else -> SyllColors.tileDepth
    }
    val border = when (state) {
        WordTileState.PRESSED, WordTileState.SELECTED, WordTileState.DRAGGING -> SyllColors.interactive
        WordTileState.CORRECT -> SyllColors.success
        WordTileState.INCORRECT -> SyllColors.error
        else -> SyllColors.stroke
    }
    val foreground = when (state) {
        WordTileState.CORRECT -> SyllColors.successInk
        WordTileState.INCORRECT -> SyllColors.errorInk
        WordTileState.SELECTED, WordTileState.DRAGGING -> SyllColors.selectedInk
        WordTileState.DISABLED -> SyllColors.ink400
        else -> SyllColors.ink
    }

    val maxSink = Spacing.tileDepth - 1.dp
    val sink = remember { Animatable(0f) }
    LaunchedEffect(bounceTrigger) {
        if (bounceTrigger == 0 || motion.reduceMotion) return@LaunchedEffect
        sink.animateTo(1f, tween(70))
        sink.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessHigh))
    }
    val drop = if (state == WordTileState.PRESSED) maxSink else maxSink * sink.value

    Box(
        modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.tileDepth)
            .drawBehind {
                drawRoundRect(depth, topLeft = Offset(0f, Spacing.tileDepth.toPx()), size = size, cornerRadius = CornerRadius(Radii.tile.toPx()))
            }
            .offset(y = drop)
            .background(face, shape)
            .border(Borders.card, border, shape)
            .defaultMinSize(minHeight = minHeight)
            .padding(horizontal = Spacing.s, vertical = Spacing.m),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = style, color = foreground, textAlign = TextAlign.Center)
    }
}

/** iOS `ChoiceTileButton`: a large answer card used by the choice tasks and the placement test. */
@Composable
fun ChoiceTileButton(
    text: String,
    state: WordTileState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = Spacing.tileMinHeight,
    style: TextStyle = Typography.choice,
) {
    val motion = LocalMotion.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var bounce by remember { mutableIntStateOf(0) }
    val canPress = state == WordTileState.IDLE || state == WordTileState.SELECTED
    val enabled = state != WordTileState.DISABLED && state != WordTileState.CORRECT
    val label = stringResource(R.string.x_option, text)
    val value = when (state) {
        WordTileState.SELECTED -> stringResource(R.string.selected)
        else -> ""
    }
    WordTile(
        text = text,
        state = if (pressed && canPress) WordTileState.PRESSED else state,
        minHeight = minHeight,
        bounceTrigger = bounce,
        style = style,
        modifier = modifier
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button) {
                if (!motion.reduceMotion) bounce += 1
                onClick()
            }
            .semantics(mergeDescendants = true) {
                contentDescription = label
                stateDescription = value
                selected = state == WordTileState.SELECTED
            },
    )
}
