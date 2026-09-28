package se.eldebosh.nastastopp.ui

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection

/**
 * Resources for explanation texts (hints, help, onboarding). While the app is being set up these
 * stay in Arabic although the UI is English (setting "Explanations in Arabic"); null = UI language.
 */
val LocalExplainResources = staticCompositionLocalOf<Resources?> { null }

/** An explanation string (Arabic while [LocalExplainResources] says so). */
@Composable
@ReadOnlyComposable
fun explain(@StringRes id: Int, vararg args: Any): String {
    val res = LocalExplainResources.current ?: return stringResource(id, *args)
    return if (args.isEmpty()) res.getString(id) else res.getString(id, *args)
}

/** An explanation paragraph; its direction follows the text, so Arabic reads right-to-left. */
@Composable
fun Hint(
    @StringRes id: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(explain(id), style = style.copy(textDirection = TextDirection.Content), color = color, modifier = modifier)
}
