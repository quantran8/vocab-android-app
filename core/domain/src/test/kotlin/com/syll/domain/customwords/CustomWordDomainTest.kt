package com.syll.domain.customwords

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Port of iOS `CustomWordDomainTests`. */
class CustomWordDomainTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private fun decode(text: String): CustomWordLookup = json.decodeFromString(CustomWordLookup.serializer(), text)

    private val sense = DraftSense(
        partOfSpeech = "noun", ipa = "/x/", cefrLevel = "C1", meaningText = "tình cờ", definitionText = "định nghĩa",
        examples = listOf(CustomWordExample("Pure serendipity.", "Tình cờ.", TargetLocator(5, 16))),
    )

    private fun word(overridden: Boolean = false, examples: List<CustomWordExample>? = null) = CustomWord(
        meaningId = "m", wordId = "w", lemma = "bank", partOfSpeech = "noun", meaningText = "bờ", definitionText = "định nghĩa",
        examples = examples, overridden = overridden, translationPending = false, recalled = false,
    )

    @Test
    fun decodesEveryLookupResultAndStatus() {
        val existing = decode(
            """{"result":"existing","wordId":"w","lemma":"bank","meanings":[
              {"meaningId":"a","status":"available","partOfSpeech":"noun","inRoadmap":false,"ipa":"/bæŋk/","meaningText":"bờ","definitionText":"d"},
              {"meaningId":"b","status":"translation_draft","partOfSpeech":"verb","inRoadmap":false,"draftId":"t","meaningText":"m","definitionText":"d"},
              {"meaningId":"c","status":"unavailable","partOfSpeech":"noun","inRoadmap":false}
            ]}""",
        )
        val word = (existing as CustomWordLookup.Existing).word
        assertEquals(listOf(CustomWordAddition.meaning("a"), CustomWordAddition.translation("t"), null), word.meanings.map { it.addition })
        assertEquals(listOf("/bæŋk/", null, null), word.meanings.map { it.ipa })

        assertEquals(CustomWordLookup.Draft(WordDraft("d", "l", emptyList())), decode("""{"result":"draft","draftId":"d","lemma":"l","senses":[]}"""))
        assertTrue(decode("""{"result":"new_meaning_draft","draftId":"d","lemma":"l","senses":[]}""") is CustomWordLookup.NewMeaningDraft)
        assertEquals(CustomWordLookup.Invalid, decode("""{"result":"invalid"}"""))
        try {
            decode("""{"result":"new_kind"}""")
            fail("An unknown result must fail the lookup")
        } catch (_: SerializationException) {
        }
    }

    @Test
    fun learnedOrOnPathMeaningsCannotBeAddedAgain() {
        val existing = decode(
            """{"result":"existing","wordId":"w","lemma":"bank","meanings":[
              {"meaningId":"a","status":"available","partOfSpeech":"noun","inRoadmap":false,"learned":true,"meaningText":"bờ"},
              {"meaningId":"b","status":"available","partOfSpeech":"noun","inRoadmap":true,"learned":false,"meaningText":"bờ"},
              {"meaningId":"c","status":"available","partOfSpeech":"noun","inRoadmap":false,"meaningText":"bờ"}
            ]}""",
        ) as CustomWordLookup.Existing
        assertEquals(listOf(ExistingMeaning.Standing.LEARNED, ExistingMeaning.Standing.ON_PATH, null), existing.word.meanings.map { it.standing })
        assertEquals(listOf(false, false, true), existing.word.meanings.map { it.canBeAdded })
    }

    @Test
    fun additionBodiesHoldOnlyOneForm() {
        assertEquals("""{"meaningId":"m"}""", CustomWordAddition.meaning("m").toJson().toString())
        assertEquals("""{"draftId":"t"}""", CustomWordAddition.translation("t").toJson().toString())
        assertEquals(setOf("draftId", "senseIndex"), CustomWordAddition.draft("d", 1, null).toJson().keys)
    }

    @Test
    fun inputRules() {
        assertNull(CustomWordInput.issue("  mother-in-law "))
        assertNull(CustomWordInput.issue("don’t"))
        assertEquals(CustomWordInput.Issue.EMPTY, CustomWordInput.issue("   "))
        assertEquals(CustomWordInput.Issue.INVALID_CHARACTERS, CustomWordInput.issue("-word"))
        assertEquals(CustomWordInput.Issue.INVALID_CHARACTERS, CustomWordInput.issue("word2"))
        assertEquals(CustomWordInput.Issue.TOO_LONG, CustomWordInput.issue("a".repeat(65)))
    }

    @Test
    fun locatorCountsUtf16Units() {
        val sentence = "😀 Serendipity!"
        val locator = TargetLocator.locating("serendipity", sentence)
        assertEquals(TargetLocator(3, 14), locator)
        assertEquals("Serendipity", locator?.substring(sentence))
        assertNull(TargetLocator(3, 99).substring(sentence))
    }

    @Test
    fun noEditsMeansNothingToSend() {
        var draft = CustomWordEditDraft.of("serendipity", sense)
        assertNull(draft.edits)
        draft = draft.withMeaning(" tình cờ ")
        assertNull("Whitespace isn't an edit", draft.edits)
    }

    @Test
    fun onlyChangedFieldsAreSent() {
        val draft = CustomWordEditDraft.of("serendipity", sense).withMeaning("may mắn")
        assertEquals(CustomWordEdits(meaningText = "may mắn"), draft.edits)
    }

    @Test
    fun rewrittenSentenceMovesTheHighlight() {
        val draft = CustomWordEditDraft.of("serendipity", sense).withExample(0, text = "  What a serendipity!  ")
        assertTrue(draft.isValid)
        assertEquals(listOf(CustomWordExample("What a serendipity!", "Tình cờ.", TargetLocator(7, 18))), draft.edits?.examples)
    }

    @Test
    fun inflectionIsHighlightedAutomatically() {
        val draft = CustomWordEditDraft.of("run", sense).withExample(0, text = "She ran home.")
        assertTrue(draft.isValid)
        assertEquals(TargetLocator(4, 7), draft.locator(draft.examples[0]))
    }

    @Test
    fun missingTargetDisablesSave() {
        val draft = CustomWordEditDraft.of("run", sense).withExample(0, text = "She went home.")
        assertFalse(draft.isValid)
        assertNull(draft.locator(draft.examples[0]))
    }

    @Test
    fun targetMustBeAWholeWord() {
        val draft = CustomWordEditDraft.of("run", sense).withExample(0, text = "A trunk.")
        assertNull(draft.locator(draft.examples[0]))
    }

    @Test
    fun wordsSkipPunctuation() {
        val sentence = "Hi, there!"
        assertEquals(listOf("Hi", "there"), TargetLocator.words(sentence).mapNotNull { it.substring(sentence) })
    }

    @Test
    fun editingAnOverriddenWordSendsOnlyWhatChanged() {
        val draft = CustomWordEditDraft.of(word(overridden = true)).withMeaning("mới")
        assertEquals("The backend merges by field", CustomWordEdits(meaningText = "mới"), draft.edits)
    }

    @Test
    fun anyExampleEditSendsTheWholeList() {
        val extra = CustomWordExample("A bank.", "Bờ.", TargetLocator(2, 6))
        val draft = CustomWordEditDraft.of(word(examples = listOf(extra, extra))).withExample(1, translation = "Một bờ.")
        assertEquals(listOf("Bờ.", "Một bờ."), draft.edits?.examples?.map { it.translation })
    }

    @Test
    fun existingMeaningCarriesEdits() {
        val meaning = ExistingMeaning(
            meaningId = "m", status = ExistingMeaning.Status.AVAILABLE, partOfSpeech = "noun", inRoadmap = false,
            meaningText = "bờ", definitionText = "d",
        )
        val draft = CustomWordEditDraft.of("bank", meaning).withMeaning("bờ sông")
        val addition = meaning.addition!!.withEdits(draft.edits)
        assertEquals(setOf("meaningId", "edits"), addition.toJson().keys)
    }

    @Test
    fun inflectionsCoverRegularAndIrregularFormsOnly() {
        assertTrue(EnglishInflection.isForm("postponed", "postpone"))
        assertTrue(EnglishInflection.isForm("studies", "study"))
        assertTrue(EnglishInflection.isForm("stopped", "stop"))
        assertTrue(EnglishInflection.isForm("children", "child"))
        assertFalse(EnglishInflection.isForm("runner", "run"))
        assertFalse(EnglishInflection.isForm("trunk", "run"))
    }
}
