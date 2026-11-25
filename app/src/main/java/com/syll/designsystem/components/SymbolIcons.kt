package com.syll.designsystem.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Domain
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * SF Symbol names (iOS catalog icons, the backend's `iosIcon`) → Material icons. An unknown name falls back to a
 * tag, like a topic without an icon on iOS.
 */
object SymbolIcons {
    private val map: Map<String, ImageVector> = mapOf(
        "sun.max" to Icons.Outlined.WbSunny,
        "graduationcap" to Icons.Outlined.School,
        "checkmark.seal" to Icons.Outlined.Verified,
        "briefcase" to Icons.Outlined.BusinessCenter,
        "square.stack" to Icons.Outlined.ViewAgenda,
        "tag" to Icons.Outlined.Sell,
        "text.bubble" to Icons.Outlined.ChatBubbleOutline,
        "airplane" to Icons.Outlined.Flight,
        "fork.knife" to Icons.Outlined.Restaurant,
        "cross.case" to Icons.Outlined.LocalHospital,
        "bubble.left.and.bubble.right" to Icons.Outlined.Forum,
        "cart" to Icons.Outlined.ShoppingCart,
        "house" to Icons.Outlined.Home,
        "figure.run" to Icons.Outlined.DirectionsRun,
        "music.note" to Icons.Outlined.MusicNote,
        "books.vertical" to Icons.Outlined.AutoStories,
        "leaf" to Icons.Outlined.Eco,
        "cpu" to Icons.Outlined.Memory,
        "person.3" to Icons.Outlined.Groups,
        "atom" to Icons.Outlined.Science,
        "globe" to Icons.Outlined.Public,
        "chart.line.uptrend.xyaxis" to Icons.AutoMirrored.Outlined.TrendingUp,
        "building.2" to Icons.Outlined.Domain,
        "person.2" to Icons.Outlined.People,
        "person.badge.plus" to Icons.Outlined.PersonAdd,
        "creditcard" to Icons.Outlined.CreditCard,
        "megaphone" to Icons.Outlined.Campaign,
        "arrow.left.arrow.right" to Icons.Outlined.SwapHoriz,
        "checklist" to Icons.Outlined.Checklist,
        "bubble.left" to Icons.AutoMirrored.Outlined.Chat,
    )

    fun icon(symbol: String?): ImageVector = symbol?.let(map::get) ?: Icons.Outlined.Sell
}
