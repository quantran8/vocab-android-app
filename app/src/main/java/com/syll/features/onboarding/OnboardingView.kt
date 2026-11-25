package com.syll.features.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.syll.R
import com.syll.app.LocalAppScope
import com.syll.app.LocalLearnerStore
import com.syll.app.LocalPracticeSessionStore
import com.syll.domain.practice.SessionMode
import com.syll.app.LocalOnboardingStore
import com.syll.app.LocalPlacementTestStore
import com.syll.app.UserMessage
import com.syll.app.text
import com.syll.designsystem.components.ActionButtonKind
import com.syll.designsystem.components.BackButton
import com.syll.designsystem.components.CardDivider
import com.syll.designsystem.components.CardRow
import com.syll.designsystem.components.CollectionMarkDot
import com.syll.designsystem.components.Eyebrow
import com.syll.designsystem.components.PinnedActionBar
import com.syll.designsystem.components.PlanSectionTitle
import com.syll.designsystem.components.PrimaryButton
import com.syll.designsystem.components.ProgressTrack
import com.syll.designsystem.components.ScreenTitle
import com.syll.designsystem.components.SyllieMood
import com.syll.designsystem.components.SyllieView
import com.syll.designsystem.components.cardSurface
import com.syll.designsystem.motion.LocalMotion
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography
import com.syll.features.placement.PlacementTestView
import com.syll.features.plan.CollectionList
import com.syll.features.plan.CollectionMarkColor
import com.syll.features.plan.DailySpeakingToggle
import com.syll.features.plan.LearningDaysPicker
import com.syll.features.plan.LevelList
import com.syll.features.plan.PlacementTestCard
import com.syll.features.plan.ReminderPicker
import com.syll.features.plan.ReviewsPerSessionPicker
import com.syll.features.plan.TopicPicker
import com.syll.features.plan.WordsPerDayStepper
import com.syll.features.plan.displayName
import com.syll.features.plan.learningDaysSummary
import com.syll.features.plan.reminderTimeText
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * iOS `OnboardingView`: Welcome → Level (optionally through the placement test) → Collection → Topics → Routine →
 * Ready (docs/app-redesign-v1.html, flow 0b). Welcome, the test and Ready are whole screens; the four questions
 * share [SetupFlow]'s frame.
 */
@Composable
fun OnboardingView() {
    val store = LocalOnboardingStore.current
    val placement = LocalPlacementTestStore.current
    val motion = LocalMotion.current

    BackHandler(enabled = store.step != OnboardingStore.Step.WELCOME && store.step != OnboardingStore.Step.READY) {
        if (store.step == OnboardingStore.Step.PLACEMENT) placement.cancel()
        store.back()
    }

    AnimatedContent(
        targetState = screenOf(store.step),
        transitionSpec = { motion.stepTransition(isMovingBack = store.isMovingBack) },
        label = "onboardingScreen",
        modifier = Modifier.fillMaxSize().background(SyllColors.canvas),
    ) { screen ->
        when (screen) {
            Screen.WELCOME -> WelcomeScreen()
            Screen.SETUP -> SetupFlow()
            Screen.PLACEMENT -> PlacementScreen()
            Screen.READY -> ReadyScreen()
        }
    }
}

private enum class Screen { WELCOME, SETUP, PLACEMENT, READY }

private fun screenOf(step: OnboardingStore.Step) = when (step) {
    OnboardingStore.Step.WELCOME -> Screen.WELCOME
    OnboardingStore.Step.PLACEMENT -> Screen.PLACEMENT
    OnboardingStore.Step.READY -> Screen.READY
    else -> Screen.SETUP
}

// Welcome

@Composable
private fun WelcomeScreen() {
    val store = LocalOnboardingStore.current
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.xl, vertical = Spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.weight(1f))
            // At this frame his body is about 160 dp wide.
            SyllieView(SyllieMood.HAPPY, Modifier.size(267.dp, 220.dp))
            ScreenTitle(stringResource(R.string.hi_i_m_syllie), Modifier.padding(top = Spacing.l))
            Text(
                stringResource(R.string.answer_four_quick_questions_and_i_ll_put_togethe),
                style = Typography.body,
                color = SyllColors.bodyInk,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.s),
            )
            Spacer(Modifier.weight(1f))
        }
        PinnedActionBar {
            PrimaryButton(stringResource(R.string.get_started), onClick = store::begin)
        }
    }
}

/** iOS `OnboardingTitle`: a step's question (28 sp extrabold) and an optional line under it. */
@Composable
fun OnboardingTitle(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(top = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(title, style = Typography.stepTitle, color = SyllColors.ink, modifier = Modifier.semantics { heading() })
        if (subtitle != null) Text(subtitle, style = Typography.secondary, color = SyllColors.bodyInk)
    }
}

// Setup flow

/** Steps counted by the header: Level, Collection, Topics, Routine. */
private const val SETUP_STEP_COUNT = 4

