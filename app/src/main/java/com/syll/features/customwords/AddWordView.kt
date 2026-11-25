package com.syll.features.customwords

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syll.R
import com.syll.app.LocalAppScope
import com.syll.app.LocalCustomWordStore
import com.syll.app.LocalSpeechPlayer
import com.syll.app.UserMessage
import com.syll.app.text
import com.syll.designsystem.components.CardDivider
import com.syll.designsystem.components.Eyebrow
import com.syll.designsystem.components.FieldInput
import com.syll.designsystem.components.FullScreenDialog
import com.syll.designsystem.components.PinnedActionBar
import com.syll.designsystem.components.PrimaryButton
import com.syll.designsystem.components.SecondaryButton
import com.syll.designsystem.components.StatusPill
import com.syll.designsystem.components.StatusPillTone
import com.syll.designsystem.components.SyllieLoadingView
import com.syll.designsystem.components.SyllieMood
import com.syll.designsystem.components.TextLink
import com.syll.designsystem.components.cardSurface
import com.syll.designsystem.components.fieldSurface
import com.syll.designsystem.motion.LocalMotion
import com.syll.designsystem.motion.MotionTokens
import com.syll.designsystem.tokens.Borders
import com.syll.designsystem.tokens.Radii
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography
import com.syll.domain.customwords.CustomWordAddition
import com.syll.domain.customwords.CustomWordEditDraft
import com.syll.domain.customwords.CustomWordInput
import com.syll.domain.customwords.CustomWordLookup
import com.syll.domain.customwords.ExistingMeaning
import com.syll.domain.customwords.ExistingWord
import com.syll.domain.customwords.WordDraft
import com.syll.features.practice.SyllieReactionView
import com.syll.features.words.ExampleTranslation
import com.syll.features.words.WordHeroHeader
import com.syll.features.words.groupByPartOfSpeech
import com.syll.features.words.localizedPartOfSpeech
import kotlinx.coroutines.launch

/**
 * iOS `AddWordView`: adding any word to the learning path. The whole flow stays in one full-screen dialog: type the
 * word, the lookup, what the backend found, editing it or looking for another meaning, and the confirmation are steps
 * inside it, with the back arrow and ✕ in the app bar (docs/app-redesign-v1.html, frames 3.1–3.7).
 */
@Composable
fun AddWordView(onDismiss: () -> Unit) {
    val store = LocalCustomWordStore.current
    val speech = LocalSpeechPlayer.current
    val motion = LocalMotion.current
    var text by rememberSaveable { mutableStateOf("") }
    /** A step pushed over a result (editing a meaning, looking for another one). */
    var reviewStep by remember { mutableStateOf<ReviewStep?>(null) }
    val stage = Stage.of(store, reviewStep)
    // Each result gets its own selection and drafts; a new lookup starts fresh.
    val review = remember(stage.resultKey) { stage.result?.let(::ReviewState) }
    LaunchedEffect(stage.resultKey) { reviewStep = null }

    DisposableEffect(Unit) {
        onDispose {
            store.resetLookup()
            speech?.stop()
        }
    }

    val back: (() -> Unit)? = when {
        reviewStep != null -> ({ reviewStep = null })
        stage.hasBack -> store::goBack
        else -> null
    }

    FullScreenDialog(title = "", onDismiss = onDismiss, onBack = back) { padding ->
        var lastOrder by remember { mutableStateOf(stage.order) }
        AnimatedContent(
            targetState = stage,
            contentKey = { it.key },
            transitionSpec = {
                val isBack = targetState.order < lastOrder
                motion.stepTransition(isMovingBack = isBack)
            },
            modifier = Modifier.fillMaxSize().padding(padding),
            label = "addWordStep",
        ) { shown ->
            SideEffectOrder(shown.order) { lastOrder = it }
            when (shown.kind) {
                StageKind.ENTRY -> WordEntry(text, { text = it }, shown.failure)
                StageKind.LOADING -> CenteredStage { LookupProgress() }
                StageKind.FAILED -> FailedStage(shown.failure)
                StageKind.RESULT -> {
                    val state = review
                    when (val lookup = shown.result) {
                        CustomWordLookup.Invalid -> InvalidStage(text.trim())
                        null -> Unit
                        else -> if (state != null) {
                            WordReview(lookup, state, shown.reviewStep, onStep = { reviewStep = it })
                        }
                    }
                }
                StageKind.ADDED -> {
                    val added = store.phase as? CustomWordStore.LookupPhase.Added
                    if (added != null) {
                        val meanings = store.addedMeanings.ifEmpty { listOfNotNull(added.meaning) }
                        AddedConfirmation(added.lemma, meanings, added.isNew, onDone = onDismiss)
                    }
                }
            }
        }
    }
}

