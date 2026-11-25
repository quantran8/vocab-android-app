package com.syll.app

import android.content.Context
import com.syll.R
import com.syll.network.ApiError

/**
 * iOS `APIError.userMessage`. Uses the activity context, whose resources follow the per-app language.
 * `APPSTORE_*` codes stay mapped for parity with iOS; Android sends Play purchases (`PLAYSTORE_*`).
 */
fun ApiError.userMessage(context: Context): String = when (this) {
    ApiError.Transport -> context.getString(R.string.can_t_reach_syll_check_your_connection_and_try_a)
    ApiError.InvalidResponse -> context.getString(R.string.something_went_wrong_on_our_side_please_try_agai)
    is ApiError.Server -> when (code) {
        "VALIDATION_FAILED" -> message
        else -> serverMessages[code]?.let(context::getString)
            ?: requestId?.let { context.getString(R.string.something_went_wrong_reference_x, it) }
            ?: context.getString(R.string.something_went_wrong_please_try_again)
    }
}

private val serverMessages = mapOf(
    "EMAIL_ALREADY_REGISTERED" to R.string.this_email_already_has_an_account_try_logging_in,
    "RESET_CODE_INVALID" to R.string.that_code_is_wrong_or_has_expired_check_the_late,
    "WRONG_PASSWORD" to R.string.your_current_password_is_wrong,
    "CUSTOM_WORD_LIMIT_REACHED" to R.string.you_can_t_add_more_words_right_now_please_try_ag,
    "CUSTOM_WORD_UNAVAILABLE" to R.string.that_meaning_is_no_longer_available,
    "LEXICAL_PROVIDER_UNAVAILABLE" to R.string.we_couldn_t_prepare_this_word_just_now_please_tr,
    "USERNAME_TAKEN" to R.string.that_name_is_taken_try_another_one,
    "USERNAME_INVALID" to R.string.use_3_20_letters_numbers_or_starting_with_a_lett,
    "USERNAME_NOT_ALLOWED" to R.string.that_name_isn_t_allowed_try_another_one,
    "AVATAR_INVALID" to R.string.that_avatar_isn_t_available_any_more,
    "USERNAME_CHANGE_TOO_SOON" to R.string.you_changed_your_name_recently_you_can_change_it,
    "REPORT_LIMIT_REACHED" to R.string.you_ve_sent_the_most_reports_for_today,
    "RANKING_DISABLED" to R.string.the_leaderboard_isn_t_open_yet,
    "FREE_COLLECTION_LOCKED" to R.string.the_free_plan_keeps_the_first_collection_you_cho,
    "FREE_TOPIC_LIMIT" to R.string.the_free_plan_has_a_limit_on_topics_upgrade_to_p,
    "PREMIUM_REQUIRED" to R.string.this_needs_syll_premium,
    "REDEEM_INVALID" to R.string.that_code_isn_t_valid_check_it_and_try_again,
    "REDEEM_EXPIRED" to R.string.that_code_has_expired,
    "REDEEM_EXHAUSTED" to R.string.that_code_has_already_been_used_up,
    "REDEEM_ALREADY_USED" to R.string.you_ve_already_used_this_code,
    "APPSTORE_TRANSACTION_OWNED" to R.string.this_subscription_belongs_to_another_syll_accoun,
    "APPSTORE_TRANSACTION_INVALID" to R.string.we_couldn_t_confirm_this_purchase_please_try_aga,
    "PLAYSTORE_PURCHASE_OWNED" to R.string.this_subscription_belongs_to_another_syll_accoun,
    "PLAYSTORE_PURCHASE_INVALID" to R.string.we_couldn_t_confirm_this_purchase_please_try_aga,
)
