package app.unlimitedmessaging.android.auth

import app.unlimitedmessaging.android.config.AppConfig
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OAuthClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OAuthClient
    private lateinit var config: AppConfig

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        config = AppConfig(
            apiBaseUrl = server.url("/").toString().trimEnd('/'),
            oauthClientId = "um-android",
            oauthScope = "device",
            oauthRedirectUri = "app.unlimitedmessaging.android:/oauth2redirect",
        )
        client = OAuthClient(config, OkHttpClient())
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `discovery reads the endpoints from RFC 8414 metadata`() {
        server.enqueue(
            MockResponse().setBody(
                """{"issuer":"x","authorization_endpoint":"https://a/authorize","token_endpoint":"https://a/token",
                   "revocation_endpoint":"https://a/revoke","response_types_supported":["code"]}""",
            ),
        )
        val metadata = client.discover()
        assertEquals("/.well-known/oauth-authorization-server", server.takeRequest().path)
        assertEquals("https://a/token", metadata.tokenEndpoint)
        assertEquals("https://a/revoke", metadata.revocationEndpoint)
    }

    @Test
    fun `authorization url carries PKCE, state and the redirect uri`() {
        val url = client.authorizationUrl(
            AuthorizationServerMetadata("https://a/authorize", "https://a/token"),
            codeChallenge = "challenge",
            state = "state123",
        ).toHttpUrl()
        assertEquals("code", url.queryParameter("response_type"))
        assertEquals("um-android", url.queryParameter("client_id"))
        assertEquals("app.unlimitedmessaging.android:/oauth2redirect", url.queryParameter("redirect_uri"))
        assertEquals("challenge", url.queryParameter("code_challenge"))
        assertEquals("S256", url.queryParameter("code_challenge_method"))
        assertEquals("state123", url.queryParameter("state"))
        assertEquals("device", url.queryParameter("scope"))
    }

    @Test
    fun `code exchange posts a form and parses tokens`() {
        server.enqueue(MockResponse().setBody("""{"access_token":"at","refresh_token":"rt","token_type":"Bearer"}"""))
        val tokens = client.exchangeCode(server.url("/token").toString(), "code1", "verifier1")
        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertTrue(request.getHeader("Content-Type")!!.startsWith("application/x-www-form-urlencoded"))
        assertTrue(body.contains("grant_type=authorization_code"))
        assertTrue(body.contains("code_verifier=verifier1"))
        assertEquals("at", tokens.accessToken)
        assertEquals("rt", tokens.refreshToken)
    }

    @Test
    fun `refresh tells a refused token from an unavailable server`() {
        val tokenUrl = server.url("/token").toString()
        server.enqueue(MockResponse().setBody("""{"access_token":"new"}"""))
        assertEquals(RefreshOutcome.Refreshed("new", null), client.refresh(tokenUrl, "rt"))

        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"invalid_grant"}"""))
        assertEquals(RefreshOutcome.Rejected, client.refresh(tokenUrl, "rt"))

        server.enqueue(MockResponse().setResponseCode(503))
        assertEquals(RefreshOutcome.Unavailable, client.refresh(tokenUrl, "rt"))
    }
}
