package com.syll.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syll.R
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography

/** iOS `SectionTitle`: a 19 sp section heading with an optional trailing control (an "Edit" link, a count). */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(bottom = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            style = Typography.sectionTitle.copy(letterSpacing = (Typography.tightTracking / 2).sp),
            color = SyllColors.ink,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.weight(1f).width(Spacing.s))
        trailing()
    }
}

/** iOS `StatusPill.Tone`. */
enum class StatusPillTone(val background: Color, val foreground: Color) {
    SUCCESS(SyllColors.green50, SyllColors.green800),
    NEUTRAL(SyllColors.ink100, SyllColors.ink600),
    ERROR(SyllColors.coral50, SyllColors.coral800),
    ACCENT(SyllColors.blue50, SyllColors.blue700),
    ACCENT_SOLID(SyllColors.blue500, Color.White),
    REWARD(SyllColors.sun50, SyllColors.sun800),
}

/** iOS `StatusPill`: a 24 dp caption pill with an optional leading icon. */
@Composable
fun StatusPill(text: String, tone: StatusPillTone = StatusPillTone.NEUTRAL, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    Row(
        modifier
            .defaultMinSize(minHeight = 24.dp)
            .background(tone.background, CircleShape)
            .padding(horizontal = Spacing.s - 2.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = tone.foreground, modifier = Modifier.size(11.dp))
        Text(text, style = Typography.caption.copy(fontWeight = FontWeight.Bold), color = tone.foreground, maxLines = 1)
    }
}

/** iOS `SearchField`: magnifier, the query, and a clear button once there is text, on a [fieldSurface]. */
@Composable
fun SearchField(placeholder: String, text: String, onTextChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var isFocused by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val clearLabel = stringResource(R.string.clear_search)
    Row(
        modifier.fillMaxWidth().fieldSurface(isFocused),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s - 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = SyllColors.ink400, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f)) {
            FieldInput(
                text, onTextChange, placeholder,
                focusRequester = focus,
                onFocusChange = { isFocused = it },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Search),
            )
        }
        if (text.isNotEmpty()) {
            Icon(
                Icons.Filled.Cancel,
                contentDescription = null,
                tint = SyllColors.ink400,
                modifier = Modifier
                    .size(Spacing.minTouchTarget - 12.dp)
                    .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                        onTextChange("")
                        focus.requestFocus()
                    }
                    .padding(4.dp)
                    .semantics { contentDescription = clearLabel },
            )
        }
    }
}
