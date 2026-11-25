package com.syll.features.words

import com.syll.designsystem.components.PageScaffold
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.syll.R
import com.syll.app.LocalAppScope
import com.syll.app.LocalAppSettingsStore
import com.syll.app.LocalPracticeSessionStore
import com.syll.designsystem.components.CardDivider
import com.syll.designsystem.components.CollectionMarkDot
import com.syll.designsystem.components.Eyebrow
import com.syll.designsystem.components.PinnedActionBar
import com.syll.designsystem.components.PrimaryButton
import com.syll.designsystem.components.SectionTitle
import com.syll.designsystem.components.SelectionMark
import com.syll.designsystem.components.SymbolIcons
import com.syll.designsystem.components.TextLink
import com.syll.designsystem.components.TopicChip
import com.syll.designsystem.components.cardSurface
import com.syll.designsystem.components.pressScaleClickable
import com.syll.designsystem.motion.LocalMotion
import com.syll.designsystem.motion.MotionTokens
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography
import com.syll.domain.Topic
import com.syll.domain.VocabCollection
import com.syll.domain.practice.PracticeFilter
import com.syll.features.plan.CatalogState
import com.syll.features.plan.CollectionMarkColor
import com.syll.features.plan.displayName
import kotlinx.coroutines.launch

/** iOS `WordSource`: a collection keeps the topics picked inside it; all of its learned topics is the whole collection. */
sealed interface WordSource {
    data object All : WordSource
    data class Collection(val id: String, val topics: Set<String>) : WordSource
    data object Custom : WordSource

    fun filter(collections: List<VocabCollection>): PracticeFilter? = when (this) {
        All -> null
        Custom -> PracticeFilter.CustomWords
        is Collection -> {
            val collection = collections.firstOrNull { it.id == id }
            if (collection == null) {
                PracticeFilter.Collection(id)
            } else {
                val learned = collection.learnedTopics.map { it.id }
                // Catalog order, so the same choice always sends the same request.
                if (topics.containsAll(learned)) PracticeFilter.Collection(id) else PracticeFilter.Topics(learned.filter { it in topics })
            }
        }
    }
}

/** Topics with at least one learned word: the only ones practice can draw from. */
val VocabCollection.learnedTopics: List<Topic> get() = topics.filter { (it.learned ?: 0) > 0 }

/**
 * iOS `RandomPracticeView`: pushed from My Words' Random Practice row. Pick the words to practise from; nothing is
 * planned until Start.
 */
@Composable
fun RandomPracticeView(onBack: () -> Unit) {
    val practice = LocalPracticeSessionStore.current
    val appScope = LocalAppScope.current
    var source by remember { mutableStateOf<WordSource>(WordSource.All) }
    // Collections the learner has learned from.
    val collections = CatalogState.collections.filter { (it.learned ?: 0) > 0 }
    val requirement = (source as? WordSource.Collection)?.takeIf { it.topics.isEmpty() }?.let { stringResource(R.string.choose_at_least_one_topic) }

    PageScaffold(
        stringResource(R.string.random_practice),
        onBack,
        bottomBar = {
            PinnedActionBar {
                PrimaryButton(
                    stringResource(R.string.start),
                    requirement = requirement,
                    enabled = requirement == null,
                    // Planning swaps this screen out, so it runs in the app's scope.
                    onClick = { appScope.launch { practice.startPractice(source.filter(collections)) } },
                )
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenGutter)
                .padding(bottom = Spacing.l),
        ) {
            SectionTitle(stringResource(R.string.word_source), Modifier.padding(top = Spacing.s))
            WordSourceList(collections, source) { source = it }
        }
    }
}

private sealed interface SourceEntry {
    data object All : SourceEntry
    data class Collection(val collection: VocabCollection) : SourceEntry
    data object Custom : SourceEntry

    fun matches(source: WordSource) = when (this) {
        All -> source == WordSource.All
        Custom -> source == WordSource.Custom
        is Collection -> (source as? WordSource.Collection)?.id == collection.id
    }
}

