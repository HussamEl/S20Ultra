package se.eldebosh.nastastopp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import se.eldebosh.nastastopp.R
import se.eldebosh.nastastopp.route.model.RouteData
import se.eldebosh.nastastopp.tts.TtsStatus
import se.eldebosh.nastastopp.ui.BigButton
import se.eldebosh.nastastopp.ui.ButtonRow
import se.eldebosh.nastastopp.ui.theme.NotLocated

@Composable
fun HomeScreen(
    route: RouteData?,
    ttsStatus: TtsStatus,
    importing: Boolean,
    onImport: () -> Unit,
    onResume: () -> Unit,
    onReview: () -> Unit,
    onClear: () -> Unit,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
    onTtsMissing: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.home_subtitle), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))

        if (ttsStatus == TtsStatus.MISSING_DATA || ttsStatus == TtsStatus.NOT_SUPPORTED || ttsStatus == TtsStatus.ERROR) {
            BigButton(
                text = stringResource(R.string.tts_missing_banner),
                onClick = onTtsMissing,
                icon = R.drawable.ic_speaker,
                containerColor = NotLocated,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (route?.active == true) {
            BigButton(
                text = stringResource(R.string.home_resume),
                onClick = onResume,
                icon = R.drawable.ic_navigation,
                modifier = Modifier.fillMaxWidth(),
                minHeight = 80.dp,
            )
        } else if (route != null && route.stops.isNotEmpty()) {
            BigButton(
                text = stringResource(R.string.home_resume_draft),
                onClick = onReview,
                icon = R.drawable.ic_edit,
                modifier = Modifier.fillMaxWidth(),
                minHeight = 80.dp,
            )
        }

        BigButton(
            text = stringResource(R.string.home_import),
            onClick = onImport,
            icon = R.drawable.ic_images,
            enabled = !importing,
            primary = route == null || route.stops.isEmpty(),
            modifier = Modifier.fillMaxWidth(),
            minHeight = 80.dp,
        )
        Text(stringResource(R.string.home_share_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        ButtonRow {
            BigButton(stringResource(R.string.home_settings), onSettings, Modifier.weight(1f), icon = R.drawable.ic_settings, primary = false)
            BigButton(stringResource(R.string.home_help), onHelp, Modifier.weight(1f), icon = R.drawable.ic_help, primary = false)
        }
        if (route != null) {
            BigButton(
                text = stringResource(R.string.home_clear),
                onClick = { confirmClear = true },
                icon = R.drawable.ic_delete,
                primary = false,
                contentColor = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.confirm_clear_title)) },
            text = { Text(stringResource(R.string.confirm_clear_text)) },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onClear() }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
