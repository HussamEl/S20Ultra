package se.eldebosh.nastastopp.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat

/**
 * Keeps the window in step with the current look: dark status and navigation bar icons by day,
 * light ones by night (whatever the phone's own mode), and the window background behind the
 * content, so nothing flashes in the other colour.
 */
@Composable
fun SystemBarsFollowTheme() {
    val view = LocalView.current
    val night = AppTheme.colors.isDark
    val background = AppTheme.colors.background
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }
        window.setBackgroundDrawable(background.toArgb().toDrawable())
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
