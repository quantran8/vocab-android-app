package com.syll.features.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syll.R
import com.syll.app.LocalLearnerStore
import com.syll.app.LocalLearningPlanStore
import com.syll.app.UserMessage
import com.syll.app.text
import com.syll.designsystem.components.CardDivider
import com.syll.designsystem.components.CardRow
import com.syll.designsystem.components.CollectionMarkDot
import com.syll.designsystem.components.InfoPopoverButton
import com.syll.designsystem.components.ScreenTitle
import com.syll.designsystem.components.SectionTitle
import com.syll.designsystem.components.SymbolIcons
import com.syll.designsystem.components.TextLink
import com.syll.designsystem.components.chipSurface
import com.syll.designsystem.components.pressScaleClickable
import com.syll.designsystem.components.cardSurface
import com.syll.designsystem.tokens.Radii
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography
import com.syll.domain.LearningPreferences
import com.syll.domain.TopicCatalog
import com.syll.domain.VocabCollection
import com.syll.features.plan.CatalogState
import com.syll.features.plan.CollectionMarkColor
import com.syll.features.plan.SyllSwitch
import com.syll.features.plan.displayName
import com.syll.features.plan.learningDaysSummary
import com.syll.features.plan.reminderTimeText
import com.syll.network.NewWordOverride
import com.syll.network.NewWordOverrideMode
import com.syll.network.asApiError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

/** Topics shown before collapsing the rest into "+N more". */
private const val VISIBLE_TOPIC_LIMIT = 3

/**
 * iOS `ProfileView`: identity, then the plan in four sections (level, focus, routine, review). The plan editors
 * open as sheets from the shell. Name and avatar, Tier and LP arrive with Ranking (Phase 2); Premium with Phase 4.
 */
@Composable
fun ProfileView(onEditLevel: () -> Unit, onEditFocus: () -> Unit, onEditRoutine: () -> Unit, onOpenSettings: () -> Unit) {
    val plan = LocalLearningPlanStore.current
    val learner = LocalLearnerStore.current
    val scope = rememberCoroutineScope()
    var saveError by remember { mutableStateOf<UserMessage?>(null) }
    val preferences = plan.current

    fun save(change: suspend () -> Unit) {
        scope.launch {
            try {
                change()
                saveError = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                saveError = UserMessage.Api(e.asApiError)
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenGutter)
            .padding(top = Spacing.l, bottom = Spacing.l),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScreenTitle(stringResource(R.string.profile), Modifier.weight(1f))
            val settingsLabel = stringResource(R.string.settings)
            Box(
                Modifier
                    .size(Spacing.minTouchTarget)
                    .pressScaleClickable(onClick = onOpenSettings)
                    .semantics { contentDescription = settingsLabel },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(36.dp).background(SyllColors.neutralSurface, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Settings, null, tint = SyllColors.ink700, modifier = Modifier.size(20.dp))
                }
            }
        }

        Identity(learner.profile?.username, Modifier.padding(top = Spacing.l))

        LevelSection(learner.profile?.proficiencyLevel?.name, onEditLevel, Modifier.padding(top = Spacing.xl + Spacing.xxs))
        FocusSection(preferences, onEditFocus, Modifier.padding(top = Spacing.xl))
        RoutineSection(
            preferences,
            onEditRoutine,
            autoBalance = learner.home?.reviewLoad?.autoBalance ?: learner.profile?.settings?.autoBalanceNewWords ?: false,
            override = learner.home?.reviewLoad?.override,
            onDailySpeaking = { save { learner.setDailySpeaking(it) } },
            onAutoBalance = { save { learner.setAutoBalance(it) } },
            onCancelOverride = { save { learner.clearNewWordOverride() } },
            modifier = Modifier.padding(top = Spacing.xl),
        )
        ReviewSection(
            preferences,
            isPaused = learner::isReviewPaused,
            onToggle = { collection, reviewed -> save { learner.setReviewPaused(!reviewed, collection.id) } },
            modifier = Modifier.padding(top = Spacing.xl),
        )
        saveError?.let {
            Text(it.text(), style = Typography.micro, color = SyllColors.errorInk, modifier = Modifier.padding(top = Spacing.s))
        }
    }
}

