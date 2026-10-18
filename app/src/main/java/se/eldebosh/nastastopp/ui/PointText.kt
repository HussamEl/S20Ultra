package se.eldebosh.nastastopp.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import se.eldebosh.nastastopp.R
import se.eldebosh.nastastopp.core.geo.Coordinates
import se.eldebosh.nastastopp.ui.theme.AppTheme

/**
 * A point on the driver's screens (322): "59.381234, 13.501234" ([Coordinates.format]) small and
 * muted in even-width digits, with a copy sign, as one touch target. A tap copies exactly that
 * text ([copyPoint]); nothing else follows on its own. Never on what the passengers see: a point
 * pins the house more exactly than its number, which the passenger display never writes.
 */
@Composable
fun PointText(
    lat: Double,
    lng: Double,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.textMuted,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    icon: Dp = 16.dp,
) {
    val text = remember(lat, lng) { Coordinates.format(lat, lng) }
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copy = stringResource(R.string.point_copy)
    val description = stringResource(R.string.point_desc, text)
    val label = stringResource(R.string.point_clip_label)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .ref(322)
            .heightIn(min = TouchTarget)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClickLabel = copy, role = Role.Button) {
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(pointClip(label, text)))
                    copied(context)
                }
            }
            .semantics { contentDescription = description }
            .padding(horizontal = 4.dp),
    ) {
        Icon(painterResource(R.drawable.ic_copy), contentDescription = null, tint = color, modifier = Modifier.size(icon))
        Spacer(Modifier.width(6.dp))
        // Digits of one width (they line up from row to row), left to right in every language.
        Text(text, style = style.copy(fontFeatureSettings = "tnum", textDirection = TextDirection.Ltr), color = color, maxLines = 1)
    }
}

/**
 * The clip for a point: [text] as it is shown, marked sensitive so the system never previews it
 * (its own extra from Android 13; the same key before it, which those versions' keyboards read).
 */
fun pointClip(label: String, text: String): ClipData = ClipData.newPlainText(label, text).apply {
    val key = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ClipDescription.EXTRA_IS_SENSITIVE else SENSITIVE_KEY
    description.extras = PersistableBundle().apply { putBoolean(key, true) }
}

/** Android 13 and later confirm a copy themselves; before them a short toast does, after the driver's tap. */
private fun copied(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, R.string.point_copied, Toast.LENGTH_SHORT).show()
}

/** [ClipDescription.EXTRA_IS_SENSITIVE]'s value, for the versions before it was named. */
private const val SENSITIVE_KEY = "android.content.extra.IS_SENSITIVE"
