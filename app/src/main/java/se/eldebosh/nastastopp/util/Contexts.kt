package se.eldebosh.nastastopp.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** The activity behind this context (a screen's context may be wrapped, e.g. for its language), or null. */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
