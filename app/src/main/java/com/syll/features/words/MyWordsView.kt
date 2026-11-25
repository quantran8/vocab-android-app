package com.syll.features.words

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syll.R
import com.syll.app.LocalAppScope
import com.syll.app.text
import com.syll.app.LocalLearnerStore
import com.syll.app.LocalPracticeSessionStore
import com.syll.designsystem.components.ActionButtonKind
import com.syll.designsystem.components.IconBadge
import com.syll.designsystem.components.IconBadgeTone
import com.syll.designsystem.components.ScreenTitle
import com.syll.designsystem.components.SearchField
import com.syll.designsystem.components.SecondaryButton
import com.syll.designsystem.components.SectionTitle
import com.syll.designsystem.components.SmallButton
import com.syll.designsystem.components.cardSurface
import com.syll.designsystem.components.pressScaleClickable
import com.syll.designsystem.motion.RollingNumber
import com.syll.designsystem.tokens.Borders
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography
import com.syll.domain.practice.SessionMode
import com.syll.network.WordProgress
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Reaching this many rows from the end loads the next page. */
private const val LOAD_MORE_DISTANCE = 10

/**
 * iOS `MyWordsView`: the learned library with its figures, the three practices and a searchable, paged word list.
 * Adding and managing your own words arrive with Custom words (Phase 2).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyWordsView(onOpenRandomPractice: () -> Unit) {
    val learner = LocalLearnerStore.current
    val practice = LocalPracticeSessionStore.current
    val appScope = LocalAppScope.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<WordProgress?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val trimmed = query.trim()

    // Searches 300 ms after typing stops. Runs in the app's scope: leaving the tab must not abandon a query the
    // store already recorded.
    LaunchedEffect(trimmed) {
        if (trimmed == learner.wordsQuery) return@LaunchedEffect
        delay(300)
        appScope.launch { learner.loadWords(trimmed) }
    }
    // The next page, once the list scrolls near its end.
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last ->
                val total = listState.layoutInfo.totalItemsCount
                if (total > 0 && last >= total - LOAD_MORE_DISTANCE) appScope.launch { learner.loadMoreWords() }
            }
    }

    val stats = learner.wordStats
    val words = learner.words
    val needPractice = stats?.needPracticeWords ?: 0
    // Starting a session swaps this screen out, so it runs in the app's scope.
    fun start(mode: SessionMode) = appScope.launch { practice.start(mode) }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                learner.refresh()
                isRefreshing = false
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.screenGutter, vertical = Spacing.l),
        ) {
            item { ScreenTitle(stringResource(R.string.my_words)) }
            if ((stats?.learnedWords ?: words.size) == 0 && learner.wordsQuery.isEmpty()) {
                item { EmptyState(Modifier.padding(top = Spacing.xl)) }
                return@LazyColumn
            }
            stats?.let { item { LibraryStats(it.learnedWords, needPractice, it.fluentWords, Modifier.padding(top = Spacing.l)) } }
            item {
                SectionTitle(stringResource(R.string.practice), Modifier.padding(top = Spacing.xl + Spacing.xxs))
                PracticeSection(needPractice, onWeak = { start(SessionMode.WEAK_WORDS_PRACTICE) }, onSpeaking = { start(SessionMode.SPEAKING_PRACTICE) }, onRandom = onOpenRandomPractice)
            }
            item {
                SectionTitle(stringResource(R.string.all_words), Modifier.padding(top = Spacing.xl + Spacing.xxs))
                SearchField(stringResource(R.string.search_your_words), query, { query = it })
            }
            if (words.isEmpty()) {
                item {
                    val error = learner.wordsError
                    when {
                        learner.isSearchingWords -> LoadingRow()
                        error != null -> RetryRow(error.text()) { appScope.launch { learner.loadWords(trimmed) } }
                        learner.wordsQuery.isNotEmpty() -> Text(
                            stringResource(R.string.no_words_match_x, learner.wordsQuery),
                            style = Typography.secondary,
                            color = SyllColors.mutedInk,
                            modifier = Modifier.padding(vertical = Spacing.l - 2.dp),
                        )
                    }
                }
            } else {
                item { Spacer(Modifier.height(Spacing.xs)) }
                items(words, key = { it.id }) { word ->
                    WordRow(word, Modifier.alpha(if (learner.isSearchingWords) 0.5f else 1f)) { selected = word }
                }
                item {
                    val error = learner.wordsError
                    when {
                        learner.isLoadingMoreWords -> LoadingRow()
                        error != null -> RetryRow(error.text()) {
                            appScope.launch { if (learner.hasMoreWords) learner.loadMoreWords() else learner.loadWords(trimmed) }
                        }
                    }
                }
            }
        }
    }

    selected?.let { word ->
        WordDetailSheet(
            wordId = word.wordId,
            lemma = word.lemma,
            ipa = word.ipa,
            onDismiss = { selected = null },
            playAudio = word.meanings.firstNotNullOfOrNull { it.meaning.audio }?.let { clip -> { practice.playClip(clip) } },
        )
    }
}

@Composable
private fun LoadingRow() {
    Box(Modifier.fillMaxWidth().padding(vertical = Spacing.m), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(24.dp), color = SyllColors.mutedInk, strokeWidth = 2.5.dp)
    }
}

@Composable
private fun RetryRow(message: String, onRetry: () -> Unit) {
    Column(Modifier.padding(vertical = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(message, style = Typography.secondary, color = SyllColors.errorInk)
        SecondaryButton(stringResource(R.string.try_again_2), onClick = onRetry, icon = Icons.Filled.Refresh)
    }
}

/**
 * iOS `LibraryStats`: words learned as the main figure; need practice and fluent are subsets of it, so they sit
 * smaller beside it. Read-only.
 */
