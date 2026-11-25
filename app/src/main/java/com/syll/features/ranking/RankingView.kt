package com.syll.features.ranking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syll.R
import com.syll.app.LocalAppScope
import com.syll.app.LocalLearnerStore
import com.syll.app.LocalRankingStore
import com.syll.app.UserMessage
import com.syll.app.text
import com.syll.designsystem.components.CardDivider
import com.syll.designsystem.components.CardRow
import com.syll.designsystem.components.Eyebrow
import com.syll.designsystem.components.FieldInput
import com.syll.designsystem.components.FullScreenDialog
import com.syll.designsystem.components.IconBadge
import com.syll.designsystem.components.IconBadgeTone
import com.syll.designsystem.components.InfoPopoverButton
import com.syll.designsystem.components.LabeledField
import com.syll.designsystem.components.PinnedActionBar
import com.syll.designsystem.components.PrimaryButton
import com.syll.designsystem.components.ScreenTitle
import com.syll.designsystem.components.SecondaryButton
import com.syll.designsystem.components.StatusPill
import com.syll.designsystem.components.StatusPillTone
import com.syll.designsystem.components.cardSurface
import com.syll.designsystem.components.pressScaleClickable
import com.syll.designsystem.tokens.Borders
import com.syll.designsystem.tokens.Radii
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography
import com.syll.domain.ranking.LeaderboardRow
import com.syll.domain.ranking.LeaderboardView
import com.syll.domain.ranking.MyRanking
import com.syll.domain.ranking.PublicName
import com.syll.domain.ranking.RankingPositionKind
import com.syll.domain.ranking.ReportReason
import com.syll.network.asApiError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale

/**
 * iOS `RankingView`: the weekly leaderboard (spec §4, §10). This week's LP and rank stay apart from the lifetime Tier,
 * and nothing here pressures: no "falling behind", no timer on Home, no rank on the session results.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingView() {
    val ranking = LocalRankingStore.current
    val learner = LocalLearnerStore.current
    val scope = rememberCoroutineScope()
    var view by rememberSaveable { mutableStateOf(LeaderboardView.TOP) }
    var showsIntro by remember { mutableStateOf(false) }
    var isEditingProfile by remember { mutableStateOf(false) }
    var reportTarget by remember { mutableStateOf<LeaderboardRow?>(null) }
    var notice by remember { mutableStateOf<UserMessage?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    val isVisible = learner.profile?.settings?.leaderboardVisible ?: false

    LaunchedEffect(Unit) {
        ranking.refreshRanking()
        if (learner.profile?.rankingIntroSeen == false && !ranking.isBoardClosed) showsIntro = true
    }

    fun setVisible(visible: Boolean) {
        scope.launch {
            notice = try {
                ranking.setVisible(visible)
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UserMessage.Api(e.asApiError)
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                ranking.refreshRanking()
                isRefreshing = false
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenGutter)
                .padding(top = Spacing.l, bottom = Spacing.l),
        ) {
            ScreenTitle(stringResource(R.string.weekly_ranking))
            val mine = ranking.mine
            if (mine != null) {
                Text(weekSubtitle(mine), style = Typography.secondary, color = SyllColors.mutedInk, modifier = Modifier.padding(top = Spacing.xs - 2.dp))
                StatsCard(mine, Modifier.padding(top = Spacing.l))
            } else if (ranking.isLoading) {
                Box(Modifier.fillMaxWidth().padding(top = Spacing.l), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = SyllColors.mutedInk)
                }
            }
            ranking.error?.let { Message(it.text(), Modifier.padding(top = Spacing.l)) }
            if (ranking.isBoardClosed) {
                Message(stringResource(R.string.the_leaderboard_opens_soon_your_lp_and_tier_alre), Modifier.padding(top = Spacing.l))
            } else if (mine != null) {
                if (!isVisible) JoinCard(Modifier.padding(top = Spacing.l)) { setVisible(true) }
                Board(view, isVisible, onViewChange = { view = it }, Modifier.padding(top = Spacing.l))
            }
            notice?.let { Message(it.text(), Modifier.padding(top = Spacing.m)) }
        }
    }

    if (showsIntro) {
        RankingIntroSheet(
            onEditProfile = {
                showsIntro = false
                isEditingProfile = true
            },
            onDismiss = { showsIntro = false },
        )
    }
    if (isEditingProfile) EditPublicProfileSheet(onDismiss = { isEditingProfile = false })
    reportTarget?.let { row ->
        ReportPlayerSheet(
            username = row.username,
            reasons = ranking.reportReasons,
            onReport = { reason ->
                reportTarget = null
                scope.launch {
                    notice = try {
                        ranking.report(row.username, reason)
                        UserMessage.Resource(R.string.thanks_we_ll_take_a_look)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        UserMessage.Api(e.asApiError)
                    }
                }
            },
            onDismiss = { reportTarget = null },
        )
    }
}

/** "09–15/11 · Resets Monday, 07:00": the ISO week in UTC, the reset in device time. */
@Composable
private fun weekSubtitle(mine: MyRanking): String {
    val locale = LocalConfiguration.current.locales[0]
    val reset = DateTimeFormatter.ofPattern(
        android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEE jj:mm"),
        locale,
    ).format(mine.weekEndsAt.atZone(ZoneId.systemDefault()))
    val range = WeekLabel.range(mine.weekStart, locale)
    return if (range == null) stringResource(R.string.resets_x, reset) else stringResource(R.string.x_resets_x, range, reset)
}

