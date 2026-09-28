package se.eldebosh.nastastopp.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import se.eldebosh.nastastopp.R
import se.eldebosh.nastastopp.tts.TtsStatus
import se.eldebosh.nastastopp.ui.BigButton
import se.eldebosh.nastastopp.ui.theme.Located
import se.eldebosh.nastastopp.util.SystemIntents

private enum class Step(@StringRes val title: Int, @StringRes val body: Int, @DrawableRes val icon: Int) {
    WELCOME(R.string.onb_welcome_title, R.string.onb_welcome_body, R.drawable.ic_pin),
    LOCATION(R.string.onb_location_title, R.string.onb_location_body, R.drawable.ic_navigation),
    NOTIFICATIONS(R.string.onb_notif_title, R.string.onb_notif_body, R.drawable.ic_next),
    BATTERY(R.string.onb_battery_title, R.string.onb_battery_body, R.drawable.ic_settings),
    OVERLAY(R.string.onb_overlay_title, R.string.onb_overlay_body, R.drawable.ic_hand),
    VOICE(R.string.onb_voice_title, R.string.onb_voice_body, R.drawable.ic_speaker),
}

/** First-run permissions, explained one at a time (Arabic by default). */
@Composable
fun OnboardingScreen(
    resumeTick: Int,
    ttsStatus: TtsStatus,
    onTestVoice: () -> Unit,
    onVoiceMissing: () -> Unit,
    onFinish: () -> Unit,
    onChooseDisplay: () -> Unit,
) {
    val context = LocalContext.current
    val steps = Step.entries.filter { it != Step.NOTIFICATIONS || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU }
    var index by rememberSaveable { mutableIntStateOf(0) }
    val step = steps[index.coerceIn(0, steps.lastIndex)]
    fun advance() {
        if (index >= steps.lastIndex) onFinish() else index++
    }

    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { advance() }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { advance() }

    // Re-read the permission state after returning from a system settings screen (resumeTick).
    val granted: Boolean? = remember(step, resumeTick, ttsStatus) {
        when (step) {
        Step.WELCOME -> null
        Step.LOCATION -> SystemIntents.hasLocation(context)
        Step.NOTIFICATIONS -> SystemIntents.hasNotifications(context)
        Step.BATTERY -> SystemIntents.isIgnoringBatteryOptimizations(context)
        Step.OVERLAY -> SystemIntents.canDrawOverlays(context)
        Step.VOICE -> ttsStatus == TtsStatus.READY
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.onb_step, index + 1, steps.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LinearProgressIndicator(progress = { (index + 1f) / steps.size }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Icon(painterResource(step.icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
        Text(stringResource(step.title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(step.body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (granted == true) {
            Text(
                if (step == Step.VOICE) stringResource(R.string.voice_ready) else stringResource(R.string.onb_granted),
                style = MaterialTheme.typography.titleMedium,
                color = Located,
            )
        }
        Spacer(Modifier.height(8.dp))

        when (step) {
            Step.WELCOME -> {
                // Role: this device controls the route (default) or is a passenger display.
                BigButton(stringResource(R.string.role_controller), { advance() }, Modifier.fillMaxWidth(), icon = R.drawable.ic_navigation, minHeight = 76.dp)
                BigButton(stringResource(R.string.role_display), onChooseDisplay, Modifier.fillMaxWidth(), icon = R.drawable.ic_display, primary = false)
                Text(stringResource(R.string.role_display_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Step.VOICE -> {
                if (granted == true) {
                    BigButton(stringResource(R.string.settings_test_voice), onTestVoice, Modifier.fillMaxWidth(), icon = R.drawable.ic_speaker, primary = false)
                } else {
                    BigButton(stringResource(R.string.help_voice_button), onVoiceMissing, Modifier.fillMaxWidth(), icon = R.drawable.ic_speaker)
                }
                BigButton(stringResource(R.string.onb_finish), onFinish, Modifier.fillMaxWidth(), minHeight = 76.dp, primary = granted == true)
            }
            else -> {
                if (granted != true) {
                    BigButton(
                        stringResource(R.string.allow),
                        {
                            when (step) {
                                Step.LOCATION -> locationLauncher.launch(
                                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                                )
                                Step.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                Step.BATTERY -> SystemIntents.requestIgnoreBatteryOptimizations(context)
                                Step.OVERLAY -> SystemIntents.openOverlaySettings(context)
                            }
                        },
                        Modifier.fillMaxWidth(),
                        minHeight = 76.dp,
                    )
                }
                BigButton(
                    stringResource(if (granted == true) R.string.next_step else R.string.skip),
                    { advance() },
                    Modifier.fillMaxWidth(),
                    primary = granted == true,
                )
            }
        }
    }
}