/** 64 dp avatar with the name; the public name and avatar arrive with Ranking. */
@Composable
private fun Identity(username: String?, modifier: Modifier) {
    val name = username ?: stringResource(R.string.learner)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(Spacing.xl * 2).background(SyllColors.blue100, RoundedCornerShape(Radii.card)), contentAlignment = Alignment.Center) {
            Text(name.take(1).uppercase(), style = Typography.screenTitle, color = SyllColors.blue700)
        }
        Text(
            name,
            style = Typography.sectionTitle.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = Typography.tightTracking.sp),
            color = SyllColors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/** "Edit" beside a section title; the spoken label names what it edits. */
@Composable
private fun EditLink(accessibilityName: String, onClick: () -> Unit) {
    TextLink(stringResource(R.string.edit), onClick = onClick, modifier = Modifier.semantics { contentDescription = accessibilityName })
}

@Composable
private fun DataRow(label: String, value: String) {
    CardRow(Modifier.semantics(mergeDescendants = true) {}) {
        Text(label, style = Typography.body, color = SyllColors.ink, modifier = Modifier.weight(1f))
        Text(value, style = Typography.body.copy(fontWeight = FontWeight.Bold), color = SyllColors.ink, textAlign = TextAlign.End)
    }
}

@Composable
private fun LevelSection(level: String?, onEdit: () -> Unit, modifier: Modifier) {
    Column(modifier) {
        SectionTitle(stringResource(R.string.your_level)) { EditLink(stringResource(R.string.edit_level), onEdit) }
        Column(Modifier.fillMaxWidth().cardSurface()) {
            DataRow(stringResource(R.string.english_level), level ?: stringResource(R.string.not_set))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FocusSection(preferences: LearningPreferences, onEdit: () -> Unit, modifier: Modifier) {
    val collection = CatalogState.collection(preferences.collectionId)
    CatalogState.collections // re-read when the catalog updates
    val topics = TopicCatalog.topics(preferences.topicIds)
    val hidden = maxOf(0, topics.size - VISIBLE_TOPIC_LIMIT)
    Column(modifier) {
        SectionTitle(stringResource(R.string.your_focus)) { EditLink(stringResource(R.string.edit_focus), onEdit) }
        collection?.let {
            Row(Modifier.padding(bottom = Spacing.s), horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                CollectionMarkDot(CollectionMarkColor.color(it), size = Spacing.xs)
                Text(it.displayName(), style = Typography.secondary.copy(fontWeight = FontWeight.SemiBold), color = SyllColors.bodyInk)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            topics.take(VISIBLE_TOPIC_LIMIT).forEach { topic ->
                Row(
                    Modifier.chipSurface(isSelected = false),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs - 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SymbolIcons.icon(topic.icon)?.let { Icon(it, null, tint = SyllColors.ink, modifier = Modifier.size(14.dp)) }
                    Text(
                        collection?.let { topic.displayName(it) } ?: topic.name,
                        style = Typography.secondary.copy(fontWeight = FontWeight.SemiBold),
                        color = SyllColors.ink,
                        maxLines = 1,
                    )
                }
            }
            if (hidden > 0) {
                val hint = stringResource(R.string.shows_all_topics)
                Box(
                    Modifier
                        .defaultMinSize(minHeight = Spacing.buttonSmallHeight)
                        .pressScaleClickable(onClick = onEdit)
                        .background(SyllColors.neutralSurface, CircleShape)
                        .padding(horizontal = Spacing.s + 2.dp)
                        .semantics { contentDescription = hint },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.x_more, hidden), style = Typography.secondary.copy(fontWeight = FontWeight.SemiBold), color = SyllColors.ink)
                }
            }
        }
    }
}

@Composable
private fun RoutineSection(
    preferences: LearningPreferences,
    onEdit: () -> Unit,
    autoBalance: Boolean,
    override: NewWordOverride?,
    onDailySpeaking: (Boolean) -> Unit,
    onAutoBalance: (Boolean) -> Unit,
    onCancelOverride: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier) {
        SectionTitle(stringResource(R.string.your_routine)) { EditLink(stringResource(R.string.edit_routine), onEdit) }
        Column(Modifier.fillMaxWidth().cardSurface()) {
            DataRow(stringResource(R.string.new_words_day), stringResource(R.string.x_words, preferences.wordsPerDay))
            CardDivider()
            DataRow(stringResource(R.string.reviews_session), stringResource(R.string.x_words, preferences.effectiveReviewsPerSession))
            CardDivider()
            DataRow(
                stringResource(R.string.learning_days),
                if (preferences.isEveryDay) stringResource(R.string.every_day) else learningDaysSummary(preferences.learningDays),
            )
            CardDivider()
            DataRow(
                stringResource(R.string.reminder),
                if (preferences.reminderEnabled) reminderTimeText(LocalTime.of(preferences.reminderHour, preferences.reminderMinute)) else stringResource(R.string.off),
            )
            CardDivider()
            ToggleRow(
                stringResource(R.string.speaking_practice_2),
                stringResource(R.string.read_words_aloud_in_your_lessons_right_or_wrong),
                stringResource(R.string.about_speaking_practice),
                preferences.dailySpeaking,
                onDailySpeaking,
            )
            CardDivider()
            ToggleRow(
                stringResource(R.string.balance_reviews_and_new_words),
                stringResource(R.string.fewer_new_words_while_many_words_wait_for_review),
                stringResource(R.string.about_balancing_reviews_and_new_words),
                autoBalance,
                onAutoBalance,
            )
            override?.let {
                CardDivider()
                OverrideRow(it, onCancelOverride)
            }
        }
    }
}

/** Label and ⓘ (the explanation is in its popover, not a subtitle), then the switch, saved at once. */
@Composable
private fun ToggleRow(label: String, info: String, infoLabel: String, isOn: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = Spacing.rowMinHeight).padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = Typography.body, color = SyllColors.ink)
        InfoPopoverButton(info, infoLabel)
        Spacer(Modifier.weight(1f))
        SyllSwitch(isOn, onChange, label)
    }
}

