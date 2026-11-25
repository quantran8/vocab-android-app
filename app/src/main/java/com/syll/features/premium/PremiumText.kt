package com.syll.features.premium

import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import com.syll.domain.premium.PremiumOffer
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

/** Prices, trial lengths and plan dates as the paywall and "Your plan" show them, in the app's language. */
object PremiumText {
    /** [micros] in the offer's currency, formatted like Play's own prices. */
    fun price(micros: Long, currencyCode: String, locale: Locale): String {
        val format = NumberFormat.getCurrencyInstance(locale)
        runCatching { Currency.getInstance(currencyCode) }.getOrNull()?.let {
            format.currency = it
            format.maximumFractionDigits = it.defaultFractionDigits.coerceAtLeast(0)
        }
        return format.format(micros / 1_000_000.0)
    }

    /** A yearly plan's price per month (iOS `monthlyDisplayPrice`). */
    fun monthlyPrice(offer: PremiumOffer, locale: Locale): String = price(offer.priceMicros / 12, offer.currencyCode, locale)

    /** "1 week", "3 days": a trial's ISO 8601 length; null for a length this can't read. */
    fun trialLength(iso: String, locale: Locale): String? {
        val match = Regex("""P(\d+)([DWMY])""").matchEntire(iso) ?: return null
        val amount = match.groupValues[1].toInt()
        val unit = when (match.groupValues[2]) {
            "D" -> MeasureUnit.DAY
            "W" -> MeasureUnit.WEEK
            "M" -> MeasureUnit.MONTH
            else -> MeasureUnit.YEAR
        }
        return MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.WIDE).format(Measure(amount, unit))
    }

    /** iOS `PremiumPlanText.date`: "23 Nov 2027" in the learner's zone. */
    fun date(instant: Instant, locale: Locale): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(instant.atZone(ZoneId.systemDefault()))

    /** "23 November 2027" (the redeem confirmation). */
    fun longDate(instant: Instant, locale: Locale): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale).format(instant.atZone(ZoneId.systemDefault()))
}
