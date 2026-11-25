package com.syll.domain

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The `LearningPreferences` / `TopicCatalog` part of iOS `OnboardingStoreTests`. */
class LearningPreferencesTest {
    @Before
    fun bundledCatalog() {
        TopicCatalog.resetToBundled()
    }

    @Test
    fun wordsPerDayIsClamped() {
        assertEquals(1, LearningPreferences.DEFAULT.copy(wordsPerDay = 1).adjustWordsPerDay(-1).wordsPerDay)
        assertEquals(30, LearningPreferences.DEFAULT.copy(wordsPerDay = 30).adjustWordsPerDay(1).wordsPerDay)
    }

    @Test
    fun everyDayTogglesBetweenAllAndNone() {
        val all = LearningPreferences.DEFAULT.toggleEveryDay()
        assertTrue(all.isEveryDay)
        val none = all.toggleEveryDay()
        assertTrue(none.learningDays.isEmpty())
        assertFalse(none.isComplete)
    }

    @Test
    fun unknownCollectionIsIgnored() {
        assertEquals(LearningPreferences.DEFAULT, LearningPreferences.DEFAULT.selectCollection("klingon"))
    }

    @Test
    fun topicsOutsideTheCollectionAreIgnored() {
        assertEquals(LearningPreferences.DEFAULT.topicIds, LearningPreferences.DEFAULT.toggleTopic("ielts-science")?.topicIds)
    }

    @Test
    fun selectAllTogglesBetweenAllAndNone() {
        val business = LearningPreferences.DEFAULT.selectCollection("business")
        val all = business.toggleAllTopics()
        assertEquals(TopicCatalog.collection("business")?.topicIds, all.topicIds)
        assertTrue(all.hasAllTopics)
        assertTrue(all.toggleAllTopics().topicIds.isEmpty())
    }

    @Test
    fun topicLimitStopsAddingButNotRemoving() {
        val two = LearningPreferences.DEFAULT
        assertNull(two.toggleTopic("health", limit = 2))
        assertEquals(setOf("travel"), two.toggleTopic("everyday-english", limit = 1)?.topicIds)
        assertTrue(two.limitsTopics(2))
        assertTrue(two.toggleAllTopics(limit = 2).topicIds.isEmpty())
    }

    @Test
    fun reviewsFollowTheNewWordGoalUntilSet() {
        val prefs = LearningPreferences.DEFAULT.copy(wordsPerDay = 3)
        assertEquals(5, prefs.effectiveReviewsPerSession)
        assertEquals(8, prefs.copy(wordsPerDay = 8).effectiveReviewsPerSession)
        assertEquals(6, prefs.adjustReviewsPerSession(1).reviewsPerSession)
        assertEquals(50, prefs.copy(reviewsPerSession = 50).adjustReviewsPerSession(1).reviewsPerSession)
    }

    @Test
    fun topicIdsAreUniqueAcrossCollections() {
        val ids = TopicCatalog.collections.flatMap { it.topics }.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun defaultTopicsExistInDefaultCollection() {
        val prefs = LearningPreferences.DEFAULT
        assertNotNull(prefs.collection)
        assertTrue(prefs.collection!!.topicIds.containsAll(prefs.topicIds))
    }

    @Test
    fun roundTripsThroughJson() {
        val prefs = LearningPreferences.DEFAULT.selectCollection("ielts").toggleTopic("ielts-education")!!
        val json = Json.encodeToString(LearningPreferences.serializer(), prefs)
        assertEquals(prefs, Json.decodeFromString(LearningPreferences.serializer(), json))
    }

    @Test
    fun backendCatalogReplacesTheBundledOne() {
        TopicCatalog.update(
            listOf(
                CatalogEntry(
                    id = "uuid-1", slug = "business-work", title = "Kinh doanh", description = null, color = "#14B8A6",
                    topics = listOf(CatalogEntry.TopicEntry("t1", "marketing", "Tiếp thị", 3, 10, iosIcon = null)),
                ),
            ),
        )
        val collection = TopicCatalog.collection("uuid-1")!!
        assertEquals("briefcase", collection.icon)
        assertEquals(0x14B8A6, collection.markHex)
        assertEquals("megaphone", collection.topics.single().icon)
        assertFalse(collection.isBundled)
        assertNull(TopicCatalog.collection("everyday"))
    }
}
