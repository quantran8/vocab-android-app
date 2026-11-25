package com.syll.network

import com.syll.domain.practice.CelebrationCounts
import com.syll.domain.practice.CelebrationVariant
import com.syll.domain.practice.CommitSummary
import com.syll.domain.practice.NeedPracticeCount
import com.syll.domain.practice.NewWordGroup
import com.syll.domain.practice.NewWordSummary
import com.syll.domain.practice.PracticeGroup
import com.syll.domain.practice.PracticeResult
import com.syll.domain.practice.PracticeSummary
import com.syll.domain.practice.PronunciationBand
import com.syll.domain.practice.ResultDateText
import com.syll.domain.practice.ResultGroups
import com.syll.domain.practice.ResultItem
import com.syll.domain.practice.ResultSection
import com.syll.domain.practice.ResultSheetBlock
import com.syll.domain.practice.ResultSheetGroup
import com.syll.domain.practice.ResultSheetModel
import com.syll.domain.practice.ResultSkill
import com.syll.domain.practice.ResultTile
import com.syll.domain.practice.ResultTiles
import com.syll.domain.practice.ReviewGroup
import com.syll.domain.practice.ReviewSummary
import com.syll.domain.practice.SpeakingGroup
import com.syll.domain.practice.SpeakingStatus
import com.syll.domain.practice.SpeakingSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

/** iOS `ResultTileTests`: the results screen rules ("Session Result Statistics" §4.3, §11) and the sheet's grouping. */
class ResultTileTest {
    private fun items(count: Int, prefix: String = "m", struggledAt: ResultSkill? = null, skill: ResultSkill? = null, status: SpeakingStatus? = null) =
        (0 until count).map { ResultItem(meaningId = "$prefix$it", struggledAt = struggledAt, skill = skill, status = status) }

    private fun review(solid: Int = 0, shaky: Int = 0, relearn: Int = 0) = ReviewSummary(
        groups = ResultGroups(mapOf(ReviewGroup.SOLID to items(solid, "s"), ReviewGroup.SHAKY to items(shaky, "h"), ReviewGroup.RELEARN to items(relearn, "r"))),
    )

    private fun newWords(vararg groups: Pair<NewWordGroup, List<ResultItem>>) = NewWordSummary(groups = ResultGroups(mapOf(*groups)))
    private fun practice(vararg groups: Pair<PracticeGroup, List<ResultItem>>, excluded: PracticeSummary.Excluded? = null) =
        PracticeSummary(excluded = excluded, groups = ResultGroups(mapOf(*groups)))

    // Review tile

    @Test fun reviewTileNamesEveryWeakerGroup() {
        assertEquals(
            ResultTile(ResultTile.Kind.REVIEW, ResultTile.Number.Fraction(7, 9), ResultTile.Caption.REMEMBERED, listOf(ResultTile.Line.Shaky(1), ResultTile.Line.Relearn(1))),
            ResultTiles.reviewTile(review(solid = 7, shaky = 1, relearn = 1)),
        )
        // An empty group gets no line; the strong group is the number itself.
        assertEquals(listOf(ResultTile.Line.Shaky(3), ResultTile.Line.Relearn(2)), ResultTiles.reviewTile(review(shaky = 3, relearn = 2)).lines)
        assertEquals(listOf(ResultTile.Line.Relearn(1)), ResultTiles.reviewTile(review(solid = 2, relearn = 1)).lines)
        assertEquals(emptyList<ResultTile.Line>(), ResultTiles.reviewTile(review(solid = 4)).lines)
    }

    @Test fun reviewTileIsEmptyWithoutReviews() {
        listOf(null, review()).forEach {
            assertEquals(ResultTile(ResultTile.Kind.REVIEW, ResultTile.Number.None, ResultTile.Caption.NO_REVIEWS, isEmpty = true), ResultTiles.reviewTile(it))
        }
    }

    // New words tile

