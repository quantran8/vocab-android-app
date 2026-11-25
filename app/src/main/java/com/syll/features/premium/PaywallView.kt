package com.syll.features.premium

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.syll.BuildConfig
import com.syll.R
import com.syll.app.LocalAppScope
import com.syll.app.LocalLearnerStore
import com.syll.app.LocalPurchaseStore
import com.syll.app.text
import com.syll.designsystem.components.ActionButtonKind
import com.syll.designsystem.components.BackButton
import com.syll.designsystem.components.CardDivider
import com.syll.designsystem.components.FieldErrorText
import com.syll.designsystem.components.PremiumGradient
import com.syll.designsystem.components.PremiumWash
import com.syll.designsystem.components.PremiumWord
import com.syll.designsystem.components.PrimaryButton
import com.syll.designsystem.motion.LocalMotion
import com.syll.designsystem.motion.MotionSpec
import com.syll.designsystem.motion.MotionTokens
import com.syll.designsystem.tokens.Borders
import com.syll.designsystem.tokens.Radii
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography
import com.syll.domain.premium.PremiumOffer
import com.syll.domain.premium.paywallOrder
import com.syll.domain.premium.yearlySavingPercent
import kotlinx.coroutines.launch

/** iOS `PaywallReason`: why the paywall opened; it picks the subtitle, so the learner sees the limit they just met. */
sealed interface PaywallReason {
    data object General : PaywallReason
    /** Tried to choose more topics than the free plan allows. */
    data class Topics(val max: Int) : PaywallReason
    /** Tried to switch collection: the free plan keeps the first one saved (`FREE_COLLECTION_LOCKED`). */
    data object Collection : PaywallReason
    /** The free plan's new words are used up. */
    data class Words(val max: Int) : PaywallReason
    /** A free take was checked on the device, without a pronunciation score. */
    data object Speaking : PaywallReason
}

/** iOS `PremiumLinks`: the subscription's terms and privacy policy (`-PtermsUrl`, `-PprivacyPolicyUrl`); empty leaves a link out. */
object PremiumLinks {
    val terms: String? = BuildConfig.TERMS_URL.ifEmpty { null }
    val privacy: String? = BuildConfig.PRIVACY_POLICY_URL.ifEmpty { null }

    /** Google Play's subscription centre, on [productId]'s page when there is one. */
    fun manage(productId: String?, packageName: String): String =
        "https://play.google.com/store/account/subscriptions" + (productId?.let { "?sku=$it&package=$packageName" } ?: "")
}

private enum class PaywallStep { COMPARE, PLANS }

