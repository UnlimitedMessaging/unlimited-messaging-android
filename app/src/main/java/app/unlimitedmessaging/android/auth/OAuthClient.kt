package app.unlimitedmessaging.android.auth

import app.unlimitedmessaging.android.config.AppConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** The parts of the RFC 8414 metadata document this app uses. */
@Serializable
data class AuthorizationServerMetadata(
    @SerialName("authorization_endpoint") val authorizationEndpoint: String,
    @SerialName("token_endpoint") val tokenEndpoint: String,
    @SerialName("revocation_endpoint") val revocationEndpoint: String? = null,
)

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
    @SerialName("scope") val scope: String? = null,
)

/** Outcome of a refresh, so callers can tell "sign in again" from "try again later". */
sealed interface RefreshOutcome {
    data class Refreshed(val accessToken: String, val refreshToken: String?) : RefreshOutcome

    /** The server refused the refresh token: the session is over. */
    data object Rejected : RefreshOutcome

    /** Network or server trouble: the session may still be valid. */
    data object Unavailable : RefreshOutcome
}

/** The server answered a token request with an error status. */
class OAuthException(val status: Int, message: String) : IOException(message)

/**
 * OAuth 2.1 authorization code flow with PKCE, against the authorization
 * server the backend already runs (the one MCP clients use).
 *
 * Blocking calls: run them off the main thread.
 */
class OAuthClient(
    private val config: AppConfig,
    private val http: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Reads the server's endpoints from its metadata document instead of hard-coding paths. */
    fun discover(): AuthorizationServerMetadata {
        val request = Request.Builder().url(config.discoveryUrl).get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw OAuthException(response.code, "Discovery failed with HTTP ${response.code}")
            }
            return json.decodeFromString(AuthorizationServerMetadata.serializer(), response.body!!.string())
        }
    }

    /** The URL to open in the browser tab to let the user sign in. */
    fun authorizationUrl(
        metadata: AuthorizationServerMetadata,
        codeChallenge: String,
        state: String,
    ): String = metadata.authorizationEndpoint.toHttpUrl().newBuilder()
        .addQueryParameter("response_type", "code")
        .addQueryParameter("client_id", config.oauthClientId)
        .addQueryParameter("redirect_uri", config.oauthRedirectUri)
        .addQueryParameter("scope", config.oauthScope)
        .addQueryParameter("state", state)
        .addQueryParameter("code_challenge", codeChallenge)
        .addQueryParameter("code_challenge_method", "S256")
        .build()
        .toString()

    fun exchangeCode(tokenEndpoint: String, code: String, codeVerifier: String): TokenResponse =
        postToken(
            tokenEndpoint,
            FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("code_verifier", codeVerifier)
                .add("client_id", config.oauthClientId)
                .add("redirect_uri", config.oauthRedirectUri)
                .build(),
        )

    fun refresh(tokenEndpoint: String, refreshToken: String): RefreshOutcome = try {
        val tokens = postToken(
            tokenEndpoint,
            FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .add("client_id", config.oauthClientId)
                .build(),
        )
        RefreshOutcome.Refreshed(tokens.accessToken, tokens.refreshToken)
    } catch (e: OAuthException) {
        // 400 (invalid_grant) or 401: the token is revoked or unknown.
        if (e.status in 400..499) RefreshOutcome.Rejected else RefreshOutcome.Unavailable
    } catch (e: IOException) {
        RefreshOutcome.Unavailable
    }

    /** RFC 7009. Best effort: an unreachable server only means the token expires on its own. */
    fun revoke(revocationEndpoint: String, token: String) {
        val request = Request.Builder()
            .url(revocationEndpoint)
            .post(
                FormBody.Builder()
                    .add("token", token)
                    .add("client_id", config.oauthClientId)
                    .build(),
            )
            .build()
        try {
            http.newCall(request).execute().close()
        } catch (_: IOException) {
            // Nothing to do: the local session is cleared either way.
        }
    }

    private fun postToken(tokenEndpoint: String, body: FormBody): TokenResponse {
        val request = Request.Builder().url(tokenEndpoint).post(body).build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw OAuthException(response.code, "Token request failed with HTTP ${response.code}")
            }
            return json.decodeFromString(TokenResponse.serializer(), text)
        }
    }
}
