package com.syll.features.premium

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syll.R
import com.syll.app.LocalAppScope
import com.syll.app.LocalLearnerStore
import com.syll.app.LocalPurchaseStore
import com.syll.app.text
import com.syll.designsystem.components.ActionButtonKind
import com.syll.designsystem.components.CardDivider
import com.syll.designsystem.components.CardRow
import com.syll.designsystem.components.PremiumBadge
import com.syll.designsystem.components.PremiumGradient
import com.syll.designsystem.components.PrimaryButton
import com.syll.designsystem.components.ProgressTrack
import com.syll.designsystem.components.SecondaryButton
import com.syll.designsystem.components.TextLink
import com.syll.designsystem.components.cardSurface
import com.syll.designsystem.components.pressScaleClickable
import com.syll.designsystem.tokens.Borders
import com.syll.designsystem.tokens.Radii
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography
import com.syll.domain.premium.PremiumOffer
import com.syll.domain.premium.PremiumProduct
import com.syll.network.PremiumPlan
import kotlinx.coroutines.launch

/**
 * iOS `ProfileUpgradeCard` (Profile 6.6): the upgrade card under the account block while the learner isn't Premium.
 * The paywall's wash, a 2 dp Grape 200 border, the Premium badge, one line of benefits, the free words used on a
 * Grape bar and the Sun Premium button (its one action).
 */
@Composable
fun ProfileUpgradeCard(limits: PremiumPlan.Limits?, onUpgrade: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Radii.card)
    Column(
        modifier
            .fillMaxWidth()
            .background(PremiumGradient.wash, shape)
            .border(Borders.card, SyllColors.grape200, shape)
            .padding(Spacing.m),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s - 2.dp), verticalAlignment = Alignment.CenterVertically) {
            PremiumBadge(size = 34.dp)
            Text(
                stringResource(R.string.upgrade_to_premium),
                style = Typography.wordClass.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (Typography.tightTracking / 2).sp),
                color = SyllColors.ink,
                modifier = Modifier.semantics { heading() },
            )
        }
        Text(
            stringResource(R.string.unlimited_new_words_every_topic_a_score_for_ever),
            style = Typography.secondary,
            color = SyllColors.ink700,
            modifier = Modifier.padding(top = Spacing.xs - 2.dp),
        )
        if (limits != null) {
            Text(
                stringResource(R.string.x_of_x_free_words_used, limits.newWordsUsed, limits.maxNewWords),
                style = Typography.micro.copy(fontWeight = FontWeight.SemiBold),
                color = SyllColors.ink700,
                modifier = Modifier.padding(top = Spacing.s),
            )
            val fraction = if (limits.maxNewWords > 0) (limits.newWordsUsed.toFloat() / limits.maxNewWords).coerceIn(0f, 1f) else 0f
            ProgressTrack(fraction, Modifier.padding(top = Spacing.xs - 2.dp), color = SyllColors.grape500)
        }
        PrimaryButton(
            stringResource(R.string.upgrade_to_premium),
            onClick = onUpgrade,
            modifier = Modifier.padding(top = Spacing.m),
            icon = Icons.Filled.WorkspacePremium,
            kind = ActionButtonKind.PREMIUM,
        )
    }
}

/**
 * iOS `ProfilePremiumCard` (Profile 6.6a): a Premium learner's plan card in the upgrade card's place. A quiet status:
 * Grape 50, a 2 dp Grape 200 border, the badge, "Premium", the renewal or end date and a ›. Opens "Your plan".
 */
@Composable
fun ProfilePremiumCard(plan: PremiumPlan?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Radii.card)
    Row(
        modifier
            .fillMaxWidth()
            .pressScaleClickable(onClick = onClick)
            .background(SyllColors.grape50, shape)
            .border(Borders.card, SyllColors.grape200, shape)
            .padding(horizontal = Spacing.m, vertical = Spacing.s + 2.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PremiumBadge()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs / 2)) {
            Text("Premium", style = Typography.wordClass.copy(fontWeight = FontWeight.ExtraBold), color = SyllColors.ink)
            dateLine(plan)?.let { Text(it, style = Typography.micro, color = SyllColors.mutedInk) }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = SyllColors.ink400)
    }
}

/** iOS `PremiumPlanText.dateLine`: "Renews 23 Nov 2027" for a store plan that renews, otherwise "Until …". */
@Composable
private fun dateLine(plan: PremiumPlan?): String? {
    val until = plan?.premiumUntil ?: return null
    val date = PremiumText.date(until, LocalConfiguration.current.locales[0])
    return stringResource(if (plan.autoRenew == true) R.string.renews_x else R.string.until_x, date)
}

