package com.syll.domain

/** Why a daily session has fewer new words than the goal (`newWordReason` and `withheldReason`). */
enum class NewWordReason(val raw: String) {
    GOAL("goal"),
    USER_OVERRIDE("user_override"),
    AUTO_BALANCED("auto_balanced"),
    CHECKPOINT_GATE("checkpoint_gate"),
    SCOPE_LIMITED("scope_limited"),
    SCOPE_EXHAUSTED("scope_exhausted"),
    REVIEW_ONLY("review_only"),
    /** The free tier's new words are used up (or are the tightest limit). */
    FREE_LIMIT("free_limit"),
    UNKNOWN("unknown");

    object Serializer : LenientEnumSerializer<NewWordReason>("NewWordReason", entries.toTypedArray(), { it.raw }, UNKNOWN)
}
