package com.syll.domain.premium

import kotlin.math.roundToInt

/**
 * The Play subscriptions that grant Premium (iOS `PremiumProduct`). Must match the backend's `PLAYSTORE_PRODUCT_IDS`
 * and the subscriptions set up in Play Console (one base plan each).
 */
object PremiumProduct {
    const val MONTHLY = "syll.premium.monthly"
    const val YEARLY = "syll.premium.yearly"
    val ids = listOf(YEARLY, MONTHLY)
}

/**
 * iOS `PremiumOffer`: a Premium plan as the paywall shows it. Names and prices come from Google Play, already
 * formatted for the learner's country.
 */
data class PremiumOffer(
    val id: String,
    val displayName: String,
    /** The recurring price, e.g. "₫1,290,000". */
    val displayPrice: String,
    val period: Period,
    /** The recurring price in millionths of [currencyCode], for comparing plans and deriving per-month prices. */
    val priceMicros: Long,
    val currencyCode: String,
    /** ISO 8601 length of a free trial the learner can still take ("P1W"); null without one. */
    val freeTrialPeriod: String? = null,
    /** Play's token for the base plan or offer [launchBillingFlow] buys; opaque to the app. */
    val offerToken: String = "",
) {
    enum class Period { MONTH, YEAR, OTHER }

    companion object {
        /** Play's billing period ("P1M", "P1Y") as a [Period]. */
        fun period(iso: String): Period = when (iso) {
            "P1M" -> Period.MONTH
            "P1Y", "P12M" -> Period.YEAR
            else -> Period.OTHER
        }
    }
}

/** The yearly price against twelve months of the monthly plan, in whole percent; null below 5% or without both. */
val List<PremiumOffer>.yearlySavingPercent: Int?
    get() {
        val monthly = firstOrNull { it.period == PremiumOffer.Period.MONTH }?.priceMicros ?: return null
        val yearly = firstOrNull { it.period == PremiumOffer.Period.YEAR }?.priceMicros ?: return null
        if (monthly <= 0) return null
        val percent = ((1 - yearly.toDouble() / (monthly.toDouble() * 12)) * 100).roundToInt()
        return percent.takeIf { it >= 5 }
    }

/** Yearly first (chosen by default), then monthly, then anything else Play sends. */
fun List<PremiumOffer>.paywallOrder(): List<PremiumOffer> = sortedBy {
    when (it.period) {
        PremiumOffer.Period.YEAR -> 0
        PremiumOffer.Period.MONTH -> 1
        PremiumOffer.Period.OTHER -> 2
    }
}