/** Remembers the order of the step on screen, so the next swap knows which way to slide. */
@Composable
private fun SideEffectOrder(order: Int, record: (Int) -> Unit) {
    androidx.compose.runtime.SideEffect { record(order) }
}

private enum class ReviewStep { EDIT, OTHER_MEANING }

private enum class StageKind { ENTRY, LOADING, FAILED, RESULT, ADDED }

/**
 * iOS `Stage`: the step on screen, from the store's phase: a key that changes with every new step, and its place in
 * the flow. Higher [order] is further in; a search for another meaning sits deeper than the word it started from.
 */
private data class Stage(
    val key: String,
    val order: Int,
    val kind: StageKind,
    val hasBack: Boolean,
    val result: CustomWordLookup? = null,
    val failure: CustomWordStore.LookupPhase.Failed? = null,
    val reviewStep: ReviewStep? = null,
) {
    val resultKey: String? get() = if (kind == StageKind.RESULT) key.substringBefore('#') else null

    companion object {
        fun of(store: CustomWordStore, reviewStep: ReviewStep?): Stage {
            val nested = if (store.baseWord != null) 2 else 0
            return when (val phase = store.phase) {
                CustomWordStore.LookupPhase.Idle -> Stage("entry", 0, StageKind.ENTRY, hasBack = false)
                CustomWordStore.LookupPhase.Loading -> Stage("loading", 1 + nested, StageKind.LOADING, hasBack = false)
                is CustomWordStore.LookupPhase.Failed ->
                    if (store.isReviewing) {
                        Stage("failed-$nested", 2 + nested, StageKind.FAILED, hasBack = true, failure = phase)
                    } else {
                        Stage("entry", 0, StageKind.ENTRY, hasBack = false, failure = phase)
                    }
                is CustomWordStore.LookupPhase.Result -> {
                    val step = reviewStep?.let { "#$it" }.orEmpty()
                    Stage(
                        "result-" + identity(phase.lookup) + step,
                        2 + nested + (if (reviewStep != null) 1 else 0),
                        StageKind.RESULT,
                        hasBack = true,
                        result = phase.lookup,
                        reviewStep = reviewStep,
                    )
                }
                is CustomWordStore.LookupPhase.Added -> Stage("added", 5, StageKind.ADDED, hasBack = false)
            }
        }

        private fun identity(lookup: CustomWordLookup): String = when (lookup) {
            is CustomWordLookup.Existing -> "existing-" + lookup.word.meanings.joinToString(",") { it.id }
            is CustomWordLookup.Draft -> "draft-" + lookup.draft.draftId
            is CustomWordLookup.NewMeaningDraft -> "meaning-" + lookup.draft.draftId
            CustomWordLookup.Invalid -> "invalid"
        }
    }
}

// region Layout

/** One step: scrolling content, and an action bar pinned to the bottom (above the keyboard). */
@Composable
private fun StepScaffold(bar: (@Composable ColumnScope.() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().imePadding()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenGutter)
                .padding(top = Spacing.xxs, bottom = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
            content = content,
        )
        if (bar != null) PinnedActionBar(content = bar)
    }
}

