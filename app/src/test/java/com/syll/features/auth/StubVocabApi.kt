package com.syll.features.auth

import com.syll.network.WordDetail
import com.syll.network.WordStats
import com.syll.network.WordProgress
import com.syll.domain.CefrLevel
import com.syll.domain.challenge.ChallengeCommitResponse
import com.syll.domain.challenge.ChallengeMap
import com.syll.domain.challenge.ChallengeNodeRef
import com.syll.domain.challenge.ChallengeRunPlan
import com.syll.domain.challenge.ChallengeRunStatus
import com.syll.domain.customwords.CustomWord
import com.syll.domain.customwords.CustomWordAdded
import com.syll.domain.customwords.CustomWordAddition
import com.syll.domain.customwords.CustomWordEdits
import com.syll.domain.customwords.CustomWordLookup
import com.syll.domain.customwords.CustomWordQuery
import com.syll.domain.ranking.Leaderboard
import com.syll.domain.ranking.LeaderboardView
import com.syll.domain.ranking.MyRanking
import com.syll.domain.ranking.PublicProfile
import com.syll.domain.ranking.PublicProfileUpdate
import com.syll.domain.ranking.RankingHistory
import com.syll.domain.ranking.RankingMeta
import com.syll.domain.ranking.ReportReason
import com.syll.domain.ranking.TierDetail
import com.syll.domain.TopicCatalog
import com.syll.domain.practice.CommitResponse
import com.syll.domain.practice.CommitStatus
import com.syll.domain.practice.PlanRequest
import com.syll.domain.practice.RecordedAudio
import com.syll.domain.practice.RecordingAssessment
import com.syll.domain.practice.SessionPlan
import com.syll.network.ApiError
import com.syll.network.CatalogCollection
import com.syll.network.CatalogTopic
import com.syll.network.LearnHome
import com.syll.network.LearningScope
import com.syll.network.NewWordOverride
import com.syll.network.NewWordOverrideMode
import com.syll.network.PlacementAnswer
import com.syll.network.PlacementQuestion
import com.syll.network.PlacementResult
import com.syll.network.SettingsUpdate
import com.syll.network.SignupResult
import com.syll.network.UserProfile
import com.syll.network.UserSettings
import com.syll.network.VocabApi
import java.time.Instant

/** iOS `StubVocabAPI`: records calls and answers with what the test configured. */
class StubVocabApi(var storedSession: Boolean = true) : VocabApi {
    var logInError: ApiError? = null
    var deleteAccountError: ApiError? = null
    var signupResult = SignupResult(session = null, confirmationRequired = true)
    var deleteAccountCalls = 0
    var signOutCalls = 0
    var logInCalls = 0

    var profileResult: Result<UserProfile> = Result.success(Companion.profile())
    val levelUpdates = mutableListOf<CefrLevel>()
    val settingsUpdates = mutableListOf<SettingsUpdate>()
    val scopeUpdates = mutableListOf<LearningScope>()
    /** Defaults to the bundled catalog, like iOS `OfflineVocabAPI`. */
    var catalogCollections: List<CatalogCollection> = TopicCatalog.collections.map { collection ->
        CatalogCollection(
            id = collection.id, slug = collection.id, title = collection.name, description = collection.summary,
            topics = collection.topics.map { CatalogTopic(it.id, it.id, it.name) },
        )
    }
    var placementQuestions: Result<List<PlacementQuestion>> = Result.success(emptyList())
    var placementResults = ArrayDeque<Result<PlacementResult>>()
    val placementSubmissions = mutableListOf<List<PlacementAnswer>>()
    var learnHomeResult: Result<LearnHome> = Result.success(LearnHome(0, 0, 0, 7, 0))
    val acknowledgedBacklogs = mutableListOf<Int>()
    val overrides = mutableListOf<Pair<Int, NewWordOverrideMode>>()
    var overrideClears = 0
    val reviewPauses = mutableListOf<Pair<String, Boolean>>()
    /** Runs inside `placementTest()`, before it answers (to hold it open). */
    var onPlacementTest: suspend () -> Unit = {}

