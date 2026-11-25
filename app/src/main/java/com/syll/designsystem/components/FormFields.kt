package com.syll.designsystem.components

import com.syll.designsystem.motion.SymbolReplace
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syll.R
import com.syll.designsystem.tokens.Borders
import com.syll.designsystem.tokens.Radii
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography

/** iOS `ScreenTitle`: 34 extrabold Ink 950, a heading for accessibility. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = Typography.display,
        color = SyllColors.ink,
        modifier = modifier.semantics { heading() },
    )
}

/** iOS `TextLink`: a semibold link-coloured text button with a 44 dp touch target. */
@Composable
fun TextLink(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = SyllColors.link,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier
            .defaultMinSize(minHeight = Spacing.minTouchTarget)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs - 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(title, style = Typography.secondary.copy(fontWeight = FontWeight.SemiBold), color = color)
    }
}

/**
 * iOS `View.fieldSurface`: white field, radius 16, 2 dp border (Blue when focused, Coral on error) and a 4 dp halo
 * around a focused field.
 */
fun Modifier.fieldSurface(isFocused: Boolean, isError: Boolean = false, minHeight: androidx.compose.ui.unit.Dp = Spacing.fieldHeight): Modifier {
    val shape = RoundedCornerShape(Radii.field)
    val border = when {
        isError -> SyllColors.error
        isFocused -> SyllColors.focusRing
        else -> SyllColors.stroke
    }
    val halo = if (isError) SyllColors.coral100 else SyllColors.focusHalo
    return this
        .drawBehind {
            if (isFocused) {
                val inset = Borders.focusHalo.toPx()
                drawRoundRect(
                    halo,
                    topLeft = Offset(-inset, -inset),
                    size = Size(size.width + inset * 2, size.height + inset * 2),
                    cornerRadius = CornerRadius(Radii.field.toPx() + inset),
                )
            }
        }
        .background(SyllColors.surface, shape)
        .border(Borders.card, border, shape)
        .defaultMinSize(minHeight = minHeight)
        .padding(horizontal = Spacing.m)
}

/** iOS `LabeledField`: the label above (semibold Ink 950), the input on a [fieldSurface]. */
@Composable
fun LabeledField(
    title: String,
    modifier: Modifier = Modifier,
    isFocused: Boolean = false,
    isError: Boolean = false,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(title, style = Typography.secondary.copy(fontWeight = FontWeight.SemiBold), color = SyllColors.ink)
        Box(Modifier.fillMaxWidth().fieldSurface(isFocused, isError), contentAlignment = Alignment.CenterStart) {
            content()
        }
    }
}

/** iOS `FieldErrorText`: info icon and semibold Coral 700 text under a form. Never an alert. */
@Composable
fun FieldErrorText(message: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = SyllColors.destructiveInk, modifier = Modifier.size(18.dp))
        Text(message, style = Typography.secondary.copy(fontWeight = FontWeight.SemiBold), color = SyllColors.destructiveInk)
    }
}

/**
 * A single-line text input styled for [LabeledField]: body text, a muted placeholder and the given keyboard.
 * [onFocusChange] lets the field's label show the focus ring (iOS `@FocusState`).
 */
@Composable
fun FieldInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onFocusChange: (Boolean) -> Unit = {},
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onSubmit: () -> Unit = {},
    visualTransformation: VisualTransformation = VisualTransformation.None,
    textStyle: TextStyle = Typography.body,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = textStyle.copy(color = SyllColors.ink),
        cursorBrush = SolidColor(SyllColors.interactive),
        keyboardOptions = keyboardOptions,
        keyboardActions = KeyboardActions(onAny = { onSubmit() }),
        visualTransformation = visualTransformation,
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { onFocusChange(it.isFocused) }
            .padding(vertical = Spacing.s),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, style = textStyle, color = SyllColors.disabledInk)
                inner()
            }
        },
    )
}

/** iOS `PasswordInput`: a password field with a show/hide toggle. Focus stays on the field when it flips. */
@Composable
fun PasswordInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onFocusChange: (Boolean) -> Unit = {},
    imeAction: ImeAction = ImeAction.Go,
    onSubmit: () -> Unit = {},
) {
    var isVisible by rememberSaveable { mutableStateOf(false) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FieldInput(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            focusRequester = focusRequester,
            onFocusChange = onFocusChange,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isVisible) KeyboardType.Text else KeyboardType.Password,
                autoCorrectEnabled = false,
                imeAction = imeAction,
            ),
            onSubmit = onSubmit,
            visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.weight(1f),
        )
        val label = stringResource(if (isVisible) R.string.hide_password else R.string.show_password)
        Box(
            Modifier
                .size(Spacing.minTouchTarget)
                .padding(start = Spacing.xs)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClickLabel = label,
                ) { isVisible = !isVisible },
            contentAlignment = Alignment.CenterEnd,
        ) {
            SymbolReplace(isVisible) { visible ->
                Icon(
                    if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = label,
                    tint = SyllColors.mutedInk,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/** The 6-digit reset code: monospaced, wide tracking (iOS `.title3` monospaced semibold, tracking 6). */
val CodeInputStyle = TextStyle(
    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
    fontSize = 20.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = 6.sp,
)
