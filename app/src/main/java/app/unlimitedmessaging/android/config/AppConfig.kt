package app.unlimitedmessaging.android.config

import app.unlimitedmessaging.android.BuildConfig

/**
 * Build-time settings. Every value comes from Gradle properties (see
 * gradle.properties), so pointing the app at another backend never needs a
 * code change.
 */
data class AppConfig(
    /** Root of the API, without a trailing slash, e.g. `https://api.unlimitedmessaging.app`. */
    val apiBaseUrl: String,
    /** The OAuth public client registered for this app on the backend. */
    val oauthClientId: String,
    /** Scope requested at sign-in. */
    val oauthScope: String,
    /** Where the browser sends the user back after signing in. */
    val oauthRedirectUri: String,
    /** How long the server may hold an outbox request open, in seconds. */
    val outboxWaitSeconds: Int = 30,
) {
    /** RFC 8414 authorization server metadata. */
    val discoveryUrl: String get() = "$apiBaseUrl/.well-known/oauth-authorization-server"

    companion object {
        fun fromBuildConfig() = AppConfig(
            apiBaseUrl = BuildConfig.API_BASE_URL.trimEnd('/'),
            oauthClientId = BuildConfig.OAUTH_CLIENT_ID,
            oauthScope = BuildConfig.OAUTH_SCOPE,
            // Private-use URI scheme named after the applicationId (RFC 8252
            // section 7.1). Must match the intent filter of OAuthRedirectActivity.
            oauthRedirectUri = "${BuildConfig.APPLICATION_ID}:/oauth2redirect",
        )
    }
}