    override fun hasStoredSession() = storedSession
    override suspend fun signUp(email: String, password: String) = signupResult
    override suspend fun logIn(email: String, password: String) {
        logInCalls += 1
        logInError?.let { throw it }
    }
    override suspend fun logInWithGoogle(idToken: String, nonce: String?) {
        logInError?.let { throw it }
    }
    override suspend fun logInWithApple(idToken: String, nonce: String?) {
        logInError?.let { throw it }
    }
    override suspend fun requestPasswordReset(email: String) {}
    override suspend fun resetPassword(email: String, code: String, newPassword: String) {}
    override suspend fun changePassword(current: String, new: String) {}
    override suspend fun signOut() {
        signOutCalls += 1
    }
    override suspend fun deleteAccount() {
        deleteAccountCalls += 1
        deleteAccountError?.let { throw it }
    }

    override suspend fun profile(): UserProfile = profileResult.getOrThrow()
    override suspend fun updateLevels(level: CefrLevel) {
        levelUpdates += level
    }
    override suspend fun placementTest(): List<PlacementQuestion> {
        onPlacementTest()
        return placementQuestions.getOrThrow()
    }
    override suspend fun submitPlacementTest(answers: List<PlacementAnswer>): PlacementResult {
        placementSubmissions += answers
        return placementResults.removeFirst().getOrThrow()
    }
    override suspend fun updateSettings(settings: SettingsUpdate) {
        settingsUpdates += settings
    }
    override suspend fun updateScope(scope: LearningScope) {
        scopeUpdates += scope
    }
    override suspend fun catalog() = catalogCollections

    override suspend fun learnHome(): LearnHome = learnHomeResult.getOrThrow()
    override suspend fun acknowledgeReviewLoad(backlog: Int) {
        acknowledgedBacklogs += backlog
    }
    override suspend fun setNewWordOverride(value: Int, mode: NewWordOverrideMode, until: Instant?): NewWordOverride {
        overrides += value to mode
        return NewWordOverride(value, mode, until)
    }
    override suspend fun clearNewWordOverride() {
        overrideClears += 1
    }
    override suspend fun setReviewPaused(collectionId: String, paused: Boolean) {
        reviewPauses += collectionId to paused
    }

    var plan: Result<SessionPlan> = Result.failure(ApiError.Transport)
    val planRequests = mutableListOf<PlanRequest>()
    val commitResults = ArrayDeque<Result<CommitResponse>>()
    val commitBodies = mutableListOf<ByteArray>()
    var assessResult: Result<RecordingAssessment> = Result.failure(ApiError.Transport)
    var status: CommitStatus? = null

    /** iOS `StubVocabAPI.library`: pages are served from it, filtered by the lemma. */
    var library = mutableListOf<WordProgress>()
    var wordsError: ApiError? = null
    /** Runs inside [words] before it answers, to hold an answer back. */
    var onWords: (suspend () -> Unit)? = null

    override suspend fun words(limit: Int, offset: Int, query: String?): List<WordProgress> {
        onWords?.invoke()
        wordsError?.let { throw it }
        return library.filter { query.isNullOrEmpty() || it.lemma.contains(query) }.drop(offset).take(limit)
    }
    override suspend fun wordStats(): WordStats = WordStats(learnedWords = library.size, needPracticeWords = 0)
    override suspend fun wordDetail(id: String): WordDetail = throw ApiError.Transport

    override suspend fun planSession(request: PlanRequest): SessionPlan {
        planRequests += request
        return plan.getOrThrow()
    }
    override suspend fun assessRecording(sessionId: String, taskId: String, audio: RecordedAudio) = assessResult.getOrThrow()
    override suspend fun commitSession(sessionId: String, body: ByteArray): CommitResponse {
        commitBodies += body
        return commitResults.removeFirst().getOrThrow()
    }
    override suspend fun commitStatus(sessionId: String): CommitStatus = status ?: throw ApiError.Transport

