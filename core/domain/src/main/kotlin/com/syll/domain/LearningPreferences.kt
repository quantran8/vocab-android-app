package com.syll.domain

import kotlinx.serialization.Serializable

/**
 * iOS `LearningPreferences`: what the learner chose during onboarding. Weekdays use `Calendar` numbering
 * (1 = Sunday … 7 = Saturday), like iOS. Immutable: every change returns a copy.
 */
@Serializable
data class LearningPreferences(
    /** One collection at a time; [topicIds] always belong to it. */
    val collectionId: String,
    val topicIds: Set<String>,
    val wordsPerDay: Int,
    /** Null follows [wordsPerDay] (see [effectiveReviewsPerSession]). */
    val reviewsPerSession: Int? = null,
    /** Read-aloud tasks in Daily (backend `dailySpeaking`, off by default). Never moves progress. */
    val dailySpeaking: Boolean = false,
    val learningDays: Set<Int>,
    val reminderEnabled: Boolean,
    val reminderHour: Int,
    val reminderMinute: Int,
) {
    val collection: VocabCollection? get() = TopicCatalog.collection(collectionId)

    val isComplete: Boolean
        get() = collection != null && topicIds.isNotEmpty() && learningDays.isNotEmpty() && wordsPerDay in WORDS_PER_DAY_RANGE

    val isEveryDay: Boolean get() = learningDays == ALL_WEEKDAYS
    /** Sessions per Journey segment: one per learning day. */
    val weeklySessionGoal: Int get() = learningDays.size

    /** The backend's rule: the chosen number, else the new-word goal but never below the minimum. */
    val effectiveReviewsPerSession: Int get() = reviewsPerSession ?: maxOf(wordsPerDay, REVIEWS_PER_SESSION_FOLLOW_MIN)

    fun adjustReviewsPerSession(amount: Int): LearningPreferences =
        copy(reviewsPerSession = (effectiveReviewsPerSession + amount).coerceIn(REVIEWS_PER_SESSION_RANGE))

    fun adjustWordsPerDay(amount: Int): LearningPreferences =
        copy(wordsPerDay = (wordsPerDay + amount).coerceIn(WORDS_PER_DAY_RANGE))

    /** Topics are collection-specific, so switching collections starts the topic choice over. */
    fun selectCollection(id: String): LearningPreferences {
        if (id == collectionId || TopicCatalog.collection(id) == null) return this
        return copy(collectionId = id, topicIds = emptySet())
    }

    /**
     * Adds or removes a topic. With a [limit] (the free plan's `maxTopics`) it won't add past it and returns null,
     * so the caller can offer Premium. Removing always works, even above the limit.
     */
    fun toggleTopic(id: String, limit: Int? = null): LearningPreferences? {
        if (collection?.topicIds?.contains(id) != true) return this
        return when {
            id in topicIds -> copy(topicIds = topicIds - id)
            limit != null && topicIds.size >= limit -> null
            else -> copy(topicIds = topicIds + id)
        }
    }

    /** The free plan can't hold every topic of this collection, so "Select all" isn't offered. */
    fun limitsTopics(limit: Int?): Boolean {
        val collection = collection ?: return false
        return limit != null && limit < collection.topicIds.size
    }

    val hasAllTopics: Boolean get() = collection?.let { topicIds == it.topicIds } ?: false

    /** "Select all" when some topics are missing, "Clear all" when all are selected. Under a limit it only clears. */
    fun toggleAllTopics(limit: Int? = null): LearningPreferences = when {
        limitsTopics(limit) -> copy(topicIds = emptySet())
        hasAllTopics -> copy(topicIds = emptySet())
        else -> copy(topicIds = collection?.topicIds ?: emptySet())
    }

    fun toggleDay(weekday: Int): LearningPreferences {
        if (weekday !in ALL_WEEKDAYS) return this
        return copy(learningDays = if (weekday in learningDays) learningDays - weekday else learningDays + weekday)
    }

    /** "Every day" when some days are missing, "Clear all" when all are selected. */
    fun toggleEveryDay(): LearningPreferences = copy(learningDays = if (isEveryDay) emptySet() else ALL_WEEKDAYS)

    companion object {
        /** Matches the backend's `dailyNewWordGoal` limits. */
        val WORDS_PER_DAY_RANGE = 1..30
        /** Matches the backend's `reviewsPerSession` limits. */
        val REVIEWS_PER_SESSION_RANGE = 1..50
        /** Floor for the unset value, which follows [wordsPerDay] (backend `followMin`). */
        const val REVIEWS_PER_SESSION_FOLLOW_MIN = 5
        val ALL_WEEKDAYS: Set<Int> = (1..7).toSet()

        val DEFAULT = LearningPreferences(
            collectionId = "everyday",
            topicIds = setOf("everyday-english", "travel"),
            wordsPerDay = 5,
            reviewsPerSession = null,
            dailySpeaking = false,
            learningDays = setOf(2, 3, 4, 5, 6),
            reminderEnabled = true,
            reminderHour = 20,
            reminderMinute = 0,
        )
    }
}
