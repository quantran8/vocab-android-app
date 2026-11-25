package com.syll.designsystem.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.syll.R
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography

// Platform chrome: iOS sheets, navigation bars and buttons map to the Material equivalents, styled with the app's
// tokens. The brand look (colours, type, cards, buttons, Syllie, motion) stays; the platform frame is Android's.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun appBarColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = SyllColors.canvas,
    scrolledContainerColor = SyllColors.canvas,
    titleContentColor = SyllColors.ink,
    navigationIconContentColor = SyllColors.ink,
    actionIconContentColor = SyllColors.interactive,
)

/**
 * A Material full-screen dialog: the title in the top app bar and ✕ at its end (✕ always sits at the top right).
 * Replaces iOS's `.large` sheets (editors, the word page, session details). A confirming action goes in
 * [bottomBar]. [content] gets the bars' padding.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenDialog(
    title: String,
    onDismiss: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    /** A step inside the dialog: the back arrow leads the app bar (✕ still closes the whole dialog). */
    onBack: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Dialog(
        onDismissRequest = { onBack?.invoke() ?: onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = SyllColors.canvas,
            topBar = {
                TopAppBar(
                    title = { Text(title, style = Typography.sectionTitle.copy(fontWeight = FontWeight.Bold), maxLines = 1) },
                    navigationIcon = { if (onBack != null) BackButton(onBack) },
                    actions = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.close), tint = SyllColors.ink)
                        }
                    },
                    colors = appBarColors(),
                )
            },
            bottomBar = bottomBar,
            content = content,
        )
    }
}

/**
 * A pushed page: the large title collapses into the top app bar as the page scrolls, with the back arrow as its
 * navigation icon (iOS large title + back button). [content] gets the bar's padding and must scroll.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageScaffold(
    title: String,
    onBack: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = SyllColors.canvas,
        topBar = {
            LargeTopAppBar(
                title = { Text(title, style = Typography.screenTitle.copy(letterSpacing = Typography.tightTracking.sp), maxLines = 1) },
                navigationIcon = { BackButton(onBack) },
                actions = actions,
                colors = appBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = bottomBar,
        content = content,
    )
}

/** The top app bar's back arrow (a plain icon button, as on Android). */
@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = SyllColors.ink)
    }
}
