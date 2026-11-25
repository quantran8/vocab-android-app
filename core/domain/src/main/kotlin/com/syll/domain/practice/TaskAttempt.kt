package com.syll.domain.practice

import java.text.Normalizer

/** iOS `PracticeAnswer`. */
object PracticeAnswer {
    /**
     * Must stay identical to the backend's `normalizeAnswer`, or instant feedback disagrees with the commit: NFC,
     * trim, collapse whitespace runs, lowercase. Punctuation and diacritics are kept.
     */
    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFC)
            .split { it.isAnswerWhitespace() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .lowercase()

    fun matches(text: String, accepted: List<String>): Boolean {
        val input = normalize(text)
        return accepted.any { normalize(it) == input }
    }

    /** Grapheme clusters of the NFC string; `graphemeIndex` counts in these. */
    fun graphemes(text: String): List<String> = clusters(Normalizer.normalize(text, Normalizer.Form.NFC))

    /** Extended grapheme clusters (`\X`), like Swift `Character` and the backend's `Intl.Segmenter`. */
    internal fun clusters(text: String): List<String> = GRAPHEME.findAll(text).map { it.value }.toList()

    private val GRAPHEME = Regex("\\X")

    /** Swift `whitespacesAndNewlines` plus the byte order mark (JavaScript's `\s` matches it too). */
    private fun Char.isAnswerWhitespace(): Boolean =
        this in "\t\n\u000B\u000C\r\u0085﻿" || Character.getType(this).toByte().let {
            it == Character.SPACE_SEPARATOR || it == Character.LINE_SEPARATOR || it == Character.PARAGRAPH_SEPARATOR
        }

    private fun String.split(isSeparator: (Char) -> Boolean): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        for (char in this) {
            if (isSeparator(char)) {
                parts += current.toString()
                current.clear()
            } else {
                current.append(char)
            }
        }
        parts += current.toString()
        return parts
    }
}

/** iOS `HintSequence`. */
object HintSequence {
    const val MAX_HINTS = 2

    fun hint(term: String, level: Int): String? {
        if (level <= 0) return null
        val letters = PracticeAnswer.clusters(term)
        val revealed = if (level == 1) 1 else maxOf(1, (letters.size + 1) / 2)
        return letters.mapIndexed { index, letter -> if (index < revealed) letter else "_" }.joinToString(" ")
    }
}

/**
 * iOS `TaskAttempt`: the learner's work on one task: replayable events, plus the state the screen renders. Pure and
 * client-side only; the outcome that counts is the backend's, computed at commit.
 *
 * Mutable (iOS mutates a struct in place); [copy] gives an independent snapshot for observable state.
 */