/**
 * iOS `PaywallView`: Premium in two steps (redesign v1, 6.1 and 6.2). Step 1 compares Free and Premium (the Premium
 * column a flat Grape block); step 2 shows both plans as cards. A full-screen dialog (Android's form of the iOS
 * `.large` sheet): ✕ at the top right, the back arrow on step 2, the Sun Premium button pinned at the bottom. Prices
 * and the trial come from Google Play. It closes itself once the server says the learner is Premium.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallView(reason: PaywallReason, onDismiss: () -> Unit) {
    val purchases = LocalPurchaseStore.current
    val learner = LocalLearnerStore.current
    val appScope = LocalAppScope.current
    val motion = LocalMotion.current
    var step by rememberSaveable { mutableStateOf(PaywallStep.COMPARE) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var isRedeeming by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        purchases.clearNotice()
        purchases.loadOffers()
        if (selectedId == null) {
            selectedId = (purchases.offers.firstOrNull { it.period == PremiumOffer.Period.YEAR } ?: purchases.offers.firstOrNull())?.id
        }
    }
    LaunchedEffect(purchases.didUnlock) {
        if (purchases.didUnlock) {
            purchases.acknowledgeUnlock()
            onDismiss()
        }
    }
    LaunchedEffect(learner.isPremium) {
        if (learner.isPremium) onDismiss()
    }
    val selectedOffer = purchases.offers.firstOrNull { it.id == selectedId } ?: purchases.offers.firstOrNull()

    Dialog(
        onDismissRequest = { if (step == PaywallStep.PLANS) step = PaywallStep.COMPARE else onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(SyllColors.surface)) {
            PremiumWash()
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    TopAppBar(
                        title = {},
                        navigationIcon = { if (step == PaywallStep.PLANS) BackButton({ step = PaywallStep.COMPARE }) },
                        actions = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.close), tint = SyllColors.ink)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
                    )
                },
                bottomBar = {
                    // Only the main action is pinned: the Sun Premium button. Restore and redeem sit in the plan step.
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(SyllColors.surface)
                            .navigationBarsPadding(),
                    ) {
                        CardDivider()
                        Column(
                            Modifier.padding(horizontal = Spacing.screenGutter).padding(top = Spacing.s, bottom = Spacing.xs),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            when (step) {
                                PaywallStep.COMPARE -> PrimaryButton(
                                    stringResource(R.string.see_plans),
                                    onClick = { step = PaywallStep.PLANS },
                                    icon = Icons.Filled.WorkspacePremium,
                                    kind = ActionButtonKind.PREMIUM,
                                )
                                PaywallStep.PLANS -> {
                                    val busy = purchases.activity != PurchaseStore.Activity.Idle &&
                                        purchases.activity != PurchaseStore.Activity.LoadingOffers
                                    if (busy) CircularProgressIndicator(Modifier.size(24.dp), color = SyllColors.mutedInk, strokeWidth = 2.5.dp)
                                    PrimaryButton(
                                        stringResource(if (selectedOffer?.freeTrialPeriod == null) R.string.subscribe else R.string.start_free_trial),
                                        onClick = { selectedOffer?.let { offer -> appScope.launch { purchases.purchase(offer) } } },
                                        icon = Icons.Filled.WorkspacePremium,
                                        enabled = selectedOffer != null && purchases.activity == PurchaseStore.Activity.Idle,
                                        kind = ActionButtonKind.PREMIUM,
                                    )
                                }
                            }
                        }
                    }
                },
            ) { padding ->
                AnimatedContent(
                    step,
                    modifier = Modifier.padding(padding),
                    transitionSpec = { motion.stepTransition(isMovingBack = targetState == PaywallStep.COMPARE) },
                    label = "paywallStep",
                ) { shown ->
                    when (shown) {
                        PaywallStep.COMPARE -> CompareStep(reason)
                        PaywallStep.PLANS -> PlansStep(selectedOffer?.id, onSelect = { selectedId = it }, onRedeem = { isRedeeming = true })
                    }
                }
            }
        }
    }
    if (isRedeeming) RedeemCodeSheet(onDismiss = { isRedeeming = false })
}

// Key visual

/**
 * A fan of vocabulary cards, no Syllie: three solid cards (Grape 500, Coral 400, Sun 400, each on its 700 edge)
 * tilted behind a white word card ("unlimited", its IPA and meaning), with two small Sun sparkles. The cards spread
 * out when the paywall opens; with reduced motion they are already spread.
 */
@Composable
private fun PremiumKeyVisual(compact: Boolean = false) {
    val motion = LocalMotion.current
    var spread by remember { mutableStateOf(motion.reduceMotion) }
    LaunchedEffect(Unit) { spread = true }
    val amount by animateFloatAsState(
        if (spread) 1f else 0.15f,
        motion.animation(MotionSpec.Spring(0.55, 0.7)),
        label = "keyVisualSpread",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (compact) 132.dp else 150.dp)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.requiredSize(260.dp, 170.dp).scale(if (compact) 0.72f else 0.8f), contentAlignment = Alignment.Center) {
            BackCard(SyllColors.grape500, SyllColors.grape700, -16f, -44, 6, amount)
            BackCard(SyllColors.coral400, SyllColors.coral700, 13f, 46, 4, amount)
            BackCard(SyllColors.sun400, SyllColors.sun700, -5f, -10, -10, amount)
            WordCard()
            Icon(Icons.Filled.AutoAwesome, null, tint = SyllColors.sun500, modifier = Modifier.offset(108.dp, (-66).dp).size(22.dp))
            Icon(Icons.Filled.AutoAwesome, null, tint = SyllColors.sun500, modifier = Modifier.offset((-104).dp, 60.dp).size(14.dp))
        }
    }
}

