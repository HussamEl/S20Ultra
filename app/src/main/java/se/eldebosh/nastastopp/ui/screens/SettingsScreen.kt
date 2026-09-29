package se.eldebosh.nastastopp.ui.screens

import androidx.annotation.StringRes
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import se.eldebosh.nastastopp.core.geo.AnnouncementDetail
import se.eldebosh.nastastopp.geo.StreetMapStore
import se.eldebosh.nastastopp.link.DisplayLinkServer
import se.eldebosh.nastastopp.settings.AppSettings
import se.eldebosh.nastastopp.settings.Appearance
import se.eldebosh.nastastopp.tts.TtsStatus
import se.eldebosh.nastastopp.ui.AppButton
import se.eldebosh.nastastopp.ui.AppCard
import se.eldebosh.nastastopp.ui.CardDivider
import se.eldebosh.nastastopp.ui.Chevron
import se.eldebosh.nastastopp.ui.ListRow
import se.eldebosh.nastastopp.ui.SectionTitle
import se.eldebosh.nastastopp.ui.TopBar
import se.eldebosh.nastastopp.ui.ref
import se.eldebosh.nastastopp.ui.refCorner
import se.eldebosh.nastastopp.ui.theme.AppTheme
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class PermissionStatus(
    val location: Boolean,
    /** Location is allowed, but only approximately (no street names). */
    val locationApproximate: Boolean = false,
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
    onLocation: () -> Unit,
    onNotifications: () -> Unit,
    onOverlay: () -> Unit,
    onBattery: () -> Unit,
    onVoice: () -> Unit,
    link: DisplayLinkServer.State,
    onToggleLink: (Boolean) -> Unit,
    onFixLink: () -> Unit,
    /** Whether a YouDrive login is saved on this phone (its values never reach this screen). */
    youDriveLoginSaved: Boolean,
    onSaveYouDriveLogin: (username: String, password: String) -> Unit,
    onDeleteYouDriveLogin: () -> Unit,
    streetMap: StreetMapStore.State,
    onDownloadStreetMap: () -> Unit,
    onDeleteStreetMap: () -> Unit,
) {
    var loginDialog by remember { mutableStateOf(false) }
    if (loginDialog) {
        YouDriveLoginDialog(
            saved = youDriveLoginSaved,
            onSave = { u, p -> onSaveYouDriveLogin(u, p); loginDialog = false },
            onDelete = { onDeleteYouDriveLogin(); loginDialog = false },
            onDismiss = { loginDialog = false },
        )
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.settings_title), onBack = onBack, backRef = 100)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionTitle(stringResource(R.string.settings_language))
            AppCard {
                listOf(Triple("en", R.string.lang_en, 101), Triple("ar", R.string.lang_ar, 102), Triple("sv", R.string.lang_sv, 103))
                    .forEachIndexed { i, (code, label, ref) ->
                        if (i > 0) CardDivider()
                        RadioRow(stringResource(label), settings.uiLanguage == code, ref) { onLanguage(code) }
                    }
                CardDivider()
                SwitchRow(R.string.settings_explain_arabic, R.string.settings_explain_arabic_hint, settings.explanationsArabic, 104) { v ->
                    onUpdate { it.copy(explanationsArabic = v) }
                }
                CardDivider()
                SwitchRow(R.string.settings_ref_numbers, R.string.settings_ref_numbers_hint, settings.showRefNumbers, 105) { v ->
                    onUpdate { it.copy(showRefNumbers = v) }
                }
            }

            SectionTitle(stringResource(R.string.settings_appearance), help = R.string.appearance_hint, helpRef = 133)
            AppCard {
                listOf(
                    Triple(Appearance.DAY, R.string.appearance_day, 130),
                    Triple(Appearance.NIGHT, R.string.appearance_night, 131),
                    Triple(Appearance.AUTOMATIC, R.string.appearance_auto, 132),
                ).forEachIndexed { i, (mode, label, ref) ->
                    if (i > 0) CardDivider()
                    RadioRow(stringResource(label), settings.appearance == mode, ref) { onUpdate { it.copy(appearance = mode) } }
                }
            }

            SectionTitle(stringResource(R.string.settings_detail))
            AppCard {
                RadioRow(stringResource(R.string.detail_full), settings.detail == AnnouncementDetail.FULL, 136) {
                    onUpdate { it.copy(detail = AnnouncementDetail.FULL) }
                }
                CardDivider()
                RadioRow(stringResource(R.string.detail_district), settings.detail == AnnouncementDetail.DISTRICT, 106) {
                    onUpdate { it.copy(detail = AnnouncementDetail.DISTRICT) }
                }
                CardDivider()
                RadioRow(stringResource(R.string.detail_town), settings.detail == AnnouncementDetail.TOWN_ONLY, 107) {
                    onUpdate { it.copy(detail = AnnouncementDetail.TOWN_ONLY) }
                }
                CardDivider()
                SwitchRow(R.string.settings_english, null, settings.englishRepeat, 108) { v -> onUpdate { it.copy(englishRepeat = v) } }
                CardDivider()
                SwitchRow(R.string.settings_say_street, R.string.help_say_street, settings.sayStreetChanges, 137) { v ->
                    onUpdate { it.copy(sayStreetChanges = v) }
                }
                CardDivider()
                var rate by remember(settings.speechRate) { mutableFloatStateOf(settings.speechRate) }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(
                        stringResource(R.string.settings_rate, String.format(Locale.ROOT, "%.1f", rate)),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Slider(
                        modifier = Modifier.ref(109),
                        value = rate,
                        onValueChange = { rate = (it * 10).roundToInt() / 10f },
                        onValueChangeFinished = { onUpdate { it.copy(speechRate = rate) } },
                        valueRange = 0.5f..1.5f,
                        steps = 9,
                    )
                    AppButton(stringResource(R.string.settings_test_voice), onTestVoice, Modifier.ref(111).fillMaxWidth(), icon = R.drawable.ic_speaker, primary = false)
                }
            }

            SectionTitle(stringResource(R.string.settings_display_section))
            LinkCard(link, onToggleLink, onFixLink, cardRef = 112, switchRef = 113)
            Spacer(Modifier.height(10.dp))
            AppCard {
                SwitchRow(R.string.display_full_address, R.string.display_full_address_hint, settings.displayFullAddress, 114) { v ->
                    onUpdate { it.copy(displayFullAddress = v) }
                }
                CardDivider()
                SwitchRow(R.string.settings_overlay_visible, null, !settings.overlayHidden, 115) { v -> onUpdate { it.copy(overlayHidden = !v) } }
            }

            SectionTitle(stringResource(R.string.settings_history))
            AppCard {
                listOf(12 to 116, 24 to 117, 168 to 118).forEachIndexed { i, (h, ref) ->
                    if (i > 0) CardDivider()
                    RadioRow(retentionLabel(h), settings.historyRetentionHours == h, ref) { onUpdate { it.copy(historyRetentionHours = h) } }
                }
            }

            // The offline street map for exact street names.
            SectionTitle(stringResource(R.string.settings_street_map), help = R.string.help_street_map)
            AppCard {
                ListRow(
                    title = stringResource(R.string.street_map_title),
                    subtitle = when (streetMap) {
                        StreetMapStore.State.None -> stringResource(R.string.street_map_none)
                        StreetMapStore.State.Loading -> stringResource(R.string.street_map_loading)
                        is StreetMapStore.State.Downloading -> stringResource(
                            if (streetMap.busy) R.string.street_map_downloading_busy else R.string.street_map_downloading,
                            streetMap.done,
                            streetMap.total,
                        )
                        is StreetMapStore.State.Ready -> stringResource(
                            R.string.street_map_ready,
                            streetMap.roads,
                            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(streetMap.createdAtMs)),
                        )
                        is StreetMapStore.State.Failed -> stringResource(R.string.street_map_failed, streetMap.done, streetMap.total)
                    },
                    subtitleColor = when (streetMap) {
                        is StreetMapStore.State.Ready -> AppTheme.colors.success
                        is StreetMapStore.State.Failed -> AppTheme.colors.danger
                        else -> null
                    },
                    onClick = if (streetMap is StreetMapStore.State.Downloading || streetMap is StreetMapStore.State.Loading) null else onDownloadStreetMap,
                    trailing = { Chevron() },
                    ref = 138,
                )
                if (streetMap is StreetMapStore.State.Ready) {
                    CardDivider()
                    ListRow(title = stringResource(R.string.street_map_delete), onClick = onDeleteStreetMap, ref = 139)
                }
            }

            // YouDrive's automatic sign-in.
            SectionTitle(stringResource(R.string.settings_youdrive))
            AppCard {
                SwitchRow(R.string.settings_youdrive_auto, R.string.help_youdrive_auto, settings.youDriveAutoSignIn, 156) { v ->
                    onUpdate { it.copy(youDriveAutoSignIn = v) }
                }
                CardDivider()
                ListRow(
                    title = stringResource(R.string.settings_youdrive_login),
                    subtitle = stringResource(if (youDriveLoginSaved) R.string.youdrive_login_saved else R.string.youdrive_login_none),
                    subtitleColor = if (youDriveLoginSaved) AppTheme.colors.success else null,
                    onClick = { loginDialog = true },
                    trailing = { Chevron() },
                    ref = 157,
                )
            }

            SectionTitle(stringResource(R.string.settings_permissions))
            AppCard {
                // Location only names the street the vehicle is on; the YouDrive page never gets it.
                StatusRow(
                    stringResource(R.string.settings_location),
                    permissions.location && !permissions.locationApproximate,
                    119,
                    onLocation,
                    statusOverride = if (permissions.locationApproximate) stringResource(R.string.location_approximate) else null,
                    help = R.string.help_location,
                )
                CardDivider()
                StatusRow(stringResource(R.string.settings_notifications), permissions.notifications, 120, onNotifications)
                CardDivider()
                StatusRow(stringResource(R.string.settings_overlay), permissions.overlay, 121, onOverlay)
                CardDivider()
                StatusRow(stringResource(R.string.settings_battery), permissions.battery, 122, onBattery)
                CardDivider()
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
            }

            Spacer(Modifier.height(12.dp))
            AppCard {
                ListRow(
                    title = stringResource(R.string.help_privacy_title),
                    help = R.string.settings_privacy_note,
                    icon = R.drawable.ic_located,
                    iconTint = AppTheme.colors.success,
                    ref = 124,
                )
            }
            // Version stamp: versionName + build date.
            Text(
                stringResource(R.string.settings_version, "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", BuildConfig.BUILD_DATE),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.ref(125).padding(start = 4.dp, top = 16.dp, bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, ref: Int, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .ref(ref),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(12.dp))
    }
}

