package com.syll.features.auth

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.syll.MainActivity

/**
 * Receives `syll-apple://callback?…`, where the backend sends the Custom Tab after Apple's sign-in, hands it to
 * [AppleSignIn] and brings [MainActivity] back to the top, which closes the tab (AppAuth's redirect receiver pattern).
 */
class AppleSignInCallbackActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppleSignIn.handleCallback(intent?.data)
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }
}
