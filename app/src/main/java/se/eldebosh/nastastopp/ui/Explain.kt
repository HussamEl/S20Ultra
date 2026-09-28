package se.eldebosh.nastastopp.ui

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.eldebosh.nastastopp.R

/**
 * Resources for explanation texts (the "?" popups, Help, onboarding). While the app is being set
 * up these stay in Arabic although the UI is English (setting "Explanations in Arabic");
 * null = UI language.
 */
val LocalExplainResources = staticCompositionLocalOf<Resources?> { null }

/** An explanation string (Arabic while [LocalExplainResources] says so). */
@Composable
@ReadOnlyComposable
fun explain(@StringRes id: Int, vararg args: Any): String {
    val res = LocalExplainResources.current ?: return stringResource(id, *args)
    return if (args.isEmpty()) res.getString(id) else res.getString(id, *args)
}

/** Explanation text inside a dialog; its direction follows the text, so Arabic reads right-to-left. */
@Composable
fun Hint(
    @StringRes id: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(explain(id), style = style.copy(textDirection = TextDirection.Content), color = color, modifier = modifier)
}

/** A very small "?" that shows explanation [id] when tapped (explanations never fill the screen). */
@Composable
fun HelpDot(@StringRes id: Int, modifier: Modifier = Modifier, title: String? = null) = HelpDot(explain(id), modifier, title)

/** A very small "?" that shows [text] in a popup when tapped. */
@Composable
fun HelpDot(text: String, modifier: Modifier = Modifier, title: String? = null) {
    var open by remember { mutableStateOf(false) }
    val description = stringResource(R.string.help_dot_desc)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button) { open = true }
            .semantics { contentDescription = description },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(16.dp).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
        ) {
            Text("?", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = title?.let { { Text(it, style = MaterialTheme.typography.titleLarge) } },
            text = {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.ok)) } },
        )
    }
}
