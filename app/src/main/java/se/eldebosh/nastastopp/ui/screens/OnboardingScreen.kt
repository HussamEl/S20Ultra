package se.eldebosh.nastastopp.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import se.eldebosh.nastastopp.R
import se.eldebosh.nastastopp.tts.TtsStatus
import se.eldebosh.nastastopp.ui.AppButton
import se.eldebosh.nastastopp.ui.HelpDot
import se.eldebosh.nastastopp.ui.ref
import se.eldebosh.nastastopp.ui.refCorner
import se.eldebosh.nastastopp.ui.theme.AppTheme
import se.eldebosh.nastastopp.util.SystemIntents

private enum class Step(@StringRes val title: Int, @StringRes val body: Int, @DrawableRes val icon: Int) {
    WELCOME(R.string.onb_welcome_title, R.string.onb_welcome_body, R.drawable.ic_pin),
    NOTIFICATIONS(R.string.onb_notif_title, R.string.onb_notif_body, R.drawable.ic_next),
    BATTERY(R.string.onb_battery_title, R.string.onb_battery_body, R.drawable.ic_settings),
    OVERLAY(R.string.onb_overlay_title, R.string.onb_overlay_body, R.drawable.ic_hand),
    VOICE(R.string.onb_voice_title, R.string.onb_voice_body, R.drawable.ic_speaker),
}

/** First-run permissions, explained one at a time (explanations in Arabic while setting up). */
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

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { advance() }

    // Re-read the permission state after returning from a system settings screen (resumeTick).
    val granted: Boolean? = remember(step, resumeTick, ttsStatus) {
        when (step) {
        Step.WELCOME -> null
        Step.NOTIFICATIONS -> SystemIntents.hasNotifications(context)
        Step.BATTERY -> SystemIntents.isIgnoringBatteryOptimizations(context)
        Step.OVERLAY -> SystemIntents.canDrawOverlays(context)
        Step.VOICE -> ttsStatus == TtsStatus.READY
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.onb_step, index + 1, steps.size), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.ref(180))
        LinearProgressIndicator(
            progress = { (index + 1f) / steps.size },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            drawStopIndicator = {},
        )
        Spacer(Modifier.height(20.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(22.dp)).background(AppTheme.colors.accent),
        ) {
            Icon(painterResource(step.icon), contentDescription = null, tint = AppTheme.colors.onAccent, modifier = Modifier.size(36.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(step.title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.ref(181).weight(1f, fill = false))
            HelpDot(step.body, Modifier.refCorner(182), title = stringResource(step.title))
        }
        if (granted == true) {
            Text(
                if (step == Step.VOICE) stringResource(R.string.voice_ready) else stringResource(R.string.onb_granted),
                style = MaterialTheme.typography.titleMedium,
                color = AppTheme.colors.success,
                modifier = Modifier.ref(183),
            )
        }
        Spacer(Modifier.height(12.dp))

        when (step) {
            Step.WELCOME -> {
                // Role: this device controls the route (default) or is a passenger display.
                AppButton(stringResource(R.string.role_controller), { advance() }, Modifier.ref(184).fillMaxWidth(), icon = R.drawable.ic_navigation, minHeight = 52.dp)
                Row(verticalAlignment = Alignment.Bottom) {
                    AppButton(stringResource(R.string.role_display), onChooseDisplay, Modifier.ref(185).weight(1f), icon = R.drawable.ic_display, primary = false)
                    HelpDot(R.string.role_display_hint, Modifier.refCorner(186).padding(start = 4.dp, bottom = 8.dp), title = stringResource(R.string.role_display))
                }
            }
            Step.VOICE -> {
                if (granted == true) {
                    AppButton(stringResource(R.string.settings_test_voice), onTestVoice, Modifier.ref(189).fillMaxWidth(), icon = R.drawable.ic_speaker, primary = false)
                } else {
                    AppButton(stringResource(R.string.help_voice_button), onVoiceMissing, Modifier.ref(189).fillMaxWidth(), icon = R.drawable.ic_speaker)
                }
                AppButton(stringResource(R.string.onb_finish), onFinish, Modifier.ref(190).fillMaxWidth(), minHeight = 52.dp, primary = granted == true)
            }
            else -> {
                if (granted != true) {
                    AppButton(
                        stringResource(R.string.allow),
                        {
                            when (step) {
                                Step.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                Step.BATTERY -> SystemIntents.requestIgnoreBatteryOptimizations(context)
                                Step.OVERLAY -> SystemIntents.openOverlaySettings(context)
                            }
                        },
                        Modifier.ref(187).fillMaxWidth(),
                        minHeight = 52.dp,
                    )
                }
                AppButton(
                    stringResource(if (granted == true) R.string.next_step else R.string.skip),
                    { advance() },
                    Modifier.ref(188).fillMaxWidth(),
                    primary = granted == true,
                )
            }
        }
    }
}
