package com.syll.features.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class AppleWebAuthTest {
    @Test
    fun authorizationUrlPostsTheResultAndHashesTheNonce() {
        val url = AppleWebAuth.authorizationUrl(
            clientId = "com.syll.signin",
            redirectUri = "https://example.com/service/vocab/v1/auth/apple/callback",
            state = "s-1",
            hashedNonce = "abc123",
        )
        assertEquals(
            "https://appleid.apple.com/auth/authorize?client_id=com.syll.signin" +
                "&redirect_uri=https%3A%2F%2Fexample.com%2Fservice%2Fvocab%2Fv1%2Fauth%2Fapple%2Fcallback" +
                "&response_type=code%20id_token&response_mode=form_post&scope=email&state=s-1&nonce=abc123",
            url,
        )
    }

    @Test
    fun callbackWithTokenAndOurState() {
        // The backend forwards Apple's fields form-encoded.
        val outcome = AppleWebAuth.parseCallback("id_token=eyJ.a%2Bb.c&state=s-1", expectedState = "s-1")
        assertEquals(AppleWebAuth.Outcome.Token("eyJ.a+b.c"), outcome)
    }

    @Test
    fun callbackForAnotherRequestFails() {
        assertEquals(AppleWebAuth.Outcome.Failed, AppleWebAuth.parseCallback("id_token=t&state=old", "s-1"))
        assertEquals(AppleWebAuth.Outcome.Failed, AppleWebAuth.parseCallback("id_token=t", "s-1"))
        assertEquals(AppleWebAuth.Outcome.Failed, AppleWebAuth.parseCallback(null, "s-1"))
    }

    @Test
    fun cancelledOnApplesPageIsSilentOtherErrorsFail() {
        assertEquals(AppleWebAuth.Outcome.Cancelled, AppleWebAuth.parseCallback("state=s-1&error=user_cancelled_authorize", "s-1"))
        assertEquals(AppleWebAuth.Outcome.Failed, AppleWebAuth.parseCallback("state=s-1&error=invalid_request", "s-1"))
        assertEquals(AppleWebAuth.Outcome.Failed, AppleWebAuth.parseCallback("state=s-1&id_token=", "s-1"))
    }
}