    @Test fun newWordsFractionLeavesKnownWordsOut() {
        val tile = ResultTiles.newWordsTile(
            newWords(
                NewWordGroup.SMOOTH to items(2), NewWordGroup.RETRIED to items(1, "t", struggledAt = ResultSkill.SPEAKING),
                NewWordGroup.REVEALED to items(1, "v", struggledAt = ResultSkill.SPEAKING), NewWordGroup.KNOWN to items(1, "k"),
            ),
        )
        assertEquals(ResultTile.Number.Fraction(2, 4), tile.number)
        assertEquals(listOf(ResultTile.Line.NeededRetry(1), ResultTile.Line.SawAnswer(1), ResultTile.Line.AlreadyKnew(1)), tile.lines)
    }

    @Test fun newWordsTileEmptyOrOnlyKnown() {
        assertEquals(
            ResultTile(ResultTile.Kind.NEW_WORDS, ResultTile.Number.None, ResultTile.Caption.NO_NEW_WORDS, isEmpty = true),
            ResultTiles.newWordsTile(NewWordSummary()),
        )
        val onlyKnown = ResultTiles.newWordsTile(newWords(NewWordGroup.KNOWN to items(2)))
        assertEquals(ResultTile.Number.None, onlyKnown.number)
        assertFalse(onlyKnown.isEmpty)
        assertEquals(listOf(ResultTile.Line.AlreadyKnew(2)), onlyKnown.lines)
    }

    // Practice tiles

    @Test fun practiceTileLines() {
        val missed = practice(PracticeGroup.CLEAN to items(5), PracticeGroup.HELPED to items(2, "h"), PracticeGroup.MISSED to items(1, "x"))
        assertEquals(
            ResultTile(ResultTile.Kind.PRACTICE, ResultTile.Number.Fraction(5, 8), ResultTile.Caption.PRACTICE_CLEAN, listOf(ResultTile.Line.Helped(2), ResultTile.Line.Missed(1))),
            ResultTiles.practiceTile(missed),
        )
        assertEquals(listOf(ResultTile.Line.Helped(2)), ResultTiles.practiceTile(practice(PracticeGroup.CLEAN to items(1), PracticeGroup.HELPED to items(2, "h"))).lines)
    }

    @Test fun practiceTileWithNothingMeasured() {
        val summary = PracticeSummary(total = 0, excluded = PracticeSummary.Excluded(4, listOf("weak_template")))
        assertEquals(
            ResultTile(ResultTile.Kind.PRACTICE, ResultTile.Number.None, ResultTile.Caption.NO_SKILL_MEASURED, isEmpty = true),
            ResultTiles.practiceTile(summary),
        )
    }

    @Test fun weakTileShowsWhatIsLeftWithTheDelta() {
        assertEquals(
            ResultTile(ResultTile.Kind.WEAK_REMAINING, ResultTile.Number.Count(8), ResultTile.Caption.WEAK_REMAINING, listOf(ResultTile.Line.WeakDelta(-3))),
            ResultTiles.weakTile(NeedPracticeCount(before = 11, after = 8)),
        )
        assertEquals(emptyList<ResultTile.Line>(), ResultTiles.weakTile(NeedPracticeCount(before = 4, after = 4)).lines)
    }

    @Test fun weakTileAtZeroCelebrates() {
        val tile = ResultTiles.weakTile(NeedPracticeCount(before = 1, after = 0))
        assertEquals(ResultTile.Caption.NO_WEAK_WORDS_LEFT, tile.caption)
        assertTrue(tile.celebrates)
        assertTrue(ResultTiles.weakTile(null).isEmpty)
    }

    // Speaking tile

    @Test fun speakingTileCountsWhatWasSaidRightAndNamesChanges() {
        val speaking = SpeakingSummary(
            total = 5, passed = 3,
            becameFluent = items(2, "f", status = SpeakingStatus.FLUENT), slipped = items(1, "s", status = SpeakingStatus.PRACTICING),
        )
        assertEquals(
            ResultTile(ResultTile.Kind.SPEAKING, ResultTile.Number.Fraction(3, 5), ResultTile.Caption.SAID_RIGHT, listOf(ResultTile.Line.BecameFluent(2), ResultTile.Line.Slipped(1))),
            ResultTiles.speakingTile(speaking),
        )
        assertEquals(emptyList<ResultTile.Line>(), ResultTiles.speakingTile(SpeakingSummary(total = 2, passed = 2)).lines)
    }

