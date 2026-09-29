package se.eldebosh.nastastopp.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

/** Permission checks and system settings screens used by onboarding / settings / help. */
object SystemIntents {

    fun hasLocation(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Precise location (GPS). Approximate location alone cannot tell which street you are on. */
    fun hasPreciseLocation(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasNotifications(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    private fun packageUri(context: Context) = "package:${context.packageName}".toUri()

    @SuppressLint("BatteryLife") // the app must keep running in the background while driving
    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (!start(context, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(context)))) {
            start(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    fun openOverlaySettings(context: Context) {
        if (!start(context, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri(context)))) {
            start(context, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }
    }

    fun openBluetoothSettings(context: Context) {
        start(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    }

    /** Asks the system to turn Bluetooth on (needs the Bluetooth permission on Android 12+). */
    fun requestEnableBluetooth(context: Context) {
        if (!start(context, Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE))) openBluetoothSettings(context)
    }

    fun openAppDetails(context: Context) {
        start(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context)))
    }

    fun installTtsData(context: Context) {
        if (!start(context, Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))) openTtsSettings(context)
    }

    fun openTtsSettings(context: Context): Boolean =
        start(context, Intent("com.android.settings.TTS_SETTINGS")) || start(context, Intent(Settings.ACTION_SETTINGS))

    private fun start(context: Context, intent: Intent): Boolean = try {
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }
}