/** A step whose content sits in the middle: the lookup, the confirmation, "not a word". */
@Composable
private fun CenteredStage(bar: (@Composable ColumnScope.() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = content,
        )
        // Sits a little above the middle, as in the design.
        Spacer(Modifier.height(Spacing.xl + Spacing.xs))
        if (bar != null) PinnedActionBar(content = bar)
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(message, style = Typography.secondary, color = SyllColors.errorInk)
}

@Composable
private fun RetryButton() {
    val store = LocalCustomWordStore.current
    val appScope = LocalAppScope.current
    SecondaryButton(stringResource(R.string.try_again_2), onClick = { appScope.launch { store.retryLookup() } }, icon = Icons.Outlined.Refresh)
}

@Composable
private fun SheetTitle(text: String) {
    Text(
        text,
        style = Typography.sheetTitle.copy(letterSpacing = Typography.sheetTracking.sp),
        color = SyllColors.ink,
        modifier = Modifier.semantics { heading() },
    )
}

// endregion

// region Entry, lookup, not a word

@Composable
private fun WordEntry(text: String, onTextChange: (String) -> Unit, failure: CustomWordStore.LookupPhase.Failed?) {
    val store = LocalCustomWordStore.current
    val appScope = LocalAppScope.current
    val issue = CustomWordInput.issue(text)
    val hint = when (issue) {
        CustomWordInput.Issue.TOO_LONG -> stringResource(R.string.keep_it_under_x_characters, CustomWordInput.MAX_TEXT_LENGTH)
        CustomWordInput.Issue.INVALID_CHARACTERS -> stringResource(R.string.use_letters_spaces_hyphens_and_apostrophes_start)
        else -> null
    }
    val focus = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    fun search() {
        if (CustomWordInput.issue(text) == null) appScope.launch { store.lookup(text) }
    }
    StepScaffold(bar = { PrimaryButton(stringResource(R.string.look_up), onClick = ::search, enabled = issue == null) }) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SheetTitle(stringResource(R.string.which_word_do_you_want_to_learn))
            Text(stringResource(R.string.we_ll_find_it_or_prepare_it_for_you), style = Typography.secondary, color = SyllColors.bodyInk)
        }
        Box(Modifier.padding(top = Spacing.xs).fieldSurface(isFocused, isError = hint != null, minHeight = Spacing.rowMinHeight), contentAlignment = Alignment.CenterStart) {
            FieldInput(
                value = text,
                onValueChange = onTextChange,
                placeholder = stringResource(R.string.type_an_english_word),
                focusRequester = focus,
                onFocusChange = { isFocused = it },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Search),
                onSubmit = ::search,
                textStyle = Typography.tileLarge,
            )
        }
        // Only once the text can't be looked up.
        hint?.let { Text(it, style = Typography.micro, color = SyllColors.errorInk) }
        failure?.let { ErrorText(it.message.text()) }
        if (failure?.canRetry == true) RetryButton()
    }
}

