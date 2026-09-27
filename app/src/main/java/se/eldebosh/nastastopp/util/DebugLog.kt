package se.eldebosh.nastastopp.util

import android.util.Log
import se.eldebosh.nastastopp.BuildConfig

/**
 * Logging that only exists in debug builds. Addresses / OCR text must never reach logcat in
 * release: every call is guarded by BuildConfig.DEBUG and the message is built lazily.
 */
object DebugLog {
    const val TAG = "NastaStopp"

    inline fun d(message: () -> String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message())
    }

    inline fun w(t: Throwable? = null, message: () -> String) {
        if (BuildConfig.DEBUG) Log.w(TAG, message(), t)
    }
}
