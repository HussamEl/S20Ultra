package se.eldebosh.nastastopp.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import se.eldebosh.nastastopp.core.route.Announcement
import se.eldebosh.nastastopp.settings.SettingsStore
import se.eldebosh.nastastopp.util.DebugLog
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

enum class TtsStatus { INITIALIZING, READY, MISSING_DATA, NOT_SUPPORTED, ERROR }

/**
 * Swedish announcements (optionally repeated in English) through TextToSpeech, as navigation
 * guidance audio. Other audio (Google Maps voice, music) is ducked while speaking via
 * AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, and focus is abandoned when the last utterance is done.
 */
class Announcer(context: Context, private val settings: SettingsStore) {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val swedish: Locale = Locale.forLanguageTag("sv-SE")
    private val english: Locale = Locale.forLanguageTag("en-US")
    private val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focusRequest: AudioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { }
        .build()

    private val _status = MutableStateFlow(TtsStatus.INITIALIZING)
    val status: StateFlow<TtsStatus> = _status.asStateFlow()

    private var tts: TextToSpeech? = null
    private var pending: Announcement? = null
    private var pendingAtMs = 0L
    private val counter = AtomicInteger()
    @Volatile private var lastUtteranceId: String? = null
    private var triedGoogleEngine = false
    private var generation = 0
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        initEngine(null)
    }

    private fun initEngine(enginePackage: String?) {
        val gen = ++generation
        _status.value = TtsStatus.INITIALIZING
        // The init callback can arrive synchronously from the constructor; post it so that
        // [tts] is assigned before it is handled.
        tts = TextToSpeech(appContext, { status ->
            mainHandler.post { if (gen == generation) onInit(status, enginePackage) }
        }, enginePackage)
    }

    private fun onInit(status: Int, enginePackage: String?) {
        val t = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            if (!tryGoogleEngine(t, enginePackage)) _status.value = TtsStatus.ERROR
            return
        }
        t.setAudioAttributes(attributes)
        t.setOnUtteranceProgressListener(progressListener)
        val availability = t.isLanguageAvailable(swedish)
        if (availability < TextToSpeech.LANG_AVAILABLE) {
            // Samsung's own engine often lacks Swedish; Google's engine usually has it.
            if (tryGoogleEngine(t, enginePackage)) return
            _status.value = if (availability == TextToSpeech.LANG_MISSING_DATA) TtsStatus.MISSING_DATA else TtsStatus.NOT_SUPPORTED
            return
        }
        t.setLanguage(swedish)
        _status.value = TtsStatus.READY
        pending?.let {
            pending = null
            // Only speak a queued announcement if it is still current (engine start-up delay).
            if (SystemClock.elapsedRealtime() - pendingAtMs < PENDING_MAX_AGE_MS) speak(it)
        }
    }

    private fun tryGoogleEngine(current: TextToSpeech, enginePackage: String?): Boolean {
        if (triedGoogleEngine || enginePackage == GOOGLE_TTS) return false
        val hasGoogle = runCatching { current.engines.any { it.name == GOOGLE_TTS } }.getOrDefault(false)
        val isDefault = runCatching { current.defaultEngine == GOOGLE_TTS }.getOrDefault(false)
        if (!hasGoogle || isDefault) return false
        triedGoogleEngine = true
        current.shutdown()
        initEngine(GOOGLE_TTS)
        return true
    }

    /** Re-checks the Swedish voice (e.g. after the user installed voice data). */
    fun recheck() {
        tts?.shutdown()
        tts = null
        triedGoogleEngine = false
        initEngine(null)
    }

    fun speak(announcement: Announcement) {
        val t = tts
        if (t == null || _status.value != TtsStatus.READY) {
            pending = announcement
            pendingAtMs = SystemClock.elapsedRealtime()
            return
        }
        val s = settings.current
        val id = counter.incrementAndGet()
        requestFocus()
        t.setSpeechRate(s.speechRate)
        t.setLanguage(swedish)
        val svId = "sv-$id"
        lastUtteranceId = svId
        t.speak(announcement.swedish, TextToSpeech.QUEUE_FLUSH, null, svId)
        val en = announcement.english
        if (s.englishRepeat && en != null && t.isLanguageAvailable(english) >= TextToSpeech.LANG_AVAILABLE) {
            t.setLanguage(english)
            val enId = "en-$id"
            lastUtteranceId = enId
            t.speak(en, TextToSpeech.QUEUE_ADD, null, enId)
            t.setLanguage(swedish)
        }
    }

    fun stop() {
        tts?.stop()
        abandonFocus()
    }

    private fun requestFocus() {
        runCatching { audioManager?.requestAudioFocus(focusRequest) }
    }

    private fun abandonFocus() {
        runCatching { audioManager?.abandonAudioFocusRequest(focusRequest) }
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}

        override fun onDone(utteranceId: String?) {
            if (utteranceId == lastUtteranceId) abandonFocus()
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            if (utteranceId == lastUtteranceId) abandonFocus()
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            DebugLog.d { "tts error $errorCode" }
            if (utteranceId == lastUtteranceId) abandonFocus()
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            if (utteranceId == lastUtteranceId) abandonFocus()
        }
    }

    companion object {
        const val GOOGLE_TTS = "com.google.android.tts"
        private const val PENDING_MAX_AGE_MS = 20_000L
    }
}
