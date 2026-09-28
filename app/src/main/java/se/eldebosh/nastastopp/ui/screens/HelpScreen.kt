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
import se.eldebosh.nastastopp.ui.ref
import se.eldebosh.nastastopp.ui.explain
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
        TopBar(stringResource(R.string.help_title), onBack = onBack, backRef = 160)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            SectionTitle(stringResource(R.string.help_battery_title))
            Paragraph(explain(R.string.help_battery_body), Modifier.ref(161))
            Spacer(Modifier.height(10.dp))
            BigButton(stringResource(R.string.help_battery_request), onBatteryRequest, Modifier.ref(162).fillMaxWidth(), primary = false)
            Spacer(Modifier.height(10.dp))
            BigButton(stringResource(R.string.help_battery_button), onAppSettings, Modifier.ref(163).fillMaxWidth(), icon = R.drawable.ic_settings, primary = false)

            SectionTitle(stringResource(R.string.help_voice_title))
            Paragraph(explain(R.string.help_voice_body), Modifier.ref(164))
            Spacer(Modifier.height(10.dp))
            BigButton(stringResource(R.string.help_voice_button), onInstallVoice, Modifier.ref(165).fillMaxWidth(), icon = R.drawable.ic_speaker)
            Spacer(Modifier.height(10.dp))
            BigButton(stringResource(R.string.help_tts_settings_button), onTtsSettings, Modifier.ref(166).fillMaxWidth(), primary = false)

            SectionTitle(stringResource(R.string.help_share_title))
            Paragraph(explain(R.string.help_share_body), Modifier.ref(167))

            SectionTitle(stringResource(R.string.help_display_title))
            Paragraph(explain(R.string.help_display_body), Modifier.ref(168))

            SectionTitle(stringResource(R.string.help_how_title))
            Paragraph(explain(R.string.help_how_body), Modifier.ref(169))

            SectionTitle(stringResource(R.string.help_overlay_title))
            Paragraph(explain(R.string.help_overlay_body), Modifier.ref(170))

            SectionTitle(stringResource(R.string.help_youdrive_title))
            Paragraph(explain(R.string.help_youdrive_body), Modifier.ref(171))

            SectionTitle(stringResource(R.string.help_refs_title))
            Paragraph(explain(R.string.help_refs_body), Modifier.ref(172))

            SectionTitle(stringResource(R.string.help_privacy_title))
            Paragraph(explain(R.string.settings_privacy_note), Modifier.ref(173))
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun TtsMissingScreen(onBack: () -> Unit, onInstall: () -> Unit, onRecheck: () -> Unit, onTtsSettings: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.tts_missing_title), onBack = onBack, backRef = 175)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
            Paragraph(explain(R.string.tts_missing_body), Modifier.ref(176))
            Spacer(Modifier.height(20.dp))
            BigButton(stringResource(R.string.tts_install), onInstall, Modifier.ref(177).fillMaxWidth(), icon = R.drawable.ic_speaker, minHeight = 76.dp)
            Spacer(Modifier.height(12.dp))
            BigButton(stringResource(R.string.help_tts_settings_button), onTtsSettings, Modifier.ref(178).fillMaxWidth(), primary = false)
            Spacer(Modifier.height(12.dp))
            BigButton(stringResource(R.string.tts_recheck), onRecheck, Modifier.ref(179).fillMaxWidth(), icon = R.drawable.ic_repeat, primary = false)
        }
    }
}
