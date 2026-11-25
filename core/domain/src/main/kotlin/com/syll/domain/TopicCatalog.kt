package com.syll.domain

/** iOS `Topic`. [icon] is an SF Symbol name (the backend's `iosIcon` or a bundled default); the app maps it. */
data class Topic(
    val id: String,
    val name: String,
    val icon: String,
    /** Distinct meanings learned and in total; null until the backend's catalog arrives. */
    val learned: Int? = null,
    val total: Int? = null,
) {
    /** Every meaning learned. A topic that gains content stops being finished. */
    val isFinished: Boolean get() = (total ?: 0) > 0 && learned == total
}

/** iOS `VocabCollection`: a vocabulary track such as Everyday or IELTS. */
data class VocabCollection(
    val id: String,
    val name: String,
    val summary: String,
    val icon: String,
    val topics: List<Topic>,
    val learned: Int? = null,
    val total: Int? = null,
    /** The backend's slug; null for the bundled catalog, whose ids are the slugs (and whose names are English keys). */
    val slug: String? = null,
    /** The backend's mark colour as 0xRRGGBB; null falls back to the slug. */
    val markHex: Int? = null,
) {
    val topicIds: Set<String> get() = topics.mapTo(mutableSetOf()) { it.id }
    val isFinished: Boolean get() = (total ?: 0) > 0 && learned == total
    /** The bundled catalog's names are English keys the app translates; the backend's are already localized. */
    val isBundled: Boolean get() = slug == null
}

/** One collection of `GET /catalog/collections`, as [TopicCatalog.update] needs it. */
data class CatalogEntry(
    val id: String,
    val slug: String,
    val title: String,
    val description: String?,
    val topics: List<TopicEntry>,
    val learnedMeanings: Int? = null,
    val totalMeanings: Int? = null,
    val color: String? = null,
) {
    data class TopicEntry(
        val id: String,
        val slug: String,
        val title: String,
        val learnedMeanings: Int? = null,
        val totalMeanings: Int? = null,
        val iosIcon: String? = null,
    )
}

/**
 * iOS `TopicCatalog`: the live taxonomy. Starts as the bundled one (previews, tests, icons); [update] swaps in the
 * backend's, whose ids are the ones `PUT /me/scope` accepts. Topic ids are unique across collections.
 *
 * Main thread only. [version] changes on every update so the app can observe it.
 */
object TopicCatalog {
    var collections: List<VocabCollection> = bundled()
        private set

    private val listeners = mutableListOf<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun update(catalog: List<CatalogEntry>) {
        val bundled = bundled()
        collections = catalog.map { collection ->
            VocabCollection(
                id = collection.id,
                name = collection.title,
                summary = collection.description.orEmpty(),
                icon = icon(bundled, collection.slug, fallback = "square.stack"),
                topics = collection.topics.map { topic ->
                    Topic(
                        id = topic.id,
                        name = topic.title,
                        icon = topic.iosIcon ?: icon(bundled, topic.slug, fallback = "tag"),
                        learned = topic.learnedMeanings,
                        total = topic.totalMeanings,
                    )
                },
                learned = collection.learnedMeanings,
                total = collection.totalMeanings,
                slug = collection.slug,
                markHex = hex(collection.color),
            )
        }
        listeners.forEach { it() }
    }

    /** Back to the bundled catalog (tests). */
    fun resetToBundled() {
        collections = bundled()
        listeners.forEach { it() }
    }

    fun collection(id: String): VocabCollection? = collections.firstOrNull { it.id == id }

    fun topic(id: String): Topic? = collections.asSequence().flatMap { it.topics }.firstOrNull { it.id == id }

    /** Selected topics in catalog order. */
    fun topics(ids: Set<String>): List<Topic> = collections.flatMap { it.topics }.filter { it.id in ids }

    private fun hex(value: String?): Int? {
        if (value == null || value.length != 7 || !value.startsWith("#")) return null
        return value.drop(1).toIntOrNull(16)
    }

    /** A slug matching a bundled entry reuses its icon; "business-work" → the bundled "business". */
    private fun icon(bundled: List<VocabCollection>, slug: String, fallback: String): String =
        bundled.firstOrNull { it.id == slug }?.icon
            ?: bundled.asSequence().flatMap { it.topics }.firstOrNull { it.id == slug }?.icon
            ?: bundled.firstOrNull { slug.startsWith(it.id + "-") }?.icon
            ?: fallback

    private fun bundled(): List<VocabCollection> = listOf(
        VocabCollection(
            "everyday", "Everyday", "Words for daily conversations and life", "sun.max",
            listOf(
                Topic("everyday-english", "Everyday English", "text.bubble"),
                Topic("travel", "Travel", "airplane"),
                Topic("food-drink", "Food & Drink", "fork.knife"),
                Topic("health", "Health", "cross.case"),
                Topic("communication", "Communication", "bubble.left.and.bubble.right"),
                Topic("shopping", "Shopping", "cart"),
                Topic("home", "Home", "house"),
                Topic("sports", "Sports", "figure.run"),
                Topic("art-music", "Art & Music", "music.note"),
            ),
        ),
        VocabCollection(
            "ielts", "IELTS", "Academic vocabulary for a higher band score", "graduationcap",
            listOf(
                Topic("ielts-education", "Education", "books.vertical"),
                Topic("ielts-environment", "Environment", "leaf"),
                Topic("ielts-technology", "Technology", "cpu"),
                Topic("ielts-health", "Health", "cross.case"),
                Topic("ielts-society", "Society", "person.3"),
                Topic("ielts-science", "Science", "atom"),
                Topic("ielts-culture", "Culture", "globe"),
                Topic("ielts-economy", "Economy", "chart.line.uptrend.xyaxis"),
            ),
        ),
        VocabCollection(
            "toeic", "TOEIC", "Workplace English that shows up on the test", "checkmark.seal",
            listOf(
                Topic("toeic-office", "Office", "building.2"),
                Topic("toeic-meetings", "Meetings", "person.2"),
                Topic("toeic-hiring", "Hiring", "person.badge.plus"),
                Topic("toeic-purchasing", "Purchasing", "cart"),
                Topic("toeic-travel", "Business travel", "airplane"),
                Topic("toeic-dining", "Dining out", "fork.knife"),
                Topic("toeic-finance", "Finance", "creditcard"),
            ),
        ),
        VocabCollection(
            "business", "Business", "Professional language for work and career", "briefcase",
            listOf(
                Topic("business", "General business", "briefcase"),
                Topic("workplace", "Workplace", "building.2"),
                Topic("marketing", "Marketing", "megaphone"),
                Topic("finance", "Finance", "creditcard"),
                Topic("interviews", "Interviews", "person.2"),
                Topic("negotiation", "Negotiation", "arrow.left.arrow.right"),
                Topic("productivity", "Productivity", "checklist"),
            ),
        ),
    )
}