/**
 * iOS `PremiumPlanSheet` (6.6b "Your plan"): a short bottom sheet. An info card (plan, renewal or end date, Play
 * price), one line about cancelling, "Restore purchases" and "Redeem a code" (while the server allows it), and the
 * outline "Manage subscription" (Google Play's subscription centre). A redeemed plan shows "Expires" and has no price
 * and no manage button; one bought on an iPhone is managed in the App Store.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumPlanSheet(onDismiss: () -> Unit) {
    val learner = LocalLearnerStore.current
    val purchases = LocalPurchaseStore.current
    val appScope = LocalAppScope.current
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    var isRedeeming by remember { mutableStateOf(false) }
    val plan = learner.profile?.plan
    val isPlay = plan?.source == PremiumPlan.Source.PLAYSTORE
    val isAppStore = plan?.source == PremiumPlan.Source.APPSTORE

    LaunchedEffect(Unit) {
        purchases.clearNotice()
        purchases.loadOffers()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = Radii.sheet, topEnd = Radii.sheet),
        containerColor = SyllColors.canvas,
    ) {
        Column(Modifier.padding(horizontal = Spacing.screenGutter).padding(bottom = Spacing.l)) {
            Text(
                stringResource(R.string.your_plan),
                style = Typography.sheetTitle.copy(letterSpacing = Typography.sheetTracking.sp),
                color = SyllColors.ink,
                modifier = Modifier.semantics { heading() },
            )
            Column(Modifier.padding(top = Spacing.l - 4.dp).cardSurface()) {
                InfoRow(stringResource(R.string.plan), planName(plan))
                plan?.premiumUntil?.let { until ->
                    CardDivider()
                    InfoRow(
                        stringResource(if ((isPlay || isAppStore) && plan.autoRenew == true) R.string.renews else R.string.expires),
                        PremiumText.date(until, locale),
                    )
                }
                if (isPlay) {
                    price(plan, purchases.offers)?.let { price ->
                        CardDivider()
                        InfoRow(stringResource(R.string.price), price)
                    }
                }
            }
            if (isPlay || isAppStore) {
                Text(
                    stringResource(if (isPlay) R.string.your_plan_is_managed_in_google_play else R.string.your_plan_is_managed_in_the_app_store_cancel_at),
                    style = Typography.micro,
                    color = SyllColors.mutedInk,
                    modifier = Modifier.padding(top = Spacing.s),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = Spacing.s).defaultMinSize(minHeight = Spacing.minTouchTarget),
                horizontalArrangement = Arrangement.spacedBy(Spacing.l, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextLink(
                    stringResource(R.string.restore_purchases),
                    onClick = { appScope.launch { purchases.restore() } },
                    enabled = purchases.activity == PurchaseStore.Activity.Idle,
                )
                if (learner.canRedeem) TextLink(stringResource(R.string.redeem_a_code), onClick = { isRedeeming = true })
            }
            val notice = purchases.notice
            if (notice != null && purchases.activity == PurchaseStore.Activity.Idle) {
                Text(
                    notice.text(),
                    style = Typography.micro,
                    color = SyllColors.mutedInk,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (isPlay) {
                SecondaryButton(
                    stringResource(R.string.manage_subscription),
                    onClick = { context.openUrl(PremiumLinks.manage(plan?.productId, context.packageName)) },
                    modifier = Modifier.padding(top = Spacing.m),
                    kind = ActionButtonKind.OUTLINE,
                )
            }
        }
    }
    if (isRedeeming) RedeemCodeSheet(onDismiss = { isRedeeming = false })
}

@Composable
private fun InfoRow(label: String, value: String) {
    CardRow(Modifier.semantics(mergeDescendants = true) {}) {
        Text(label, style = Typography.body, color = SyllColors.bodyInk, modifier = Modifier.weight(1f))
        Text(value, style = Typography.body.copy(fontWeight = FontWeight.SemiBold), color = SyllColors.ink, textAlign = TextAlign.End)
    }
}

@Composable
private fun planName(plan: PremiumPlan?): String = when (plan?.productId) {
    PremiumProduct.YEARLY -> "Syll Premium · " + stringResource(R.string.yearly)
    PremiumProduct.MONTHLY -> "Syll Premium · " + stringResource(R.string.monthly)
    else -> "Syll Premium"
}

/** Google Play's price for the learner's product, once the offers have loaded. */
@Composable
private fun price(plan: PremiumPlan?, offers: List<PremiumOffer>): String? {
    val offer = offers.firstOrNull { it.id == plan?.productId } ?: return null
    return when (offer.period) {
        PremiumOffer.Period.YEAR -> stringResource(R.string.x_year, offer.displayPrice)
        PremiumOffer.Period.MONTH -> stringResource(R.string.x_month, offer.displayPrice)
        PremiumOffer.Period.OTHER -> offer.displayPrice
    }
}
