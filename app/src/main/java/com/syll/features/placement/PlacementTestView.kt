package com.syll.features.placement

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syll.R
import com.syll.app.LocalPlacementTestStore
import com.syll.app.UserMessage
import com.syll.app.text
import com.syll.designsystem.components.ActionButtonKind
import com.syll.designsystem.components.ActionContext
import com.syll.designsystem.components.ChoiceTileButton
import com.syll.designsystem.components.ConfirmSheet
import com.syll.designsystem.components.ConfirmSheetStyle
import com.syll.designsystem.components.FlowHeader
import com.syll.designsystem.components.LocalActionContext
import com.syll.designsystem.components.PinnedActionBar
import com.syll.designsystem.components.PrimaryButton
import com.syll.designsystem.components.SecondaryButton
import com.syll.designsystem.components.StatusScreen
import com.syll.designsystem.components.SyllieMood
import com.syll.designsystem.components.SyllieView
import com.syll.designsystem.components.WordTileState
import com.syll.designsystem.motion.LocalMotion
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography
import com.syll.features.plan.example
import com.syll.features.plan.title
import com.syll.network.PlacementQuestion
import com.syll.network.PlacementResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * iOS `PlacementTestView`: the placement test, from loading to its result. Onboarding hosts it as a step and
 * Profile as a full-screen page, so where each exit leads is up to the host.
 */
