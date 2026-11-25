package com.syll.domain.challenge

// iOS `ChallengeFixtures`: static maps for previews and `OfflineVocabApi`. Never used to decide anything for a
// signed-in learner.

fun ChallengeLevel.Companion.preview(levelNo: Int, state: ChallengeLevel.State, bestStars: Int = 0) = ChallengeLevel(
    levelNo = levelNo,
    state = state,
    bestStars = bestStars,
    bestScore = if (bestStars > 0) 1_200 + bestStars * 300 else 0,
)

val ChallengeMap.Companion.preview: ChallengeMap
    get() = ChallengeMap(
        chapters = listOf(
            ChallengeChapter(
                id = "preview-1",
                chapterNo = 1,
                state = ChallengeChapter.State.COMPLETED,
                explanationLocale = "vi-VN",
                starsEarned = 7,
                maxStars = 9,
                levels = listOf(
                    ChallengeLevel.preview(1, ChallengeLevel.State.CLEARED, bestStars = 3),
                    ChallengeLevel.preview(2, ChallengeLevel.State.CLEARED, bestStars = 2),
                    ChallengeLevel.preview(3, ChallengeLevel.State.CLEARED, bestStars = 2),
                ),
            ),
            ChallengeChapter(
                id = "preview-2",
                chapterNo = 2,
                state = ChallengeChapter.State.ACTIVE,
                explanationLocale = "vi-VN",
                starsEarned = 1,
                maxStars = 9,
                levels = listOf(
                    ChallengeLevel.preview(1, ChallengeLevel.State.CLEARED, bestStars = 1),
                    ChallengeLevel.preview(2, ChallengeLevel.State.AVAILABLE),
                    ChallengeLevel.preview(3, ChallengeLevel.State.LOCKED),
                ),
            ),
        ),
        nextChapter = ChallengeChapterPreview(3, ChallengeChapterPreview.State.LOCKED, eligibleNewCount = 5, requiredNewCount = 12),
        currentNode = ChallengeNodeRef(chapterNo = 2, levelNo = 2),
    )

val ChallengeMap.Companion.previewNotEligible: ChallengeMap
    get() = ChallengeMap(
        chapters = emptyList(),
        nextChapter = ChallengeChapterPreview(1, ChallengeChapterPreview.State.NEEDS_VOCABULARY, eligibleNewCount = 7, requiredNewCount = 12),
        currentNode = null,
    )
