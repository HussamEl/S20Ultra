package se.eldebosh.nastastopp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import se.eldebosh.nastastopp.BuildConfig
import se.eldebosh.nastastopp.R
import androidx.compose.ui.text.style.TextDirection
import se.eldebosh.nastastopp.ui.ref
import se.eldebosh.nastastopp.ui.explain
import se.eldebosh.nastastopp.ui.Hint
import se.eldebosh.nastastopp.core.geo.AnnouncementDetail
import se.eldebosh.nastastopp.settings.AppSettings
import se.eldebosh.nastastopp.tts.TtsStatus
import se.eldebosh.nastastopp.link.DisplayLinkServer
import se.eldebosh.nastastopp.ui.BigButton
import se.eldebosh.nastastopp.ui.SectionTitle
import se.eldebosh.nastastopp.ui.TopBar
import se.eldebosh.nastastopp.ui.TouchTarget
import java.util.Locale
import kotlin.math.roundToInt

data class PermissionStatus(
    val notifications: Boolean,
    val overlay: Boolean,
    val battery: Boolean,
)

@Composable
fun SettingsScreen(
    settings: AppSettings,
    permissions: PermissionStatus,
    ttsStatus: TtsStatus,
    onBack: () -> Unit,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onLanguage: (String) -> Unit,
    onTestVoice: () -> Unit,
    onNotifications: () -> Unit,
    onOverlay: () -> Unit,
    onBattery: () -> Unit,
    onVoice: () -> Unit,
    link: DisplayLinkServer.State,
    onToggleLink: (Boolean) -> Unit,
    onFixLink: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.settings_title), onBack = onBack, backRef = 100)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        ) {
            SectionTitle(stringResource(R.string.settings_language))
            listOf(Triple("en", R.string.lang_en, 101), Triple("ar", R.string.lang_ar, 102), Triple("sv", R.string.lang_sv, 103))
                .forEach { (code, label, ref) ->
                    RadioRow(stringResource(label), settings.uiLanguage == code, ref) { onLanguage(code) }
                }
            SwitchRow(stringResource(R.string.settings_explain_arabic), explain(R.string.settings_explain_arabic_hint), settings.explanationsArabic, 104) { v ->
                onUpdate { it.copy(explanationsArabic = v) }
            }
            SwitchRow(stringResource(R.string.settings_ref_numbers), explain(R.string.settings_ref_numbers_hint), settings.showRefNumbers, 105) { v ->
                onUpdate { it.copy(showRefNumbers = v) }
            }

            SectionTitle(stringResource(R.string.settings_detail))
            RadioRow(stringResource(R.string.detail_district), settings.detail == AnnouncementDetail.DISTRICT, 106) {
                onUpdate { it.copy(detail = AnnouncementDetail.DISTRICT) }
            }
            RadioRow(stringResource(R.string.detail_town), settings.detail == AnnouncementDetail.TOWN_ONLY, 107) {
                onUpdate { it.copy(detail = AnnouncementDetail.TOWN_ONLY) }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .ref(108)
                    .fillMaxWidth()
                    .heightIn(min = TouchTarget)
                    .clickable(role = Role.Switch) { onUpdate { it.copy(englishRepeat = !it.englishRepeat) } }
                    .padding(top = 12.dp),
            ) {
                Text(stringResource(R.string.settings_english), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = settings.englishRepeat, onCheckedChange = { v -> onUpdate { it.copy(englishRepeat = v) } })
            }

            var rate by remember(settings.speechRate) { mutableFloatStateOf(settings.speechRate) }
            SectionTitle(stringResource(R.string.settings_rate, String.format(Locale.ROOT, "%.1f", rate)))
            Slider(
                modifier = Modifier.ref(109),
                value = rate,
                onValueChange = { rate = (it * 10).roundToInt() / 10f },
                onValueChangeFinished = { onUpdate { it.copy(speechRate = rate) } },
                valueRange = 0.5f..1.5f,
                steps = 9,
            )

            Spacer(Modifier.height(8.dp))
            BigButton(stringResource(R.string.settings_test_voice), onTestVoice, Modifier.ref(111).fillMaxWidth(), icon = R.drawable.ic_speaker)

            SectionTitle(stringResource(R.string.settings_display_section))
            LinkCard(link, onToggleLink, onFixLink, cardRef = 112, switchRef = 113)
            SwitchRow(
                stringResource(R.string.display_full_address),
                explain(R.string.display_full_address_hint),
                settings.displayFullAddress,
                114,
            ) { v -> onUpdate { it.copy(displayFullAddress = v) } }
            SwitchRow(stringResource(R.string.settings_overlay_visible), null, !settings.overlayHidden, 115) { v ->
                onUpdate { it.copy(overlayHidden = !v) }
            }

            SectionTitle(stringResource(R.string.settings_history))
            listOf(12 to 116, 24 to 117, 168 to 118).forEach { (h, ref) ->
                RadioRow(retentionLabel(h), settings.historyRetentionHours == h, ref) { onUpdate { it.copy(historyRetentionHours = h) } }
            }

            SectionTitle(stringResource(R.string.settings_permissions))
            StatusRow(stringResource(R.string.settings_notifications), permissions.notifications, 120, onNotifications)
            StatusRow(stringResource(R.string.settings_overlay), permissions.overlay, 121, onOverlay)
            StatusRow(stringResource(R.string.settings_battery), permissions.battery, 122, onBattery)
            StatusRow(
                stringResource(R.string.settings_voice),
                ttsStatus == TtsStatus.READY,
                123,
                onVoice,
                statusOverride = when (ttsStatus) {
                    TtsStatus.READY -> stringResource(R.string.voice_ready)
                    TtsStatus.INITIALIZING -> stringResource(R.string.voice_checking)
                    else -> stringResource(R.string.voice_missing)
                },
            )

            Spacer(Modifier.height(16.dp))
            Hint(R.string.settings_privacy_note, Modifier.ref(124).fillMaxWidth(), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(20.dp))
            // Version stamp: versionName + build date.
            Text(
                stringResource(R.string.settings_version, BuildConfig.VERSION_NAME, BuildConfig.BUILD_DATE),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.ref(125).padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, ref: Int, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .ref(ref)
            .fillMaxWidth()
            .heightIn(min = TouchTarget)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean, ref: Int, onClick: () -> Unit, statusOverride: String? = null) {
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .ref(ref)
            .fillMaxWidth()
            .heightIn(min = TouchTarget)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            statusOverride ?: stringResource(if (ok) R.string.perm_granted else R.string.perm_tap),
            style = MaterialTheme.typography.bodyMedium,
            color = if (ok) se.eldebosh.nastastopp.ui.theme.Located else se.eldebosh.nastastopp.ui.theme.NotLocated,
        )
    }
}

@Composable
private fun SwitchRow(label: String, hint: String?, checked: Boolean, ref: Int, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .ref(ref)
            .fillMaxWidth()
            .heightIn(min = TouchTarget)
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(vertical = 6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
