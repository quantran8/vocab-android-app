package com.vocabflow.designsystem.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vocabflow.designsystem.motion.LocalMotion
import com.vocabflow.designsystem.motion.MotionTokens
import com.vocabflow.designsystem.tokens.Borders
import com.vocabflow.designsystem.tokens.Spacing
import com.vocabflow.designsystem.tokens.SyllColors
import com.vocabflow.designsystem.tokens.Typography

/**
 * Where a button sits (iOS `ActionContext`). The primary button is Ink with a depth edge everywhere; Blue
 * [ActionButtonKind.PRACTICE] is only for actions that start practice, passed explicitly. The context picks the
 * secondary button: white [ActionButtonKind.OUTLINE_3D] with a depth edge in practice, flat [ActionButtonKind.OUTLINE]
 * elsewhere. `AppRoot` sets [ActionContext.PRACTICE] on session, challenge and Lab task screens.
 */
enum class ActionContext { PRACTICE, APP }

val LocalActionContext = staticCompositionLocalOf { ActionContext.APP }

/** iOS `ActionButtonStyle.Kind`. */
enum class ActionButtonKind(
    val face: Color,
    val foreground: Color,
    val depthColor: Color,
    val depth: Dp,
    val hasBorder: Boolean,
) {
    /** Blue 500 on Blue 700: actions that start practice. */
    PRACTICE(SyllColors.blue500, Color.White, SyllColors.interactiveDepth, Spacing.buttonDepth, false),
    /** Green 600 on Green 800: Continue in a correct-answer panel. */
    SUCCESS(SyllColors.green600, Color.White, SyllColors.successActionDepth, Spacing.buttonDepth, false),
    /** Coral 600 on Coral 800: Try again in a wrong-answer panel. */
    RETRY(SyllColors.coral600, Color.White, SyllColors.errorActionDepth, Spacing.buttonDepth, false),
    /** Ink 800 on Ink 950: the primary action everywhere, practice included. */
    APP(SyllColors.primaryAction, Color.White, SyllColors.primaryDepth, Spacing.buttonDepth, false),
    /** White, Ink 200 border and 3 dp Ink 200 edge: secondary practice action. */
    OUTLINE_3D(SyllColors.surface, SyllColors.ink, SyllColors.tileDepth, Spacing.tileDepth, true),
    /** White, Ink 200 border, flat. */
    OUTLINE(SyllColors.surface, SyllColors.ink, Color.Transparent, 0.dp, true),
    /** Coral 600, white text, flat. */
    DANGER(SyllColors.coral600, Color.White, Color.Transparent, 0.dp, false),
    /** Ink text only: no fill, border or edge ("I know this word"). */
    PLAIN(Color.Transparent, SyllColors.ink, Color.Transparent, 0.dp, false),
    /** Sun 400 on Sun 600 with a Sun 900 label: Premium entry points and the paywall's action only. */
    PREMIUM(SyllColors.sun400, SyllColors.sun900, SyllColors.sun600, Spacing.buttonDepth, false),
}

enum class ActionButtonSize(val height: Dp) { REGULAR(Spacing.buttonHeight), SMALL(Spacing.buttonSmallHeight) }

/**
 * Fully rounded button (iOS `ActionButtonStyle`). Depth kinds have a thick edge the face sinks into when pressed;
 * flat kinds shrink to 97%. Disabled is a flat Ink 100 face.
 */
@Composable
fun ActionButton(
    kind: ActionButtonKind,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: ActionButtonSize = ActionButtonSize.REGULAR,
    foregroundOverride: Color? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val motion = LocalMotion.current
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val pressed = isPressed && enabled
    val depth = if (enabled) kind.depth else 0.dp
    val sink by animateDpAsState(
        targetValue = if (pressed && depth > 0.dp) depth else 0.dp,
        animationSpec = motion.animation(MotionTokens.press),
        label = "sink",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed && kind.depth == 0.dp) motion.pressedScale else 1f,
        animationSpec = motion.animation(MotionTokens.press),
        label = "scale",
    )
    val face = when {
        kind == ActionButtonKind.PLAIN -> Color.Transparent
        !enabled -> SyllColors.disabledSurface
        else -> kind.face
    }
    val foreground = if (!enabled) SyllColors.disabledInk else foregroundOverride ?: kind.foreground

    Box(
        modifier
            // The layout always reserves the edge, so enabling a button doesn't move it.
            .padding(bottom = kind.depth)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBehind {
                if (depth > 0.dp) {
                    // `this.size`: the draw size, not the button's `size` parameter.
                    val radius = CornerRadius(this.size.height / 2)
                    drawRoundRect(kind.depthColor, topLeft = Offset(0f, depth.toPx()), size = this.size, cornerRadius = radius)
                }
            }
            .offset(y = sink)
            .clip(CircleShape)
            .background(face)
            .then(if (kind.hasBorder && enabled) Modifier.border(Borders.card, SyllColors.stroke, CircleShape) else Modifier)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = size.height),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides foreground,
            LocalTextStyle provides if (size == ActionButtonSize.SMALL) Typography.actionSmall else Typography.action,
        ) {
            Row(
                Modifier.padding(horizontal = if (size == ActionButtonSize.SMALL) Spacing.m else Spacing.screenGutter),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

/** iOS `ActionButtonLabel`: title with an optional icon, leading or trailing. */
@Composable
private fun RowScope.ActionButtonLabel(title: String, icon: ImageVector?, iconLeading: Boolean) {
    if (iconLeading && icon != null) Icon(icon, contentDescription = null, Modifier.size(18.dp))
    Text(title, textAlign = TextAlign.Center)
    if (!iconLeading && icon != null) Icon(icon, contentDescription = null, Modifier.size(18.dp))
}

/** iOS `PrimaryButton`. [kind] null is Ink; pass [ActionButtonKind.PRACTICE] for an action that starts practice. */
@Composable
fun PrimaryButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    requirement: String? = null,
    enabled: Boolean = true,
    kind: ActionButtonKind? = null,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (!enabled && requirement != null) {
            Text(requirement, style = Typography.micro, color = SyllColors.mutedInk, textAlign = TextAlign.Center)
        }
        ActionButton(kind ?: ActionButtonKind.APP, onClick, Modifier.fillMaxWidth(), enabled) {
            // The Premium button's crown leads its label.
            ActionButtonLabel(title, icon, iconLeading = kind == ActionButtonKind.PREMIUM)
        }
    }
}

/** iOS `SecondaryButton`: [kind] null follows [LocalActionContext]. */
@Composable
fun SecondaryButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    kind: ActionButtonKind? = null,
) {
    val context = LocalActionContext.current
    val resolved = kind ?: if (context == ActionContext.PRACTICE) ActionButtonKind.OUTLINE_3D else ActionButtonKind.OUTLINE
    ActionButton(resolved, onClick, modifier.fillMaxWidth()) {
        ActionButtonLabel(title, icon, iconLeading = true)
    }
}

/** iOS `DestructiveButton`: flat Coral 600 with white text; a confirmation sheet's final action. */
@Composable
fun DestructiveButton(title: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    ActionButton(ActionButtonKind.DANGER, onClick, modifier.fillMaxWidth()) {
        ActionButtonLabel(title, icon, iconLeading = true)
    }
}

/** iOS `SmallButton`: 36 dp, hugs its label ("+ Add word", "Practise now"). */
@Composable
fun SmallButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    kind: ActionButtonKind = ActionButtonKind.OUTLINE,
) {
    ActionButton(kind, onClick, modifier, size = ActionButtonSize.SMALL) {
        ActionButtonLabel(title, icon, iconLeading = true)
    }
}
