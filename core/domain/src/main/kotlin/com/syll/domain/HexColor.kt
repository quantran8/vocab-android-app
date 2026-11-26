package com.syll.domain

/** The backend's admin-set colours (`#RRGGBB`) as 0xRRGGBB; null when missing or not in that form. */
object HexColor {
    fun parse(value: String?): Int? {
        if (value == null || value.length != 7 || !value.startsWith("#")) return null
        return value.drop(1).toIntOrNull(16)
    }
}