@Composable
private fun StatusRow(
    label: String,
    ok: Boolean,
    ref: Int,
    onClick: () -> Unit,
    statusOverride: String? = null,
    @StringRes help: Int? = null,
) {
    ListRow(
        title = label,
        help = help,
        subtitle = statusOverride ?: stringResource(if (ok) R.string.perm_granted else R.string.perm_tap),
        subtitleColor = if (ok) AppTheme.colors.success else AppTheme.colors.danger,
        onClick = onClick,
        trailing = { Chevron() },
        ref = ref,
    )
}

@Composable
private fun SwitchRow(@StringRes label: Int, @StringRes help: Int?, checked: Boolean, ref: Int, onChange: (Boolean) -> Unit) {
    ListRow(
        title = stringResource(label),
        help = help,
        onClick = { onChange(!checked) },
        trailing = { Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.refCorner(ref)) },
    )
}

/**
 * Types the YouDrive login in once, to be kept encrypted on this phone. The fields always start
 * empty: a saved login is never shown again, only replaced or deleted.
 */
@Composable
private fun YouDriveLoginDialog(saved: Boolean, onSave: (String, String) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.youdrive_login_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.youdrive_login_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textMuted,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text(stringResource(R.string.youdrive_login_user)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, autoCorrectEnabled = false),
                    modifier = Modifier.ref(150).fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.youdrive_login_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    modifier = Modifier.ref(151).fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(user, password) }, enabled = user.isNotBlank() && password.isNotEmpty(), modifier = Modifier.ref(158)) {
                Text(stringResource(R.string.youdrive_login_save))
            }
        },
        dismissButton = {
            Row {
                if (saved) {
                    TextButton(onClick = onDelete, modifier = Modifier.ref(159)) {
                        Text(stringResource(R.string.youdrive_login_delete), color = AppTheme.colors.danger)
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.ref(154)) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
