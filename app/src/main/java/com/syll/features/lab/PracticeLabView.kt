package com.syll.features.lab

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.ShortText
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.syll.R
import com.syll.app.LocalChallengeRunStore
import com.syll.app.LocalPracticeSessionStore
import com.syll.designsystem.components.ActionButton
import com.syll.designsystem.components.ActionButtonKind
import com.syll.designsystem.components.ActionContext
import com.syll.designsystem.components.BackButton
import com.syll.designsystem.components.FlowHeader
import com.syll.designsystem.components.LocalActionContext
import com.syll.designsystem.motion.LocalMotion
import com.syll.designsystem.tokens.Radii
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography
import com.syll.domain.challenge.ChallengeRunPlan
import com.syll.domain.practice.LabTemplate
import com.syll.domain.practice.PlanStep
import com.syll.domain.practice.ResultLabFixture
import com.syll.features.practice.LocalStepActionBarHost
import com.syll.features.practice.LocalStepIndex
import com.syll.features.practice.PracticeTaskView
import com.syll.features.practice.StepActionBarHost
import com.syll.features.practice.results.PracticeResultsView
import com.syll.network.ApiJson

/**
 * iOS `PracticeLabView`: every exercise template, the Match Madness sandbox and example results screens. Results here
 * are practice only. A page with the back arrow (iOS "‹ Home").
 */
@Composable
fun PracticeLabView(sandbox: TemplateSandboxStore) {
    val challengeRun = LocalChallengeRunStore.current
    val context = LocalContext.current
    val resultFixtures = remember {
        ResultLabFixture.decode(ApiJson, context.assets.open("results_lab.json").bufferedReader().use { it.readText() })
    }
    var shownResult by remember { mutableStateOf<ResultLabFixture?>(null) }

    BackHandler { sandbox.closeLab() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(horizontal = Spacing.xs)) {
            BackButton(onClick = sandbox::closeLab)
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenGutter)
                .padding(top = Spacing.xs, bottom = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    stringResource(R.string.practice_lab),
                    style = Typography.display.copy(letterSpacing = (-1).sp),
                    color = SyllColors.ink,
                    modifier = Modifier.semantics { heading() },
                )
                Text(stringResource(R.string.try_any_exercise_template_results_here_are_pract), style = Typography.secondary, color = SyllColors.mutedInk)
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                sandbox.templates.forEach { template ->
                    LabCard(
                        title = template.title,
                        subtitle = template.action,
                        icon = template.icon,
                        hint = stringResource(R.string.opens_the_exercise),
                        detail = if (template.tasks.size > 1) stringResource(R.string.x_examples, template.tasks.size) else null,
                    ) { sandbox.open(template.id) }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                SectionHeader(stringResource(R.string.challenge_games))
                // A static plan on the same engine as a live run: no API, no commit, no stars.
                LabCard(
                    stringResource(R.string.match_madness), stringResource(R.string.a_90_second_round_with_example_words), Icons.Outlined.Timer,
                    hint = stringResource(R.string.starts_a_practice_round), accent = true,
                ) { challengeRun.startSandbox(ChallengeRunPlan.sandbox(), timed = true) }
                LabCard(
                    stringResource(R.string.match_madness_untimed), stringResource(R.string.the_same_board_with_no_clock_at_your_own_pace), Icons.Outlined.Pets,
                    hint = stringResource(R.string.starts_a_practice_round), accent = true,
                ) { challengeRun.startSandbox(ChallengeRunPlan.sandbox(), timed = false) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                SectionHeader(stringResource(R.string.results_screens))
                // Example commit summaries on the real results screen: no session, no API.
                resultFixtures.forEach { fixture ->
                    LabCard(fixture.title, fixture.subtitle, Icons.Outlined.BarChart, hint = stringResource(R.string.shows_the_results_screen)) {
                        shownResult = fixture
                    }
                }
            }
        }
    }

    shownResult?.let { fixture ->
        // iOS `.fullScreenCover`.
        Dialog(onDismissRequest = { shownResult = null }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Box(Modifier.fillMaxSize().background(SyllColors.canvas)) {
                CompositionLocalProvider(LocalActionContext provides ActionContext.PRACTICE) {
                    PracticeResultsView(
                        result = fixture.result,
                        scopeEvents = null,
                        onChooseTopics = null,
                        firstTryCount = 0,
                        onPracticeAgain = null,
                        onDone = { shownResult = null },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = Typography.emphasis, color = SyllColors.ink, modifier = Modifier.semantics { heading() })
}

/** iOS `TemplateCard` / `GameCard` / `ResultFixtureCard`: an outline button with an icon tile, a title and a line. */
@Composable
private fun LabCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    hint: String,
    detail: String? = null,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    ActionButton(ActionButtonKind.OUTLINE, onClick, Modifier.fillMaxWidth().semantics { contentDescription = "$title. $subtitle. $hint" }) {
        Row(Modifier.fillMaxWidth().padding(Spacing.m), horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(48.dp).background(if (accent) SyllColors.selectedSurface else SyllColors.neutralSurface, RoundedCornerShape(Radii.chip)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = if (accent) SyllColors.selectedInk else SyllColors.ink)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(title, style = Typography.emphasis, color = SyllColors.ink)
                Text(subtitle, style = Typography.secondary, color = SyllColors.mutedInk)
                detail?.let { Text(it, style = Typography.micro.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = SyllColors.selectedInk) }
            }
        }
    }
}

/** SF Symbol names in `practice_lab.json` → Material icons. */
private val LabTemplate.icon: ImageVector
    get() = when (systemImage) {
        "checklist" -> Icons.Outlined.Checklist
        "text.insert" -> Icons.Outlined.ShortText
        "keyboard" -> Icons.Outlined.Keyboard
        "character.cursor.ibeam" -> Icons.Outlined.Spellcheck
        "arrow.left.arrow.right" -> Icons.Outlined.CompareArrows
        "scissors" -> Icons.Outlined.ContentCut
        "mic.fill" -> Icons.Outlined.Mic
        else -> Icons.AutoMirrored.Outlined.FormatListBulleted
    }

/** iOS `LabTaskView`: plays one Lab template with the same task views as a live session, on the Lab's own store. */
@Composable
fun LabTaskView(sandbox: TemplateSandboxStore) {
    val session = sandbox.session
    val motion = LocalMotion.current
    val host = remember(session.sessionToken) { StepActionBarHost() }

    BackHandler { sandbox.closeTemplate() }

    CompositionLocalProvider(LocalPracticeSessionStore provides session) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            FlowHeader(
                stage = stringResource(R.string.practice_lab),
                stageCurrent = session.stepIndex + 1,
                stageTotal = session.steps.size,
                step = session.stepIndex + 1,
                totalSteps = session.steps.size,
                closeLabel = stringResource(R.string.back_to_lab),
                onClose = sandbox::closeTemplate,
                modifier = Modifier.padding(horizontal = Spacing.screenGutter).padding(top = Spacing.xxs, bottom = Spacing.s),
            )
            CompositionLocalProvider(LocalStepActionBarHost provides host) {
                AnimatedContent(
                    targetState = session.stepIndex,
                    transitionSpec = { motion.stepTransition(isMovingBack = false) },
                    label = "labStep",
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) { index ->
                    CompositionLocalProvider(LocalStepIndex provides index) {
                        val step = session.steps.getOrNull(index) as? PlanStep.Practice
                        val attempt = step?.let { session.attempts[it.task.id] }
                        if (attempt != null) PracticeTaskView(attempt)
                    }
                }
            }
            host.bar?.invoke()
        }
    }
}