    @Test fun speakingTileIsEmptyWithoutScoredRecordings() {
        assertTrue(ResultTiles.speakingTile(null).isEmpty)
        assertTrue(ResultTiles.speakingTile(SpeakingSummary()).isEmpty)
    }

    @Test fun signedDeltaUsesARealMinus() {
        assertEquals("−3", ResultTile.Line.signed(-3))
        assertEquals("+2", ResultTile.Line.signed(2))
    }

    // Tile set

    @Test fun eachModeShowsOnlyTheTilesWithSomethingToSay() {
        fun kinds(result: PracticeResult) = result.tiles?.map { it.kind }
        val both = PracticeResult(PracticeResult.Kind.DAILY, CommitSummary(review = review(solid = 1), newWords = newWords(NewWordGroup.SMOOTH to items(1))))
        assertEquals(listOf(ResultTile.Kind.REVIEW, ResultTile.Kind.NEW_WORDS), kinds(both))
        val spoken = SpeakingSummary(total = 1, passed = 1)
        val dailySpeaking = PracticeResult(PracticeResult.Kind.DAILY, CommitSummary(review = review(solid = 1), newWords = NewWordSummary(), speaking = spoken))
        assertEquals(listOf(ResultTile.Kind.REVIEW, ResultTile.Kind.SPEAKING), kinds(dailySpeaking))
        // Review only: no new-word tile at all, not an empty one.
        val reviewOnly = PracticeResult(PracticeResult.Kind.DAILY, CommitSummary(review = review(solid = 1), newWords = NewWordSummary()))
        assertEquals(listOf(ResultTile.Kind.REVIEW), kinds(reviewOnly))

        val summary = CommitSummary(practice = practice(PracticeGroup.CLEAN to items(1)), speaking = spoken, needPractice = NeedPracticeCount(2, 1))
        assertEquals(listOf(ResultTile.Kind.PRACTICE, ResultTile.Kind.SPEAKING), kinds(PracticeResult(PracticeResult.Kind.RANDOM_PRACTICE, summary)))
        assertEquals(listOf(ResultTile.Kind.PRACTICE, ResultTile.Kind.WEAK_REMAINING), kinds(PracticeResult(PracticeResult.Kind.WEAK_PRACTICE, summary)))
        val unmeasured = CommitSummary(practice = PracticeSummary())
        assertEquals(emptyList<ResultTile.Kind>(), kinds(PracticeResult(PracticeResult.Kind.RANDOM_PRACTICE, unmeasured)))
        assertEquals(emptyList<ResultTile.Kind>(), kinds(PracticeResult(PracticeResult.Kind.WEAK_PRACTICE, unmeasured)))
        assertEquals(listOf(ResultTile.Kind.SPEAKING), kinds(PracticeResult(PracticeResult.Kind.SPEAKING_PRACTICE, CommitSummary(speaking = spoken))))
    }

    @Test fun olderBackendsAndCheckpointsUseTheCountGrid() {
        assertNull(PracticeResult(PracticeResult.Kind.DAILY, CommitSummary(newWordGoalSlots = 2, reviewedMeanings = 1, practiceTasks = 8)).tiles)
        assertNull(PracticeResult(PracticeResult.Kind.RANDOM_PRACTICE, CommitSummary(practiceTasks = 8)).tiles)
        assertNull(PracticeResult(PracticeResult.Kind.CHECKPOINT, CommitSummary(review = review(solid = 1))).tiles)
    }

    // Sheet

    @Test fun dailySheetPutsWhatNeedsAttentionFirstAndFoldsTheEasyGroup() {
        val result = PracticeResult(PracticeResult.Kind.DAILY, CommitSummary(review = review(solid = 3, relearn = 1), newWords = newWords(NewWordGroup.SMOOTH to items(2))))
        val blocks = ResultSheetModel.blocks(result)
        assertEquals(listOf(ResultSection.REVIEW, ResultSection.NEW_WORDS), blocks.map { it.section })
        assertEquals(listOf(ResultSheetGroup.Kind.RELEARN, ResultSheetGroup.Kind.SOLID), blocks[0].groups.map { it.kind })
        assertEquals(listOf(false, true), blocks[0].groups.map { it.collapsedByDefault })
        // Alone in its block, the easy group stays open.
        assertEquals(listOf(false), blocks[1].groups.map { it.collapsedByDefault })
        assertEquals(listOf(3, 0, 1), blocks[0].bar)
        assertEquals(listOf(2, 0, 0), blocks[1].bar)
    }