/** Syllie flying while the backend looks the word up. */
@Composable
private fun LookupProgress() {
    Column(Modifier.semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
        SyllieLoadingView(Modifier.size(112.dp).clearAndSetSemantics { })
        Text(
            stringResource(R.string.looking_up_your_word),
            style = Typography.tileLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = Typography.tightTracking.sp),
            color = SyllColors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.screenGutter),
        )
        Text(
            stringResource(R.string.new_words_can_take_up_to_a_minute),
            style = Typography.secondary,
            color = SyllColors.bodyInk,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

@Composable
private fun FailedStage(failure: CustomWordStore.LookupPhase.Failed?) {
    val store = LocalCustomWordStore.current
    val word = store.baseWord
    if (word != null) {
        OtherMeaningStage(word.lemma, store.lastContext, failure)
    } else {
        StepScaffold {
            failure?.let { ErrorText(it.message.text()) }
            if (failure?.canRetry == true) RetryButton()
        }
    }
}

/** Syllie tilts his head over what was typed, so the spelling mistake is easy to see. */
@Composable
private fun InvalidStage(text: String) {
    val store = LocalCustomWordStore.current
    CenteredStage(bar = { PrimaryButton(stringResource(R.string.try_another_word), onClick = store::resetLookup) }) {
        SyllieReactionView(SyllieMood.OOPS, Modifier.size(96.dp, 80.dp).rotate(-8f).clearAndSetSemantics { })
        if (text.isNotEmpty()) {
            Text(
                text,
                style = Typography.body.copy(fontWeight = FontWeight.SemiBold),
                color = SyllColors.bodyInk,
                maxLines = 1,
                modifier = Modifier
                    .padding(top = Spacing.screenGutter)
                    .background(SyllColors.neutralSurface, CircleShape)
                    .padding(horizontal = Spacing.m, vertical = Spacing.xs),
            )
        }
        Text(
            stringResource(R.string.that_doesn_t_look_like_a_word),
            style = Typography.screenTitle.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = Typography.tightTracking.sp),
            color = SyllColors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.m).semantics { heading() },
        )
        Text(
            stringResource(R.string.check_the_spelling_or_try_another_word),
            style = Typography.secondary,
            color = SyllColors.bodyInk,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

// endregion

// region Review

/** iOS `ReviewSense`: a meaning shown in full, whichever lookup result it came from. */
private data class ReviewSense(
    val id: String,
    val partOfSpeech: String,
    val ipa: String?,
    val tag: Int?,
    /** Null when the meaning is already learned or on the path. */
    val addition: CustomWordAddition?,
    val standing: ExistingMeaning.Standing?,
    val draft: CustomWordEditDraft,
) {
    companion object {
        fun of(word: ExistingWord): List<ReviewSense> = word.meanings.mapNotNull { meaning ->
            if (meaning.status == ExistingMeaning.Status.UNAVAILABLE || !(meaning.canBeAdded || meaning.standing != null)) return@mapNotNull null
            ReviewSense(
                id = meaning.id,
                partOfSpeech = meaning.partOfSpeech,
                ipa = meaning.ipa,
                tag = if (meaning.status == ExistingMeaning.Status.TRANSLATION_DRAFT) R.string.ai_translated else null,
                addition = if (meaning.canBeAdded) meaning.addition else null,
                standing = meaning.standing,
                draft = CustomWordEditDraft.of(word.lemma, meaning),
            )
        }

        fun of(draft: WordDraft, tag: Int): List<ReviewSense> = draft.senses.mapIndexed { index, sense ->
            ReviewSense(
                id = index.toString(),
                partOfSpeech = sense.partOfSpeech,
                ipa = sense.ipa,
                tag = tag,
                addition = CustomWordAddition.draft(draft.draftId, index, null),
                standing = null,
                draft = CustomWordEditDraft.of(draft.lemma, sense),
            )
        }
    }
}

/** Selection, expansion and edits of one lookup result; kept while its steps come and go. */
private class ReviewState(lookup: CustomWordLookup) {
    val lemma: String
    val senses: List<ReviewSense>
    val closed: List<ExistingMeaning>
    /** The way to another meaning: context only works on a word the backend already has. */
    val searchesAgain: Boolean

    init {
        when (lookup) {
            is CustomWordLookup.Existing -> {
                lemma = lookup.word.lemma
                senses = ReviewSense.of(lookup.word)
                closed = lookup.word.meanings.filter { meaning -> senses.none { it.id == meaning.id } }
                searchesAgain = false
            }
            is CustomWordLookup.Draft -> {
                lemma = lookup.draft.lemma
                senses = ReviewSense.of(lookup.draft, R.string.ai_generated)
                closed = emptyList()
                searchesAgain = false
            }
            is CustomWordLookup.NewMeaningDraft -> {
                lemma = lookup.draft.lemma
                senses = ReviewSense.of(lookup.draft, R.string.private_to_you)
                closed = emptyList()
                searchesAgain = true
            }
            CustomWordLookup.Invalid -> {
                lemma = ""
                senses = emptyList()
                closed = emptyList()
                searchesAgain = false
            }
        }
    }

    // Opens on a meaning that can still be added, when there is one.
    private val first = (senses.firstOrNull { it.addition != null } ?: senses.firstOrNull())?.id
    var selectedId by mutableStateOf(first)
    var openIds by mutableStateOf(setOfNotNull(first))
    var selectedIds by mutableStateOf(setOfNotNull(senses.firstOrNull { it.addition != null }?.id))
    var editedIds by mutableStateOf(emptySet<String>())
    /** One editable copy per meaning, so switching between them keeps the changes. */
    var drafts by mutableStateOf(senses.associate { it.id to it.draft })

}

/** iOS `WordReview`: grouped meanings with independent selection, expansion and editing. */
@Composable
private fun WordReview(lookup: CustomWordLookup, state: ReviewState, step: ReviewStep?, onStep: (ReviewStep?) -> Unit) {
    val store = LocalCustomWordStore.current
    when (step) {
        ReviewStep.EDIT -> {
            val selected = state.senses.firstOrNull { it.id == state.selectedId } ?: return
            val draft = state.drafts[selected.id] ?: selected.draft
            EditStage(draft) { edited ->
                state.drafts = state.drafts + (selected.id to edited)
                state.selectedIds = state.selectedIds + selected.id
                state.editedIds = state.editedIds + selected.id
                onStep(null)
            }
        }
        ReviewStep.OTHER_MEANING -> OtherMeaningStage(state.lemma, if (state.searchesAgain) store.lastContext else "", null)
        null -> ReviewDetails(lookup, state, onStep)
    }
}

@Composable
private fun ReviewDetails(lookup: CustomWordLookup, state: ReviewState, onStep: (ReviewStep?) -> Unit) {
    val store = LocalCustomWordStore.current
    val appScope = LocalAppScope.current
    val pronunciationsDiffer = state.senses.map { it.ipa.orEmpty() }.toSet().size > 1
    val pending = state.senses.filter { it.id in state.selectedIds && it.id !in store.addedSenseIds && it.addition != null }

    StepScaffold(bar = {
        store.notice?.let { ErrorText(it.text()) }
        PrimaryButton(
            if (pending.size > 1) stringResource(R.string.add_x_meanings, pending.size) else stringResource(R.string.add_word),
            enabled = pending.isNotEmpty() && !store.isAdding,
            onClick = {
                val selections = pending.mapNotNull { sense ->
                    val addition = sense.addition ?: return@mapNotNull null
                    val draft = state.drafts[sense.id] ?: sense.draft
                    CustomWordStore.Selection(sense.id, addition.withEdits(draft.edits), draft.meaningText)
                }
                appScope.launch { store.addSelected(selections, state.lemma) }
            },
        )
    }) {
        WordHeroHeader(state.lemma, state.senses.firstOrNull()?.ipa, playAudio = null, isPlayingAudio = false, showsPronunciation = !pronunciationsDiffer)
        Row(Modifier.padding(top = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.choose_meanings_to_add), style = Typography.emphasis, color = SyllColors.ink, modifier = Modifier.weight(1f))
            state.senses.firstNotNullOfOrNull { it.tag }?.let { StatusPill(stringResource(it)) }
        }
        groupByPartOfSpeech(state.senses) { it.partOfSpeech }.forEach { (partOfSpeech, senses) ->
            Eyebrow(localizedPartOfSpeech(partOfSpeech).uppercase())
            senses.forEach { sense -> SenseCard(sense, state, pronunciationsDiffer, onEdit = {
                state.selectedId = sense.id
                onStep(ReviewStep.EDIT)
            }) }
        }
        TextLink(stringResource(R.string.find_another_meaning), onClick = { onStep(ReviewStep.OTHER_MEANING) })
        state.closed.forEach { ClosedMeaningRow(it) }
    }
}

@Composable
private fun SenseCard(sense: ReviewSense, state: ReviewState, pronunciationsDiffer: Boolean, onEdit: () -> Unit) {
    val store = LocalCustomWordStore.current
    val draft = state.drafts[sense.id] ?: sense.draft
    val added = sense.id in store.addedSenseIds
    val canSelect = sense.addition != null && !added
    val chosen = canSelect && sense.id in state.selectedIds
    val open = sense.id in state.openIds
    val shape = RoundedCornerShape(Radii.card)
    val indent = if (canSelect) 56.dp else 0.dp
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (chosen) SyllColors.selectedSurface else SyllColors.surface, shape)
            .border(Borders.card, if (chosen) SyllColors.interactive else SyllColors.stroke, shape)
            .padding(Spacing.s)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            if (canSelect) {
                val label = stringResource(R.string.select_meaning)
                Box(
                    Modifier
                        .size(Spacing.minTouchTarget)
                        .clickable(role = Role.Checkbox) {
                            state.selectedIds = if (chosen) state.selectedIds - sense.id else state.selectedIds + sense.id
                        }
                        .semantics {
                            contentDescription = label
                            selected = chosen
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (chosen) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                        null,
                        tint = if (chosen) SyllColors.interactive else SyllColors.ink300,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            val expanded = stringResource(if (open) R.string.expanded else R.string.collapsed)
            Row(
                Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = Spacing.minTouchTarget)
                    .clickable { state.openIds = if (open) state.openIds - sense.id else state.openIds + sense.id }
                    .semantics(mergeDescendants = true) { stateDescription = expanded },
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(draft.meaningText, style = Typography.emphasis, color = if (chosen) SyllColors.selectedInk else SyllColors.ink, modifier = Modifier.weight(1f))
                when {
                    added || sense.standing != null ->
                        StatusPill(stringResource(if (sense.standing == ExistingMeaning.Standing.LEARNED) R.string.already_learned else R.string.on_your_path))
                    sense.id in state.editedIds -> StatusPill(stringResource(R.string.edited))
                }
                Icon(
                    Icons.Outlined.KeyboardArrowDown, null, tint = SyllColors.mutedInk,
                    modifier = Modifier.size(22.dp).rotate(if (open) 180f else 0f),
                )
            }
        }
        if (pronunciationsDiffer && sense.ipa != null) {
            Text(sense.ipa, style = Typography.secondary, color = SyllColors.bodyInk, modifier = Modifier.padding(start = indent))
        }
        if (open) {
            Column(Modifier.padding(start = indent, bottom = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                if (draft.definitionText.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Eyebrow(stringResource(R.string.definition).uppercase())
                        Text(draft.definitionText, style = Typography.secondary, color = SyllColors.ink)
                    }
                }
                draft.examples.forEach { example ->
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Eyebrow(stringResource(R.string.example).uppercase())
                        Text(highlightedSentence(example.text, draft.locator(example)), style = Typography.secondary, color = SyllColors.ink)
                        if (example.translation.isNotEmpty()) ExampleTranslation(example.translation)
                    }
                }
                if (canSelect) TextLink(stringResource(R.string.edit_this_meaning), onClick = onEdit)
            }
        }
    }
}

