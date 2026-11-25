package com.syll.features.auth

import android.content.Context
import android.util.Base64
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

/** iOS `SignInNonce`: a one-time value for an ID-token request. Google gets its SHA-256, the backend the raw value. */
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