    @Test fun weakSheetListsEveryAnswerInOneBlock() {
        val summary = practice(
            PracticeGroup.CLEAN to listOf(ResultItem("a", skill = ResultSkill.LISTENING)),
            PracticeGroup.MISSED to listOf(ResultItem("b", skill = ResultSkill.SPELLING), ResultItem("c")),
            excluded = PracticeSummary.Excluded(1, listOf("unavailable")),
        )
        val blocks = ResultSheetModel.blocks(PracticeResult(PracticeResult.Kind.WEAK_PRACTICE, CommitSummary(practice = summary)))
        assertEquals(listOf(ResultSheetBlock.Title.ANSWERS), blocks.map { it.title })
        assertEquals(listOf(ResultSheetGroup.Kind.MISSED, ResultSheetGroup.Kind.CLEAN), blocks[0].groups.map { it.kind })
        assertEquals(listOf("b", "c"), blocks[0].groups[0].items.map { it.meaningId })
        assertEquals(1, blocks[0].excluded?.count)
    }

    @Test fun speakingBlockFollowsTheOthersWhenARecordingWasScored() {
        val speaking = SpeakingSummary(
            total = 3, passed = 2,
            becameFluent = items(1, "f", status = SpeakingStatus.FLUENT), slipped = items(1, "s", status = SpeakingStatus.PRACTICING),
        )
        val daily = ResultSheetModel.blocks(PracticeResult(PracticeResult.Kind.DAILY, CommitSummary(review = review(solid = 1), speaking = speaking)))
        assertEquals(listOf(ResultSection.REVIEW, ResultSection.SPEAKING), daily.map { it.section })
        assertEquals(listOf(ResultSheetGroup.Kind.SLIPPED, ResultSheetGroup.Kind.BECAME_FLUENT), daily[1].groups.map { it.kind })
        assertEquals(ResultSheetBlock.SpokenTally(2, 3), daily[1].spoken)
        assertNull(daily[1].bar)

        // No status changed: the block still says how it went, so the tile has somewhere to open.
        val quiet = ResultSheetModel.blocks(PracticeResult(PracticeResult.Kind.SPEAKING_PRACTICE, CommitSummary(speaking = SpeakingSummary(total = 2, passed = 1))))
        assertEquals(listOf(ResultSheetBlock.Title.SPEAKING), quiet.map { it.title })
        assertEquals(emptyList<ResultSheetGroup>(), quiet[0].groups)
        assertTrue(ResultSheetModel.blocks(PracticeResult(PracticeResult.Kind.SPEAKING_PRACTICE, CommitSummary())).isEmpty())
    }

    @Test fun speakingBlockListsEveryScoredWordWhenTheServerSendsGroups() {
        val speaking = SpeakingSummary(
            total = 3, passed = 2,
            groups = ResultGroups(mapOf(SpeakingGroup.PASSED to items(2, "p"), SpeakingGroup.NOT_PASSED to items(1, "n"))),
            becameFluent = items(1, "f", status = SpeakingStatus.FLUENT),
        )
        val block = ResultSheetModel.blocks(PracticeResult(PracticeResult.Kind.SPEAKING_PRACTICE, CommitSummary(speaking = speaking))).first()
        assertEquals(listOf(ResultSheetGroup.Kind.NOT_PASSED, ResultSheetGroup.Kind.SPOKE_RIGHT, ResultSheetGroup.Kind.BECAME_FLUENT), block.groups.map { it.kind })
        assertEquals(listOf(false, false, true), block.groups.map { it.collapsedByDefault })
        assertNull(block.spoken)
        assertEquals(listOf(2, 0, 1), block.bar)
    }

