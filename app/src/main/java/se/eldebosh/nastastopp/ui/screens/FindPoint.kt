package se.eldebosh.nastastopp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import se.eldebosh.nastastopp.R
import se.eldebosh.nastastopp.geo.PointFinder
import se.eldebosh.nastastopp.ui.HelpDot
import se.eldebosh.nastastopp.ui.PointText
import se.eldebosh.nastastopp.ui.TouchTarget
import se.eldebosh.nastastopp.ui.ref
import se.eldebosh.nastastopp.ui.refCorner
import se.eldebosh.nastastopp.ui.theme.AppTheme

/**
 * What the find tool holds while it is open: the address typed, whether it is being looked for,
 * and the answer. Only in memory, for as long as its window is open; nothing of it is kept.
 */
@Stable
private class FindState(private val onFind: suspend (String) -> PointFinder.Answer) {
    var text by mutableStateOf("")
        private set
    var searching by mutableStateOf(false)
        private set
    var answer by mutableStateOf<PointFinder.Answer?>(null)
        private set

    val canFind: Boolean get() = text.isNotBlank() && !searching

    /** The search under way, for the text it was started for. */
    private var search: Job? = null

    /** A new text clears the answer to the one before, and stops a search for the one before. */
    fun type(typed: String) {
        if (typed != text) {
            answer = null
            search?.cancel()
        }
        text = typed
    }

    fun find(scope: CoroutineScope) {
        if (!canFind) return
        searching = true
        answer = null
        val asked = text
        search = scope.launch {
            try {
                val found = onFind(asked)
                // Only the answer for the text still in the field.
                if (text == asked) answer = found
            } finally {
                searching = false
            }
        }
    }
}

/** The address field (328): any address, as on a list ("Storgatan 14, 652 24 Karlstad"); the keyboard's search key finds it. */
@Composable
private fun FindField(state: FindState, scope: CoroutineScope) {
    OutlinedTextField(
        value = state.text,
        onValueChange = state::type,
        label = { Text(stringResource(R.string.find_point_field)) },
        placeholder = { Text(stringResource(R.string.dialog_address_hint)) },
        textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { state.find(scope) }),
        minLines = 1,
        maxLines = 3,
        modifier = Modifier.ref(328).fillMaxWidth(),
    )
}

/**
 * The answer (330): the address as it was found and its point (322, a tap copies it); or that it
 * is being looked for, was not found (the postcode or town, never guessed), or that the geocoder
 * could not answer. Nothing before the first search.
 */
@Composable
private fun FindAnswer(state: FindState) {
    val colors = AppTheme.colors
    val answer = state.answer
    if (!state.searching && answer == null) return
    Column(Modifier.ref(330).fillMaxWidth().padding(top = 8.dp)) {
        when {
            state.searching -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.find_point_searching), style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
            }
            answer is PointFinder.Answer.Found -> {
                Text(answer.address, style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content), color = colors.text)
                PointText(answer.lat, answer.lng, color = colors.text, style = MaterialTheme.typography.bodyLarge, icon = 20.dp)
            }
            answer == PointFinder.Answer.NotFound ->
                Text(stringResource(R.string.find_point_not_found), style = MaterialTheme.typography.bodyMedium, color = colors.danger)
            answer == PointFinder.Answer.NoAnswer ->
                Text(stringResource(R.string.find_point_no_answer), style = MaterialTheme.typography.bodyMedium, color = colors.warning)
        }
    }
}

/**
 * The phone's find tool (326), from the home (323) or the route screen (324): any address in, its
 * point out, copied with a tap. It never adds a stop or touches the route ([PointFinder]).
 */
@Composable
fun FindPointDialog(onFind: suspend (String) -> PointFinder.Answer, onDismiss: () -> Unit) {
    val state = remember { FindState(onFind) }
    val scope = rememberCoroutineScope()
    val title = stringResource(R.string.find_point_title)
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.ref(326),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, modifier = Modifier.weight(1f, fill = false))
                HelpDot(R.string.find_point_hint, Modifier.refCorner(327), title = title)
            }
        },
        text = {
            Column {
                FindField(state, scope)
                FindAnswer(state)
            }
        },
        confirmButton = {
            TextButton(onClick = { state.find(scope) }, enabled = state.canFind, modifier = Modifier.ref(329).heightIn(min = TouchTarget)) {
                Text(stringResource(R.string.find_point_button), style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.ref(331).heightIn(min = TouchTarget)) {
                Text(stringResource(R.string.find_point_close), style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}

/**
 * The passenger display's find tool (326, from its sign 325), for the driver: a window like the
 * trip card's ([FloatingWindow], kept by [place]: its bar 332 moves it, its border 333 sizes it,
 * two fingers do both), with the address field, Find and the answer. It lies in a window of its
 * own over the display, clear and without a shade, so the keyboard never lays the display out
 * again (the display keeps clear of the keyboard's insets); the system bars stay hidden. A tap
 * beside it or its × (331) closes it.
 */
@Composable
internal fun FindPointWindow(place: WindowState, onFind: suspend (String) -> PointFinder.Answer, onClose: () -> Unit) {
    val state = remember { FindState(onFind) }
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            window?.setDimAmount(0f)
            window?.let { w ->
                WindowCompat.getInsetsController(w, view).apply {
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsetsCompat.Type.systemBars())
                }
            }
            onDispose { }
        }
        val colors = AppTheme.colors
        val shape = RoundedCornerShape(FIND_CORNER)
        Box(Modifier.fillMaxSize().imePadding()) {
            // Beside the window: a tap closes it.
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onClose() } })
            FloatingWindow(place, edgeRef = 333) {
                Column(
                    Modifier
                        .ref(326)
                        .width(FIND_WIDTH)
                        .shadow(FIND_SHADOW, shape)
                        .clip(shape)
                        .background(colors.card)
                        .border(1.dp, colors.cardBorder, shape)
                        // A tap on the window is the driver using it: it stays.
                        .pointerInput(Unit) { detectTapGestures { } },
                ) {
                    // The bar: a finger on it moves the window.
                    val move = stringResource(R.string.window_move)
                    val title = stringResource(R.string.find_point_title)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .refCorner(332)
                            .fillMaxWidth()
                            .background(colors.tonal)
                            .movesWindow(place)
                            .semantics { contentDescription = move }
                            .padding(start = FIND_PAD, end = 4.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_find_point), contentDescription = null, tint = colors.highlight, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        // The title gives way in a narrow window; the "?" stays beside it.
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            HelpDot(R.string.find_point_hint, Modifier.refCorner(327), title = title)
                        }
                        val close = stringResource(R.string.find_point_close)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .refCorner(331)
                                .size(TouchTarget)
                                .clip(CircleShape)
                                .clickable(onClickLabel = close, role = Role.Button, onClick = onClose)
                                .semantics { contentDescription = close },
                        ) {
                            Icon(painterResource(R.drawable.ic_close), contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
                        }
                    }
                    Column(
                        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = FIND_PAD, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FindField(state, scope)
                        TextButton(onClick = { state.find(scope) }, enabled = state.canFind, modifier = Modifier.ref(329).heightIn(min = TouchTarget).align(Alignment.End)) {
                            Text(stringResource(R.string.find_point_button), style = MaterialTheme.typography.labelLarge)
                        }
                        FindAnswer(state)
                    }
                }
            }
        }
    }
}

private val FIND_WIDTH = 380.dp
private val FIND_CORNER = 16.dp
private val FIND_PAD = 14.dp
private val FIND_SHADOW = 12.dp