@Composable
private fun LibraryStats(learned: Int, needPractice: Int, fluent: Int?, modifier: Modifier) {
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min).cardSurface()) {
        Column(Modifier.padding(Spacing.m).semantics(mergeDescendants = true) {}) {
            RollingNumber(learned, Typography.stat.copy(letterSpacing = Typography.tightTracking.sp), SyllColors.ink)
            Caption(stringResource(R.string.words_learned))
        }
        Box(Modifier.width(Borders.hairline).fillMaxHeight().background(SyllColors.divider))
        Column(Modifier.weight(1f).padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Subset(needPractice, stringResource(R.string.need_practice), if (needPractice > 0) SyllColors.coral600 else SyllColors.ink400)
            fluent?.let { Subset(it, stringResource(R.string.said_fluently), SyllColors.successInk) }
        }
    }
}

@Composable
private fun Subset(value: Int, caption: String, color: Color) {
    Row(Modifier.semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        RollingNumber(value, Typography.statSmall, color, Modifier.width(36.dp))
        Caption(caption)
    }
}

@Composable
private fun Caption(text: String) {
    Text(text, style = Typography.micro.copy(fontWeight = FontWeight.SemiBold), color = SyllColors.mutedInk)
}

/** Weak Words and Speaking start at once (Weak Words only with words to practise); Random needs one more step (›). */
@Composable
private fun PracticeSection(needPractice: Int, onWeak: () -> Unit, onSpeaking: () -> Unit, onRandom: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            PracticeTile(
                Icons.Filled.GpsFixed, IconBadgeTone.ERROR, stringResource(R.string.weak_words),
                stringResource(if (needPractice > 0) R.string.words_you_keep_missing else R.string.no_words_need_practice),
                Modifier.weight(1f),
            ) {
                if (needPractice > 0) {
                    SmallButton(stringResource(R.string.practice_now), onClick = onWeak, modifier = Modifier.fillMaxWidth(), kind = ActionButtonKind.PRACTICE)
                }
            }
            PracticeTile(
                Icons.Filled.Mic, IconBadgeTone.SUCCESS, stringResource(R.string.speaking_practice), stringResource(R.string.say_your_words_out_loud),
                Modifier.weight(1f),
            ) {
                SmallButton(stringResource(R.string.speak_now), onClick = onSpeaking, modifier = Modifier.fillMaxWidth(), icon = Icons.Filled.Mic, kind = ActionButtonKind.PRACTICE)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .pressScaleClickable(onClick = onRandom)
                .cardSurface()
                .padding(Spacing.m)
                .semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(Icons.Filled.Shuffle, IconBadgeTone.ACCENT)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(stringResource(R.string.random_practice), style = Typography.emphasis, color = SyllColors.ink)
                Text(stringResource(R.string.choose_a_collection_or_topics_to_practice), style = Typography.micro, color = SyllColors.mutedInk)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = SyllColors.ink400)
        }
    }
}

