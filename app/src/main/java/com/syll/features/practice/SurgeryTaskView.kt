package com.syll.features.practice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syll.R
import com.syll.app.LocalPracticeSessionStore
import com.syll.designsystem.components.Eyebrow
import com.syll.designsystem.components.cardSurface
import com.syll.designsystem.components.pressScaleClickable
import com.syll.designsystem.motion.LocalMotion
import com.syll.designsystem.tokens.Borders
import com.syll.designsystem.tokens.Radii
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography
import com.syll.domain.practice.PracticeAnswer
import com.syll.domain.practice.TaskAttempt
import com.syll.domain.practice.TaskBody

private val LETTER_TEXT = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 22.sp)
private val LETTER_WIDTH = 38.dp
private val LETTER_HEIGHT = 50.dp
private val LETTER_GAP = Spacing.xxs + 2.dp

/**
 * iOS `SurgeryTaskView`: select a slot, then tap its replacement on the letter keyboard; Remove deletes the
 * grapheme. Fixes stay put while other slots are selected; picking the original letter again undoes a fix.
 * (The iOS flying-letter swap is a later polish; here the letter changes in place.)
 */
@Composable
fun SurgeryTaskView(attempt: TaskAttempt) {
    val store = LocalPracticeSessionStore.current
    val motion = LocalMotion.current
    val body = attempt.task.body as? TaskBody.Surgery ?: return
    val letters = remember(body.word) { PracticeAnswer.graphemes(body.word) }
    var selectedIndex by remember(attempt.task.id) { mutableStateOf<Int?>(null) }
    // Grapheme index → its fix; "" when the letter is deleted.
    val fixes = remember(attempt.task.id) { mutableStateMapOf<Int, String>() }
    // The keyboard closes once a key is picked and reopens when the selected slot is tapped again.
    var isKeyboardOpen by remember { mutableStateOf(true) }

    LaunchedEffect(attempt.status) {
        // On "Try again" the fixes stay, so the learner only changes what was wrong.
        if (attempt.status == TaskAttempt.Status.ANSWERING) {
            selectedIndex = null
            isKeyboardOpen = true
        }
    }

    fun select(index: Int) {
        store.answerPicked()
        if (selectedIndex == index) {
            if (isKeyboardOpen) selectedIndex = null else isKeyboardOpen = true
        } else {
            selectedIndex = index
            isKeyboardOpen = true
        }
    }

    fun pick(letter: String) {
        val index = selectedIndex ?: return
        val original = letters.getOrNull(index) ?: return
        store.answerPicked()
        // Picking the original letter back undoes the fix.
        if (letter.isNotEmpty() && PracticeAnswer.normalize(letter) == PracticeAnswer.normalize(original)) fixes.remove(index) else fixes[index] = letter
        isKeyboardOpen = false
    }

    val enabled = attempt.status == TaskAttempt.Status.ANSWERING && !attempt.isFinished
    TaskScaffold(
        attempt,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
                BoxWithConstraints(Modifier.fillMaxWidth().padding(top = Spacing.m)) {
                    // 38 dp at most; a long word narrows the boxes to fit one line.
                    val width = minOf(LETTER_WIDTH, (maxWidth - LETTER_GAP * (letters.size - 1)) / letters.size)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LETTER_GAP, Alignment.CenterHorizontally)) {
                        letters.forEachIndexed { index, letter ->
                            LetterBox(
                                letter = letter,
                                fix = fixes[index],
                                isSelected = selectedIndex == index,
                                label = stringResource(R.string.letter_x_of_x_x, index + 1, letters.size, fixes[index] ?: letter),
                                deleted = stringResource(R.string.deleted),
                                enabled = enabled,
                                onClick = { select(index) },
                                modifier = Modifier.width(width),
                            )
                        }
                    }
                }
                AnimatedVisibility(
                    visible = selectedIndex != null && isKeyboardOpen && !attempt.isFinished,
                    enter = if (motion.reduceMotion) fadeIn() else slideInVertically { it / 3 } + fadeIn(),
                    exit = fadeOut(),
                ) {
                    Column(
                        Modifier.cardSurface().padding(horizontal = Spacing.xs, vertical = Spacing.s),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp),
                    ) {
                        Eyebrow(stringResource(R.string.replace_with), Modifier.padding(horizontal = Spacing.xxs))
                        LetterKeyboard(enabled = enabled, onLetter = ::pick, onDelete = { pick("") })
                    }
                }
            }
        },
        action = {
            TaskActionBar(
                attempt,
                checkTitle = stringResource(R.string.check_fix),
                canCheck = fixes.isNotEmpty(),
                onCheck = {
                    selectedIndex = null
                    store.submitRepair(fixes.toMap())
                },
            )
        },
    )
}

