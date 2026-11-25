package com.syll.features.auth

import com.syll.network.ApiError
import com.syll.network.SignupResult
import com.syll.network.VocabApi

/** iOS `StubVocabAPI` (auth part): records calls and throws the configured errors. */
class StubVocabApi(var storedSession: Boolean = true) : VocabApi {
    var logInError: ApiError? = null
    var deleteAccountError: ApiError? = null
    var signupResult = SignupResult(session = null, confirmationRequired = true)
    var deleteAccountCalls = 0
    var signOutCalls = 0
    var logInCalls = 0

    override fun hasStoredSession() = storedSession
    override suspend fun signUp(email: String, password: String) = signupResult
    override suspend fun logIn(email: String, password: String) {
        logInCalls += 1
        logInError?.let { throw it }
    }
    override suspend fun logInWithGoogle(idToken: String, nonce: String?) {
        logInError?.let { throw it }
    }
    override suspend fun logInWithApple(idToken: String, nonce: String?) {
        logInError?.let { throw it }
    }
    override suspend fun requestPasswordReset(email: String) {}
    override suspend fun resetPassword(email: String, code: String, newPassword: String) {}
    override suspend fun changePassword(current: String, new: String) {}
    override suspend fun signOut() {
        signOutCalls += 1
    }
    override suspend fun deleteAccount() {
        deleteAccountCalls += 1
        deleteAccountError?.let { throw it }
    }
}