/** Every source in one card. Picking a collection unfolds its topics right under that row, inside the same card. */
@Composable
private fun WordSourceList(collections: List<VocabCollection>, source: WordSource, onChange: (WordSource) -> Unit) {
    val settings = LocalAppSettingsStore.current
    val motion = LocalMotion.current
    val entries = listOf(SourceEntry.All) + collections.map { SourceEntry.Collection(it) } + SourceEntry.Custom
    Column(Modifier.fillMaxWidth().cardSurface()) {
        entries.forEachIndexed { index, entry ->
            if (index > 0) CardDivider()
            SourceRow(entry, entry.matches(source)) {
                settings.feedback.selectionChanged()
                when (entry) {
                    SourceEntry.All -> onChange(WordSource.All)
                    SourceEntry.Custom -> onChange(WordSource.Custom)
                    // A new collection starts with every topic; tapping the chosen one keeps its topics.
                    is SourceEntry.Collection -> if (!entry.matches(source)) {
                        onChange(WordSource.Collection(entry.collection.id, entry.collection.learnedTopics.map { it.id }.toSet()))
                    }
                }
            }
            if (entry is SourceEntry.Collection) {
                val picked = (source as? WordSource.Collection)?.takeIf { it.id == entry.collection.id }
                // Unfolds and folds with the `snap` spring.
                AnimatedVisibility(
                    picked != null,
                    enter = expandVertically(motion.animation(MotionTokens.snap), expandFrom = Alignment.Top) + fadeIn(motion.animation(MotionTokens.snap)),
                    exit = shrinkVertically(motion.animation(MotionTokens.snap), shrinkTowards = Alignment.Top) + fadeOut(motion.animation(MotionTokens.snap)),
                ) {
                    val topics = picked?.topics ?: emptySet()
                    TopicSection(entry.collection, topics) {
                        settings.feedback.selectionChanged()
                        onChange(WordSource.Collection(entry.collection.id, it))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopicSection(collection: VocabCollection, picked: Set<String>, onChange: (Set<String>) -> Unit) {
    val topics = collection.learnedTopics
    val allPicked = topics.all { it.id in picked }
    Column(
        Modifier
            .fillMaxWidth()
            .background(SyllColors.blue50.copy(alpha = 0.6f))
            .padding(horizontal = Spacing.m)
            .padding(bottom = Spacing.m),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = Spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(stringResource(R.string.topics_x_x, topics.count { it.id in picked }, topics.size), Modifier.weight(1f))
            TextLink(
                stringResource(if (allPicked) R.string.deselect_all else R.string.select_all),
                onClick = { onChange(if (allPicked) emptySet() else topics.map { it.id }.toSet()) },
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            topics.forEach { topic ->
                TopicChip(
                    title = topic.displayName(collection),
                    isSelected = topic.id in picked,
                    onClick = { onChange(if (topic.id in picked) picked - topic.id else picked + topic.id) },
                    icon = SymbolIcons.icon(topic.icon),
                    selectedFill = SyllColors.surface,
                )
            }
        }
    }
}

/** One row of the source card: the collection's mark dot (or the stack icon for every word), then a round check. */
@Composable
private fun SourceRow(entry: SourceEntry, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressScaleClickable(onClick = onClick)
            .semantics(mergeDescendants = true) { selected = isSelected }
            .padding(Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(Spacing.screenGutter), contentAlignment = Alignment.Center) {
            when (entry) {
                SourceEntry.All -> Icon(Icons.Outlined.Layers, null, tint = SyllColors.ink700, modifier = Modifier.size(20.dp))
                SourceEntry.Custom -> CollectionMarkDot(CollectionMarkColor.custom)
                is SourceEntry.Collection -> CollectionMarkDot(CollectionMarkColor.color(entry.collection))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                when (entry) {
                    SourceEntry.All -> stringResource(R.string.all_learned_words)
                    SourceEntry.Custom -> stringResource(R.string.words_you_added)
                    is SourceEntry.Collection -> entry.collection.displayName()
                },
                style = Typography.emphasis,
                color = SyllColors.ink,
            )
            if (entry is SourceEntry.Collection) {
                Text(stringResource(R.string.x_words_learned, entry.collection.learned ?: 0), style = Typography.micro, color = SyllColors.mutedInk)
            }
        }
        SelectionMark(isSelected)
    }
}

