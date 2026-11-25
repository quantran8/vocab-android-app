package com.lamls.vocabflow.features.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lamls.vocabflow.network.ApiClient

/**
 * iOS `AuthStore`, Phase 0 skeleton: only the phase that drives routing. Login, sign-up, password reset,
 * Google sign-in and account deletion arrive in Phase 1 (docs/PORT_PLAN.md).
 */
class AuthStore(private val client: ApiClient) {
    enum class Phase { LAUNCHING, SIGNED_OUT, SIGNED_IN }

    var phase by mutableStateOf(Phase.LAUNCHING)
        private set

    /** Clears every account-scoped store (iOS `onSignedOut`), wired in `AppContainer`. */
    var onSignedOut: () -> Unit = {}

    /** On launch: a stored token pair means signed in; the first request refreshes it if needed. */
    fun restore() {
        phase = if (client.hasStoredSession) Phase.SIGNED_IN else Phase.SIGNED_OUT
    }

    /** The backend rejected the refresh token (`ApiClient` session-ended handler). */
    fun sessionEnded() {
        if (phase == Phase.SIGNED_OUT) return
        phase = Phase.SIGNED_OUT
        onSignedOut()
    }

    suspend fun signOut() {
        client.signOut()
        phase = Phase.SIGNED_OUT
        onSignedOut()
    }
}