/** iOS `ClosedMeaningRow`: a meaning that can't be picked: already on the path, or not available. */
@Composable
private fun ClosedMeaningRow(meaning: ExistingMeaning) {
    val unavailable = meaning.status == ExistingMeaning.Status.UNAVAILABLE
    Row(
        Modifier.cardSurface(fill = if (unavailable) SyllColors.disabledSurface else SyllColors.surface).padding(Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Eyebrow(localizedPartOfSpeech(meaning.partOfSpeech).uppercase())
            if (unavailable) Text(stringResource(R.string.not_available_right_now), style = Typography.emphasis, color = SyllColors.disabledInk)
            meaning.meaningText?.let { Text(it, style = Typography.emphasis, color = SyllColors.ink) }
            meaning.definitionText?.let { Text(it, style = Typography.secondary, color = SyllColors.mutedInk) }
        }
        if (meaning.inRoadmap) StatusPill(stringResource(R.string.on_your_path), StatusPillTone.SUCCESS)
    }
}

// endregion

// region Other steps

/** iOS `EditStage`: saves the changes and returns to the meanings, with this one selected. */
@Composable
private fun EditStage(initial: CustomWordEditDraft, onSave: (CustomWordEditDraft) -> Unit) {
    var draft by remember { mutableStateOf(initial) }
    StepScaffold(bar = {
        PrimaryButton(
            stringResource(R.string.save),
            onClick = { onSave(draft) },
            requirement = stringResource(R.string.fill_in_every_field),
            enabled = draft.isValid,
        )
    }) {
        EditWordTitle(draft.lemma)
        CustomWordEditFields(draft, { draft = it }, Modifier.padding(top = Spacing.xs))
    }
}