@Composable
fun PlacementTestView(
    doneTitle: String,
    onDone: (PlacementResult) -> Unit,
    /** The learner sets a level themselves. Carries the result if there is one, which is saved already. */
    onChooseLevel: (PlacementResult?) -> Unit,
    onLeave: () -> Unit,
    isDoneEnabled: Boolean = true,
    /** A failure in whatever the host does next, shown beside the action that retries it. */
    doneError: UserMessage? = null,
) {
    val store = LocalPlacementTestStore.current
    val scope = rememberCoroutineScope()

    fun leave() {
        store.cancel()
        onLeave()
    }

    // Answering questions is practice: Blue actions with a depth edge.
    CompositionLocalProvider(LocalActionContext provides ActionContext.PRACTICE) {
        Box(Modifier.fillMaxSize().background(SyllColors.canvas).safeDrawingPadding()) {
            when (val stage = store.stage) {
                PlacementTestStore.Stage.Idle, PlacementTestStore.Stage.Loading -> StatusScreen(
                    title = stringResource(R.string.preparing_your_test),
                    isBusy = true,
                    showsFlyingSyllie = true,
                ) {
                    SecondaryButton(stringResource(R.string.cancel), onClick = ::leave)
                }
                PlacementTestStore.Stage.NoContent -> StatusScreen(
                    title = stringResource(R.string.the_test_isn_t_ready_yet),
                    message = stringResource(R.string.choose_the_level_that_feels_closest_you_can_chan),
                ) {
                    PrimaryButton(stringResource(R.string.choose_my_level), onClick = { onChooseLevel(null) })
                }
                is PlacementTestStore.Stage.LoadFailed -> StatusScreen(
                    title = stringResource(R.string.couldn_t_load_the_test),
                    message = stage.message.text(),
                ) {
                    PrimaryButton(stringResource(R.string.try_again_2), onClick = { store.start() })
                    SecondaryButton(stringResource(R.string.choose_my_level), onClick = { onChooseLevel(null) })
                }
                PlacementTestStore.Stage.Answering -> store.currentQuestion?.let { question ->
                    // The host's scope: the last answer submits, which swaps this screen out mid-request.
                    PlacementQuestionScreen(question, scope, onLeave = ::leave)
                }
                PlacementTestStore.Stage.Submitting -> StatusScreen(
                    title = stringResource(R.string.working_out_your_level),
                    isBusy = true,
                    showsFlyingSyllie = true,
                )
                is PlacementTestStore.Stage.SubmitFailed -> StatusScreen(
                    title = stringResource(R.string.couldn_t_get_your_result),
                    message = stage.message.text(),
                ) {
                    if (stage.canRetry) {
                        PrimaryButton(stringResource(R.string.try_again_2), onClick = { scope.launch { store.retrySubmit() } })
                    }
                    SecondaryButton(stringResource(R.string.choose_my_level), onClick = { onChooseLevel(null) })
                }
                is PlacementTestStore.Stage.Finished -> PlacementResultScreen(stage.result) {
                    doneError?.let {
                        Text(it.text(), style = Typography.secondary, color = SyllColors.errorInk, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                    PrimaryButton(doneTitle, onClick = { onDone(stage.result) }, enabled = isDoneEnabled)
                    SecondaryButton(stringResource(R.string.choose_a_different_level), onClick = { onChooseLevel(stage.result) })
                }
            }
        }
    }
}

/** The level in large type with its name and example line, the same words as the level list. */
@Composable
private fun PlacementResultScreen(result: PlacementResult, actions: @Composable () -> Unit) {
    val level = result.proficiencyLevel
    Column(
        Modifier.fillMaxSize().padding(horizontal = Spacing.screenGutter).padding(bottom = Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.semantics(mergeDescendants = true) {}) {
            SyllieView(SyllieMood.PROUD, Modifier.size(160.dp, 132.dp))
            Text(
                stringResource(R.string.your_level),
                style = Typography.body.copy(fontWeight = FontWeight.SemiBold),
                color = SyllColors.mutedInk,
                modifier = Modifier.padding(top = Spacing.l),
            )
            Text(
                level.code,
                style = Typography.display.copy(fontSize = 96.sp, fontWeight = FontWeight.Black, letterSpacing = (-4).sp),
                color = SyllColors.ink,
                maxLines = 1,
                modifier = Modifier.padding(top = Spacing.xxs),
            )
            Text(stringResource(level.title), style = Typography.sectionTitle, color = SyllColors.ink, modifier = Modifier.padding(top = Spacing.xs))
            Text(
                stringResource(level.example),
                style = Typography.secondary,
                color = SyllColors.bodyInk,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            Text(
                stringResource(R.string.x_x_correct, result.correctCount, result.totalCount),
                style = Typography.secondary.copy(fontWeight = FontWeight.Bold),
                color = SyllColors.ink700,
                modifier = Modifier
                    .padding(top = Spacing.m + 4.dp)
                    .background(SyllColors.neutralSurface, CircleShape)
                    .defaultMinSize(minHeight = 32.dp)
                    .padding(horizontal = Spacing.s + 2.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) { actions() }
    }
}

@Composable
private fun PlacementQuestionScreen(question: PlacementQuestion, scope: CoroutineScope, onLeave: () -> Unit) {
    val store = LocalPlacementTestStore.current
    val motion = LocalMotion.current
    var isConfirmingLeave by remember { mutableStateOf(false) }
    var lastSkip by remember { mutableLongStateOf(0L) }

    /** Skip stays enabled and in place from one question to the next, so a double tap must not skip two. */
    fun skip() {
        val now = System.currentTimeMillis()
        if (now - lastSkip <= 400) return
        lastSkip = now
        scope.launch { store.skipQuestion() }
    }

    Column(Modifier.fillMaxSize()) {
        FlowHeader(
            stage = stringResource(R.string.placement_test),
            stageCurrent = store.index + 1,
            stageTotal = store.questions.size,
            step = store.index + 1,
            totalSteps = store.questions.size,
            closeLabel = stringResource(R.string.leave_the_test),
            onClose = { isConfirmingLeave = true },
            modifier = Modifier.padding(horizontal = Spacing.screenGutter, vertical = Spacing.xs),
        )

        AnimatedContent(
            targetState = question,
            transitionSpec = { motion.stepTransition(isMovingBack = false) },
            label = "placementQuestion",
            modifier = Modifier.weight(1f),
        ) { shown ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screenGutter)
                    .padding(top = Spacing.m, bottom = Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                shown.question?.let {
                    Text(
                        it,
                        style = Typography.screenTitle.copy(letterSpacing = (-0.4).sp),
                        color = SyllColors.ink,
                        modifier = Modifier.fillMaxWidth().semantics { heading() },
                    )
                }
                Text(
                    shown.prompt.text,
                    style = Typography.challengeTerm,
                    color = SyllColors.ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.s),
                )
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    shown.options.forEach { option ->
                        ChoiceTileButton(
                            text = option.text,
                            state = if (store.selectedOptionId == option.id) WordTileState.SELECTED else WordTileState.IDLE,
                            onClick = { store.select(option.id) },
                        )
                    }
                }
            }
        }

        PinnedActionBar {
            Text(
                stringResource(R.string.not_sure_tap_skip_a_guess_can_place_you_too_high),
                style = Typography.micro,
                color = SyllColors.mutedInk,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                // A third of the row: Next is the action this screen wants.
                SecondaryButton(stringResource(R.string.skip), onClick = ::skip, kind = ActionButtonKind.OUTLINE, modifier = Modifier.weight(1f))
                PrimaryButton(
                    title = stringResource(if (store.isOnLastQuestion) R.string.see_my_level else R.string.next),
                    onClick = { scope.launch { store.confirmAnswer() } },
                    enabled = store.selectedOptionId != null,
                    kind = ActionButtonKind.PRACTICE,
                    modifier = Modifier.weight(2f),
                )
            }
        }
    }

    if (isConfirmingLeave) {
        ConfirmSheet(
            style = ConfirmSheetStyle.LeavePractice,
            title = stringResource(R.string.leave_the_test_2),
            message = stringResource(R.string.your_answers_won_t_be_saved),
            cancelTitle = stringResource(R.string.keep_going),
            confirmTitle = stringResource(R.string.leave),
            onDismiss = { isConfirmingLeave = false },
            onConfirm = {
                isConfirmingLeave = false
                onLeave()
            },
        )
    }
}
