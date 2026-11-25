package com.syll.app

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.syll.network.ApiError

/**
 * Text a store shows to the learner (iOS stores hold a `String(localized:)`). Stores have no Context, so they keep
 * what to say and the view resolves it in the current app language.
 */
sealed interface UserMessage {
    data class Resource(@StringRes val id: Int) : UserMessage

    /** iOS `APIError.userMessage`. */
    data class Api(val error: ApiError) : UserMessage
}

@Composable
fun UserMessage.text(): String = when (this) {
    is UserMessage.Resource -> stringResource(id)
    is UserMessage.Api -> error.userMessage(LocalContext.current)
}