@Composable
private fun LetterBox(
    letter: String,
    fix: String?,
    isSelected: Boolean,
    label: String,
    deleted: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Radii.chip)
    val isPlaceholder = fix == ""
    val isTinted = isSelected || fix != null
    Box(
        modifier
            .height(LETTER_HEIGHT)
            // The selected box gets a 4 dp Blue 100 halo, like a focused field.
            .drawBehind {
                if (isSelected) {
                    val inset = Borders.focusHalo.toPx()
                    drawRoundRect(
                        SyllColors.focusHalo,
                        topLeft = Offset(-inset, -inset),
                        size = Size(size.width + inset * 2, size.height + inset * 2),
                        cornerRadius = CornerRadius(Radii.chip.toPx() + inset),
                    )
                }
            }
            .background(if (isPlaceholder) SyllColors.neutralSurface else if (isTinted) SyllColors.selectedSurface else SyllColors.surface, shape)
            .then(
                when {
                    isSelected -> Modifier.border(Borders.card, SyllColors.interactive, shape)
                    !isPlaceholder -> Modifier.border(Borders.card, if (fix != null) SyllColors.selectedDepth else SyllColors.stroke, shape)
                    else -> Modifier
                },
            )
            .pressScaleClickable(enabled = enabled, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                selected = isSelected
                if (isPlaceholder) stateDescription = deleted
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (isPlaceholder) " " else fix ?: letter,
            style = LETTER_TEXT,
            color = if (isTinted) SyllColors.selectedInk else SyllColors.ink,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

private val KEYBOARD_ROWS = listOf(
    listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
    listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
    listOf("z", "x", "c", "v", "b", "n", "m"),
)

/** iOS `LetterKeyboard`: a QWERTY letter pad for the fix; the last row ends with a key that removes the letter. */
@Composable
private fun LetterKeyboard(enabled: Boolean, onLetter: (String) -> Unit, onDelete: () -> Unit) {
    val removeLabel = stringResource(R.string.delete_letter)
    BoxWithConstraints(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.6f)) {
        val gap = Spacing.xxs
        val keyWidth = (maxWidth - gap * 9) / 10
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs + 2.dp)) {
            KEYBOARD_ROWS.forEachIndexed { rowIndex, row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally)) {
                    row.forEach { letter ->
                        Key(keyWidth, SyllColors.neutralSurface, enabled, { onLetter(letter) }, letter) {
                            Text(letter, style = Typography.tileLarge, color = SyllColors.ink)
                        }
                    }
                    if (rowIndex == KEYBOARD_ROWS.lastIndex) {
                        // Two keys wide.
                        Key(keyWidth * 2 + gap, SyllColors.stroke, enabled, onDelete, removeLabel) {
                            Text(
                                stringResource(R.string.remove),
                                style = Typography.secondary.copy(fontWeight = FontWeight.SemiBold),
                                color = SyllColors.ink,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Key(
    width: androidx.compose.ui.unit.Dp,
    fill: androidx.compose.ui.graphics.Color,
    enabled: Boolean,
    onClick: () -> Unit,
    label: String,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(width, 46.dp)
            .pressScaleClickable(enabled = enabled, onClick = onClick)
            .background(fill, RoundedCornerShape(8.dp))
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
}
