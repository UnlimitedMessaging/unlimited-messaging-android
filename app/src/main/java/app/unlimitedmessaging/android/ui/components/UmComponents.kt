package app.unlimitedmessaging.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.unlimitedmessaging.android.ui.theme.Bricolage
import app.unlimitedmessaging.android.ui.theme.LocalUmColors

/**
 * A section of a screen: white on the off-white page, a faint teal border,
 * no shadow. The website's cards, in Compose.
 */
@Composable
fun UmCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(20.dp), content = content)
    }
}

private val ButtonPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)

/** Deep green, white bold label, 8dp corners. */
@Composable
fun UmPrimaryButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        contentPadding = ButtonPadding,
        elevation = null,
        content = content,
    )
}

/** Green outline on white: secondary actions. */
@Composable
fun UmSecondaryButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = MaterialTheme.shapes.small,
        contentPadding = ButtonPadding,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        content = content,
    )
}

/** Mist background, green label: compact actions inside a card. */
@Composable
fun UmTonalButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        elevation = null,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        content = content,
    )
}

@Composable
fun UmLinkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.small) {
        Text(text, color = LocalUmColors.current.link, style = MaterialTheme.typography.labelLarge)
    }
}

/** "Unlimited" in ink, "Messaging" in green: the website's wordmark. */
@Composable
fun UmWordmark(modifier: Modifier = Modifier, size: Float = 22f) {
    val ink = MaterialTheme.colorScheme.onBackground
    val green = MaterialTheme.colorScheme.primary
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = ink)) { append("Unlimited") }
            withStyle(SpanStyle(color = green)) { append("Messaging") }
        },
        modifier = modifier,
        style = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = size.sp, letterSpacing = (-0.03 * size).sp),
    )
}

/** A small round status light. */
@Composable
fun StatusDot(color: Color, size: Dp = 10.dp) {
    Box(Modifier.size(size).background(color, CircleShape))
}
