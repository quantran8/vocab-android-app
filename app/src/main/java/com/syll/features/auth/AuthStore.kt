package com.syll.features.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syll.R
import com.syll.app.UserMessage
import com.syll.network.ApiError
import com.syll.network.VocabApi
import com.syll.network.asApiError
import kotlinx.coroutines.CancellationException

/** iOS `AuthStore`. Main thread only, like the iOS `@MainActor` store. */
class AuthStore(private val api: VocabApi) {
    enum class Phase { LAUNCHING, SIGNED_OUT, SIGNED_IN }

    var phase by mutableStateOf(Phase.LAUNCHING)
        private set
    var isSubmitting by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<UserMessage?>(null)
        private set
    var isDeletingAccount by mutableStateOf(false)
        private set
    var accountDeletionError by mutableStateOf<UserMessage?>(null)
        private set
    /** Shown on the sign-in screen after the account is gone. */
    var accountDeletedNotice by mutableStateOf<UserMessage?>(null)
        private set
    /** Email awaiting confirmation after signing up. */
    var pendingConfirmationEmail by mutableStateOf<String?>(null)
        private set
    /** Email a reset-password code was just sent to; the reset page then asks for the code. */
    var passwordResetEmail by mutableStateOf<String?>(null)
        private set
    var isChangingPassword by mutableStateOf(false)
        private set
    var passwordChangeError by mutableStateOf<UserMessage?>(null)
        private set

    /** Clears per-account state. Runs on sign-out and when the session can't be refreshed. */
    var onSignedOut: () -> Unit = {}
    /** Erases what this device remembers about the account, which signing out keeps. */
    var onAccountDeleted: () -> Unit = {}

    fun restore() {
        if (phase != Phase.LAUNCHING) return
        phase = if (api.hasStoredSession()) Phase.SIGNED_IN else Phase.SIGNED_OUT
    }

    suspend fun logIn(email: String, password: String) = submit {
        api.logIn(email, password)
        phase = Phase.SIGNED_IN
    }

    /** The token comes from Google; pass the raw nonce whose hash went into its request. */
    suspend fun logInWithGoogle(idToken: String, nonce: String?) = submit(rejectedMessage = providerRejected) {
        api.logInWithGoogle(idToken, nonce)
        phase = Phase.SIGNED_IN
    }

    suspend fun logInWithApple(idToken: String, nonce: String?) = submit(rejectedMessage = providerRejected) {
        api.logInWithApple(idToken, nonce)
        phase = Phase.SIGNED_IN
    }

    suspend fun requestPasswordReset(email: String) = submit {
        api.requestPasswordReset(email)
        passwordResetEmail = email
    }

    suspend fun resetPassword(code: String, newPassword: String) {
        val email = passwordResetEmail ?: return
        submit {
            api.resetPassword(email, code, newPassword)
            passwordResetEmail = null
            phase = Phase.SIGNED_IN
        }
    }

    fun cancelPasswordReset() {
        passwordResetEmail = null
        clearError()
    }

    /** Returns whether the password changed. */
    suspend fun changePassword(current: String, new: String): Boolean {
        if (isChangingPassword) return false
        isChangingPassword = true
        passwordChangeError = null
        try {
            api.changePassword(current, new)
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            passwordChangeError = UserMessage.Api(e.asApiError)
            return false
        } finally {
            isChangingPassword = false
        }
    }

    fun clearPasswordChangeError() {
        passwordChangeError = null
    }

    /** A Google or Apple sign-in that failed before reaching the backend. */
    fun providerSignInFailed(message: UserMessage? = null) {
        errorMessage = message ?: providerRejected
    }

    suspend fun signUp(email: String, password: String) = submit {
        val result = api.signUp(email, password)
        if (result.session != null) {
            phase = Phase.SIGNED_IN
        } else if (result.confirmationRequired) {
            pendingConfirmationEmail = email
        }
    }

    fun dismissConfirmation() {
        pendingConfirmationEmail = null
    }

    fun clearError() {
        errorMessage = null
        accountDeletedNotice = null
    }

    suspend fun signOut() {
        api.signOut()
        sessionEnded()
    }

    suspend fun deleteAccount() {
        if (isDeletingAccount) return
        isDeletingAccount = true
        accountDeletionError = null
        try {
            api.deleteAccount()
            accountDeletedNotice = UserMessage.Resource(R.string.your_account_has_been_deleted)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = e.asApiError
            if (error.status == 401) {
                // Either the session expired or an earlier attempt succeeded and its answer was lost.
                errorMessage = UserMessage.Resource(R.string.log_in_again_to_delete_your_account)
            } else {
                accountDeletionError = UserMessage.Api(error)
                return
            }
        } finally {
            isDeletingAccount = false
        }
        api.signOut()
        onAccountDeleted()
        sessionEnded()
    }

    /** The refresh token was rejected. */
    fun sessionEnded() {
        if (phase == Phase.SIGNED_OUT) return
        phase = Phase.SIGNED_OUT
        onSignedOut()
    }

    /** [rejectedMessage] replaces "Wrong email or password." for a 401. */
    private suspend fun submit(rejectedMessage: UserMessage? = null, work: suspend () -> Unit) {
        if (isSubmitting) return
        isSubmitting = true
        errorMessage = null
        accountDeletedNotice = null
        try {
            work()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error: ApiError = e.asApiError
            errorMessage = if (error.status == 401) {
                rejectedMessage ?: UserMessage.Resource(R.string.wrong_email_or_password)
            } else {
                UserMessage.Api(error)
            }
        } finally {
            isSubmitting = false
        }
    }

    private companion object {
        val providerRejected = UserMessage.Resource(R.string.couldn_t_sign_in_please_try_again)
    }
}
