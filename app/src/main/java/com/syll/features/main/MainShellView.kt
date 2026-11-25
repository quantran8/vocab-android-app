package com.syll.features.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.syll.R
import com.syll.app.LocalAuthStore
import com.syll.app.LocalLearnerStore
import com.syll.app.LocalPracticeSessionStore
import com.syll.features.practice.PracticeSessionStore
import com.syll.designsystem.components.SecondaryButton
import com.syll.designsystem.components.StatusScreen
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography
import com.syll.features.home.HomeActions
import com.syll.features.home.HomeView
import kotlinx.coroutines.launch

/** iOS `MainTab`. */
enum class MainTab(val title: Int, val icon: ImageVector) {
    HOME(R.string.home, Icons.Outlined.Home),
    WORDS(R.string.my_words, Icons.Outlined.AutoStories),
    RANKING(R.string.ranking, Icons.Outlined.EmojiEvents),
    PROFILE(R.string.profile, Icons.Outlined.AccountCircle),
}

/**
 * iOS `MainShellView`: Home · My Words · Ranking · Profile on the platform tab bar (Material 3 `NavigationBar`).
 * Session screens live outside this shell. [tab] is owned by the root: the route swap unmounts this view during a
 * session, and closing one returns to the same tab.
 *
 * My Words and Profile arrive with step 7, Ranking with Phase 2; until then they are placeholders (Profile keeps
 * Log out).
 */
@Composable
fun MainShellView(tab: MainTab, onTabChange: (MainTab) -> Unit, homeActions: HomeActions) {
    val learner = LocalLearnerStore.current
    val practice = LocalPracticeSessionStore.current

    LaunchedEffect(Unit) {
        // A session screen may have asked for the next step as it closed. Focus and Random practice open with
        // step 7 (Profile and My Words); Premium with Phase 4. Later steps add ads and the challenge map.
        when (practice.consumeFollowUp()) {
            PracticeSessionStore.FollowUp.CHOOSE_TOPICS -> onTabChange(MainTab.PROFILE)
            PracticeSessionStore.FollowUp.RANDOM_PRACTICE -> onTabChange(MainTab.WORDS)
            PracticeSessionStore.FollowUp.UPGRADE, null -> Unit
        }
        learner.refresh()
    }
    // Back from another tab returns to Home before leaving the app.
    BackHandler(enabled = tab != MainTab.HOME) { onTabChange(MainTab.HOME) }

    Scaffold(
        containerColor = SyllColors.canvas,
        bottomBar = {
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
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            when (tab) {
                MainTab.HOME -> HomeView(homeActions)
                MainTab.WORDS -> PlaceholderTab(R.string.my_words)
                MainTab.RANKING -> PlaceholderTab(R.string.ranking)
                MainTab.PROFILE -> ProfilePlaceholder()
            }
        }
    }
}

@Composable
private fun PlaceholderTab(title: Int) {
    StatusScreen(title = stringResource(title), modifier = Modifier.statusBarsPadding())
}

@Composable
private fun ProfilePlaceholder() {
    val auth = LocalAuthStore.current
    val scope = rememberCoroutineScope()
    StatusScreen(title = stringResource(R.string.profile), modifier = Modifier.statusBarsPadding()) {
        SecondaryButton(stringResource(R.string.log_out), onClick = { scope.launch { auth.signOut() } })
    }
}
