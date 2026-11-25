package com.vocabflow.network

import kotlinx.serialization.Serializable

/** Both tokens are stored together, so a refresh can never leave a mismatched pair behind. */
@Serializable
data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    /** Epoch milliseconds. */
    val expiresAtMillis: Long,
)

/** Synchronous like the iOS Keychain store; implementations must be thread-safe. */
interface TokenStoring {
    fun load(): AuthTokens?
    fun save(tokens: AuthTokens)
    fun clear()
}

class InMemoryTokenStore(private var tokens: AuthTokens? = null) : TokenStoring {
    @Synchronized override fun load(): AuthTokens? = tokens
    @Synchronized override fun save(tokens: AuthTokens) { this.tokens = tokens }
    @Synchronized override fun clear() { tokens = null }
}
