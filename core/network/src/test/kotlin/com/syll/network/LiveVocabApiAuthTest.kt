package com.syll.network

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The auth endpoints of `LiveVocabApi`: paths, bodies (FRONTEND_FLOW §2) and which answers store a session. */
class LiveVocabApiAuthTest {
    private lateinit var server: MockWebServer
    private val tokens = InMemoryTokenStore()
    private lateinit var api: LiveVocabApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = LiveVocabApi(ApiClient(baseUrl = server.url("/").toString(), tokens = tokens, pause = {}))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueue(status: Int = 200, body: String = "{}") {
        server.enqueue(MockResponse().setResponseCode(status).setBody(body))
    }

    @Test
    fun logInPostsCredentialsAndStoresTheSession() = runBlocking {
        enqueue(body = SESSION)
        api.logIn("a@example.com", "secret123")
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v1/auth/login", request.path)
        assertNull(request.getHeader("Authorization"))
        assertEquals("""{"email":"a@example.com","password":"secret123"}""", request.body.readUtf8())
        assertEquals("access", tokens.load()?.accessToken)
        assertTrue(api.hasStoredSession())
    }

    @Test
    fun signUpNeedingConfirmationStoresNothing() = runBlocking {
        enqueue(body = """{"session":null,"confirmationRequired":true}""")
        val result = api.signUp("a@example.com", "secret123")
        assertEquals("/v1/auth/signup", server.takeRequest().path)
        assertTrue(result.confirmationRequired)
        assertNull(result.session)
        assertFalse(api.hasStoredSession())
    }

    @Test
    fun signUpWithSessionStoresIt() = runBlocking {
        enqueue(body = """{"session":$SESSION,"confirmationRequired":false}""")
        api.signUp("a@example.com", "secret123")
        assertTrue(api.hasStoredSession())
    }

    @Test
    fun googleSendsTheRawNonceAndOmitsAMissingOne() = runBlocking {
        enqueue(body = SESSION)
        enqueue(body = SESSION)
        api.logInWithGoogle("id-token", "raw")
        api.logInWithApple("apple-token", null)
        val google = server.takeRequest()
        assertEquals("/v1/auth/google", google.path)
        assertEquals("""{"idToken":"id-token","nonce":"raw"}""", google.body.readUtf8())
        val apple = server.takeRequest()
        assertEquals("/v1/auth/apple", apple.path)
        assertEquals("""{"idToken":"apple-token"}""", apple.body.readUtf8())
    }

    @Test
    fun passwordResetInTwoSteps() = runBlocking {
        enqueue(status = 202, body = "")
        enqueue(body = SESSION)
        api.requestPasswordReset("a@example.com")
        assertFalse(api.hasStoredSession())
        api.resetPassword("a@example.com", "123456", "newpass12")
        val recover = server.takeRequest()
        assertEquals("/v1/auth/recover", recover.path)
        assertEquals("""{"email":"a@example.com"}""", recover.body.readUtf8())
        val verify = server.takeRequest()
        assertEquals("/v1/auth/recover/verify", verify.path)
        assertEquals("""{"email":"a@example.com","code":"123456","password":"newpass12"}""", verify.body.readUtf8())
        assertTrue(api.hasStoredSession())
    }

    @Test
    fun changePasswordIsAnAuthorizedPut() = runBlocking {
        tokens.save(AuthTokens("access", "refresh", System.currentTimeMillis() + 3_600_000))
        enqueue(status = 204, body = "")
        api.changePassword("old12345", "new12345")
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/v1/me/password", request.path)
        assertEquals("Bearer access", request.getHeader("Authorization"))
        assertEquals("""{"currentPassword":"old12345","newPassword":"new12345"}""", request.body.readUtf8())
    }

    @Test
    fun deleteAccountIsADeleteOnMe() = runBlocking {
        tokens.save(AuthTokens("access", "refresh", System.currentTimeMillis() + 3_600_000))
        enqueue(status = 204, body = "")
        api.deleteAccount()
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/v1/me", request.path)
    }

    private companion object {
        const val SESSION = """{"accessToken":"access","refreshToken":"refresh","expiresIn":3600,"user":{"id":"u1","email":"a@example.com"}}"""
    }
}