    @Test fun speakingItemDecodesItsBand() {
        val json = """{"total":1,"passed":0,"groups":{"passed":[],"notPassed":[{"meaningId":"m","lemma":"fatigue","score":64,"band":"fair"}]},"becameFluent":[],"slipped":[]}"""
        val speaking = ApiJson.decodeFromString(SpeakingSummary.serializer(), json)
        val item = speaking.groups!![SpeakingGroup.NOT_PASSED].first()
        assertEquals(64.0, item.score)
        assertEquals(PronunciationBand.FAIR, item.band)
    }

    @Test fun randomSheetListsAnswersThenTheReviewsTheyCounted() {
        val summary = practice(PracticeGroup.CLEAN to listOf(ResultItem("a", skill = ResultSkill.SPELLING), ResultItem("b", skill = ResultSkill.LISTENING)))
        val blocks = ResultSheetModel.blocks(PracticeResult(PracticeResult.Kind.RANDOM_PRACTICE, CommitSummary(practice = summary, countedAsReview = listOf(ResultItem("a")))))
        assertEquals(listOf(ResultSheetBlock.Title.ANSWERS), blocks.map { it.title })
        assertEquals(listOf(ResultSheetGroup.Kind.CLEAN), blocks[0].groups.map { it.kind })
        assertEquals(listOf("a"), blocks[0].countedAsReview.map { it.meaningId })
    }

    // Dates

    @Test fun nextReviewIsRelativeInTheLearnersTimeZone() {
        val zone = ZoneId.of("Asia/Ho_Chi_Minh")
        val now = Instant.parse("2026-11-13T10:00:00Z") // 17:00 local
        assertEquals(ResultDateText.NextReview.Tonight, ResultDateText.nextReview(Instant.parse("2026-11-13T15:00:00Z"), now, zone))
        // 01:00 UTC on the 14th is already the 14th in Hanoi.
        assertEquals(ResultDateText.NextReview.Tomorrow, ResultDateText.nextReview(Instant.parse("2026-11-13T18:00:00Z"), now, zone))
        val sunday = Instant.parse("2026-11-15T05:00:00Z")
        assertEquals(ResultDateText.NextReview.Weekday(sunday), ResultDateText.nextReview(sunday, now, zone))
        assertEquals(ResultDateText.NextReview.InDays(12), ResultDateText.nextReview(Instant.parse("2026-11-25T05:00:00Z"), now, zone))
        assertNotNull(ResultDateText.recentlyLearned(Instant.parse("2026-11-09T05:00:00Z"), now, zone))
        assertNull(ResultDateText.recentlyLearned(Instant.parse("2026-11-01T05:00:00Z"), now, zone))
    }

    // Celebration (iOS SessionCelebration / CelebrationVariant rules)

    @Test fun celebrationCountsAndPerfect() {
        val perfect = CelebrationCounts.of(CommitSummary(review = review(solid = 2), newWords = newWords(NewWordGroup.SMOOTH to items(3))))
        assertEquals(CelebrationCounts(newWords = 3, reviewed = 2, perfect = true), perfect)
        val shaky = CelebrationCounts.of(CommitSummary(review = review(solid = 2, shaky = 1)))
        assertFalse(shaky.perfect)
        assertEquals(3, shaky.reviewed)
        // Practice counts each meaning once, however many answers it had.
        val practiced = CelebrationCounts.of(CommitSummary(practice = practice(PracticeGroup.CLEAN to listOf(ResultItem("a"), ResultItem("a"), ResultItem("b")))))
        assertEquals(2, practiced.reviewed)
        // An older backend: legacy counts, never perfect.
        assertEquals(CelebrationCounts(1, 4, false), CelebrationCounts.of(CommitSummary(newWordGoalSlots = 2, knownMeanings = 1, reviewedMeanings = 4)))
        assertFalse(CelebrationCounts.of(CommitSummary(review = review())).perfect)
    }

    @Test fun celebrationVariantNeverRepeatsTheLastOne() {
        val random = Random(7)
        CelebrationVariant.entries.forEach { last ->
            repeat(20) { assertNotEquals(last, CelebrationVariant.pick(last, random)) }
        }
    }
}
