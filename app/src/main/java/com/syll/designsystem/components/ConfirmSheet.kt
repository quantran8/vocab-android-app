package com.syll.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syll.designsystem.tokens.Radii
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography

/** iOS `ConfirmSheet.Style`. */
sealed interface ConfirmSheetStyle {
    /** Coral icon, Cancel + Delete side by side (flat). */
    data class Destructive(val icon: ImageVector) : ConfirmSheetStyle
    /** Syllie on top, stacked Ink "keep going" + flat outline "leave" (practice). */
    data object LeavePractice : ConfirmSheetStyle
}

/**
 * iOS `ConfirmSheet`: a destructive / leave confirmation in a short bottom sheet sized to its content.
 * Cancel is always visible. [onDismiss] runs for Cancel, a swipe down or a tap outside.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmSheet(
    style: ConfirmSheetStyle,
    title: String,
    message: String,
    cancelTitle: String,
    confirmTitle: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = Radii.sheet, topEnd = Radii.sheet),
        containerColor = SyllColors.canvas,
    ) {
        val titleStyle = Typography.screenTitle.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = Typography.tightTracking.sp)
        when (style) {
            is ConfirmSheetStyle.Destructive -> Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.practiceGutter)
                    .padding(top = Spacing.xs, bottom = Spacing.m),
            ) {
                Box(Modifier.size(48.dp).background(SyllColors.coral50, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(style.icon, contentDescription = null, tint = SyllColors.coral600, modifier = Modifier.size(24.dp))
                }
                Text(title, style = titleStyle, color = SyllColors.ink, modifier = Modifier.padding(top = Spacing.m).semantics { heading() })
                Text(message, style = Typography.secondary, color = SyllColors.bodyInk, modifier = Modifier.padding(top = Spacing.xs))
                Row(Modifier.padding(top = Spacing.l), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    SecondaryButton(cancelTitle, onClick = onDismiss, kind = ActionButtonKind.OUTLINE, modifier = Modifier.weight(1f))
                    DestructiveButton(confirmTitle, onClick = onConfirm, modifier = Modifier.weight(1f))
                }
            }
            ConfirmSheetStyle.LeavePractice -> Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.practiceGutter).padding(bottom = Spacing.m),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SyllieView(SyllieMood.OOPS, Modifier.size(96.dp, 80.dp))
                Text(title, style = titleStyle, color = SyllColors.ink, modifier = Modifier.padding(top = Spacing.s).semantics { heading() })
                Text(
                    message,
                    style = Typography.secondary,
                    color = SyllColors.bodyInk,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
                Column(Modifier.padding(top = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    PrimaryButton(cancelTitle, onClick = onDismiss, kind = ActionButtonKind.APP)
                    ActionButton(
                        ActionButtonKind.OUTLINE,
                        onClick = onConfirm,
                        modifier = Modifier.fillMaxWidth(),
                        foregroundOverride = SyllColors.destructiveInk,
                    ) { Text(confirmTitle) }
                }
            }
        }
    }
}