    // Challenge
    var challengeMapResult: Result<ChallengeMap> = Result.failure(ApiError.Transport)
    var challengeMapCalls = 0
    val issueResults = ArrayDeque<Result<ChallengeRunPlan>>()
    val issuedRuns = mutableListOf<ChallengeNodeRef>()
    val startResults = ArrayDeque<Result<Unit>>()
    val startedRuns = mutableListOf<String>()
    val challengeCommitResults = ArrayDeque<Result<ChallengeCommitResponse>>()
    val challengeCommitBodies = mutableListOf<ByteArray>()
    var runStatus: Result<ChallengeRunStatus> = Result.failure(ApiError.Transport)
    val statusRequests = mutableListOf<String>()
    val quitRuns = mutableListOf<String>()

    override suspend fun challengeMap(): ChallengeMap {
        challengeMapCalls++
        return challengeMapResult.getOrThrow()
    }
    override suspend fun issueChallengeRun(chapterNo: Int, levelNo: Int): ChallengeRunPlan {
        issuedRuns += ChallengeNodeRef(chapterNo, levelNo)
        return issueResults.removeFirst().getOrThrow()
    }
    override suspend fun startChallengeRun(runId: String) {
        startedRuns += runId
        if (startResults.isNotEmpty()) startResults.removeFirst().getOrThrow()
    }
    override suspend fun commitChallengeRun(runId: String, body: ByteArray): ChallengeCommitResponse {
        challengeCommitBodies += body
        return challengeCommitResults.removeFirst().getOrThrow()
    }
    override suspend fun challengeRunStatus(runId: String): ChallengeRunStatus {
        statusRequests += runId
        return runStatus.getOrThrow()
    }
    override suspend fun quitChallengeRun(runId: String) {
        quitRuns += runId
    }

    // Ranking
    var rankingMetaResult: Result<RankingMeta> = Result.failure(ApiError.Transport)
    var myRankingResult: Result<MyRanking> = Result.failure(ApiError.Transport)
    var tierDetailResult: Result<TierDetail> = Result.failure(ApiError.Transport)
    val leaderboards = mutableMapOf<LeaderboardView, Result<Leaderboard>>()
    val leaderboardRequests = mutableListOf<LeaderboardView>()
    var introSeenCalls = 0
    var publicProfileResult: Result<PublicProfile> = Result.failure(ApiError.Transport)
    val publicProfileUpdates = mutableListOf<PublicProfileUpdate>()
    val reports = mutableListOf<Pair<String, ReportReason>>()

    override suspend fun rankingMeta() = rankingMetaResult.getOrThrow()
    override suspend fun myRanking() = myRankingResult.getOrThrow()
    override suspend fun tierDetail() = tierDetailResult.getOrThrow()
    override suspend fun leaderboard(view: LeaderboardView): Leaderboard {
        leaderboardRequests += view
        return (leaderboards[view] ?: Result.failure(ApiError.Transport)).getOrThrow()
    }
    override suspend fun rankingHistory(weeks: Int): RankingHistory = throw ApiError.Transport
    override suspend fun markRankingIntroSeen() {
        introSeenCalls++
    }
    override suspend fun updatePublicProfile(update: PublicProfileUpdate): PublicProfile {
        publicProfileUpdates += update
        return publicProfileResult.getOrThrow()
    }
    override suspend fun reportLearner(username: String, reason: ReportReason) {
        reports += username to reason
    }

    // Custom words
    val lookupResults = ArrayDeque<Result<CustomWordLookup>>()
    val lookups = mutableListOf<CustomWordQuery>()
    /** Runs while a lookup is in flight, before it answers (a sheet closing meanwhile). */
    var onLookup: (suspend () -> Unit)? = null
    val addResults = ArrayDeque<Result<CustomWordAdded>>()
    val additions = mutableListOf<CustomWordAddition>()
    var customWordList: List<CustomWord> = emptyList()
    var customWordsError: ApiError? = null
    val removed = mutableListOf<String>()
    var removeError: ApiError? = null
    val customWordOverrides = mutableListOf<Pair<String, CustomWordEdits>>()
    val clearedOverrides = mutableListOf<String>()

