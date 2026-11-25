package com.syll.features.main

import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import com.syll.designsystem.motion.MotionTokens
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.syll.R
import com.syll.app.LocalAppScope
import com.syll.app.LocalLearnerStore
import com.syll.app.LocalLearningPlanStore
import com.syll.app.LocalPracticeSessionStore
import com.syll.app.UserMessage
import com.syll.app.text
import com.syll.designsystem.components.StatusScreen
import com.syll.designsystem.motion.LocalMotion
import com.syll.designsystem.tokens.Radii
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography
import com.syll.domain.CefrLevel
import com.syll.domain.LearningPreferences
import com.syll.features.home.HomeActions
import com.syll.features.home.HomeView
import com.syll.features.practice.PracticeSessionStore
import com.syll.features.profile.EditFocusSheet
import com.syll.features.profile.EditLevelSheet
import com.syll.features.profile.EditRoutineSheet
import com.syll.features.profile.ProfileView
import com.syll.features.settings.AppSettingsView
import com.syll.features.words.MyWordsView
import com.syll.features.words.RandomPracticeView
import com.syll.network.asApiError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** iOS `MainTab`. */
enum class MainTab(val title: Int, val icon: ImageVector) {
    HOME(R.string.home, Icons.Outlined.Home),
    WORDS(R.string.my_words, Icons.Outlined.AutoStories),
    RANKING(R.string.ranking, Icons.Outlined.EmojiEvents),
    PROFILE(R.string.profile, Icons.Outlined.AccountCircle),
}

/** iOS `ShellPage`: pages the tabs push. Settings belongs to Profile, Random Practice to My Words. */
enum class ShellPage { SETTINGS, RANDOM_PRACTICE }

/** iOS `PlanSheet`: plan editors Profile presents as bottom sheets. */
enum class PlanSheet { LEVEL, FOCUS, ROUTINE }

/**
 * iOS `MainShellView`: Home · My Words · Ranking · Profile on the platform tab bar (Material 3 `NavigationBar`).
 * Session screens live outside this shell. [tab] is owned by the root: the route swap unmounts this view during a
 * session, and closing one returns to the same tab. Each tab's pushed page is saved state, so Settings stays open when
 * changing the language recreates the activity. Ranking arrives with Phase 2.
 */
