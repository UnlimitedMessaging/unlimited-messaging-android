package app.unlimitedmessaging.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.unlimitedmessaging.android.R
import app.unlimitedmessaging.android.auth.AuthError
import app.unlimitedmessaging.android.auth.AuthState
import app.unlimitedmessaging.android.ui.components.UmCard
import app.unlimitedmessaging.android.ui.components.UmPrimaryButton
import app.unlimitedmessaging.android.ui.components.UmWordmark
import app.unlimitedmessaging.android.ui.theme.LocalUmColors

/**
 * A single button: the form itself is the website's, opened in a browser tab.
 */
@Composable
fun SignInScreen(
    state: AuthState,
    error: AuthError?,
    /** Shown in debug builds, to make the target backend obvious. */
    apiBaseUrl: String?,
    onSignIn: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.safeDrawingPadding().padding(horizontal = 20.dp, vertical = 24.dp).fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            UmWordmark(size = 28f)
            Spacer(Modifier.height(28.dp))

            UmCard {
                Text(
                    stringResource(R.string.sign_in_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.sign_in_explainer),
                    style = MaterialTheme.typography.bodyLarge,
                    color = LocalUmColors.current.textSecondary,
                )
                Spacer(Modifier.height(24.dp))

                val busy = state == AuthState.AwaitingBrowser || state == AuthState.Exchanging
                Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    } else {
                        UmPrimaryButton(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.sign_in_button))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.sign_in_methods),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalUmColors.current.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                error?.let {
                    Spacer(Modifier.height(16.dp))
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
                        Text(
                            stringResource(it.messageRes()),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                        )
                    }
                }
            }

            apiBaseUrl?.let {
                Spacer(Modifier.height(20.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = LocalUmColors.current.muted)
            }
        }
    }
}

private fun AuthError.messageRes(): Int = when (this) {
    AuthError.NETWORK -> R.string.sign_in_error_network
    AuthError.NO_BROWSER -> R.string.sign_in_error_no_browser
    AuthError.REJECTED -> R.string.sign_in_error_rejected
    AuthError.INVALID_REDIRECT -> R.string.sign_in_error_invalid_redirect
    AuthError.SESSION_EXPIRED -> R.string.sign_in_error_session_expired
}
