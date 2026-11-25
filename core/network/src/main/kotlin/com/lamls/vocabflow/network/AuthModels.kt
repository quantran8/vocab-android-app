package com.lamls.vocabflow.network

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

@Serializable
internal data class RefreshRequest(val refreshToken: String)
