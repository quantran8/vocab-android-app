package com.syll.designsystem.components

import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.animation.core.animateDpAsState
import com.syll.designsystem.motion.SymbolReplace
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syll.R
import com.syll.designsystem.motion.LocalMotion
import com.syll.designsystem.motion.MotionTokens
import com.syll.designsystem.tokens.Borders
import com.syll.designsystem.tokens.Radii
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography

/** iOS `PressScaleButtonStyle`: a plain clickable that shrinks a little while pressed. */
fun Modifier.pressScaleClickable(
    enabled: Boolean = true,
    role: Role = Role.Button,
    onClick: () -> Unit,
): Modifier = composed {
    val motion = LocalMotion.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && enabled) motion.pressedScale else 1f,
        motion.animation(MotionTokens.press),
        label = "pressScale",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interaction, indication = null, enabled = enabled, role = role, onClick = onClick)
}

/** iOS `Eyebrow`: 12 sp bold, tracked, upper case. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = SyllColors.mutedInk) {
    Text(text.uppercase(), style = Typography.eyebrow, color = color, modifier = modifier)
}

/** iOS `PlanSectionTitle`. */
@Composable
fun PlanSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = Typography.emphasis.copy(letterSpacing = (-0.4).sp),
        color = SyllColors.ink,
        modifier = modifier.semantics { heading() },
    )
}

/** iOS `SelectionMark`: a filled Blue check when on, an Ink 300 ring when off. */
@Composable
fun SelectionMark(isOn: Boolean, modifier: Modifier = Modifier) {
    SymbolReplace(isOn, modifier.size(24.dp)) { on ->
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (on) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SyllColors.interactive, modifier = Modifier.size(24.dp))
            } else {
                Box(Modifier.size(20.dp).border(Borders.card, SyllColors.ink300, CircleShape))
            }
        }
    }
}

/** iOS `IconBadge`: a 40 dp rounded-square icon on a tinted fill, leading a row or card. */
enum class IconBadgeTone(val background: Color, val foreground: Color) {
    NEUTRAL(SyllColors.ink50, SyllColors.ink700),
    ACCENT(SyllColors.blue50, SyllColors.blue600),
    SUCCESS(SyllColors.green50, SyllColors.green700),
    ERROR(SyllColors.coral50, SyllColors.coral600),
    REWARD(SyllColors.sun50, SyllColors.sun600),
    ON_ACCENT(Color.White, SyllColors.blue600),
    PREMIUM(SyllColors.grape50, SyllColors.grape600),
}

@Composable
fun IconBadge(icon: ImageVector, tone: IconBadgeTone = IconBadgeTone.NEUTRAL, size: Dp = Spacing.iconBadge) {
    Box(
        Modifier.size(size).background(tone.background, RoundedCornerShape(Radii.chip)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tone.foreground, modifier = Modifier.size(size * 0.5f))
    }
}

/** iOS `CardRow`: 56 dp minimum, 16 dp side padding. */
@Composable
fun CardRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Spacing.rowMinHeight)
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** iOS `PinnedActionBar`: the screen's main action, pinned above the navigation bar on the canvas. */
@Composable
fun PinnedActionBar(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .background(SyllColors.canvas)
            .navigationBarsPadding()
            .padding(horizontal = Spacing.screenGutter)
            .padding(top = Spacing.s, bottom = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
        content = content,
    )
}

/** iOS `ChoiceCard`: rows in one card with a divider between them, for single-choice lists. */
@Composable
fun <T> ChoiceCard(items: List<T>, modifier: Modifier = Modifier, row: @Composable (T) -> Unit) {
    Column(modifier.cardSurface()) {
        items.forEachIndexed { index, item ->
            if (index > 0) CardDivider()
            row(item)
        }
    }
}

/** iOS `ChoiceRow`: Blue 50 fill and a filled [SelectionMark] when selected. */
@Composable
fun ChoiceRow(
    isSelected: Boolean,
    onClick: () -> Unit,
    alignTop: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val motion = LocalMotion.current
    val fill by animateColorAsState(
        if (isSelected) SyllColors.selectedSurface else SyllColors.surface,
        motion.animation(MotionTokens.press),
        label = "choiceFill",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .background(fill)
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) { selected = isSelected }
            .defaultMinSize(minHeight = Spacing.rowMinHeight)
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = if (alignTop) Alignment.Top else Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = if (alignTop) Alignment.Top else Alignment.CenterVertically,
            content = content,
        )
        SelectionMark(isSelected, Modifier.padding(top = if (alignTop) 2.dp else 0.dp))
    }
}

/**
 * iOS `chipSurface`: off: 1.5 dp Ink 200 border; on: 2 dp Blue 500 border, Blue 50 fill, Blue 700 text.
 * Selection changes animate with `press`, as the iOS chips do.
 */
fun Modifier.chipSurface(isSelected: Boolean, selectedFill: Color = SyllColors.selectedSurface): Modifier = composed {
    val motion = LocalMotion.current
    val fill by animateColorAsState(if (isSelected) selectedFill else SyllColors.surface, motion.animation(MotionTokens.press), label = "chipFill")
    val stroke by animateColorAsState(if (isSelected) SyllColors.interactive else SyllColors.stroke, motion.animation(MotionTokens.press), label = "chipStroke")
    val width by animateDpAsState(if (isSelected) Borders.card else Borders.hairline, motion.animation(MotionTokens.press), label = "chipBorder")
    defaultMinSize(minHeight = Spacing.buttonSmallHeight)
        .background(fill, CircleShape)
        .border(width, stroke, CircleShape)
        .padding(horizontal = Spacing.s + 2.dp)
}