@Composable
fun MainShellView(tab: MainTab, onTabChange: (MainTab) -> Unit, homeActions: HomeActions) {
    val learner = LocalLearnerStore.current
    val plan = LocalLearningPlanStore.current
    val practice = LocalPracticeSessionStore.current
    val appScope = LocalAppScope.current
    val motion = LocalMotion.current
    var wordsPage by rememberSaveable { mutableStateOf<ShellPage?>(null) }
    var profilePage by rememberSaveable { mutableStateOf<ShellPage?>(null) }
    var planSheet by rememberSaveable { mutableStateOf<PlanSheet?>(null) }
    var toast by remember { mutableStateOf<UserMessage?>(null) }
    var toastToken by remember { mutableIntStateOf(0) }
    val toastScope = rememberCoroutineScope()

    fun showToast(message: UserMessage) {
        val token = ++toastToken
        toast = message
        toastScope.launch {
            delay(TOAST_MILLIS)
            if (toastToken == token) toast = null
        }
    }

    /** The plan sheets hang off Profile, so the Focus editor opens there. */
    fun openFocus() {
        onTabChange(MainTab.PROFILE)
        planSheet = PlanSheet.FOCUS
    }

    fun openRandomPractice() {
        onTabChange(MainTab.WORDS)
        wordsPage = ShellPage.RANDOM_PRACTICE
    }

    LaunchedEffect(Unit) {
        // A session screen may have asked for the next step as it closed. Premium arrives with Phase 4, ads after it.
        when (practice.consumeFollowUp()) {
            PracticeSessionStore.FollowUp.CHOOSE_TOPICS -> openFocus()
            PracticeSessionStore.FollowUp.RANDOM_PRACTICE -> openRandomPractice()
            PracticeSessionStore.FollowUp.UPGRADE, null -> Unit
        }
        learner.refresh()
    }
    // Back closes the pushed page first, then returns to Home before leaving the app.
    val page = when (tab) {
        MainTab.WORDS -> wordsPage
        MainTab.PROFILE -> profilePage
        else -> null
    }
    BackHandler(enabled = page != null || tab != MainTab.HOME) {
        when {
            tab == MainTab.WORDS && wordsPage != null -> wordsPage = null
            tab == MainTab.PROFILE && profilePage != null -> profilePage = null
            else -> onTabChange(MainTab.HOME)
        }
    }

    val shellHomeActions = remember(homeActions) {
        HomeActions(
            onStartDaily = homeActions.onStartDaily,
            onStartReviewOnly = homeActions.onStartReviewOnly,
            onOpenRandomPractice = ::openRandomPractice,
            onChooseTopics = ::openFocus,
            isPracticeActive = homeActions.isPracticeActive,
        )
    }

    Scaffold(
        containerColor = SyllColors.canvas,
        bottomBar = {
            // Pushed pages hide the tab bar (iOS `.toolbar(.hidden, for: .tabBar)`).
            if (page == null) {
                NavigationBar(containerColor = SyllColors.canvas) {
                    MainTab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = item == tab,
                            onClick = { onTabChange(item) },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(stringResource(item.title), style = Typography.tab) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = SyllColors.interactive,
                                selectedTextColor = SyllColors.interactive,
                                indicatorColor = SyllColors.selectedSurface,
                                unselectedIconColor = SyllColors.mutedInk,
                                unselectedTextColor = SyllColors.mutedInk,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            when (tab) {
                MainTab.HOME -> HomeView(shellHomeActions)
                MainTab.WORDS -> PushedPage(wordsPage) { shown ->
                    when (shown) {
                        ShellPage.RANDOM_PRACTICE -> RandomPracticeView(onBack = { wordsPage = null })
                        else -> MyWordsView(onOpenRandomPractice = ::openRandomPractice)
                    }
                }
                MainTab.RANKING -> StatusScreen(title = stringResource(R.string.ranking), modifier = Modifier.statusBarsPadding())
                MainTab.PROFILE -> PushedPage(profilePage) { shown ->
                    when (shown) {
                        ShellPage.SETTINGS -> AppSettingsView(onBack = { profilePage = null })
                        else -> ProfileView(
                            onEditLevel = { planSheet = PlanSheet.LEVEL },
                            onEditFocus = { planSheet = PlanSheet.FOCUS },
                            onEditRoutine = { planSheet = PlanSheet.ROUTINE },
                            onOpenSettings = { profilePage = ShellPage.SETTINGS },
                        )
                    }
                }
            }
            // Pops in above the tab bar (iOS `ToastView` under `popTransition`).
            AnimatedVisibility(
                toast != null,
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = Spacing.screenGutter, vertical = Spacing.s),
                // In with the `feedback` spring, out with `press` (iOS `motion.perform` around the toast).
                enter = if (motion.reduceMotion) fadeIn(motion.animation(MotionTokens.feedback)) else
                    scaleIn(MotionTokens.feedback.spec(), initialScale = 0.88f) + fadeIn(MotionTokens.feedback.spec()),
                exit = if (motion.reduceMotion) fadeOut(motion.animation(MotionTokens.press)) else
                    scaleOut(MotionTokens.press.spec(), targetScale = 0.88f) + fadeOut(MotionTokens.press.spec()),
            ) {
                val shown = remember(toast) { toast }
                shown?.let { ToastView(it.text()) }
            }
        }
    }

    fun savePreferences(preferences: LearningPreferences, success: UserMessage) {
        planSheet = null
        appScope.launch {
            try {
                learner.savePlan(preferences)
                showToast(success)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showToast(UserMessage.Api(e.asApiError))
            }
        }
    }

    fun saveLevel(level: CefrLevel, success: UserMessage) {
        planSheet = null
        appScope.launch {
            try {
                learner.updateLevel(level)
                showToast(success)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showToast(UserMessage.Api(e.asApiError))
            }
        }
    }

    val levelUpdated = UserMessage.Resource(R.string.level_updated)
    val focusUpdated = UserMessage.Resource(R.string.focus_updated)
    val routineUpdated = UserMessage.Resource(R.string.routine_updated)
    when (planSheet) {
        PlanSheet.LEVEL -> EditLevelSheet(
            initial = learner.profile?.proficiencyLevel,
            onSave = { saveLevel(it, levelUpdated) },
            onPlaced = {
                planSheet = null
                showToast(levelUpdated)
            },
            onDismiss = { planSheet = null },
        )
        PlanSheet.FOCUS -> EditFocusSheet(plan.current, onSave = { savePreferences(it, focusUpdated) }, onDismiss = { planSheet = null })
        PlanSheet.ROUTINE -> EditRoutineSheet(plan.current, onSave = { savePreferences(it, routineUpdated) }, onDismiss = { planSheet = null })
        null -> Unit
    }
}

private const val TOAST_MILLIS = 2_400L

/** A tab's root and the page pushed over it, swapped like a system push. */
@Composable
private fun PushedPage(page: ShellPage?, content: @Composable (ShellPage?) -> Unit) {
    val motion = LocalMotion.current
    AnimatedContent(
        page,
        transitionSpec = { motion.stepTransition(isMovingBack = targetState == null) },
        label = "shellPage",
    ) { content(it) }
}

/** iOS `ToastView`: Ink pill, white bold caption, announced when it appears. */
@Composable
private fun ToastView(text: String) {
    Text(
        text,
        style = Typography.micro.copy(fontWeight = FontWeight.Bold),
        color = Color.White,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(Radii.chip + 2.dp), ambientColor = SyllColors.ink.copy(alpha = 0.2f), spotColor = SyllColors.ink.copy(alpha = 0.2f))
            .background(SyllColors.ink, RoundedCornerShape(Radii.chip + 2.dp))
            .padding(vertical = Spacing.s + 1.dp, horizontal = Spacing.m)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}
