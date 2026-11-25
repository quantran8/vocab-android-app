package com.syll.features.auth

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.syll.BuildConfig
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.CompletableDeferred

/** iOS `SignInNonce`: a one-time value for an ID-token request. Google and Apple get its SHA-256, the backend the raw value. */
class SignInNonce {
    val raw: String = ByteArray(32).also(SecureRandom()::nextBytes)
        .let { Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP) }

    val hashed: String get() = sha256Hex(raw)

    companion object {
        fun sha256Hex(value: String): String =
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

/**
 * iOS `GoogleSignIn`. Android uses Credential Manager ("Sign in with Google" sheet) instead of a web PKCE flow:
 * it returns the ID token directly, whose audience is the backend's Web client ID ([BuildConfig.GOOGLE_SERVER_CLIENT_ID]).
 */
object GoogleSignIn {
    sealed class Failure : Exception() {
        data object NotConfigured : Failure()
        data object Cancelled : Failure()
        data object Rejected : Failure()
    }

    data class Result(val idToken: String, val nonce: String)

    val serverClientId: String?
        get() = BuildConfig.GOOGLE_SERVER_CLIENT_ID.trim().takeIf { it.endsWith(".apps.googleusercontent.com") }

    /** [context] must be an activity: the sheet is shown over it. */
    suspend fun signIn(context: Context): Result {
        val clientId = serverClientId ?: throw Failure.NotConfigured
        val nonce = SignInNonce()
        val option = GetSignInWithGoogleOption.Builder(clientId)
            .setNonce(nonce.hashed)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val response = try {
            CredentialManager.create(context).getCredential(context, request)
        } catch (e: GetCredentialCancellationException) {
            throw Failure.Cancelled
        } catch (e: NoCredentialException) {
            throw Failure.Rejected
        } catch (e: GetCredentialException) {
            throw Failure.Rejected
        }
        val credential = response.credential
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw Failure.Rejected
        }
        val token = try {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (e: Exception) {
            throw Failure.Rejected
        }
        return Result(idToken = token, nonce = nonce.raw)
    }
}

/**
 * iOS `SignInWithAppleButton`'s request, as Apple's web flow in a Custom Tab ([AppleWebAuth]). The audience of the
 * ID token is the Services ID ([BuildConfig.APPLE_SERVICE_ID]); Apple returns through the backend's
 * `v1/auth/apple/callback` ([BuildConfig.APPLE_REDIRECT_URI]) to [AppleSignInCallbackActivity].
 *
 * Main thread only. A request waits for [handleCallback]; closing the tab is seen as the app coming back
 * ([hostResumed] after [hostStopped]) with no callback, which cancels. If the process dies while the tab is open,
 * the callback finds no request and the learner simply taps again.
 */
object AppleSignIn {
    sealed class Failure : Exception() {
        data object NotConfigured : Failure()
        data object Cancelled : Failure()
        data object Rejected : Failure()
    }

    data class Result(val idToken: String, val nonce: String)

    val serviceId: String?
        get() = BuildConfig.APPLE_SERVICE_ID.trim().takeIf { it.isNotEmpty() && BuildConfig.APPLE_REDIRECT_URI.startsWith("https://") }

    private class Request(val state: String) {
        val outcome = CompletableDeferred<AppleWebAuth.Outcome>()
        /** Set once the Custom Tab covers the app, so coming back without a callback means it was closed. */
        var leftApp = false
    }

    private var pending: Request? = null

    /** [context] must be an activity: the tab opens over it. */
    suspend fun signIn(context: Context): Result {
        val clientId = serviceId ?: throw Failure.NotConfigured
        val nonce = SignInNonce()
        val request = Request(state = SignInNonce().raw)
        pending?.outcome?.complete(AppleWebAuth.Outcome.Cancelled)
        pending = request
        try {
            val url = AppleWebAuth.authorizationUrl(clientId, BuildConfig.APPLE_REDIRECT_URI, request.state, nonce.hashed)
            try {
                CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
                    .build()
                    .launchUrl(context, Uri.parse(url))
            } catch (e: ActivityNotFoundException) {
                throw Failure.Rejected
            }
            return when (val outcome = request.outcome.await()) {
                is AppleWebAuth.Outcome.Token -> Result(idToken = outcome.idToken, nonce = nonce.raw)
                AppleWebAuth.Outcome.Cancelled -> throw Failure.Cancelled
                AppleWebAuth.Outcome.Failed -> throw Failure.Rejected
            }
        } finally {
            if (pending === request) pending = null
        }
    }

    /** `syll-apple://callback?…` from [AppleSignInCallbackActivity]. */
    fun handleCallback(uri: Uri?) {
        val request = pending ?: return
        if (uri?.scheme != AppleWebAuth.CALLBACK_SCHEME) return
        request.outcome.complete(AppleWebAuth.parseCallback(uri.encodedQuery, request.state))
    }

    fun hostStopped() {
        pending?.leftApp = true
    }

    fun hostResumed() {
        pending?.takeIf { it.leftApp }?.outcome?.complete(AppleWebAuth.Outcome.Cancelled)
    }
}