/** iOS `TopicChip`: pill toggle with a leading icon; selection is colour and border, never a checkmark. */
@Composable
fun TopicChip(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    mark: Color? = null,
    progress: Pair<Int, Int>? = null,
    isFinished: Boolean = false,
    isLocked: Boolean = false,
    /** iOS `selectedFill`: Random Practice's tinted topic panel selects on white. */
    selectedFill: Color = SyllColors.selectedSurface,
) {
    val motion = LocalMotion.current
    val foreground by animateColorAsState(if (isSelected) SyllColors.selectedInk else SyllColors.ink, motion.animation(MotionTokens.press), label = "chipInk")
    val lockAlpha by animateFloatAsState(if (isLocked) 0.4f else 1f, motion.animation(MotionTokens.press), label = "chipLock")
    val progressLabel = progress?.let { stringResource(R.string.x_of_x_words_learned, it.first, it.second) }
    Box(
        Modifier
            .defaultMinSize(minHeight = Spacing.minTouchTarget)
            .pressScaleClickable(onClick = onClick)
            .semantics(mergeDescendants = true) { selected = isSelected },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.alpha(lockAlpha).chipSurface(isSelected, selectedFill),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs - 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                isFinished -> Icon(Icons.Outlined.Verified, null, tint = foreground, modifier = Modifier.size(16.dp))
                mark != null -> CollectionMarkDot(mark)
                icon != null -> Icon(icon, null, tint = foreground, modifier = Modifier.size(16.dp))
            }
            Text(
                title,
                style = Typography.secondary.copy(fontWeight = FontWeight.SemiBold),
                color = foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = Spacing.xs),
            )
            if (isFinished) {
                Text(stringResource(R.string.finished), style = Typography.micro.copy(fontWeight = FontWeight.SemiBold), color = SyllColors.mutedInk)
            } else if (progress != null) {
                Text(
                    "${progress.first}/${progress.second}",
                    style = Typography.micro,
                    color = SyllColors.mutedInk,
                    modifier = Modifier.semantics { contentDescription = progressLabel.orEmpty() },
                )
            }
        }
    }
}

/** iOS `CollectionMarkDot`. */
@Composable
fun CollectionMarkDot(color: Color, size: Dp = 10.dp) {
    Box(Modifier.size(size).background(color, CircleShape))
}

/** iOS `DayToggle`: a fixed-size square toggle for weekday pickers. */
@Composable
fun DayToggle(title: String, accessibilityName: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Radii.chip)
    val motion = LocalMotion.current
    val fill by animateColorAsState(if (isSelected) SyllColors.selectedSurface else SyllColors.surface, motion.animation(MotionTokens.press), label = "dayFill")
    val stroke by animateColorAsState(if (isSelected) SyllColors.interactive else SyllColors.stroke, motion.animation(MotionTokens.press), label = "dayStroke")
    val ink by animateColorAsState(if (isSelected) SyllColors.selectedInk else SyllColors.ink600, motion.animation(MotionTokens.press), label = "dayInk")
    Box(
        modifier
            .defaultMinSize(minHeight = Spacing.fieldHeight)
            .pressScaleClickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityName
                selected = isSelected
            }
            .background(fill, shape)
            .border(Borders.card, stroke, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title,
            style = Typography.secondary.copy(fontWeight = FontWeight.Bold),
            color = ink,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** iOS `ProgressTrack`: one continuous bar on an Ink 100 track. */
@Composable
fun ProgressTrack(fraction: Float, modifier: Modifier = Modifier, height: Dp = 8.dp, color: Color = SyllColors.interactive) {
    val motion = LocalMotion.current
    val shown by animateFloatAsState(fraction.coerceIn(0f, 1f), motion.animation(MotionTokens.progress), label = "progress")
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(SyllColors.track)) {
        Box(Modifier.fillMaxWidth(shown).fillMaxHeight().background(color, CircleShape))
    }
}

/**
 * ⓘ beside a label (iOS `InfoPopoverButton`): the explanation opens in a Material rich tooltip on tap, so rows keep
 * a single line.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoPopoverButton(text: String, accessibilityLabel: String) {
    val state = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberRichTooltipPositionProvider(),
        tooltip = {
            RichTooltip(colors = TooltipDefaults.richTooltipColors(containerColor = SyllColors.surface, contentColor = SyllColors.ink)) {
                Text(text, style = Typography.secondary, color = SyllColors.ink)
            }
        },
        state = state,
    ) {
        IconButton(onClick = { scope.launch { state.show() } }, modifier = Modifier.semantics { contentDescription = accessibilityLabel }) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = SyllColors.mutedInk, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * iOS `FlowHeader` (continuous style): a bare ✕ and a progress bar centred between equal side slots.
 */
@Composable
fun FlowHeader(
    stage: String,
    stageCurrent: Int,
    stageTotal: Int,
    step: Int,
    totalSteps: Int,
    closeLabel: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val value = stringResource(R.string.question_x_of_x, stageCurrent, stageTotal)
    // ✕ always sits at the top right; an equal empty slot on the left keeps the progress bar centred.
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(SIDE_SLOT))
        ProgressTrack(
            fraction = if (totalSteps > 0) step.toFloat() / totalSteps else 0f,
            height = 10.dp,
            modifier = Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = "$stage, $value" },
        )
        Box(
            Modifier
                .width(SIDE_SLOT)
                .height(Spacing.minTouchTarget)
                .pressScaleClickable(onClick = onClose)
                .semantics { contentDescription = closeLabel },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Close, contentDescription = null, tint = SyllColors.ink400, modifier = Modifier.size(22.dp))
        }
    }
}

private val SIDE_SLOT = 24.dp