    override suspend fun lookupCustomWord(query: CustomWordQuery): CustomWordLookup {
        lookups += query
        onLookup?.invoke()
        return lookupResults.removeFirst().getOrThrow()
    }
    override suspend fun addCustomWord(addition: CustomWordAddition): CustomWordAdded {
        additions += addition
        return addResults.removeFirst().getOrThrow()
    }
    override suspend fun customWords(): List<CustomWord> {
        customWordsError?.let { throw it }
        return customWordList
    }
    override suspend fun removeCustomWord(meaningId: String) {
        removed += meaningId
        removeError?.let { throw it }
    }
    override suspend fun overrideCustomWord(meaningId: String, edits: CustomWordEdits) {
        customWordOverrides += meaningId to edits
    }
    override suspend fun clearCustomWordOverride(meaningId: String) {
        clearedOverrides += meaningId
    }

    /** Answers for `syncPlayStorePurchase`, in order; the last one repeats. */
    var syncResults: MutableList<Result<com.syll.network.PremiumPlan>> = mutableListOf(Result.failure(ApiError.Transport))
    val syncedPurchases = mutableListOf<String>()
    var redeemResult: Result<com.syll.network.PremiumPlan> = Result.failure(ApiError.Transport)

    override suspend fun syncPlayStorePurchase(purchaseToken: String, productId: String): com.syll.network.PremiumPlan {
        syncedPurchases += purchaseToken
        val result = if (syncResults.size > 1) syncResults.removeAt(0) else syncResults.first()
        return result.getOrThrow()
    }
    override suspend fun redeem(code: String): com.syll.network.PremiumPlan = redeemResult.getOrThrow()

    /** Answers `learningJourney(period)`, per period; a missing one fails offline. */
    val journeyResults = mutableMapOf<com.syll.domain.journey.JourneyPeriod, Result<com.syll.domain.journey.LearningJourney>>()
    val journeyPeriodsRequested = mutableListOf<com.syll.domain.journey.JourneyPeriod>()
    override suspend fun learningJourney(period: com.syll.domain.journey.JourneyPeriod): com.syll.domain.journey.LearningJourney {
        journeyPeriodsRequested += period
        return (journeyResults[period] ?: Result.failure(com.syll.network.ApiError.Transport)).getOrThrow()
    }

    /** Answers `learningJourneyWords`, one page per call, in order. */
    val journeyWordPages = ArrayDeque<com.syll.domain.journey.JourneyWordsPage>()
    val journeyWordOffsets = mutableListOf<Int>()
    override suspend fun learningJourneyWords(period: com.syll.domain.journey.JourneyPeriod, limit: Int, offset: Int): com.syll.domain.journey.JourneyWordsPage {
        journeyWordOffsets += offset
        return journeyWordPages.removeFirstOrNull() ?: throw com.syll.network.ApiError.Transport
    }

    /** Answers `submitContentReport`, in order; once used up, every send succeeds. */
    val contentReportResults = ArrayDeque<Result<com.syll.network.ContentReportReceipt>>()
    val contentReports = mutableListOf<com.syll.domain.report.ContentReportRequest>()
    override suspend fun submitContentReport(report: com.syll.domain.report.ContentReportRequest): com.syll.network.ContentReportReceipt {
        contentReports += report
        return contentReportResults.removeFirstOrNull()?.getOrThrow() ?: com.syll.network.ContentReportReceipt("r-${contentReports.size}")
    }

    companion object {
        fun profile(
            level: CefrLevel? = null,
            scope: LearningScope? = null,
            settings: UserSettings = UserSettings("vi", "en-US", "UTC", dailyNewWordGoal = 5),
        ) = UserProfile(id = "u1", proficiencyLevel = level, settings = settings, scope = scope)
    }
}