class TaskAttempt private constructor(
    val task: PracticeTask,
    /** The first recall of a new word needs a real attempt: a reveal alone rejects the entire commit. */
    val requiresSubmission: Boolean,
) {
    constructor(task: PracticeTask, phase: TaskPhase) : this(
        task,
        // A known check must be tried before the answer can be shown.
        (phase == TaskPhase.NEW_PRACTICE || phase == TaskPhase.KNOWN_CHECK) &&
            (task.template == TaskTemplate.TYPING || task.template == TaskTemplate.FLY_REORDER),
    )

    enum class Status {
        ANSWERING,
        /** A wrong answer is on screen, waiting for "Try again" or "Show answer". */
        WRONG,
        SOLVED,
        REVEALED,
        /** Recording only: assessed, or reported as unavailable. */
        RECORDED,
        /** Ran out of tries or events on a wrong answer; ends here so commit stays valid. Adds no event. */
        EXHAUSTED,
    }

    private val _events = mutableListOf<TaskEvent>()
    val events: List<TaskEvent> get() = _events

    /** Ms since the task was shown, set by the session before each change; null leaves events untimed. */
    var elapsedMs: Int? = null
        set(value) {
            field = value?.let { minOf(maxOf(it, _events.lastOrNull()?.elapsedMs ?: 0), MAX_ELAPSED_MS) }
        }

    var status: Status = Status.ANSWERING
        private set
    var submitCount = 0
        private set
    var hintsUsed = 0
        private set
    /** Tile ids currently in the answer row, in order. */
    var placedTileIds: List<Int> = emptyList()
        private set
    var matchedLeftIds: Set<Int> = emptySet()
        private set
    /** Left items out of tries. They score incorrect and no longer hold up the board. */
    var exhaustedLeftIds: Set<Int> = emptySet()
        private set
    var lastWrongOptionId: Int? = null
        private set
    var lastWrongPair: PairKey? = null
        private set
    private var missesByLeftId = mutableMapOf<Int, Int>()
    private var assessmentCount = 0
    private var passedFirstTake = false
    private var audioDelivery = DeliveryMode.NONE

    fun copy(): TaskAttempt {
        val source = this
        return TaskAttempt(task, requiresSubmission).also { copy ->
            copy._events += source._events
            copy.elapsedMs = source.elapsedMs
            copy.status = source.status
            copy.submitCount = source.submitCount
            copy.hintsUsed = source.hintsUsed
            copy.placedTileIds = source.placedTileIds
            copy.matchedLeftIds = source.matchedLeftIds
            copy.exhaustedLeftIds = source.exhaustedLeftIds
            copy.lastWrongOptionId = source.lastWrongOptionId
            copy.lastWrongPair = source.lastWrongPair
            copy.missesByLeftId = source.missesByLeftId.toMutableMap()
            copy.assessmentCount = source.assessmentCount
            copy.passedFirstTake = source.passedFirstTake
            copy.audioDelivery = source.audioDelivery
        }
    }

    /** A local display figure, not a score. */
    val isRightFirstTime: Boolean
        get() = when (status) {
            Status.SOLVED -> submitCount <= 1 && hintsUsed == 0
            Status.RECORDED -> passedFirstTake
            else -> false
        }

    val isFinished: Boolean
        get() = status in setOf(Status.SOLVED, Status.REVEALED, Status.RECORDED, Status.EXHAUSTED)

    val canReveal: Boolean
        get() = !isFinished && task.template != TaskTemplate.RECORDING && (!requiresSubmission || submitCount > 0)

    val canUseHint: Boolean
        get() = !isFinished && status != Status.WRONG && hintsUsed < MAX_HINTS && acceptedAnswer != null && hasRoomForEvent

    /** The answer shown once the task is finished. Never display it before that. */
    val acceptedAnswer: String?
        get() = when (val body = task.body) {
            is TaskBody.Typing -> body.accepted.firstOrNull()
            is TaskBody.Reorder -> body.accepted.firstOrNull()
            else -> null
        }

    val hints: List<String>
        get() {
            val answer = acceptedAnswer ?: return emptyList()
            return (0 until hintsUsed).mapNotNull { HintSequence.hint(answer, it + 1) }
        }

    val bankTiles: List<TaskOption>
        get() = (task.body as? TaskBody.Reorder)?.tiles?.filter { it.id !in placedTileIds } ?: emptyList()

    val placedTiles: List<TaskOption>
        get() {
            val tiles = (task.body as? TaskBody.Reorder)?.tiles ?: return emptyList()
            return placedTileIds.mapNotNull { id -> tiles.firstOrNull { it.id == id } }
        }

    val result: TaskResult get() = TaskResult(task.id, deliveryMode, events.toList())

    /** `audio` only when sound really played. Text prompts send nothing. */
    val deliveryMode: DeliveryMode? get() = if (task.prompt?.isAudio == true) audioDelivery else null

    // Answers. Each returns whether the answer was right; null means it was ignored.

    fun submitOption(id: Int): Boolean? {
        val body = task.body as? TaskBody.Choice ?: return null
        if (!canAnswer || body.options.none { it.id == id }) return null
        lastWrongOptionId = if (id == body.correctOptionId) null else id
        return finishSubmit(TaskEvent.Action.SubmitOption(id), id == body.correctOptionId)
    }

    fun submitText(text: String): Boolean? {
        val body = task.body as? TaskBody.Typing ?: return null
        if (!canAnswer || PracticeAnswer.normalize(text).isEmpty()) return null
        val value = truncated(text, MAX_VALUE_LENGTH)
        return finishSubmit(TaskEvent.Action.SubmitValue(value), PracticeAnswer.matches(value, body.accepted))
    }

    fun submitTiles(): Boolean? {
        val body = task.body as? TaskBody.Reorder ?: return null
        if (!canAnswer || placedTileIds.isEmpty()) return null
        val text = placedTiles.joinToString("") { it.text }
        return finishSubmit(TaskEvent.Action.SubmitTiles(placedTileIds), PracticeAnswer.matches(text, body.accepted))
    }

    /**
     * [edits] maps grapheme index to its replacement; an empty one deletes the grapheme. Like the backend, scores the
     * repaired word, so any set of fixes that spells it right counts.
     */
    fun submitRepair(edits: Map<Int, String>): Boolean? {
        val body = task.body as? TaskBody.Surgery ?: return null
        if (!canAnswer) return null
        val letters = PracticeAnswer.graphemes(body.word)
        if (edits.isEmpty() || edits.size > MAX_REPAIR_EDITS || edits.keys.any { it !in letters.indices }) return null
        // A blank replacement deletes the grapheme. Sent as "", because the backend scores the repaired word after
        // normalizing it, and spaces left inside it would split the word.
        val fixes = edits.keys.sorted().map { index ->
            val replacement = edits[index].orEmpty().let { if (it.isBlank()) "" else it }
            RepairEdit(index, truncated(replacement, MAX_REPLACEMENT_LENGTH))
        }
        val repaired = applying(fixes, letters)
        val correct = applying(listOf(RepairEdit(body.wrongGraphemeIndex, body.replacement)), letters)
        return finishSubmit(TaskEvent.Action.SubmitRepair(fixes), PracticeAnswer.normalize(repaired) == PracticeAnswer.normalize(correct))
    }

    /** Correct pairs lock. Tries count per left item. The task ends by itself once no left item is open. */
    fun submitPair(leftId: Int, rightId: Int): Boolean? {
        val body = task.body as? TaskBody.Match ?: return null
        if (isFinished || !hasRoomForEvent || body.left.none { it.id == leftId } || body.right.none { it.id == rightId } ||
            leftId in matchedLeftIds || leftId in exhaustedLeftIds
        ) {
            return null
        }
        append(TaskEvent.Action.Pair(leftId, rightId))
        val isCorrect = body.correct.any { it.leftId == leftId && it.rightId == rightId }
        if (isCorrect) {
            matchedLeftIds = matchedLeftIds + leftId
            lastWrongPair = null
        } else {
            lastWrongPair = PairKey(leftId, rightId)
            val misses = (missesByLeftId[leftId] ?: 0) + 1
            missesByLeftId[leftId] = misses
            if (isOutOfTries(misses)) exhaustedLeftIds = exhaustedLeftIds + leftId
        }
        if (matchedLeftIds.size == body.left.size) {
            status = Status.SOLVED
        } else if (matchedLeftIds.size + exhaustedLeftIds.size == body.left.size) {
            status = Status.EXHAUSTED
        }
        endIfOutOfEvents()
        return isCorrect
    }

    // Tiles: every move is recorded because the backend replays them to rebuild the answer row.

    fun tapTile(id: Int) {
        if (!canAnswer || bankTiles.none { it.id == id }) return
        placedTileIds = placedTileIds + id
        append(TaskEvent.Action.TapTile(id))
        endIfOutOfEvents()
    }

    fun removeTile(id: Int) {
        if (!canAnswer || id !in placedTileIds) return
        placedTileIds = placedTileIds - id
        append(TaskEvent.Action.RemoveTile(id))
        endIfOutOfEvents()
    }

    fun moveTile(id: Int, toIndex: Int) {
        if (!canAnswer || id !in placedTileIds || toIndex !in placedTileIds.indices || placedTileIds[toIndex] == id) return
        placedTileIds = placedTileIds.toMutableList().apply {
            remove(id)
            add(toIndex, id)
        }
        append(TaskEvent.Action.MoveTile(id, toIndex))
        endIfOutOfEvents()
    }

    // Help

    fun useHint() {
        if (!canUseHint) return
        hintsUsed += 1
        append(TaskEvent.Action.Hint)
        endIfOutOfEvents()
    }

    fun reveal() {
        if (!canReveal) return
        append(TaskEvent.Action.Reveal)
        status = Status.REVEALED
    }

    fun tryAgain() {
        if (status != Status.WRONG) return
        status = Status.ANSWERING
        lastWrongOptionId = null
    }

    // Recording. Exactly one terminal event, whatever happened before it.

    val canRecordAgain: Boolean get() = !isOutOfTries(assessmentCount)

    /** Every receipt counts, invalid audio included. An upload that got none does not. */
    fun countAssessment(passed: Boolean = false) {
        assessmentCount += 1
        if (assessmentCount == 1) passedFirstTake = passed
    }

    fun finishRecording(receiptId: String) {
        if (isFinished || task.template != TaskTemplate.RECORDING) return
        append(TaskEvent.Action.Assessed(receiptId))
        status = Status.RECORDED
    }

    /** On-device takes are one `transcribed` event each and can't mix with the other two. */
    fun recordTranscript(transcript: String, passed: Boolean = false) {
        if (isFinished || task.template != TaskTemplate.RECORDING || !canRecordAgain) return
        append(TaskEvent.Action.Transcribed(truncated(transcript, 200)))
        countAssessment(passed)
    }

    val hasTranscripts: Boolean get() = _events.any { it.action is TaskEvent.Action.Transcribed }

    fun finishTranscribed() {
        if (isFinished || !hasTranscripts) return
        status = Status.RECORDED
    }

    fun markRecordingUnavailable(reason: String) {
        if (isFinished || task.template != TaskTemplate.RECORDING) return
        append(TaskEvent.Action.Unavailable(reason))
        status = Status.RECORDED
    }

    // Audio prompt delivery

    fun recordAudioPlayback(succeeded: Boolean) {
        if (succeeded) {
            audioDelivery = DeliveryMode.AUDIO
        } else if (audioDelivery == DeliveryMode.NONE) {
            audioDelivery = DeliveryMode.FALLBACK
        }
    }

    // After the task: the answer in words

    /** The answer in words, for after it is solved or revealed. */
    val solutionText: String?
        get() = when (val body = task.body) {
            is TaskBody.Choice -> body.options.firstOrNull { it.id == body.correctOptionId }?.text
            is TaskBody.Typing -> body.accepted.firstOrNull()
            is TaskBody.Reorder -> body.accepted.firstOrNull()
            is TaskBody.Surgery -> PracticeAnswer.graphemes(body.word)
                .mapIndexed { index, letter -> if (index == body.wrongGraphemeIndex) body.replacement else letter }
                .joinToString("")
            is TaskBody.Match, is TaskBody.Recording -> null
        }

    /** Match only: the pairs that ran out of tries, one per line. */
    val missedPairsText: String? get() = pairsText { it.id in exhaustedLeftIds }

    /** Every pair of a match, for when the learner asks to see the answer. */
    val allPairsText: String? get() = pairsText { true }

    private fun pairsText(isIncluded: (TaskOption) -> Boolean): String? {
        val body = task.body as? TaskBody.Match ?: return null
        return body.left.filter(isIncluded).mapNotNull { term ->
            val rightId = body.correct.firstOrNull { it.leftId == term.id }?.rightId ?: return@mapNotNull null
            val gloss = body.right.firstOrNull { it.id == rightId } ?: return@mapNotNull null
            "${term.text} — ${gloss.text}"
        }.joinToString("\n")
    }

    // Private

    private val canAnswer: Boolean get() = status == Status.ANSWERING && hasRoomForEvent

    /** One slot stays free for the event that ends the task. */
    private val hasRoomForEvent: Boolean get() = _events.size < MAX_EVENTS - 1

    /** One more submit past this point rejects the entire commit. */
    private fun isOutOfTries(tries: Int): Boolean = task.maxRetries?.let { tries > it } ?: false

    private fun append(action: TaskEvent.Action) {
        _events += TaskEvent(_events.size + 1, action, elapsedMs)
    }

    private fun finishSubmit(action: TaskEvent.Action, isCorrect: Boolean): Boolean {
        append(action)
        submitCount += 1
        status = when {
            isCorrect -> Status.SOLVED
            isOutOfTries(submitCount) -> Status.EXHAUSTED
            else -> Status.WRONG
        }
        endIfOutOfEvents()
        return isCorrect
    }

    /** Past the limit the commit would be rejected, so the task ends in whichever way is still valid. */
    private fun endIfOutOfEvents() {
        if (isFinished || hasRoomForEvent) return
        if (submitCount > 0 && task.template != TaskTemplate.MATCH) {
            status = Status.EXHAUSTED
        } else if (requiresSubmission) {
            append(TaskEvent.Action.SubmitTiles(placedTileIds))
            submitCount += 1
            status = Status.EXHAUSTED
        } else {
            append(TaskEvent.Action.Reveal)
            status = Status.REVEALED
        }
    }

    companion object {
        /** Mirrors the backend's per-task limit; going over rejects the entire commit. */
        const val MAX_EVENTS = 200
        /** The backend rejects a larger `elapsedMs`. */
        const val MAX_ELAPSED_MS = 30 * 60 * 1000
        const val MAX_VALUE_LENGTH = 200
        const val MAX_REPLACEMENT_LENGTH = 32
        const val MAX_REPAIR_EDITS = 64
        const val MAX_HINTS = HintSequence.MAX_HINTS

        private fun applying(fixes: List<RepairEdit>, letters: List<String>): String {
            val byIndex = fixes.associate { it.graphemeIndex to it.replacement }
            return letters.mapIndexed { index, letter -> byIndex[index] ?: letter }.joinToString("")
        }

        /** The backend's limits count UTF-16 units; whole characters only. */
        private fun truncated(text: String, limit: Int): String {
            if (text.length <= limit) return text
            val result = StringBuilder()
            for (cluster in PracticeAnswer.clusters(text)) {
                if (result.length + cluster.length > limit) break
                result.append(cluster)
            }
            return result.toString()
        }
    }
}