/** iOS `OtherMeaningStage`: a field describing the meaning to look for. Shown after a failed search too. */
@Composable
private fun OtherMeaningStage(lemma: String, initialContext: String, failure: CustomWordStore.LookupPhase.Failed?) {
    val store = LocalCustomWordStore.current
    val appScope = LocalAppScope.current
    var context by rememberSaveable { mutableStateOf(initialContext) }
    val isValid = context.trim().length in 1..CustomWordInput.MAX_CONTEXT_LENGTH
    StepScaffold(bar = {
        // The lemma, not the typed form: context only applies to a word the backend already has.
        PrimaryButton(stringResource(R.string.find_meaning), enabled = isValid, onClick = { appScope.launch { store.lookup(lemma, context) } })
    }) {
        WordHeroHeader(lemma, null, playAudio = null, isPlayingAudio = false, showsPronunciation = false)
        EditorField(
            stringResource(R.string.meaning_to_find), context, { context = it },
            modifier = Modifier.padding(top = Spacing.m),
            maxLength = CustomWordInput.MAX_CONTEXT_LENGTH, minLines = 2, maxLines = 6, focusesOnAppear = true,
        )
        failure?.let { ErrorText(it.message.text()) }
        if (failure?.canRetry == true) RetryButton()
    }
}

