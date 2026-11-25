package com.syll.features.plan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syll.data.KeyValueStore
import com.syll.domain.LearningPreferences
import kotlinx.serialization.json.Json

/** iOS `PreferencesStoring`: where the plan lives on this device. */
interface PreferencesStoring {
    var preferences: LearningPreferences?
    var hasCompletedOnboarding: Boolean
}

/** iOS `UserDefaultsPreferencesStore`, on [KeyValueStore]. */
class KeyValuePreferencesStore(private val storage: KeyValueStore) : PreferencesStoring {
    private val json = Json { ignoreUnknownKeys = true }

    override var preferences: LearningPreferences?
        get() = storage.getString(PREFERENCES_KEY)?.let {
            runCatching { json.decodeFromString(LearningPreferences.serializer(), it) }.getOrNull()
        }
        set(value) = storage.putString(PREFERENCES_KEY, value?.let { json.encodeToString(LearningPreferences.serializer(), it) })

    override var hasCompletedOnboarding: Boolean
        get() = storage.getBoolean(COMPLETED_KEY)
        set(value) = storage.putBoolean(COMPLETED_KEY, value)

    private companion object {
        const val PREFERENCES_KEY = "learningPreferences"
        const val COMPLETED_KEY = "hasCompletedOnboarding"
    }
}

/** iOS `InMemoryPreferencesStore`. */
class InMemoryPreferencesStore(
    override var preferences: LearningPreferences? = null,
    override var hasCompletedOnboarding: Boolean = false,
) : PreferencesStoring

/** iOS `ReminderScheduling`: one repeating reminder per learning day. */
fun interface ReminderScheduling {
    fun apply(preferences: LearningPreferences)
}

/** iOS `NoReminders`, for tests and previews. The app uses `AlarmReminderScheduler`. */
object NoReminders : ReminderScheduling {
    override fun apply(preferences: LearningPreferences) {}
}

/** iOS `LearningPlanStore`: the saved learning plan. Onboarding creates it; Profile edits it. */
class LearningPlanStore(private val storage: PreferencesStoring, private val reminders: ReminderScheduling) {
    var preferences by mutableStateOf(storage.preferences)
        private set
    var hasCompletedOnboarding by mutableStateOf(storage.hasCompletedOnboarding)
        private set

    val current: LearningPreferences get() = preferences ?: LearningPreferences.DEFAULT

    fun save(preferences: LearningPreferences) {
        if (!preferences.isComplete) return
        storage.preferences = preferences
        this.preferences = preferences
        reminders.apply(preferences)
    }

    fun rescheduleReminders() {
        reminders.apply(current)
    }

    fun completeOnboarding() {
        if (preferences == null) return
        storage.hasCompletedOnboarding = true
        hasCompletedOnboarding = true
    }

    /**
     * The account already has a plan (new device, or back after sign-out). The server owns the scope and daily goal;
     * learning days and reminders stay on this device.
     */
    fun adopt(
        collectionId: String,
        topicIds: List<String>,
        wordsPerDay: Int,
        reviewsPerSession: Int?,
        dailySpeaking: Boolean = false,
    ) {
        val next = current.copy(
            collectionId = collectionId,
            topicIds = topicIds.toSet(),
            wordsPerDay = minOf(wordsPerDay, LearningPreferences.WORDS_PER_DAY_RANGE.last),
            reviewsPerSession = reviewsPerSession,
            dailySpeaking = dailySpeaking,
        )
        storage.preferences = next
        preferences = next
        storage.hasCompletedOnboarding = true
        hasCompletedOnboarding = true
    }

    /** The signed-in account hasn't finished onboarding, whatever this device remembers. */
    fun resetOnboarding() {
        storage.hasCompletedOnboarding = false
        hasCompletedOnboarding = false
    }

    /** The account was deleted: forget the plan and cancel its reminders. */
    fun erase() {
        reminders.apply(current.copy(reminderEnabled = false))
        storage.preferences = null
        preferences = null
        resetOnboarding()
    }
}
