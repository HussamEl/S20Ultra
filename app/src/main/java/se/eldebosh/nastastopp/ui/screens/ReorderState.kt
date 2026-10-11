package se.eldebosh.nastastopp.ui.screens

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Drag-to-reorder for a LazyColumn whose items are keyed. The dragged item stays under the finger:
 * its translation ([offset]) is where the finger has taken it less where the list lays it out now,
 * read from the list's own layout, so it never jumps when the list moves it to a new place. When its
 * middle enters a neighbour, [onMove] swaps them.
 */
class ReorderState(
    private val listState: LazyListState,
    private val onMove: (from: Int, to: Int) -> Unit,
) {
    var draggingKey by mutableStateOf<Any?>(null)
        private set

    /** Where the dragged item was laid out when the drag began, and how far the finger has gone since. */
    private var startOffset = 0
    private var moved by mutableFloatStateOf(0f)
    private var expectedIndex: Int? = null

    /** The dragged item's translation (read while drawing, so it follows each layout). */
    val offset: Float
        get() {
            val key = draggingKey ?: return 0f
            val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return 0f
            return startOffset + moved - item.offset
        }

    fun start(key: Any) {
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        draggingKey = key
        startOffset = item.offset
        moved = 0f
        expectedIndex = null
    }

    fun drag(dy: Float) {
        val key = draggingKey ?: return
        moved += dy
        val info = listState.layoutInfo
        val current = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        // Wait for the layout to show the last move before moving again.
        expectedIndex?.let { if (current.index != it) return else expectedIndex = null }

        // Near an edge of the list, it scrolls; the item stays under the finger ([offset] follows its layout).
        val top = startOffset + moved
        val bottom = top + current.size
        val scroll = when {
            bottom > info.viewportEndOffset - EDGE_PX -> SCROLL_PX
            top < info.viewportStartOffset + EDGE_PX -> -SCROLL_PX
            else -> 0f
        }
        if (scroll != 0f) listState.dispatchRawDelta(scroll)

        val middle = startOffset + moved + current.size / 2f
        val target = info.visibleItemsInfo.firstOrNull {
            it.key != key && middle >= it.offset && middle <= it.offset + it.size
        } ?: return
        onMove(current.index, target.index)
        expectedIndex = target.index
    }

    fun end() {
        draggingKey = null
        moved = 0f
        expectedIndex = null
    }

    private companion object {
        const val EDGE_PX = 80
        const val SCROLL_PX = 18f
    }
}
