package com.syll.features.auth

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Sign in with Apple's web flow, the Android stand-in for iOS `SignInWithAppleButton` (no Android SDK exists).
 * The app opens [authorizationUrl] in a Custom Tab; Apple posts the result to the backend's
 * `v1/auth/apple/callback` (the Services ID's return URL), which sends the browser back to
 * `syll-apple://callback?…` ([AppleSignInCallbackActivity]); [parseCallback] reads that query.
 * Like iOS, Apple gets the nonce's SHA-256 and the backend the raw nonce. Pure, so it is unit tested.
 */
object AppleWebAuth {
    const val CALLBACK_SCHEME = "syll-apple"
    const val CALLBACK_HOST = "callback"

    private const val AUTHORIZE_URL = "https://appleid.apple.com/auth/authorize"

    /** Apple's `error` when the learner backs out on its page. */
    private const val USER_CANCELLED = "user_cancelled_authorize"

    sealed interface Outcome {
        data class Token(val idToken: String) : Outcome
        data object Cancelled : Outcome
        data object Failed : Outcome
    }

    /**
     * `response_mode=form_post` because the email scope is requested (as on iOS), which Apple only allows with a
     * posted response; `code id_token` returns the ID token straight away.
     */
    fun authorizationUrl(clientId: String, redirectUri: String, state: String, hashedNonce: String): String {
        val params = linkedMapOf(
            "client_id" to clientId,
            "redirect_uri" to redirectUri,
            "response_type" to "code id_token",
            "response_mode" to "form_post",
            "scope" to "email",
            "state" to state,
            "nonce" to hashedNonce,
        )
        return AUTHORIZE_URL + "?" + params.entries.joinToString("&") { (key, value) -> "$key=${encode(value)}" }
    }

    /** [query] is the callback URI's raw query. A missing or different `state` is not ours and fails. */
    fun parseCallback(query: String?, expectedState: String): Outcome {
        val fields = parseQuery(query)
        if (fields["state"] != expectedState) return Outcome.Failed
        fields["error"]?.let { return if (it == USER_CANCELLED) Outcome.Cancelled else Outcome.Failed }
        val token = fields["id_token"]?.takeIf { it.isNotBlank() } ?: return Outcome.Failed
        return Outcome.Token(token)
    }

    internal fun parseQuery(query: String?): Map<String, String> =
        query.orEmpty().split('&').filter { it.isNotEmpty() }.associate { pair ->
            val key = pair.substringBefore('=')
            val value = if ('=' in pair) pair.substringAfter('=') else ""
            decode(key) to decode(value)
        }

    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    private fun decode(value: String) = URLDecoder.decode(value, Charsets.UTF_8.name())
}