@Composable
private fun BackCard(face: Color, edge: Color, rotation: Float, x: Int, y: Int, amount: Float) {
    val shape = RoundedCornerShape(Radii.card)
    Box(
        Modifier
            .offset((x * amount).dp, (y * amount).dp)
            .rotate(rotation * amount)
            .size(168.dp, 113.dp),
    ) {
        Box(Modifier.offset(y = 5.dp).size(168.dp, 108.dp).background(edge, shape))
        Box(Modifier.size(168.dp, 108.dp).background(face, shape))
    }
}

@Composable
private fun WordCard() {
    val shape = RoundedCornerShape(Radii.card)
    Box(Modifier.offset(y = 6.dp).rotate(3f)) {
        // The Stroke edge, 5 dp under the card.
        Box(Modifier.matchParentSize().offset(y = 5.dp).background(SyllColors.stroke, shape))
        Column(
            Modifier
                .width(176.dp)
                .background(SyllColors.surface, shape)
                .border(Borders.card, SyllColors.stroke, shape)
                .padding(horizontal = Spacing.m, vertical = Spacing.s + 2.dp),
        ) {
            Text("unlimited", style = Typography.body.copy(fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp), color = SyllColors.ink)
            Text("/ʌnˈlɪmɪtɪd/", style = Typography.secondary.copy(fontSize = 13.sp), color = SyllColors.mutedInk, modifier = Modifier.padding(top = Spacing.xxs))
            CardDivider(Modifier.padding(top = Spacing.s - 2.dp))
            Text(
                stringResource(R.string.without_limits),
                style = Typography.secondary.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                color = SyllColors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

/** A benefit Premium (or Free) includes: a flat Grape 500 disc with a white check. A missing one: a white ✕ on Ink 200. */
@Composable
private fun BenefitMark(included: Boolean) {
    if (included) {
        Box(Modifier.size(28.dp).background(SyllColors.grape500, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    } else {
        Box(Modifier.size(24.dp).background(SyllColors.ink200, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(13.dp))
        }
    }
}

// Step 1: Free vs Premium

private class BenefitRow(val title: String, val detail: String?, val free: Boolean, val isReason: Boolean = false)

private val rowHeight = 54.dp
private val freeWidth = 78.dp
private val premiumWidth = 96.dp
/** The Grape band around the Premium column's white inside. */
private val band = 7.dp

@Composable
private fun CompareStep(reason: PaywallReason) {
    val learner = LocalLearnerStore.current
    val purchases = LocalPurchaseStore.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenGutter)
            .padding(bottom = Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PremiumKeyVisual()
        Text(
            "SYLL PREMIUM",
            style = Typography.eyebrow.copy(letterSpacing = Typography.eyebrowTracking.sp),
            color = SyllColors.grape700,
        )
        val headline = stringResource(R.string.learn_without_limits_with)
        Column(
            Modifier.padding(top = Spacing.xs - 2.dp).semantics(mergeDescendants = true) { heading() },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val style = Typography.display.copy(letterSpacing = Typography.displayTracking.sp, textAlign = TextAlign.Center)
            Text(headline, style = style, color = SyllColors.ink)
            PremiumWord(style)
        }
        reasonLine(reason)?.let {
            Text(
                it,
                style = Typography.body,
                color = SyllColors.bodyInk,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        BenefitTable(rows(reason, learner.freeLimits?.maxNewWords, learner.freeLimits?.maxTopics, learner.profile?.features?.adEverySessions ?: 0), Modifier.padding(top = Spacing.m))
        purchases.notice?.let { FieldErrorText(it.text(), Modifier.padding(top = Spacing.s)) }
    }
}

/** The limit that opened the paywall; nothing for a general upgrade. */
@Composable
private fun reasonLine(reason: PaywallReason): String? = when (reason) {
    PaywallReason.General -> null
    is PaywallReason.Topics -> stringResource(R.string.the_free_plan_includes_x_topics_upgrade_to_learn, reason.max)
    PaywallReason.Collection -> stringResource(R.string.the_free_plan_keeps_the_first_collection_you_cho_2)
    is PaywallReason.Words -> stringResource(R.string.you_ve_learned_your_x_free_words_upgrade_to_keep, reason.max)
    PaywallReason.Speaking -> stringResource(R.string.upgrade_to_get_a_pronunciation_score_and_see_how)
}

/** From what Free has to what it doesn't (✓ before ✕); among the ✕ rows the limit just met leads, in bold with its free amount. */
@Composable
private fun rows(reason: PaywallReason, maxNewWords: Int?, maxTopics: Int?, adEverySessions: Int): List<BenefitRow> {
    val rows = buildList {
        add(BenefitRow(stringResource(R.string.daily_lessons_and_reviews), null, free = true))
        add(
            BenefitRow(
                stringResource(R.string.unlimited_new_words),
                maxNewWords?.let { stringResource(R.string.free_x_words, it) },
                free = false,
                isReason = reason is PaywallReason.Words,
            ),
        )
        // Scope: the free plan keeps one collection and `maxTopics` of its topics.
        add(
            BenefitRow(
                stringResource(R.string.every_collection_and_topic),
                maxTopics?.let { stringResource(R.string.free_1_collection_x_topics, it) },
                free = false,
                isReason = reason is PaywallReason.Topics || reason == PaywallReason.Collection,
            ),
        )
        add(BenefitRow(stringResource(R.string.pronunciation_score), stringResource(R.string.for_every_syllable), free = false, isReason = reason == PaywallReason.Speaking))
        if (adEverySessions > 0) add(BenefitRow(stringResource(R.string.no_ads), null, free = false))
    }
    return rows.filter { it.free } + rows.filter { !it.free && it.isReason } + rows.filter { !it.free && !it.isReason }
}

@Composable
private fun BenefitTable(rows: List<BenefitRow>, modifier: Modifier = Modifier) {
    val summary = stringResource(R.string.premium_includes_every_benefit_in_the_list_the_f)
    Row(
        modifier
            .fillMaxWidth()
            .padding(bottom = band)
            .clearAndSetSemantics { contentDescription = summary },
        verticalAlignment = Alignment.Bottom,
    ) {
        // Benefits
        Column(Modifier.weight(1f)) {
            Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).padding(bottom = Spacing.xs), contentAlignment = Alignment.BottomStart) {
                Text(stringResource(R.string.benefits), style = Typography.body.copy(fontWeight = FontWeight.ExtraBold), color = SyllColors.ink)
            }
            rows.forEach { row ->
                Column(Modifier.fillMaxWidth().height(rowHeight)) {
                    CardDivider()
                    Column(Modifier.fillMaxSize().padding(end = Spacing.xs), verticalArrangement = Arrangement.Center) {
                        Text(
                            row.title,
                            style = Typography.secondary.copy(fontWeight = if (row.isReason) FontWeight.Bold else FontWeight.Normal),
                            color = SyllColors.ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (row.isReason && row.detail != null) {
                            Text(row.detail, style = Typography.caption, color = SyllColors.mutedInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        // Free: an Ink 50 strip, a little shorter than Premium.
        Column(
            Modifier
                .width(freeWidth)
                .background(SyllColors.ink50, RoundedCornerShape(topStart = Radii.tile + 2.dp, topEnd = Radii.tile + 2.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 40.dp).padding(bottom = Spacing.xs), contentAlignment = Alignment.BottomCenter) {
                Text(stringResource(R.string.free), style = Typography.secondary.copy(fontWeight = FontWeight.Bold), color = SyllColors.mutedInk, maxLines = 1)
            }
            rows.forEach { row ->
                Column(Modifier.fillMaxWidth().height(rowHeight)) {
                    Box(Modifier.fillMaxWidth().height(Borders.hairline).background(SyllColors.surface))
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BenefitMark(row.free) }
                }
            }
        }

        // Premium: a taller flat Grape 500 block, its title in white on the band, a white inside for the checks.
        Column(
            Modifier
                .width(premiumWidth)
                .offset(y = band)
                .background(SyllColors.grape500, RoundedCornerShape(Radii.card)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 54.dp).padding(bottom = Spacing.s - 2.dp), contentAlignment = Alignment.BottomCenter) {
                Text("Premium", style = Typography.body.copy(fontWeight = FontWeight.ExtraBold), color = Color.White, maxLines = 1)
            }
            Column(
                Modifier
                    .padding(start = band, end = band, bottom = band)
                    .background(SyllColors.surface, RoundedCornerShape(Radii.card - band)),
            ) {
                rows.forEachIndexed { index, _ ->
                    Column(Modifier.fillMaxWidth().height(rowHeight)) {
                        if (index > 0) CardDivider()
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BenefitMark(true) }
                    }
                }
            }
        }
    }
}

// Step 2: plans

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlansStep(selectedId: String?, onSelect: (String) -> Unit, onRedeem: () -> Unit) {
    val purchases = LocalPurchaseStore.current
    val learner = LocalLearnerStore.current
    val appScope = LocalAppScope.current
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenGutter)
            .padding(bottom = Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PremiumKeyVisual(compact = true)
        Text(
            choosePlanTitle(stringResource(R.string.choose_your_x_plan)),
            style = Typography.display.copy(letterSpacing = Typography.displayTracking.sp),
            color = SyllColors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.xxs).semantics { heading() },
        )
        Text(
            stringResource(R.string.learn_without_limits_cancel_anytime),
            style = Typography.body,
            color = SyllColors.bodyInk,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.xs),
        )

        val offers = purchases.offers
        if (offers.isEmpty()) {
            if (purchases.activity == PurchaseStore.Activity.LoadingOffers) {
                Box(Modifier.fillMaxWidth().height(Spacing.rowMinHeight * 2), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), color = SyllColors.mutedInk, strokeWidth = 2.5.dp)
                }
            }
        } else {
            val selected = offers.firstOrNull { it.id == selectedId } ?: offers.first()
            Column(Modifier.padding(top = Spacing.l - 4.dp), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                offers.paywallOrder().forEach { offer ->
                    PlanCard(offer, offers, isOn = offer.id == selected.id, onClick = { onSelect(offer.id) })
                }
            }
        }

        purchases.notice?.let { FieldErrorText(it.text(), Modifier.padding(top = Spacing.s)) }

        Column(Modifier.padding(top = Spacing.l - 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(
                stringResource(R.string.subscriptions_renew_automatically_google_play),
                style = Typography.caption,
                color = SyllColors.mutedInk,
                textAlign = TextAlign.Center,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.m, Alignment.CenterHorizontally)) {
                LegalLink(stringResource(R.string.restore_purchases), enabled = purchases.activity == PurchaseStore.Activity.Idle) {
                    appScope.launch { purchases.restore() }
                }
                if (learner.canRedeem) LegalLink(stringResource(R.string.have_a_code), onClick = onRedeem)
                PremiumLinks.terms?.let { url -> LegalLink(stringResource(R.string.terms_of_use)) { context.openUrl(url) } }
                PremiumLinks.privacy?.let { url -> LegalLink(stringResource(R.string.privacy_policy)) { context.openUrl(url) } }
            }
        }
    }
}

/** "Choose your Premium plan" with "Premium" in the signature gradient. */
private fun choosePlanTitle(template: String): AnnotatedString {
    val parts = template.split("%1\$s", limit = 2)
    return buildAnnotatedString {
        append(parts[0])
        withStyle(SpanStyle(brush = PremiumGradient.accent)) { append("Premium") }
        if (parts.size > 1) append(parts[1])
    }
}

@Composable
private fun LegalLink(title: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        title,
        style = Typography.caption.copy(fontWeight = FontWeight.SemiBold),
        color = SyllColors.link,
        modifier = Modifier
            .defaultMinSize(minHeight = Spacing.minTouchTarget)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = enabled, onClick = onClick)
            .padding(vertical = Spacing.s),
    )
}

fun android.content.Context.openUrl(url: String) {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/**
 * The chosen plan: Grape 50 with a 2 dp Grape 500 border and a Grape check. The other: white, Stroke, an empty ring.
 * The yearly card carries the real saving as a Coral pill over its top edge.
 */
@Composable
private fun PlanCard(offer: PremiumOffer, offers: List<PremiumOffer>, isOn: Boolean, onClick: () -> Unit) {
    val motion = LocalMotion.current
    val locale = LocalConfiguration.current.locales[0]
    val shape = RoundedCornerShape(Radii.card)
    val monthly = offers.firstOrNull { it.period == PremiumOffer.Period.MONTH }
    val saving = if (offer.period == PremiumOffer.Period.YEAR) offers.yearlySavingPercent else null
    val fill by androidx.compose.animation.animateColorAsState(
        if (isOn) SyllColors.grape50 else SyllColors.surface,
        motion.animation(MotionTokens.snap),
        label = "planFill",
    )
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .background(fill, shape)
                .border(Borders.card, if (isOn) SyllColors.grape500 else SyllColors.stroke, shape)
                .selectable(selected = isOn, onClick = onClick)
                .padding(horizontal = Spacing.m, vertical = Spacing.s + 2.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (offer.period) {
                        PremiumOffer.Period.YEAR -> stringResource(R.string.yearly)
                        PremiumOffer.Period.MONTH -> stringResource(R.string.monthly)
                        PremiumOffer.Period.OTHER -> offer.displayName
                    },
                    style = Typography.wordClass.copy(fontWeight = FontWeight.Bold),
                    color = SyllColors.ink,
                    modifier = Modifier.weight(1f),
                )
                if (isOn) {
                    BenefitMark(true)
                } else {
                    Box(Modifier.size(28.dp).border(Borders.card, SyllColors.ink300, CircleShape))
                }
            }
            when (offer.period) {
                PremiumOffer.Period.YEAR -> {
                    PriceLine(PremiumText.monthlyPrice(offer, locale))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs - 2.dp)) {
                        Text(stringResource(R.string.s_12_months_x, offer.displayPrice), style = Typography.secondary, color = SyllColors.ink)
                        if (saving != null && monthly != null) {
                            Text(
                                PremiumText.price(monthly.priceMicros * 12, monthly.currencyCode, locale),
                                style = Typography.secondary.copy(textDecoration = TextDecoration.LineThrough),
                                color = SyllColors.ink400,
                            )
                        }
                    }
                }
                PremiumOffer.Period.MONTH -> {
                    PriceLine(offer.displayPrice)
                    Text(stringResource(R.string.billed_monthly), style = Typography.secondary, color = SyllColors.mutedInk)
                }
                PremiumOffer.Period.OTHER -> Text(offer.displayPrice, style = Typography.body.copy(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold), color = SyllColors.ink)
            }
            offer.freeTrialPeriod?.let { PremiumText.trialLength(it, locale) }?.let { trial ->
                Text(
                    stringResource(R.string.x_free_then_x, trial, offer.displayPrice),
                    style = Typography.micro.copy(fontWeight = FontWeight.Bold),
                    color = SyllColors.grape800,
                    modifier = Modifier
                        .padding(top = Spacing.xs - 2.dp)
                        .background(SyllColors.grape100, CircleShape)
                        .padding(horizontal = Spacing.s - 2.dp, vertical = Spacing.xxs),
                )
            }
        }
        if (saving != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = -(Spacing.xl + Spacing.l), y = (-14).dp)
                    .height(28.dp)
                    .widthIn(min = 28.dp)
                    .background(SyllColors.coral500, CircleShape)
                    .padding(horizontal = Spacing.s),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.save_x_x, saving), style = Typography.micro.copy(fontWeight = FontWeight.Bold), color = Color.White)
            }
        }
    }
}

/** The price per month, large, with a smaller muted "/ month". */
@Composable
private fun PriceLine(price: String) {
    val template = stringResource(R.string.x_month, "\u0000")
    val parts = template.split("\u0000", limit = 2)
    Text(
        buildAnnotatedString {
            append(parts[0])
            withStyle(SpanStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp, color = SyllColors.ink)) { append(price) }
            if (parts.size > 1) append(parts[1])
        },
        style = Typography.body.copy(fontWeight = FontWeight.SemiBold),
        color = SyllColors.mutedInk,
    )
}