@Composable
private fun StatsCard(mine: MyRanking, modifier: Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min).cardSurface()) {
        Column(
            Modifier.weight(1f).padding(Spacing.m).semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            PositionValue(mine)
            Text(
                stringResource(if (mine.position?.kind == RankingPositionKind.PERCENTILE) R.string.estimated else R.string.weekly_rank),
                style = Typography.micro.copy(fontWeight = FontWeight.SemiBold),
                color = SyllColors.mutedInk,
            )
        }
        Box(Modifier.width(Borders.hairline).fillMaxHeight().background(SyllColors.divider))
        Column(Modifier.weight(1f).padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs - 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bolt, null, tint = SyllColors.reward, modifier = Modifier.size(26.dp))
                Text(
                    NumberFormat.getIntegerInstance(locale).format(mine.weeklyLP),
                    style = Typography.stat.copy(letterSpacing = Typography.tightTracking.sp, fontFeatureSettings = "tnum"),
                    color = SyllColors.ink,
                    maxLines = 1,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.lp_this_week), style = Typography.micro.copy(fontWeight = FontWeight.SemiBold), color = SyllColors.mutedInk)
                InfoPopoverButton(
                    stringResource(R.string.you_earn_lp_by_learning_new_words_reviewing_word),
                    stringResource(R.string.about_lp),
                )
            }
        }
    }
}

@Composable
private fun PositionValue(mine: MyRanking) {
    val locale = LocalConfiguration.current.locales[0]
    val position = mine.position
    val rank = position?.rank
    val percentile = position?.percentile
    when {
        position?.kind == RankingPositionKind.RANK && rank != null -> Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text("#$rank", style = Typography.stat.copy(letterSpacing = Typography.tightTracking.sp, fontFeatureSettings = "tnum"), color = SyllColors.ink, maxLines = 1)
            val total = mine.totalListed
            if (total != null && total >= rank) {
                Text(
                    "/ $total",
                    style = Typography.emphasis.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                    color = SyllColors.mutedInk,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
        position?.kind == RankingPositionKind.PERCENTILE && percentile != null -> {
            val percent = NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = 0 }.format(percentile / 100.0)
            Text(
                stringResource(R.string.about_top_x, percent),
                style = Typography.screenTitle.copy(fontWeight = FontWeight.ExtraBold),
                color = SyllColors.ink,
                maxLines = 2,
            )
        }
        else -> {
            val label = stringResource(R.string.not_on_the_board)
            Text("–", style = Typography.stat, color = SyllColors.mutedInk, modifier = Modifier.clearAndSetSemantics { contentDescription = label })
        }
    }
}

@Composable
private fun Message(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = Typography.secondary,
        color = SyllColors.mutedInk,
        modifier = modifier.fillMaxWidth().background(SyllColors.neutralSurface, RoundedCornerShape(Radii.tile)).padding(Spacing.m),
    )
}