/** A practice that starts at once: icon, title and subtitle on top, its button pinned to the bottom so tiles line up. */
@Composable
private fun PracticeTile(
    icon: ImageVector,
    tone: IconBadgeTone,
    title: String,
    subtitle: String,
    modifier: Modifier,
    action: @Composable () -> Unit,
) {
    Column(modifier.fillMaxHeight().cardSurface().padding(Spacing.m)) {
        IconBadge(icon, tone)
        Column(Modifier.padding(top = Spacing.s).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = Typography.emphasis, color = SyllColors.ink)
            Text(subtitle, style = Typography.micro, color = SyllColors.mutedInk)
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.padding(top = Spacing.m)) { action() }
    }
}

@Composable
private fun WordRow(word: WordProgress, modifier: Modifier, onClick: () -> Unit) {
    val hint = stringResource(R.string.shows_every_meaning_of_the_word)
    Column(modifier.pressScaleClickable(onClick = onClick).semantics(mergeDescendants = true) { contentDescription = "${word.lemma}. $hint" }) {
        Row(Modifier.fillMaxWidth().padding(vertical = Spacing.s), horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs / 2)) {
                Text(word.lemma, style = Typography.wordClass.copy(fontWeight = FontWeight.Bold), color = SyllColors.ink)
                word.ipa?.let { Text(it, style = Typography.secondary, color = SyllColors.mutedInk) }
            }
            MeaningDots(learned = word.meanings.size, total = word.meaningCount)
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = SyllColors.ink300, modifier = Modifier.size(20.dp))
        }
        Box(Modifier.fillMaxWidth().height(Borders.hairline).background(SyllColors.divider))
    }
}

/** iOS `MeaningDots`: one dot per meaning, Green learned and Ink 200 not yet; past six, a count instead. */
@Composable
private fun MeaningDots(learned: Int, total: Int?) {
    val count = maxOf(total ?: learned, learned)
    val label = if (count == 1) stringResource(R.string.s_1_of_1_meaning_learned) else stringResource(R.string.x_of_x_meanings_learned, learned, count)
    Box(Modifier.semantics { contentDescription = label }) {
        if (count > MAX_DOTS) {
            Text("$learned/$count", style = Typography.micro.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"), color = SyllColors.mutedInk)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                repeat(count) { index ->
                    Box(Modifier.size(Spacing.xs).background(if (index < learned) SyllColors.success else SyllColors.stroke, CircleShape))
                }
            }
        }
    }
}

private const val MAX_DOTS = 6

@Composable
private fun EmptyState(modifier: Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Box(Modifier.rotate(-8f)) { IconBadge(Icons.Outlined.Book, IconBadgeTone.ACCENT, size = Spacing.xl * 2) }
        Text(stringResource(R.string.no_words_yet), style = Typography.sectionTitle, color = SyllColors.ink, modifier = Modifier.padding(top = Spacing.xs))
        Text(
            stringResource(R.string.finish_today_s_flow_and_your_words_will_collect),
            style = Typography.secondary,
            color = SyllColors.bodyInk,
            textAlign = TextAlign.Center,
        )
    }
}
