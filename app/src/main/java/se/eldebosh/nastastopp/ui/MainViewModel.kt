package se.eldebosh.nastastopp.ui

import android.app.Application
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import se.eldebosh.nastastopp.App
import se.eldebosh.nastastopp.R

enum class Screen { ONBOARDING, HOME, REVIEW, ACTIVE, SETTINGS, HELP, TTS_MISSING }

sealed interface ImportUi {
    data object Idle : ImportUi
    data class Running(val done: Int, val total: Int) : ImportUi
}

/** A snackbar message (resolved to text in the UI) with an optional undo action. */
data class UiMessage(@StringRes val text: Int, val arg: Int? = null, val undo: (() -> Unit)? = null)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val graph = App.from(app).graph

    private val _stack = MutableStateFlow(listOf(initialScreen()))
    val stack: StateFlow<List<Screen>> = _stack.asStateFlow()

    private val _import = MutableStateFlow<ImportUi>(ImportUi.Idle)
    val importState: StateFlow<ImportUi> = _import.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    private fun initialScreen(): Screen = when {
        !graph.settings.current.onboardingDone -> Screen.ONBOARDING
        graph.controller.isActive -> Screen.ACTIVE
        else -> Screen.HOME
    }

    val current: Screen get() = _stack.value.last()

    fun navigate(screen: Screen) {
        if (screen == Screen.REVIEW) graph.controller.beginEdit()
        if (current != screen) _stack.value = _stack.value + screen
    }

    /** Returns false if already at the root. */
    fun back(): Boolean {
        val s = _stack.value
        if (s.size <= 1) return false
        if (s.last() == Screen.REVIEW) graph.controller.finishEdit()
        _stack.value = s.dropLast(1)
        return true
    }

    /** "Back to route" from the review screen of an active route. */
    fun leaveReviewToActive() {
        graph.controller.finishEdit()
        val s = _stack.value.dropLast(1)
        _stack.value = if (s.lastOrNull() == Screen.ACTIVE) s else listOf(Screen.HOME, Screen.ACTIVE)
    }

    fun resetTo(vararg screens: Screen) {
        _stack.value = screens.toList().ifEmpty { listOf(Screen.HOME) }
    }

    fun finishOnboarding() {
        graph.settings.update { it.copy(onboardingDone = true) }
        resetTo(if (graph.controller.isActive) Screen.ACTIVE else Screen.HOME)
    }

    fun message(msg: UiMessage) {
        _messages.tryEmit(msg)
    }

    /** OCR + extraction of shared / picked screenshots, appended in the order received. */
    fun importImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _import.value = ImportUi.Running(0, uris.size)
            val result = try {
                graph.importer.import(uris) { done, total -> _import.value = ImportUi.Running(done, total) }
            } finally {
                _import.value = ImportUi.Idle
            }
            val added = graph.controller.addExtracted(result.stops)
            when {
                added > 0 -> message(UiMessage(R.string.import_result, added))
                result.failedImages > 0 -> message(UiMessage(R.string.import_failed, result.failedImages))
                else -> message(UiMessage(R.string.import_none))
            }
            if ((graph.controller.route.value?.stops?.size ?: 0) > 0) showReview()
        }
    }

    private fun showReview() {
        when (current) {
            Screen.REVIEW -> Unit
            Screen.ONBOARDING -> {
                resetTo(Screen.HOME)
                navigate(Screen.REVIEW)
            }
            else -> navigate(Screen.REVIEW)
        }
    }
}
