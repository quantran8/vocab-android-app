package com.syll.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.TextButton
import com.syll.designsystem.tokens.Spacing
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
 * A destructive / leave confirmation as a Material `AlertDialog` (iOS shows a short sheet). Leaving practice stacks
 * Ink "keep going" over a red outline "leave", like iOS; a destructive one confirms in red.
 * [onDismiss] runs for Cancel, Back or a tap outside.
 */
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
    val buttonText = Typography.body.copy(fontWeight = FontWeight.Bold)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SyllColors.surface,
        titleContentColor = SyllColors.ink,
        textContentColor = SyllColors.bodyInk,
        icon = {
            when (style) {
                is ConfirmSheetStyle.Destructive -> Icon(style.icon, contentDescription = null, tint = SyllColors.coral600)
                ConfirmSheetStyle.LeavePractice -> SyllieView(SyllieMood.OOPS, Modifier.size(96.dp, 80.dp))
            }
        },
        title = { Text(title, style = Typography.sectionTitle.copy(fontWeight = FontWeight.ExtraBold), textAlign = TextAlign.Center) },
        text = { Text(message, style = Typography.secondary, textAlign = TextAlign.Center) },
        confirmButton = {
            when (style) {
                is ConfirmSheetStyle.Destructive -> TextButton(onClick = onConfirm) {
                    Text(confirmTitle, style = buttonText, color = SyllColors.destructiveInk)
                }
                ConfirmSheetStyle.LeavePractice -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    PrimaryButton(cancelTitle, onDismiss, Modifier.fillMaxWidth())
                    SecondaryButton(confirmTitle, onConfirm, kind = ActionButtonKind.OUTLINE, foregroundOverride = SyllColors.destructiveInk)
                }
            }
        },
        dismissButton = when (style) {
            is ConfirmSheetStyle.Destructive -> {
                {
                    TextButton(onClick = onDismiss) {
                        Text(cancelTitle, style = buttonText, color = SyllColors.ink)
                    }
                }
            }
            ConfirmSheetStyle.LeavePractice -> null
        },
    )
}
