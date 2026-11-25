package com.syll.features.plan

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.syll.R
import com.syll.designsystem.tokens.SyllColors
import com.syll.domain.Topic
import com.syll.domain.TopicCatalog
import com.syll.domain.VocabCollection

/**
 * Makes [TopicCatalog] observable to Compose (iOS `@Observable CatalogState`): [collections] reads a version that
 * every catalog update bumps. [install] once, from `AppContainer`.
 */
object CatalogState {
    private var version by mutableIntStateOf(0)

    fun install() {
        TopicCatalog.addListener { version += 1 }
    }

    val collections: List<VocabCollection>
        get() {
            version
            return TopicCatalog.collections
        }

    fun collection(id: String): VocabCollection? = collections.firstOrNull { it.id == id }
}

/** The bundled catalog's English names and summaries, translated (iOS `String(localized:)` in `TopicCatalog`). */
private val bundledText: Map<String, Int> = mapOf(
    "Everyday" to R.string.everyday,
    "Words for daily conversations and life" to R.string.words_for_daily_conversations_and_life,
    "Everyday English" to R.string.everyday_english,
    "Travel" to R.string.travel,
    "Food & Drink" to R.string.food_drink,
    "Health" to R.string.health,
    "Communication" to R.string.communication,
    "Shopping" to R.string.shopping,
    "Home" to R.string.home,
    "Sports" to R.string.sports,
    "Art & Music" to R.string.art_music,
    "IELTS" to R.string.ielts,
    "Academic vocabulary for a higher band score" to R.string.academic_vocabulary_for_a_higher_band_score,
    "Education" to R.string.education,
    "Environment" to R.string.environment,
    "Technology" to R.string.technology,
    "Society" to R.string.society,
    "Science" to R.string.science,
    "Culture" to R.string.culture,
    "Economy" to R.string.economy,
    "TOEIC" to R.string.toeic,
    "Workplace English that shows up on the test" to R.string.workplace_english_that_shows_up_on_the_test,
    "Office" to R.string.office,
    "Meetings" to R.string.meetings,
    "Hiring" to R.string.hiring,
    "Purchasing" to R.string.purchasing,
    "Business travel" to R.string.business_travel,
    "Dining out" to R.string.dining_out,
    "Finance" to R.string.finance,
    "Business" to R.string.business,
    "Professional language for work and career" to R.string.professional_language_for_work_and_career,
    "General business" to R.string.general_business,
    "Workplace" to R.string.workplace,
    "Marketing" to R.string.marketing,
    "Interviews" to R.string.interviews,
    "Negotiation" to R.string.negotiation,
    "Productivity" to R.string.productivity,
)

@Composable
private fun catalogText(text: String, isBundled: Boolean): String {
    @StringRes val id = if (isBundled) bundledText[text] else null
    return if (id != null) stringResource(id) else text
}

/** The backend's names are already in the learner's language; the bundled ones are translated here. */
@Composable
fun VocabCollection.displayName(): String = catalogText(name, isBundled)

@Composable
fun VocabCollection.displaySummary(): String = catalogText(summary, isBundled)

@Composable
fun Topic.displayName(collection: VocabCollection): String = catalogText(name, collection.isBundled)

/**
 * iOS `CollectionMarkColor`: each collection's mark colour. The backend's colour wins; without one a collection is
 * recognised by its slug (or id/name), then by its icon.
 */
object CollectionMarkColor {
    val custom: Color = SyllColors.markCustom

    fun color(collection: VocabCollection): Color {
        collection.markHex?.let { return Color(0xFF000000 or it.toLong()) }
        val key = listOfNotNull(collection.slug, collection.id, collection.name).joinToString(" ").lowercase()
        return when {
            "everyday" in key -> SyllColors.markEveryday
            "ielts" in key -> SyllColors.markIELTS
            "business" in key -> SyllColors.markBusiness
            "toeic" in key -> SyllColors.markTOEIC
            else -> when (collection.icon) {
                "sun.max" -> SyllColors.markEveryday
                "graduationcap" -> SyllColors.markIELTS
                "briefcase" -> SyllColors.markBusiness
                "checkmark.seal" -> SyllColors.markTOEIC
                else -> SyllColors.interactive
            }
        }
    }
}