/** One persistent frame for the four questions. The header and pinned action stay; only the content slides. */
@Composable
private fun SetupFlow() {
    val store = LocalOnboardingStore.current
    val motion = LocalMotion.current
    val scope = rememberCoroutineScope()
    val stepNumber = when (store.step) {
        OnboardingStore.Step.COLLECTION -> 2
        OnboardingStore.Step.TOPICS -> 3
        OnboardingStore.Step.ROUTINE -> 4
        else -> 1
    }
    val canAct = when (store.step) {
        OnboardingStore.Step.LEVEL -> store.level != null && !store.isSaving
        OnboardingStore.Step.COLLECTION -> store.canContinueFromCollection
        OnboardingStore.Step.TOPICS -> store.canContinueFromTopics
        OnboardingStore.Step.ROUTINE -> store.draft.isComplete && !store.isSaving
        else -> false
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        SetupHeader(stepNumber, onBack = store::back)

        AnimatedContent(
            targetState = store.step,
            transitionSpec = { motion.stepTransition(isMovingBack = store.isMovingBack) },
            label = "setupStep",
            modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds(),
            contentAlignment = Alignment.TopStart,
        ) { step ->
            when (step) {
                OnboardingStore.Step.COLLECTION -> CollectionStep()
                OnboardingStore.Step.TOPICS -> TopicsStep()
                OnboardingStore.Step.ROUTINE -> RoutineStep()
                else -> LevelStep()
            }
        }

        PinnedActionBar {
            PrimaryButton(
                title = stringResource(if (store.step == OnboardingStore.Step.ROUTINE) R.string.create_my_study_plan else R.string.s_continue),
                enabled = canAct,
                onClick = {
                    when (store.step) {
                        OnboardingStore.Step.LEVEL -> scope.launch { store.continueToCollection() }
                        OnboardingStore.Step.COLLECTION -> store.continueToTopics()
                        OnboardingStore.Step.TOPICS -> store.continueToRoutine()
                        OnboardingStore.Step.ROUTINE -> scope.launch { store.createFlow() }
                        else -> Unit
                    }
                },
            )
        }
    }
}

/** A back button and one segment per question; the bar says which step this is, so there's no "Step 2 of 4". */
@Composable
private fun SetupHeader(step: Int, onBack: () -> Unit) {
    val label = stringResource(R.string.step_x_of_x, step, SETUP_STEP_COUNT)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screenGutter - (Spacing.minTouchTarget - Spacing.iconButton) / 2).padding(top = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackButton(onBack)
        Row(
            Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = label },
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs - 2.dp),
        ) {
            repeat(SETUP_STEP_COUNT) { index ->
                ProgressTrack(if (index < step) 1f else 0f, Modifier.weight(1f), height = 6.dp)
            }
        }
        // Balances the back button so the bar stays centred.
        Spacer(Modifier.width(Spacing.minTouchTarget).height(1.dp))
    }
}

/** A scrolling step body with the frame's margins. */
@Composable
private fun StepPage(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screenGutter).padding(bottom = Spacing.l),
        content = content,
    )
}

@Composable
private fun SaveErrorText(message: UserMessage?) {
    if (message == null) return
    Row(
        Modifier.fillMaxWidth().padding(top = Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Filled.Error, contentDescription = null, tint = SyllColors.destructiveInk, modifier = Modifier.size(18.dp))
        Text(message.text(), style = Typography.secondary.copy(fontWeight = FontWeight.Medium), color = SyllColors.destructiveInk)
    }
}

// Level

@Composable
private fun LevelStep() {
    val store = LocalOnboardingStore.current
    val placement = LocalPlacementTestStore.current
    StepPage {
        OnboardingTitle(stringResource(R.string.what_s_your_english_level))
        Box(Modifier.padding(top = Spacing.l)) {
            PlacementTestCard(onClick = {
                placement.start()
                store.openPlacementTest()
            })
        }
        Eyebrow(stringResource(R.string.or_choose_it_yourself), Modifier.padding(top = Spacing.l, bottom = Spacing.s - 2.dp))
        LevelList(store.level, onSelect = store::selectLevel)
        SaveErrorText(store.errorMessage)
    }
}

@Composable
private fun PlacementScreen() {
    val store = LocalOnboardingStore.current
    val scope = rememberCoroutineScope()
    PlacementTestView(
        doneTitle = stringResource(R.string.use_this_level),
        isDoneEnabled = !store.isSaving,
        doneError = store.errorMessage,
        onDone = { result -> scope.launch { store.acceptPlacement(result) } },
        onChooseLevel = store::chooseLevelByHand,
        onLeave = store::back,
    )
}

// Collection

@Composable
private fun CollectionStep() {
    val store = LocalOnboardingStore.current
    StepPage {
        OnboardingTitle(
            stringResource(R.string.which_collection_do_you_want_to_learn),
            stringResource(R.string.pick_one_you_can_change_it_any_time),
        )
        Box(Modifier.padding(top = Spacing.l)) {
            CollectionList(store.draft.collectionId, onSelect = store::selectCollection)
        }
    }
}

