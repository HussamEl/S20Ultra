package se.eldebosh.nastastopp.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import se.eldebosh.nastastopp.R
import se.eldebosh.nastastopp.ui.BigButton
import se.eldebosh.nastastopp.ui.Paragraph
import se.eldebosh.nastastopp.ui.SectionTitle
import se.eldebosh.nastastopp.ui.TopBar

@Composable
fun HelpScreen(
    onBack: () -> Unit,
    onAppSettings: () -> Unit,
    onBatteryRequest: () -> Unit,
    onInstallVoice: () -> Unit,
    onTtsSettings: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.help_title), onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            SectionTitle(stringResource(R.string.help_battery_title))
            Paragraph(stringResource(R.string.help_battery_body))
            Spacer(Modifier.height(10.dp))
            BigButton(stringResource(R.string.help_battery_request), onBatteryRequest, Modifier.fillMaxWidth(), primary = false)
            Spacer(Modifier.height(10.dp))
            BigButton(stringResource(R.string.help_battery_button), onAppSettings, Modifier.fillMaxWidth(), icon = R.drawable.ic_settings, primary = false)

            SectionTitle(stringResource(R.string.help_voice_title))
            Paragraph(stringResource(R.string.help_voice_body))
            Spacer(Modifier.height(10.dp))
            BigButton(stringResource(R.string.help_voice_button), onInstallVoice, Modifier.fillMaxWidth(), icon = R.drawable.ic_speaker)
            Spacer(Modifier.height(10.dp))
            BigButton(stringResource(R.string.help_tts_settings_button), onTtsSettings, Modifier.fillMaxWidth(), primary = false)

            SectionTitle(stringResource(R.string.help_share_title))
            Paragraph(stringResource(R.string.help_share_body))

            SectionTitle(stringResource(R.string.help_display_title))
            Paragraph(stringResource(R.string.help_display_body))

            SectionTitle(stringResource(R.string.help_how_title))
            Paragraph(stringResource(R.string.help_how_body))

            SectionTitle(stringResource(R.string.help_overlay_title))
            Paragraph(stringResource(R.string.help_overlay_body))

            SectionTitle(stringResource(R.string.help_privacy_title))
            Paragraph(stringResource(R.string.settings_privacy_note))
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun TtsMissingScreen(onBack: () -> Unit, onInstall: () -> Unit, onRecheck: () -> Unit, onTtsSettings: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.tts_missing_title), onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
            Paragraph(stringResource(R.string.tts_missing_body))
            Spacer(Modifier.height(20.dp))
            BigButton(stringResource(R.string.tts_install), onInstall, Modifier.fillMaxWidth(), icon = R.drawable.ic_speaker, minHeight = 76.dp)
            Spacer(Modifier.height(12.dp))
            BigButton(stringResource(R.string.help_tts_settings_button), onTtsSettings, Modifier.fillMaxWidth(), primary = false)
            Spacer(Modifier.height(12.dp))
            BigButton(stringResource(R.string.tts_recheck), onRecheck, Modifier.fillMaxWidth(), icon = R.drawable.ic_repeat, primary = false)
        }
    }
}
