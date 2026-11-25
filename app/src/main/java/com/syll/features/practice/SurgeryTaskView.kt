package com.syll.features.practice

import kotlin.random.Random
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.syll.designsystem.motion.MotionTokens
import com.syll.designsystem.motion.Curves
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.key
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
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
 * The key's letter flies up into the slot and the old one is thrown away.
 */
@Composable
fun SurgeryTaskView(attempt: TaskAttempt) {
    val store = LocalPracticeSessionStore.current
    val motion = LocalMotion.current
    val density = LocalDensity.current
    val body = attempt.task.body as? TaskBody.Surgery ?: return
    val letters = remember(body.word) { PracticeAnswer.graphemes(body.word) }
    var selectedIndex by remember(attempt.task.id) { mutableStateOf<Int?>(null) }
    // Grapheme index → its fix; "" when the letter is deleted.
    val fixes = remember(attempt.task.id) { mutableStateMapOf<Int, String>() }
    // The keyboard closes once a key is picked and reopens when the selected slot is tapped again.
    var isKeyboardOpen by remember { mutableStateOf(true) }
    /** The slot a letter is flying into; it is a gray stand-in until the letter lands. */
    var landingIndex by remember { mutableStateOf<Int?>(null) }
    var isSwapping by remember { mutableStateOf(false) }
    val frames = remember { TileFrames<SurgeryFrame>() }
    val flyers = remember { mutableStateListOf<Flyer>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(attempt.status) {
        // On "Try again" the fixes stay, so the learner only changes what was wrong.
        if (attempt.status == TaskAttempt.Status.ANSWERING) {
            selectedIndex = null
            isKeyboardOpen = true
        }
    }

    fun select(index: Int) {
        if (isSwapping) return
        store.answerPicked()
        if (selectedIndex == index) {
            if (isKeyboardOpen) selectedIndex = null else isKeyboardOpen = true
        } else {
            selectedIndex = index
            isKeyboardOpen = true
        }
    }

    /**
     * Throws the slot's current letter away, then flies the tapped key's letter into the slot. [letter] is "" for
     * the delete key: the slot is left empty.
     */
    fun pick(letter: String) {
        val index = selectedIndex ?: return
        if (isSwapping) return
        val original = letters.getOrNull(index) ?: return
        store.answerPicked()
        val outgoing = fixes[index] ?: original
        val apply = {
            // Picking the original letter back undoes the fix.
            if (letter.isNotEmpty() && PracticeAnswer.normalize(letter) == PracticeAnswer.normalize(original)) fixes.remove(index) else fixes[index] = letter
            isKeyboardOpen = false
        }
        val box = frames[SurgeryFrame.Letter(index)]
        if (letter == outgoing || motion.reduceMotion || box == null) {
            apply()
            return
        }
        isSwapping = true
        val center = box.center
        if (outgoing.isNotEmpty()) {
            // Lobbed up in a parabola: the apex sits well above the box, then it drops away off one side.
            val side = if (Random.nextBoolean()) 1f else -1f
            val lift = Random.nextInt(420, 561).toFloat() * density.density
            val reach = density.density
            val thrown = Flyer(
                letter = outgoing,
                from = center,
                control = Offset(center.x + side * 260 * reach, center.y - lift * 2),
                to = Offset(center.x + side * 640 * reach, center.y + 80 * reach),
                size = box.size,
                spin = side * Random.nextInt(120, 241),
                isThrown = true,
            )
            flyers += thrown
            scope.launch {
                delay(THROW_DURATION_MS)
                flyers.remove(thrown)
            }
        }
        val key = frames[SurgeryFrame.Key(letter)]
        if (letter.isEmpty() || key == null) {
            apply()
            scope.launch {
                delay(DELETE_SETTLE_MS)
                isSwapping = false
            }
            return
        }
        landingIndex = index
        // The key's frame is already captured, so the keyboard can leave while the letter flies.
        apply()
        val start = key.center
        // Rises from the key and arcs over into the slot.
        val incoming = Flyer(
            letter = letter,
            from = start,
            control = Offset((start.x + center.x) / 2, minOf(start.y, center.y) - 60 * density.density),
            to = center,
            size = box.size,
            spin = 0f,
            isThrown = false,
        )
        scope.launch {
            delay(INCOMING_DELAY_MS)
            flyers += incoming
            delay(INCOMING_FLIGHT_MS)
            landingIndex = null
            flyers.remove(incoming)
            isSwapping = false
        }
    }

    val enabled = attempt.status == TaskAttempt.Status.ANSWERING && !attempt.isFinished && !isSwapping
    TaskScaffold(
        attempt,
        content = {
            Box(Modifier.fillMaxWidth().onGloballyPositioned { frames.container = it }) {
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
                                isLanding = landingIndex == index,
                                modifier = Modifier.width(width).onGloballyPositioned { frames.place(SurgeryFrame.Letter(index), it) },
                            )
                        }
                    }
                }
                // The keyboard comes and goes with the `snap` spring, from the bottom edge.
                AnimatedVisibility(
                    visible = selectedIndex != null && isKeyboardOpen && !attempt.isFinished,
                    enter = if (motion.reduceMotion) {
                        fadeIn(motion.animation(MotionTokens.snap))
                    } else {
                        expandVertically(MotionTokens.snap.spec(), expandFrom = Alignment.Top) +
                            slideInVertically(MotionTokens.snap.spec()) { it } + fadeIn(MotionTokens.snap.spec())
                    },
                    exit = if (motion.reduceMotion) {
                        fadeOut(motion.animation(MotionTokens.snap))
                    } else {
                        shrinkVertically(MotionTokens.snap.spec(), shrinkTowards = Alignment.Top) +
                            slideOutVertically(MotionTokens.snap.spec()) { it } + fadeOut(MotionTokens.snap.spec())
                    },
                ) {
                    Column(
                        Modifier.cardSurface().padding(horizontal = Spacing.xs, vertical = Spacing.s),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp),
                    ) {
                        Eyebrow(stringResource(R.string.replace_with), Modifier.padding(horizontal = Spacing.xxs))
                        LetterKeyboard(
                            enabled = enabled,
                            onLetter = ::pick,
                            onDelete = { pick("") },
                            onKeyPlaced = { letter, layout -> frames.place(SurgeryFrame.Key(letter), layout) },
                        )
                    }
                }
            }
            // The flying letters, above the boxes and the keyboard.
            Box(Modifier.matchParentSize()) {
                flyers.forEach { flyer -> key(flyer.id) { FlyingPiece(flyer) } }
            }
            }
        },
        action = {
            TaskActionBar(
                attempt,
                checkTitle = stringResource(R.string.check_fix),
                canCheck = fixes.isNotEmpty() && !isSwapping,
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
    isLanding: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Radii.chip)
    // A deleted slot, or one a letter is landing in, is a gray stand-in like the reorder drop preview.
    val isPlaceholder = fix == "" || isLanding
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
                if (fix == "") stateDescription = deleted
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
private fun LetterKeyboard(
    enabled: Boolean,
    onLetter: (String) -> Unit,
    onDelete: () -> Unit,
    onKeyPlaced: (String, LayoutCoordinates) -> Unit,
) {
    val removeLabel = stringResource(R.string.delete_letter)
    BoxWithConstraints(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.6f)) {
        val gap = Spacing.xxs
        val keyWidth = (maxWidth - gap * 9) / 10
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs + 2.dp)) {
            KEYBOARD_ROWS.forEachIndexed { rowIndex, row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally)) {
                    row.forEach { letter ->
                        Key(keyWidth, SyllColors.neutralSurface, enabled, { onLetter(letter) }, letter, Modifier.onGloballyPositioned { onKeyPlaced(letter, it) }) {
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
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .size(width, 46.dp)
            .pressScaleClickable(enabled = enabled, onClick = onClick)
            .background(fill, RoundedCornerShape(8.dp))
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
}

private sealed interface SurgeryFrame {
    data class Letter(val index: Int) : SurgeryFrame
    data class Key(val letter: String) : SurgeryFrame
}

private const val THROW_DURATION_MS = 700L
/** The key's letter leaves just after the old one is thrown, so the two don't overlap in the slot. */
private const val INCOMING_DELAY_MS = 120L
/** `snap` mostly settles in this time; the slot then shows the letter for real. */
private const val INCOMING_FLIGHT_MS = 420L
private const val DELETE_SETTLE_MS = 300L

private class Flyer(
    val letter: String,
    val from: Offset,
    val control: Offset,
    val to: Offset,
    val size: Size,
    val spin: Float,
    /** True for the discarded letter (flies off screen); false for the key's letter flying into the slot. */
    val isThrown: Boolean,
) {
    val id = nextId++

    private companion object {
        var nextId = 0L
    }
}

/** iOS `FlyingPiece` + `ArcEffect`: a letter moving along a quadratic Bézier, centred on the curve. */
@Composable
private fun FlyingPiece(flyer: Flyer) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, if (flyer.isThrown) tween(650, easing = Curves.easeIn) else MotionTokens.snap.spec())
    }
    val shape = RoundedCornerShape(Radii.chip)
    val density = LocalDensity.current
    // Both pieces wear the selected look: the slot they leave or land in is the selected one.
    Box(
        Modifier
            .size(with(density) { flyer.size.width.toDp() }, with(density) { flyer.size.height.toDp() })
            .graphicsLayer {
                val t = progress.value
                val u = 1 - t
                val x = u * u * flyer.from.x + 2 * u * t * flyer.control.x + t * t * flyer.to.x
                val y = u * u * flyer.from.y + 2 * u * t * flyer.control.y + t * t * flyer.to.y
                translationX = x - flyer.size.width / 2
                translationY = y - flyer.size.height / 2
                val scale = if (flyer.isThrown) 1.3f + 0.1f * t else 0.8f + 0.2f * t
                scaleX = scale
                scaleY = scale
                rotationZ = flyer.spin * t
                alpha = if (flyer.isThrown) 1 - t else 1f
            }
            .background(SyllColors.selectedSurface, shape)
            .border(Borders.card, SyllColors.interactive, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(flyer.letter, style = LETTER_TEXT, color = SyllColors.selectedInk, maxLines = 1)
    }
}
