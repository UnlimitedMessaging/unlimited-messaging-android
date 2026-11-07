package app.unlimitedmessaging.android.auth

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import app.unlimitedmessaging.android.config.AppConfig
import app.unlimitedmessaging.android.data.GatewayPreferences
import app.unlimitedmessaging.android.service.GatewayNotifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

sealed interface AuthState {
    data object SignedOut : AuthState

    /** The sign-in page is open in the browser tab. */
    data object AwaitingBrowser : AuthState

    /** Back from the browser, trading the authorization code for tokens. */
    data object Exchanging : AuthState

    data object SignedIn : AuthState
}

/** Why the user is looking at the sign-in screen again, when it is worth telling them. */
enum class AuthError { NETWORK, NO_BROWSER, REJECTED, INVALID_REDIRECT, SESSION_EXPIRED }

/**
 * Owns the signed-in session: starting the browser sign-in, completing it
 * from the redirect, refreshing the access token, and ending it.
 *
 * The login form itself is never drawn by the app. It is the website's own
 * page (email/password, magic link, Google, GitHub), opened in a Custom Tab:
 * whatever the website supports, the app supports, with nothing to keep in
 * sync, and the user's password never passes through the app.
 */
class AuthRepository(
    context: Context,
    private val config: AppConfig,
    private val oauth: OAuthClient,
    private val tokenStore: TokenStore,
    private val preferences: GatewayPreferences,
) {
    private val appContext = context.applicationContext

    /**
     * The verifier and state of a sign-in in progress. Persisted rather than
     * kept in memory: Android may kill the app while the user is in the
     * browser, and the redirect then starts a fresh process.
     */
    private val pending = appContext.getSharedPreferences("oauth_pending", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        if (tokenStore.session != null) AuthState.SignedIn else AuthState.SignedOut,
    )
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val _error = MutableStateFlow<AuthError?>(null)
    val error: StateFlow<AuthError?> = _error.asStateFlow()

    val isSignedIn: Boolean get() = tokenStore.session != null

    private val refreshLock = Any()

    /**
     * Fetches the server metadata, prepares PKCE, then hands the sign-in URL
     * to [openBrowser] (called on the main thread).
     */
    suspend fun startSignIn(openBrowser: (Uri) -> Unit) {
        _error.value = null
        _state.value = AuthState.AwaitingBrowser
        try {
            val url = withContext(Dispatchers.IO) {
                val metadata = oauth.discover()
                val verifier = Pkce.newVerifier()
                val state = Pkce.newState()
                pending.edit(commit = true) {
                    putString(KEY_STATE, state)
                    putString(KEY_VERIFIER, verifier)
                    putString(KEY_TOKEN_ENDPOINT, metadata.tokenEndpoint)
                    putString(KEY_REVOCATION_ENDPOINT, metadata.revocationEndpoint)
                }
                oauth.authorizationUrl(metadata, Pkce.challengeFor(verifier), state)
            }
            withContext(Dispatchers.Main) { openBrowser(Uri.parse(url)) }
        } catch (e: android.content.ActivityNotFoundException) {
            fail(AuthError.NO_BROWSER, e)
        } catch (e: IOException) {
            fail(AuthError.NETWORK, e)
        } catch (e: Exception) {
            fail(AuthError.NETWORK, e)
        }
    }

    /** Called synchronously when the redirect arrives, before the screen comes back. */
    fun markExchanging() {
        _state.value = AuthState.Exchanging
    }

    /**
     * The screen came back to the foreground. If the browser tab was still
     * expected to answer, the user closed it without signing in.
     */
    fun onAppResumed() {
        if (_state.value == AuthState.AwaitingBrowser) _state.value = AuthState.SignedOut
    }

    /** Completes the sign-in from the redirect URI the browser sent back. */
    suspend fun completeSignIn(redirect: Uri) = withContext(Dispatchers.IO) {
        val expectedState = pending.getString(KEY_STATE, null)
        val verifier = pending.getString(KEY_VERIFIER, null)
        val tokenEndpoint = pending.getString(KEY_TOKEN_ENDPOINT, null)
        val revocationEndpoint = pending.getString(KEY_REVOCATION_ENDPOINT, null)
        pending.edit(commit = true) { clear() }

        val code = redirect.getQueryParameter("code")
        when {
            redirect.getQueryParameter("error") != null -> return@withContext fail(AuthError.REJECTED)
            expectedState == null || verifier == null || tokenEndpoint == null ->
                return@withContext fail(AuthError.INVALID_REDIRECT)
            // A redirect we did not ask for, or a replayed one: never use its code.
            redirect.getQueryParameter("state") != expectedState ->
                return@withContext fail(AuthError.INVALID_REDIRECT)
            code.isNullOrEmpty() -> return@withContext fail(AuthError.INVALID_REDIRECT)
        }

        try {
            val tokens = oauth.exchangeCode(tokenEndpoint!!, code!!, verifier!!)
            tokenStore.save(
                Session(
                    accessToken = tokens.accessToken,
                    refreshToken = tokens.refreshToken,
                    tokenEndpoint = tokenEndpoint,
                    revocationEndpoint = revocationEndpoint,
                ),
            )
            preferences.setGatewayEnabled(true)
            GatewayNotifications.cancelSessionExpired(appContext)
            _error.value = null
            _state.value = AuthState.SignedIn
        } catch (e: OAuthException) {
            fail(AuthError.REJECTED, e)
        } catch (e: IOException) {
            fail(AuthError.NETWORK, e)
        }
    }

    /**
     * Trades the refresh token for a new access token. Blocking, and
     * serialized: when several requests hit a 401 at once, only the first
     * refreshes and the others reuse its result.
     *
     * @param rejectedAccessToken the token the server just refused.
     */
    fun refreshBlocking(rejectedAccessToken: String?): RefreshOutcome = synchronized(refreshLock) {
        val session = tokenStore.session ?: return RefreshOutcome.Rejected
        if (session.accessToken != rejectedAccessToken) {
            // Another request already refreshed while this one waited.
            return RefreshOutcome.Refreshed(session.accessToken, session.refreshToken)
        }
        val refreshToken = session.refreshToken ?: run {
            onSessionExpired()
            return RefreshOutcome.Rejected
        }
        when (val outcome = oauth.refresh(session.tokenEndpoint, refreshToken)) {
            is RefreshOutcome.Refreshed -> {
                tokenStore.save(
                    session.copy(
                        accessToken = outcome.accessToken,
                        // No rotation on this server: keep the one we have when none comes back.
                        refreshToken = outcome.refreshToken ?: refreshToken,
                    ),
                )
                outcome
            }
            RefreshOutcome.Rejected -> {
                onSessionExpired()
                outcome
            }
            RefreshOutcome.Unavailable -> outcome
        }
    }

    /**
     * The server no longer accepts this session (refresh refused, or the
     * device was removed from the website). Clears it and tells the user,
     * since the gateway stops working until they sign in again.
     */
    fun onSessionExpired() {
        if (tokenStore.session == null) return
        tokenStore.clear()
        _error.value = AuthError.SESSION_EXPIRED
        _state.value = AuthState.SignedOut
        GatewayNotifications.showSessionExpired(appContext)
    }

    /** Revokes the refresh token on the server (best effort) and forgets the session. */
    suspend fun signOut() = withContext(Dispatchers.IO) {
        val session = tokenStore.session
        if (session?.revocationEndpoint != null) {
            // Revoking either token revokes the grant; prefer the long-lived one.
            withTimeoutOrNull(REVOKE_TIMEOUT_MS) {
                runInterruptible { oauth.revoke(session.revocationEndpoint, session.refreshToken ?: session.accessToken) }
            }
        }
        tokenStore.clear()
        _error.value = null
        _state.value = AuthState.SignedOut
    }

    private fun fail(error: AuthError, cause: Throwable? = null) {
        if (cause != null) Log.w(TAG, "Sign-in failed: $error", cause)
        _error.value = error
        _state.value = AuthState.SignedOut
    }

    private companion object {
        const val TAG = "AuthRepository"
        const val REVOKE_TIMEOUT_MS = 10_000L
        const val KEY_STATE = "state"
        const val KEY_VERIFIER = "verifier"
        const val KEY_TOKEN_ENDPOINT = "token_endpoint"
        const val KEY_REVOCATION_ENDPOINT = "revocation_endpoint"
    }
}
