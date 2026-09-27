package se.eldebosh.nastastopp.ui.screens

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Minimal drag-to-reorder for a LazyColumn whose items are keyed. The dragged item follows the
 * finger (translationY = [offset]); when its centre enters a neighbour, [onMove] swaps them.
 */
class ReorderState(
    private val listState: LazyListState,
    private val onMove: (from: Int, to: Int) -> Unit,
) {
    var draggingKey by mutableStateOf<Any?>(null)
        private set
    var offset by mutableFloatStateOf(0f)
        private set
    private var expectedIndex: Int? = null

    fun start(key: Any) {
        draggingKey = key
        offset = 0f
        expectedIndex = null
    }

    fun drag(dy: Float) {
        val key = draggingKey ?: return
        offset += dy
        val info = listState.layoutInfo
        val current = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        // Wait for the layout to reflect the previous move before moving again.
        expectedIndex?.let { if (current.index != it) return else expectedIndex = null }

        // Auto-scroll when dragging near the edges.
        val top = current.offset + offset
        val bottom = top + current.size
        val edge = 80
        val scroll = when {
            bottom > info.viewportEndOffset - edge -> 18f
            top < info.viewportStartOffset + edge -> -18f
            else -> 0f
        }
        if (scroll != 0f) {
            val consumed = listState.dispatchRawDelta(scroll)
            offset += consumed
        }

        val center = current.offset + offset + current.size / 2f
        val target = info.visibleItemsInfo.firstOrNull {
            it.key != key && center >= it.offset && center <= it.offset + it.size
        } ?: return
        val newOffset = if (target.index > current.index) target.offset + target.size - current.size else target.offset
        onMove(current.index, target.index)
        offset += current.offset - newOffset
        expectedIndex = target.index
    }

    fun end() {
        draggingKey = null
        offset = 0f
        expectedIndex = null
    }
}
