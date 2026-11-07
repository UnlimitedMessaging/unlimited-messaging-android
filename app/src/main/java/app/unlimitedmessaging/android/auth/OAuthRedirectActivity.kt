package app.unlimitedmessaging.android.auth

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import app.unlimitedmessaging.android.container
import app.unlimitedmessaging.android.ui.MainActivity
import kotlinx.coroutines.launch

/**
 * Catches `<applicationId>:/oauth2redirect?code=...&state=...` when the
 * browser tab finishes the sign-in, starts the code exchange, and brings the
 * main screen back on top of the tab (which CLEAR_TOP closes).
 *
 * It has no UI of its own and is never kept in the back stack.
 */
class OAuthRedirectActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val redirect = intent?.data
        if (redirect != null) {
            val container = container
            container.auth.markExchanging()
            // App scope, not this activity's: it finishes right away.
            container.appScope.launch { container.auth.completeSignIn(redirect) }
        }
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }
}
