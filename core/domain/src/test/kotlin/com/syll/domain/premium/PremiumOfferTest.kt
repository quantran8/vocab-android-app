package com.syll.domain.premium

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PremiumOfferTest {
    private fun offer(id: String, period: PremiumOffer.Period, micros: Long) =
        PremiumOffer(id, id, "$", period, micros, "USD")

    @Test
    fun yearlySavingComparesTwelveMonths() {
        val offers = listOf(offer("m", PremiumOffer.Period.MONTH, 6_990_000), offer("y", PremiumOffer.Period.YEAR, 49_990_000))
        assertEquals(40, offers.yearlySavingPercent)
    }

    @Test
    fun noSavingBelowFivePercentOrWithoutBothPlans() {
        assertNull(listOf(offer("m", PremiumOffer.Period.MONTH, 10_000_000), offer("y", PremiumOffer.Period.YEAR, 118_000_000)).yearlySavingPercent)
        assertNull(listOf(offer("y", PremiumOffer.Period.YEAR, 49_990_000)).yearlySavingPercent)
    }

    @Test
    fun yearlyComesFirstAndPeriodsParse() {
        val offers = listOf(offer("m", PremiumOffer.Period.MONTH, 1), offer("o", PremiumOffer.Period.OTHER, 1), offer("y", PremiumOffer.Period.YEAR, 1))
        assertEquals(listOf("y", "m", "o"), offers.paywallOrder().map { it.id })
        assertEquals(PremiumOffer.Period.MONTH, PremiumOffer.period("P1M"))
        assertEquals(PremiumOffer.Period.YEAR, PremiumOffer.period("P1Y"))
        assertEquals(PremiumOffer.Period.OTHER, PremiumOffer.period("P3M"))
    }
}
