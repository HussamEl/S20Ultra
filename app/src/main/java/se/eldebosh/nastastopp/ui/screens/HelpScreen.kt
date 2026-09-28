package se.eldebosh.nastastopp.ui.screens

import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import se.eldebosh.nastastopp.R
import se.eldebosh.nastastopp.ui.AppButton
import se.eldebosh.nastastopp.ui.AppCard
import se.eldebosh.nastastopp.ui.HelpDot
import se.eldebosh.nastastopp.ui.Paragraph
import se.eldebosh.nastastopp.ui.TopBar
import se.eldebosh.nastastopp.ui.explain
import se.eldebosh.nastastopp.ui.ref

/** Help: one closed card per topic; tapping a card opens its explanation (and its buttons). */
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
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Topic(R.string.help_battery_title, R.string.help_battery_body, 161) {
                AppButton(stringResource(R.string.help_battery_request), onBatteryRequest, Modifier.ref(162).fillMaxWidth(), primary = false)
                AppButton(stringResource(R.string.help_battery_button), onAppSettings, Modifier.ref(163).fillMaxWidth(), icon = R.drawable.ic_settings, primary = false)
            }
            Topic(R.string.help_voice_title, R.string.help_voice_body, 164) {
                AppButton(stringResource(R.string.help_voice_button), onInstallVoice, Modifier.ref(165).fillMaxWidth(), icon = R.drawable.ic_speaker)
                AppButton(stringResource(R.string.help_tts_settings_button), onTtsSettings, Modifier.ref(166).fillMaxWidth(), primary = false)
            }
            Topic(R.string.help_share_title, R.string.help_share_body, 167)
            Topic(R.string.help_display_title, R.string.help_display_body, 168)
            Topic(R.string.help_how_title, R.string.help_how_body, 169)
            Topic(R.string.help_overlay_title, R.string.help_overlay_body, 170)
            Topic(R.string.help_youdrive_title, R.string.help_youdrive_body, 171)
            Topic(R.string.help_refs_title, R.string.help_refs_body, 172)
            Topic(R.string.help_privacy_title, R.string.settings_privacy_note, 173)
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** A help topic: its title; tap to open the explanation (and [actions]). */
@Composable
private fun Topic(@StringRes title: Int, @StringRes body: Int, ref: Int, actions: (@Composable ColumnScope.() -> Unit)? = null) {
    var open by rememberSaveable { mutableStateOf(false) }
    AppCard(Modifier.ref(ref).animateContentSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { open = !open }.heightIn(min = 56.dp).padding(horizontal = 16.dp),
        ) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(
                painterResource(R.drawable.ic_expand),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp).rotate(if (open) 180f else 0f),
            )
        }
        if (open) {
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Paragraph(explain(body))
                actions?.invoke(this)
            }
        }
    }
}

@Composable
fun TtsMissingScreen(onBack: () -> Unit, onInstall: () -> Unit, onRecheck: () -> Unit, onTtsSettings: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.tts_missing_title), onBack = onBack, backRef = 175) {
            HelpDot(R.string.tts_missing_body, Modifier.ref(176, centered = true), title = stringResource(R.string.tts_missing_title))
            Spacer(Modifier.size(8.dp))
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppButton(stringResource(R.string.tts_install), onInstall, Modifier.ref(177).fillMaxWidth(), icon = R.drawable.ic_speaker, minHeight = 52.dp)
            AppButton(stringResource(R.string.help_tts_settings_button), onTtsSettings, Modifier.ref(178).fillMaxWidth(), primary = false)
            AppButton(stringResource(R.string.tts_recheck), onRecheck, Modifier.ref(179).fillMaxWidth(), icon = R.drawable.ic_repeat, primary = false)
        }
    }
}
