package com.syll.network

import kotlinx.serialization.Serializable

/** `200 session` from login, signup, social sign-in and `auth/refresh` (FRONTEND_FLOW §2). */
@Serializable
data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int,
    val user: User,
) {
    @Serializable
    data class User(val id: String, val email: String? = null)
}

/** `auth/signup`: a session, or `confirmationRequired` when the email must be confirmed first. */
@Serializable
data class SignupResult(
    val session: AuthSession? = null,
    val confirmationRequired: Boolean = false,
)

@Serializable
internal data class RefreshRequest(val refreshToken: String)

@Serializable
internal data class Credentials(val email: String, val password: String)

@Serializable
internal data class IdTokenCredentials(val idToken: String, val nonce: String?)

@Serializable
internal data class PasswordReset(val email: String)

@Serializable
internal data class ResetVerification(val email: String, val code: String, val password: String)

@Serializable
internal data class PasswordChange(val currentPassword: String, val newPassword: String)
