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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import se.eldebosh.nastastopp.core.route.Announcement
import se.eldebosh.nastastopp.core.route.Announcements
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

    private val _said = MutableSharedFlow<Int>(extraBufferCapacity = 4)

    /**
     * Each part of a next-stop announcement once it has been said, by its place in
     * [Announcements.parts] (0 "Nästa stopp …", 1 "Därefter …"), so a passenger display on this
     * device moves on with the voice.
     */
    val said: SharedFlow<Int> = _said.asSharedFlow()

    private var tts: TextToSpeech? = null
    private var pending: Announcement? = null
    private var pendingAtMs = 0L
    private val counter = AtomicInteger()
    @Volatile private var lastUtteranceId: String? = null
    /** The next-stop announcement being said, whose parts [said] tells. */
    @Volatile private var currentId = 0
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

    /**
     * Says [announcement]. [interrupt] = false queues it after what is being said (the street's name
     * said by itself must not cut off a next-stop announcement); such a line is dropped, not kept for
     * later, when the voice is not ready.
     */
    fun speak(announcement: Announcement, interrupt: Boolean = true) {
        val t = tts
        if (t == null || _status.value != TtsStatus.READY) {
            if (!interrupt) return
            pending = announcement
            pendingAtMs = SystemClock.elapsedRealtime()
            return
        }
        val s = settings.current
        val id = counter.incrementAndGet()
        requestFocus()
        t.setSpeechRate(s.speechRate)
        t.setLanguage(swedish)
        val mode = if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val parts = Announcements.parts(announcement.swedish)
        if (Announcements.isNextStops(announcement.swedish)) {
            // In step with the passenger display: silent while the next stop comes into view, and
            // between its part and "Därefter …" while that comes in its place.
            currentId = id
            silence(t, Announcements.LEAD_MS, mode, "lead-$id")
            parts.forEachIndexed { k, part ->
                if (k > 0) silence(t, Announcements.GAP_MS, TextToSpeech.QUEUE_ADD, "gap-$id-$k")
                val svId = "$PART$id-$k"
                lastUtteranceId = svId
                t.speak(part, TextToSpeech.QUEUE_ADD, null, svId)
            }
        } else {
            val svId = "sv-$id"
            lastUtteranceId = svId
            t.speak(announcement.swedish, mode, null, svId)
        }
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

    private fun silence(t: TextToSpeech, ms: Long, mode: Int, utteranceId: String) {
        runCatching { t.playSilentUtterance(ms, mode, utteranceId) }
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
            partOf(utteranceId)?.let { _said.tryEmit(it) }
            if (utteranceId == lastUtteranceId) abandonFocus()
        }

        /** The part of the current next-stop announcement that [utteranceId] is ("part-7-1" → 1). */
        private fun partOf(utteranceId: String?): Int? {
            val rest = utteranceId?.removePrefix(PART)?.takeIf { it != utteranceId } ?: return null
            val id = rest.substringBefore('-').toIntOrNull() ?: return null
            return rest.substringAfter('-').toIntOrNull()?.takeIf { id == currentId }
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
        private const val PART = "part-"
        private const val PENDING_MAX_AGE_MS = 20_000L
    }
}
