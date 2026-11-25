package com.syll.designsystem.tokens

import androidx.compose.ui.graphics.Color

/**
 * iOS `Colors.swift` (docs/app-redesign-v1.html): 60 white · 30 Ink · 10 accent.
 * The ramps are the source of truth; the semantic names below map onto them. Keep both apps in sync.
 */
object SyllColors {
    // Ramps

    val ink50 = Color(0xFFF7F7F9)
    val ink100 = Color(0xFFEFEFF2)
    val ink200 = Color(0xFFE2E2E7)
    val ink300 = Color(0xFFC8C8D0)
    val ink400 = Color(0xFFA3A3AE)
    val ink500 = Color(0xFF80808C)
    val ink600 = Color(0xFF62626D)
    val ink700 = Color(0xFF4A4A54)
    val ink800 = Color(0xFF34343C)
    val ink900 = Color(0xFF222228)
    val ink950 = Color(0xFF131217)

    val blue50 = Color(0xFFEEF3FD)
    val blue100 = Color(0xFFDCE6FB)
    val blue200 = Color(0xFFBACCF6)
    val blue300 = Color(0xFF92AEEF)
    val blue400 = Color(0xFF6A8DE6)
    val blue500 = Color(0xFF3E68DB)
    val blue600 = Color(0xFF3053C2)
    val blue700 = Color(0xFF28449E)
    val blue800 = Color(0xFF22387D)
    val blue900 = Color(0xFF1A2A5B)
    val blue950 = Color(0xFF0E1733)

    val green50 = Color(0xFFEAFBF1)
    val green100 = Color(0xFFCFF5DE)
    val green200 = Color(0xFFA0EABE)
    val green300 = Color(0xFF6BDC9A)
    val green400 = Color(0xFF45D17E)
    val green500 = Color(0xFF2BC86A)
    val green600 = Color(0xFF1FA857)
    val green700 = Color(0xFF188646)
    val green800 = Color(0xFF12673A)
    val green900 = Color(0xFF0E4A2A)
    val green950 = Color(0xFF062615)

    val coral50 = Color(0xFFFFF1EE)
    val coral100 = Color(0xFFFFDFD8)
    val coral200 = Color(0xFFFFBFB1)
    val coral300 = Color(0xFFFF9883)
    val coral400 = Color(0xFFFF7559)
    val coral500 = Color(0xFFFF5A3C)
    val coral600 = Color(0xFFE5432A)
    val coral700 = Color(0xFFBA3421)
    val coral800 = Color(0xFF8C2A1D)
    val coral900 = Color(0xFF5E1E16)
    val coral950 = Color(0xFF320E09)

    val sun50 = Color(0xFFFFFBEA)
    val sun100 = Color(0xFFFFF4C5)
    val sun200 = Color(0xFFFFE88A)
    val sun300 = Color(0xFFFFD84A)
    val sun400 = Color(0xFFFFC81F)
    val sun500 = Color(0xFFFFB800)
    val sun600 = Color(0xFFE09A00)
    val sun700 = Color(0xFFB47800)
    val sun800 = Color(0xFF875A06)
    val sun900 = Color(0xFF5C3E08)
    val sun950 = Color(0xFF301F03)

    /** Grape: the Premium identity only (paywall, Premium entry points, plan cards). Sun 400 is its spark. */
    val grape50 = Color(0xFFF4F0FF)
    val grape100 = Color(0xFFE8DFFF)
    val grape200 = Color(0xFFD2C0FF)
    val grape300 = Color(0xFFB497FF)
    val grape400 = Color(0xFF9670FA)
    val grape500 = Color(0xFF7C4FF0)
    val grape600 = Color(0xFF6838D6)
    val grape700 = Color(0xFF5329AE)
    val grape800 = Color(0xFF3F2085)
    val grape900 = Color(0xFF2C175C)
    val grape950 = Color(0xFF1A0D38)

    // Collection marks: each collection's dot and progress bar.
    val markEveryday = Color(0xFF2F9BF4)
    val markIELTS = Color(0xFFF0458A)
    val markBusiness = Color(0xFF14B8A6)
    val markTOEIC = Color(0xFFFF8A1F)
    /** The learner's own words. */
    val markCustom = sun500

    /** Sheet and dialog scrim (Ink 950 at 40%). */
    val scrim = ink950.copy(alpha = 0.4f)

    // Semantic

    val canvas = Color.White
    val surface = Color.White
    val ink = ink950
    val mutedInk = ink500
    /** Body copy that isn't the main text (definitions, sheet messages). */
    val bodyInk = ink600
    /** Primary (Ink) button: Ink 800 face on an Ink 950 edge. */
    val primaryAction = ink800
    val interactive = blue500
    val link = blue600
    val reward = sun500
    val success = green500
    val error = coral500
    val neutralSurface = ink100
    val stroke = ink200
    /** Hairline between rows inside a card (1.5 pt). */
    val divider = ink100

    // State support
    val selectedSurface = blue50
    val selectedInk = blue700
    val successSurface = green50
    val successInk = green800
    val errorSurface = coral50
    val errorInk = coral800
    /** Destructive text links ("Delete account"). */
    val destructiveInk = coral700
    val infoSurface = blue50
    val infoInk = blue800
    val focusRing = blue500
    val focusHalo = blue100
    val disabledSurface = ink100
    val disabledInk = ink400
    val quietSurface = ink50
    val rewardSurface = sun50
    val rewardInk = sun800

    // Stepped depth under tactile surfaces
    val primaryDepth = ink950
    val interactiveDepth = blue700
    val successActionDepth = green800
    val errorActionDepth = coral800
    val rewardDepth = sun700
    val tileDepth = ink200
    val selectedDepth = blue200
    val successDepth = green200
    val errorDepth = coral200

    // Results bar: strong / mid / weak, told apart by lightness and pattern too, never by colour alone.
    val resultStrong = blue600
    val resultMid = blue300
    val resultWeakSurface = blue50
    val resultWeakStripe = blue300

    val track = ink100
    val slotTargetedSurface = blue100
}