/** iOS `AddedConfirmation`: Syllie with a green check badge, then the word and the meanings just added. */
@Composable
private fun AddedConfirmation(lemma: String, meanings: List<String>, isNew: Boolean, onDone: () -> Unit) {
    CenteredStage(bar = { PrimaryButton(stringResource(R.string.done), onClick = onDone) }) {
        Box(Modifier.size(112.dp, 96.dp).clearAndSetSemantics { }) {
            SyllieReactionView(SyllieMood.HAPPY, Modifier.fillMaxSize())
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = Spacing.xs, y = Spacing.xxs)
                    .background(SyllColors.surface, CircleShape)
                    .padding(Borders.focusHalo)
                    .size(Spacing.iconBadge)
                    .background(SyllColors.success, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        Text(
            stringResource(if (isNew) R.string.word_added else R.string.already_on_your_path),
            style = Typography.screenTitle.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = Typography.tightTracking.sp),
            color = SyllColors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.l).semantics { heading() },
        )
        Text(lemma, style = Typography.wordTitle, color = SyllColors.ink, modifier = Modifier.padding(top = Spacing.xs))
        if (meanings.isNotEmpty()) {
            Column(Modifier.padding(top = Spacing.m).cardSurface()) {
                meanings.forEachIndexed { index, meaning ->
                    if (index > 0) CardDivider()
                    Row(Modifier.fillMaxWidth().padding(Spacing.m), horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, null, tint = SyllColors.success, modifier = Modifier.size(20.dp))
                        Text(meaning, style = Typography.secondary.copy(fontWeight = FontWeight.SemiBold), color = SyllColors.ink)
                    }
                }
            }
        }
    }
}

// endregion