@Composable
private fun JoinCard(modifier: Modifier, onJoin: () -> Unit) {
    Column(modifier.cardSurface().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(stringResource(R.string.you_re_not_on_the_leaderboard), style = Typography.emphasis, color = SyllColors.ink)
        Text(stringResource(R.string.if_you_join_your_name_avatar_tier_and_lp_this_we), style = Typography.secondary, color = SyllColors.bodyInk)
        PrimaryButton(stringResource(R.string.join_the_leaderboard), onClick = onJoin, modifier = Modifier.padding(top = Spacing.xs))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Board(view: LeaderboardView, isVisible: Boolean, onViewChange: (LeaderboardView) -> Unit, modifier: Modifier) {
    val ranking = LocalRankingStore.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        // iOS uses its segmented control; Android's is the Material segmented button row.
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val options = listOf(LeaderboardView.TOP to R.string.all, LeaderboardView.AROUND to R.string.around_me)
            options.forEachIndexed { index, (value, title) ->
                SegmentedButton(
                    selected = view == value,
                    onClick = { onViewChange(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = SyllColors.selectedSurface,
                        activeContentColor = SyllColors.selectedInk,
                        inactiveContainerColor = SyllColors.surface,
                        inactiveContentColor = SyllColors.ink,
                    ),
                ) { Text(stringResource(title), style = Typography.secondary.copy(fontWeight = FontWeight.SemiBold)) }
            }
        }
        val rows = if (view == LeaderboardView.TOP) ranking.top else ranking.around
        if (rows.isEmpty()) {
            val empty = when {
                view == LeaderboardView.TOP -> R.string.nobody_is_on_the_board_yet_this_week
                isVisible -> R.string.earn_lp_this_week_to_appear_on_the_board
                else -> R.string.join_the_leaderboard_to_see_who_s_around_you
            }
            Text(stringResource(empty), style = Typography.secondary, color = SyllColors.mutedInk, modifier = Modifier.padding(vertical = Spacing.s))
        } else {
            Column(Modifier.cardSurface()) {
                rows.forEachIndexed { index, row ->
                    if (index > 0) {
                        if (skipsRanks(rows[index - 1], row)) {
                            CardDivider()
                            Text(
                                "···",
                                style = Typography.secondary.copy(fontWeight = FontWeight.Bold),
                                color = SyllColors.ink300,
                                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = Spacing.xl).padding(top = Spacing.xs).clearAndSetSemantics { },
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                        CardDivider()
                    }
                    // ⋯ (report) hidden until reporting works, as on iOS.
                    LeaderboardRowView(row)
                }
            }
        }
    }
}

/** Ranks that aren't next to each other (top three, then the learner's neighbours) get a "···" gap. */
private fun skipsRanks(previous: LeaderboardRow, row: LeaderboardRow): Boolean {
    val before = previous.rank ?: return false
    val rank = row.rank ?: return false
    return rank > before + 1
}

/** iOS `LeaderboardRowView`. [onMore] opens the report sheet; null for the learner's own row. */
@Composable
fun LeaderboardRowView(row: LeaderboardRow, onMore: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (row.isMe) SyllColors.selectedSurface else androidx.compose.ui.graphics.Color.Transparent)
            .defaultMinSize(minHeight = Spacing.tileMinHeight)
            .padding(horizontal = Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                row.rank?.toString() ?: "–",
                style = Typography.body.copy(fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"),
                color = if (row.rank == 1) SyllColors.sun600 else SyllColors.ink400,
                maxLines = 1,
                modifier = Modifier.width(Spacing.l + Spacing.xxs),
            )
            AvatarView(row.avatarKey, size = Spacing.iconBadge, initial = row.username)
            Text(
                row.username,
                style = Typography.body.copy(fontWeight = FontWeight.SemiBold),
                color = SyllColors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (row.isMe) StatusPill(stringResource(R.string.you), StatusPillTone.ACCENT_SOLID)
            Text(
                stringResource(R.string.x_lp, row.weeklyLP),
                style = Typography.secondary.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                color = SyllColors.ink,
                maxLines = 1,
            )
        }
        if (onMore != null) {
            val label = stringResource(R.string.actions_for_x, row.username)
            Box(
                Modifier.size(Spacing.l, Spacing.minTouchTarget).pressScaleClickable(onClick = onMore).semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                Text("⋯", style = Typography.secondary.copy(fontWeight = FontWeight.Bold), color = SyllColors.ink400)
            }
        }
    }
}

/** iOS `ReportPlayerSheet`: one reason per row, Cancel always visible. A Material bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportPlayerSheet(username: String, reasons: List<ReportReason>, onReport: (ReportReason) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = Radii.sheet, topEnd = Radii.sheet),
        containerColor = SyllColors.canvas,
    ) {
        Column(Modifier.padding(horizontal = Spacing.practiceGutter).padding(top = Spacing.s, bottom = Spacing.m)) {
            Box(Modifier.size(Spacing.fieldHeight).background(SyllColors.coral50, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Flag, null, tint = SyllColors.coral600)
            }
            Text(
                stringResource(R.string.report_x, username),
                style = Typography.screenTitle.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = Typography.tightTracking.sp),
                color = SyllColors.ink,
                modifier = Modifier.padding(top = Spacing.m).semantics { heading() },
            )
            Column(Modifier.padding(top = Spacing.l).cardSurface()) {
                reasons.forEachIndexed { index, reason ->
                    if (index > 0) CardDivider()
                    CardRow(Modifier.clickable { onReport(reason) }) {
                        Text(stringResource(reason.titleRes), style = Typography.body.copy(fontWeight = FontWeight.SemiBold), color = SyllColors.ink, modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SyllColors.ink400)
                    }
                }
            }
            SecondaryButton(stringResource(R.string.cancel), onClick = onDismiss, modifier = Modifier.padding(top = Spacing.m))
        }
    }
}

val ReportReason.titleRes: Int
    get() = when (this) {
        ReportReason.OFFENSIVE_NAME -> R.string.offensive_name
        ReportReason.IMPERSONATION -> R.string.pretending_to_be_someone_else
        ReportReason.SUSPICIOUS_ACTIVITY -> R.string.suspicious_activity
        ReportReason.OTHER -> R.string.something_else
    }

/** iOS `WeekLabel`: ranking weeks start Monday 00:00 UTC, so their dates read in UTC whatever the device zone. */
object WeekLabel {
    /** Monday to Sunday of the week, e.g. "09–15/11". Null when [weekStart] isn't a `yyyy-MM-dd` date. */
    fun range(
        weekStart: String,
        locale: Locale,
        pattern: () -> String = { android.text.format.DateFormat.getBestDateTimePattern(locale, "ddMM") },
    ): String? {
        val start = try {
            LocalDate.parse(weekStart)
        } catch (e: DateTimeParseException) {
            return null
        }
        val end = start.plusDays(6)
        val formatter = DateTimeFormatter.ofPattern(pattern(), locale).withZone(ZoneOffset.UTC)
        return "${formatter.format(start)}–${formatter.format(end)}"
    }
}

/** iOS `RankingIntroSheet`: what the leaderboard is and what becomes public. Seeing it never opts in (spec §4.3). */
@Composable
fun RankingIntroSheet(onEditProfile: () -> Unit, onDismiss: () -> Unit) {
    val ranking = LocalRankingStore.current
    val learner = LocalLearnerStore.current
    val appScope = LocalAppScope.current
    var error by remember { mutableStateOf<UserMessage?>(null) }
    var isJoining by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { ranking.markIntroSeen() }

    fun join() {
        isJoining = true
        appScope.launch {
            try {
                ranking.setVisible(true)
                onDismiss()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = UserMessage.Api(e.asApiError)
            } finally {
                isJoining = false
            }
        }
    }

    FullScreenDialog(
        title = stringResource(R.string.weekly_leaderboard),
        onDismiss = onDismiss,
        bottomBar = {
            PinnedActionBar {
                PrimaryButton(stringResource(R.string.join), onClick = ::join, enabled = !isJoining)
                SecondaryButton(stringResource(R.string.edit_name_and_avatar), onClick = onEditProfile)
                SecondaryButton(stringResource(R.string.not_now), onClick = onDismiss)
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenGutter)
                .padding(top = Spacing.s, bottom = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(stringResource(R.string.compare_the_lp_you_earn_this_week_with_other_lea), style = Typography.body, color = SyllColors.ink)
            val profile = learner.profile
            val username = profile?.username
            if (username != null) {
                Row(
                    Modifier.cardSurface().padding(Spacing.m),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AvatarView(profile.avatarKey.orEmpty(), size = Spacing.fieldHeight, initial = username)
                    Text(username, style = Typography.emphasis, color = SyllColors.ink)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Icon(Icons.Outlined.Visibility, null, tint = SyllColors.mutedInk, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.if_you_join_your_name_avatar_tier_and_lp_this_we_2), style = Typography.secondary, color = SyllColors.mutedInk)
            }
            error?.let { Text(it.text(), style = Typography.secondary, color = SyllColors.errorInk) }
        }
    }
}

/** iOS `EditPublicProfileSheet`: username and avatar shown on the leaderboard. Never derived from the email. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditPublicProfileSheet(onDismiss: () -> Unit) {
    val ranking = LocalRankingStore.current
    val learner = LocalLearnerStore.current
    val appScope = LocalAppScope.current
    val profile = learner.profile
    var username by rememberSaveable { mutableStateOf(profile?.username.orEmpty()) }
    var avatarKey by rememberSaveable { mutableStateOf(profile?.avatarKey.orEmpty()) }
    var error by remember { mutableStateOf<UserMessage?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]

    LaunchedEffect(Unit) { ranking.loadMeta() }

    val canonicalName = username.trim().lowercase()
    val nameChanged = canonicalName != profile?.username
    val nameLocked = profile?.usernameChangeAvailableAt != null
    val canSave = !isSaving && if (nameChanged) {
        !nameLocked && PublicName.isWellFormed(canonicalName)
    } else {
        avatarKey.isNotEmpty() && avatarKey != profile?.avatarKey
    }

    fun save() {
        if (!canSave) return
        isSaving = true
        error = null
        val name = if (nameChanged) canonicalName else null
        val avatar = avatarKey.takeIf { it != profile?.avatarKey }
        appScope.launch {
            try {
                ranking.updateProfile(name, avatar)
                onDismiss()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = UserMessage.Api(e.asApiError)
            } finally {
                isSaving = false
            }
        }
    }

    FullScreenDialog(
        title = stringResource(R.string.your_public_profile),
        onDismiss = onDismiss,
        bottomBar = {
            PinnedActionBar {
                if (isSaving) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = SyllColors.mutedInk, strokeWidth = 2.5.dp)
                    }
                }
                PrimaryButton(stringResource(R.string.save), onClick = ::save, enabled = canSave)
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenGutter)
                .padding(top = Spacing.s, bottom = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(stringResource(R.string.only_shown_on_the_leaderboard_if_you_join_it_you), style = Typography.secondary, color = SyllColors.mutedInk)
            LabeledField(stringResource(R.string.name), isFocused = isFocused) {
                FieldInput(
                    value = username,
                    onValueChange = { username = it },
                    placeholder = stringResource(R.string.name),
                    onFocusChange = { isFocused = it },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done,
                    ),
                    onSubmit = ::save,
                )
            }
            val changeAt = profile?.usernameChangeAvailableAt
            Text(
                if (changeAt != null) {
                    val date = DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "dMMMM"), locale)
                        .format(changeAt.atZone(ZoneId.systemDefault()))
                    stringResource(R.string.you_can_change_your_name_again_on_x, date)
                } else {
                    stringResource(R.string.s_3_20_letters_numbers_or_starting_with_a_letter)
                },
                style = Typography.micro,
                color = SyllColors.mutedInk,
            )
            Eyebrow(stringResource(R.string.avatar).uppercase(), Modifier.padding(top = Spacing.xs))
            val keys = ranking.avatarKeys.ifEmpty { listOf(avatarKey).filter { it.isNotEmpty() } }
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
                maxItemsInEachRow = 4,
            ) {
                keys.forEachIndexed { index, key ->
                    val label = stringResource(R.string.avatar_x, index + 1)
                    val isSelected = key == avatarKey
                    Box(
                        Modifier
                            .weight(1f)
                            .pressScaleClickable { avatarKey = key }
                            .semantics {
                                contentDescription = label
                                selected = isSelected
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        AvatarView(
                            key,
                            size = 56.dp,
                            modifier = Modifier
                                .border(3.dp, if (isSelected) SyllColors.interactive else androidx.compose.ui.graphics.Color.Transparent, CircleShape)
                                .padding(Spacing.xxs),
                        )
                    }
                }
                // Keeps the last row's avatars on the grid.
                repeat((4 - keys.size % 4) % 4) { Box(Modifier.weight(1f)) }
            }
            error?.let { Text(it.text(), style = Typography.secondary, color = SyllColors.errorInk) }
        }
    }
}
