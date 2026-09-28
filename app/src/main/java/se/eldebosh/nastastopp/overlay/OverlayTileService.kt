package se.eldebosh.nastastopp.overlay

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import se.eldebosh.nastastopp.App
import se.eldebosh.nastastopp.R
import se.eldebosh.nastastopp.settings.SettingsStore
import se.eldebosh.nastastopp.util.LocaleHelper

/**
 * Quick Settings tile "Floating button": shows or hides the floating button during a route with
 * one tap from the notification shade. Unavailable when no route is active.
 */
class OverlayTileService : TileService() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base, SettingsStore.readLanguage(base)))
    }

    override fun onStartListening() {
        super.onStartListening()
        update()
    }

    override fun onClick() {
        super.onClick()
        val graph = App.from(this).graph
        if (graph.controller.isActive && Settings.canDrawOverlays(this)) {
            graph.settings.update { it.copy(overlayHidden = !it.overlayHidden, overlayMinimized = false) }
        }
        update()
    }

    private fun update() {
        val tile = qsTile ?: return
        val graph = App.from(this).graph
        val available = graph.controller.isActive && Settings.canDrawOverlays(this)
        val hidden = graph.settings.current.overlayHidden
        tile.label = getString(R.string.tile_label)
        tile.state = when {
            !available -> Tile.STATE_UNAVAILABLE
            hidden -> Tile.STATE_INACTIVE
            else -> Tile.STATE_ACTIVE
        }
        tile.subtitle = getString(
            when {
                !available -> R.string.tile_no_route
                hidden -> R.string.tile_hidden
                else -> R.string.tile_shown
            },
        )
        tile.updateTile()
    }

    companion object {
        fun component(context: Context) = ComponentName(context, OverlayTileService::class.java)

        /** Android 13+: asks the system to add the tile to Quick Settings (one tap for the driver). */
        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        fun requestAdd(context: Context, onResult: (Boolean) -> Unit) {
            val sbm = context.getSystemService(StatusBarManager::class.java) ?: return onResult(false)
            try {
                sbm.requestAddTileService(
                    component(context),
                    context.getString(R.string.tile_label),
                    Icon.createWithResource(context, R.drawable.ic_tile),
                    context.mainExecutor,
                ) { result ->
                    onResult(
                        result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                            result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED,
                    )
                }
            } catch (_: Exception) {
                onResult(false)
            }
        }
    }
}