@Composable
private fun OverrideRow(override: NewWordOverride, onCancel: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val until = override.until
    val untilText = if (override.mode == NewWordOverrideMode.UNTIL_DATE && until != null) {
        val local = until.atZone(ZoneId.systemDefault())
        val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(local)
        stringResource(R.string.until_x, "${local.dayOfWeek.getDisplayName(TextStyle.FULL, locale)} $time")
    } else {
        stringResource(R.string.until_10_or_fewer_words_are_waiting)
    }
    val cancelLabel = stringResource(R.string.cancel_lowered_new_words)
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = Spacing.rowMinHeight).padding(horizontal = Spacing.m, vertical = Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.lowered_to_x_new_words, override.value), style = Typography.body, color = SyllColors.ink)
            Text(untilText, style = Typography.micro, color = SyllColors.mutedInk)
        }
        TextLink(stringResource(R.string.cancel), onClick = onCancel, modifier = Modifier.semantics { contentDescription = cancelLabel })
    }
}

/** Collections the learner has learned from. The active one is always reviewed. */
@Composable
private fun ReviewSection(
    preferences: LearningPreferences,
    isPaused: (String) -> Boolean,
    onToggle: (VocabCollection, Boolean) -> Unit,
    modifier: Modifier,
) {
    val collections = CatalogState.collections.filter { (it.learned ?: 0) > 0 }
    Column(modifier) {
        SectionTitle(stringResource(R.string.review))
        if (collections.isEmpty()) {
            Text(stringResource(R.string.collections_you_learn_from_will_show_up_here), style = Typography.secondary, color = SyllColors.mutedInk)
        } else {
            Column(Modifier.fillMaxWidth().cardSurface()) {
                collections.forEachIndexed { index, collection ->
                    if (index > 0) CardDivider()
                    val isActive = collection.id == preferences.collectionId
                    val isReviewed = isActive || !isPaused(collection.id)
                    val hint = stringResource(R.string.review_this_collection_s_words)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = Spacing.rowMinHeight)
                            .padding(horizontal = Spacing.m, vertical = Spacing.s)
                            .semantics { contentDescription = hint },
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CollectionMarkDot(CollectionMarkColor.color(collection), size = Spacing.s - 2.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(collection.displayName(), style = Typography.body.copy(fontWeight = FontWeight.SemiBold), color = SyllColors.ink)
                            Text(
                                if (isActive) {
                                    stringResource(R.string.x_words_your_current_collection_is_always_review, collection.learned ?: 0)
                                } else {
                                    stringResource(R.string.x_words_learned, collection.learned ?: 0)
                                },
                                style = Typography.micro,
                                color = SyllColors.mutedInk,
                            )
                        }
                        // The active collection is always reviewed, so its switch is on and fixed.
                        SyllSwitch(isReviewed, { onToggle(collection, it) }, collection.displayName(), enabled = !isActive)
                    }
                }
            }
        }
    }
}