// Topics

@Composable
private fun TopicsStep() {
    val store = LocalOnboardingStore.current
    val learner = LocalLearnerStore.current
    StepPage {
        OnboardingTitle(
            stringResource(R.string.which_topics_interest_you),
            stringResource(R.string.new_words_come_from_the_topics_you_choose),
        )
        store.draft.collection?.let { collection ->
            Box(Modifier.padding(top = Spacing.l)) {
                TopicPicker(
                    collection = collection,
                    selectedIds = store.draft.topicIds,
                    // Past the free limit nothing is added; Android offers no Premium until Phase 4.
                    onToggleTopic = { store.toggleTopic(it, learner.maxTopics) },
                    onToggleAll = { store.toggleAllTopics(learner.maxTopics) },
                    countsSelection = true,
                    showsProgress = false,
                    maxTopics = learner.maxTopics,
                )
            }
        }
    }
}

// Routine

@Composable
private fun RoutineStep() {
    val store = LocalOnboardingStore.current
    StepPage {
        OnboardingTitle(stringResource(R.string.your_routine))
        Column(Modifier.padding(top = Spacing.l + 4.dp), verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s - 2.dp)) {
                PlanSectionTitle(stringResource(R.string.new_words_day))
                WordsPerDayStepper(store.draft.wordsPerDay, onAdjust = store::adjustWordsPerDay)
            }
            ReviewsPerSessionPicker(store.draft, onChange = { store.draft = it }, onSelectionChanged = store.haptics::selectionChanged)
            DailySpeakingToggle(store.draft, onChange = { store.draft = it }, onSelectionChanged = store.haptics::selectionChanged)
            LearningDaysPicker(store.draft.learningDays, onToggleDay = store::toggleDay, onToggleEveryDay = store::toggleEveryDay)
            ReminderPicker(store.draft, onChange = { store.draft = it })
        }
        SaveErrorText(store.errorMessage)
    }
}

// Ready

/** The plan in one card, and the first lesson one tap away. */
@Composable
private fun ReadyScreen() {
    val store = LocalOnboardingStore.current
    val practice = LocalPracticeSessionStore.current
    val appScope = LocalAppScope.current
    val draft = store.draft
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenGutter)
                .padding(top = Spacing.xl + Spacing.xs, bottom = Spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SyllieView(SyllieMood.PROUD, Modifier.size(187.dp, 154.dp))
            ScreenTitle(stringResource(R.string.your_plan_is_ready), Modifier.padding(top = Spacing.l))
            Text(
                stringResource(R.string.your_first_lesson_has_x_new_words, draft.wordsPerDay),
                style = Typography.secondary,
                color = SyllColors.bodyInk,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.xs),
            )

            Column(Modifier.padding(top = Spacing.xl).cardSurface()) {
                store.level?.let {
                    SummaryRow(stringResource(R.string.level)) { Text(it.code) }
                    CardDivider()
                }
                draft.collection?.let { collection ->
                    SummaryRow(stringResource(R.string.collection)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                            CollectionMarkDot(CollectionMarkColor.color(collection))
                            Text(stringResource(R.string.x_x_topics, collection.displayName(), draft.topicIds.size), textAlign = TextAlign.End)
                        }
                    }
                    CardDivider()
                }
                SummaryRow(stringResource(R.string.each_day)) { Text(stringResource(R.string.x_new_words, draft.wordsPerDay)) }
                CardDivider()
                SummaryRow(stringResource(R.string.learning_days)) { Text(learningDaysSummary(draft.learningDays)) }
                CardDivider()
                SummaryRow(stringResource(R.string.reminder)) {
                    Text(
                        if (draft.reminderEnabled) {
                            reminderTimeText(LocalTime.of(draft.reminderHour, draft.reminderMinute))
                        } else {
                            stringResource(R.string.off)
                        },
                    )
                }
            }

            Text(
                stringResource(R.string.you_can_change_this_in_profile),
                style = Typography.micro,
                color = SyllColors.mutedInk,
                modifier = Modifier.padding(top = Spacing.s),
            )
        }
        PinnedActionBar {
            // Both in one turn: the route leaves onboarding straight for the session, never showing Home.
            PrimaryButton(
                stringResource(R.string.start_my_first_lesson),
                onClick = {
                    store.finish()
                    appScope.launch { practice.start(SessionMode.DAILY) }
                },
                kind = ActionButtonKind.PRACTICE,
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: @Composable () -> Unit) {
    CardRow(Modifier.semantics(mergeDescendants = true) {}) {
        Text(label, style = Typography.body, color = SyllColors.bodyInk)
        Spacer(Modifier.weight(1f))
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalTextStyle provides Typography.emphasis.copy(color = SyllColors.ink),
        ) { value() }
    }
}
