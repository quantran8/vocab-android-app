package com.syll.features.auth

import com.syll.R
import com.syll.app.UserMessage
import com.syll.network.ApiError
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Port of iOS `AccountDeletionTests` (store part) plus the log-in error mapping. */
class AuthStoreTest {
    private fun signedInStore(api: StubVocabApi) = AuthStore(api).also { it.restore() }

    private val unauthorized = ApiError.Server(401, "UNAUTHORIZED", "", retryable = false, requestId = null)

    @Test
    fun successEndsTheSessionAndErasesTheDevice() = runBlocking {
        val api = StubVocabApi()
        val store = signedInStore(api)
        var erased = 0
        var signedOut = 0
        store.onAccountDeleted = { erased += 1 }
        store.onSignedOut = { signedOut += 1 }

        store.deleteAccount()

        assertEquals(1, api.deleteAccountCalls)
        assertEquals(1, api.signOutCalls)
        assertEquals(1, erased)
        assertEquals(1, signedOut)
        assertEquals(AuthStore.Phase.SIGNED_OUT, store.phase)
        assertNotNull(store.accountDeletedNotice)
        assertNull(store.errorMessage)
        assertFalse(store.isDeletingAccount)
    }

    @Test
    fun failureKeepsTheSessionAndTheDeviceData() = runBlocking {
        val api = StubVocabApi().apply { deleteAccountError = ApiError.Transport }
        val store = signedInStore(api)
        var erased = 0
        store.onAccountDeleted = { erased += 1 }

        store.deleteAccount()

        assertEquals(0, api.signOutCalls)
        assertEquals(0, erased)
        assertEquals(AuthStore.Phase.SIGNED_IN, store.phase)
        assertNotNull(store.accountDeletionError)
        assertNull(store.accountDeletedNotice)
        assertFalse(store.isDeletingAccount)
    }

    @Test
    fun retryClearsThePreviousError() = runBlocking {
        val api = StubVocabApi().apply { deleteAccountError = ApiError.Transport }
        val store = signedInStore(api)
        store.deleteAccount()

        api.deleteAccountError = null
        store.deleteAccount()

        assertNull(store.accountDeletionError)
        assertEquals(AuthStore.Phase.SIGNED_OUT, store.phase)
    }

    /** The account may still exist, so the sign-in screen must not say it was deleted. */
    @Test
    fun rejectedSessionAsksToLogInAgain() = runBlocking {
        val api = StubVocabApi().apply { deleteAccountError = unauthorized }
        val store = signedInStore(api)

        store.deleteAccount()

        assertEquals(AuthStore.Phase.SIGNED_OUT, store.phase)
        assertNotNull(store.errorMessage)
        assertNull(store.accountDeletedNotice)
        assertNull(store.accountDeletionError)
    }

    @Test
    fun noticeClearsWhenLoggingInAgain() = runBlocking {
        val store = signedInStore(StubVocabApi())
        store.deleteAccount()

        store.logIn("a@example.com", "correct-horse")

        assertNull(store.accountDeletedNotice)
    }

    @Test
    fun wrongPasswordAndRejectedProviderSayDifferentThings() = runBlocking {
        val api = StubVocabApi(storedSession = false).apply { logInError = unauthorized }
        val store = signedInStore(api)

        store.logIn("a@example.com", "wrong-pass1")
        assertEquals(UserMessage.Resource(R.string.wrong_email_or_password), store.errorMessage)

        store.logInWithGoogle("token", "nonce")
        assertEquals(UserMessage.Resource(R.string.couldn_t_sign_in_please_try_again), store.errorMessage)
        assertEquals(AuthStore.Phase.SIGNED_OUT, store.phase)
    }

    @Test
    fun otherErrorsUseTheApiMessage() = runBlocking {
        val api = StubVocabApi(storedSession = false).apply { logInError = ApiError.Transport }
        val store = signedInStore(api)

        store.logIn("a@example.com", "secret123")

        assertEquals(UserMessage.Api(ApiError.Transport), store.errorMessage)
        assertFalse(store.isSubmitting)
    }

    @Test
    fun signUpNeedingConfirmationRemembersTheEmail() = runBlocking {
        val store = signedInStore(StubVocabApi(storedSession = false))

        store.signUp("a@example.com", "secret123")

        assertEquals("a@example.com", store.pendingConfirmationEmail)
        assertEquals(AuthStore.Phase.SIGNED_OUT, store.phase)
    }

    @Test
    fun resetPasswordSignsInAfterTheCode() = runBlocking {
        val store = signedInStore(StubVocabApi(storedSession = false))

        store.resetPassword("123456", "secret123")
        assertEquals(AuthStore.Phase.SIGNED_OUT, store.phase) // no code was sent yet

        store.requestPasswordReset("a@example.com")
        assertEquals("a@example.com", store.passwordResetEmail)
        store.resetPassword("123456", "secret123")

        assertNull(store.passwordResetEmail)
        assertEquals(AuthStore.Phase.SIGNED_IN, store.phase)
    }
}
