package se.eldebosh.nastastopp.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.invalidateMeasurement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import se.eldebosh.nastastopp.ui.theme.LocalAppColors

/**
 * Reference numbers: a small number on every button, switch and piece of information, so a
 * control can be named by its number ("change 64"). The numbers are listed in the README and
 * shown only while Settings 105 is on (off by default). The test tags "ref_<n>" are always there.
 */
object RefNumbers {
    var enabled by mutableStateOf(false)
}

/**
 * Reference number [n] in a thin strip of its own above this element, so it never covers text.
 * [centered] adds the same space below (keeps the element centred in a row, e.g. a title).
 */
fun Modifier.ref(n: Int, centered: Boolean = false): Modifier =
    // The number is drawn over the strip's full size, so the draw part comes before the strip.
    this.refId(n) then RefDrawElement(n, corner = false) then RefStripElement(centered)

/** For icon buttons and switches: the number sits in the control's own empty top-start corner. */
fun Modifier.refCorner(n: Int): Modifier = this.refId(n) then RefDrawElement(n, corner = true)

/**
 * The test tag "ref_<n>", exported as resource-id on the element itself, so it also works in
 * dialogs and menus (separate windows, outside the app roots).
 */
private fun Modifier.refId(n: Int): Modifier = semantics {
    testTag = refTag(n)
    testTagsAsResourceId = true
}

/**
 * Stable id of numbered element [n] ("ref_78"): a test tag that UI Automator sees as the
 * resource-id (the app roots set testTagsAsResourceId), so device tests find controls by number.
 */
fun refTag(n: Int) = "ref_$n"

private val STRIP = 11.dp

/** Reserves the strip above (and, when centred, below) the element while numbers are shown. */
private data class RefStripElement(val centered: Boolean) : ModifierNodeElement<RefStripNode>() {
    override fun create() = RefStripNode(centered)

    override fun update(node: RefStripNode) {
        node.centered = centered
        node.invalidateMeasurement()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "refStrip"
        properties["centered"] = centered
    }
}

private class RefStripNode(var centered: Boolean) : Modifier.Node(), LayoutModifierNode {
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        if (!RefNumbers.enabled) {
            val p = measurable.measure(constraints)
            return layout(p.width, p.height) { p.place(0, 0) }
        }
        val strip = STRIP.roundToPx()
        val extra = if (centered) 2 * strip else strip
        val p = measurable.measure(constraints.offset(vertical = -extra))
        return layout(p.width, constraints.constrainHeight(p.height + extra)) { p.place(0, strip) }
    }
}

private data class RefDrawElement(val n: Int, val corner: Boolean) : ModifierNodeElement<RefDrawNode>() {
    override fun create() = RefDrawNode(n, corner)

    override fun update(node: RefDrawNode) {
        node.n = n
        node.corner = corner
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "ref"
        properties["n"] = n
        properties["corner"] = corner
    }
}

private class RefDrawNode(var n: Int, var corner: Boolean) : Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {
    private var measurer: TextMeasurer? = null

    override fun ContentDrawScope.draw() {
        drawContent()
        if (!RefNumbers.enabled) return
        val m = measurer ?: TextMeasurer(currentValueOf(LocalFontFamilyResolver), this, layoutDirection).also { measurer = it }
        val colors = currentValueOf(LocalAppColors) // day or night
        if (corner) {
            val text = m.measure(n.toString(), PILL_STYLE.copy(color = colors.onRefPill))
            val padX = 3.dp.toPx()
            val w = text.size.width + 2 * padX
            val h = text.size.height.toFloat()
            // Nudged up and out, so it sits beside the control's corner instead of on its edge
            // (a switch track, a "?" ring).
            val out = 4.dp.toPx()
            val x = if (layoutDirection == LayoutDirection.Ltr) -out else size.width - w + out
            drawRoundRect(colors.refPill, topLeft = Offset(x, -out), size = Size(w, h), cornerRadius = CornerRadius(h / 2))
            drawText(text, topLeft = Offset(x + padX, -out))
        } else {
            val text = m.measure(n.toString(), STRIP_STYLE.copy(color = colors.refText))
            val x = if (layoutDirection == LayoutDirection.Ltr) 2.dp.toPx() else size.width - text.size.width - 2.dp.toPx()
            drawText(text, topLeft = Offset(x, (STRIP.toPx() - text.size.height) / 2))
        }
    }

    companion object {
        // Colours come from the look's roles (refText, refPill, onRefPill).
        private val STRIP_STYLE = TextStyle(fontSize = 8.5.sp, fontWeight = FontWeight.Bold, lineHeight = 10.sp)
        private val PILL_STYLE = TextStyle(fontSize = 8.sp, fontWeight = FontWeight.Bold, lineHeight = 10.sp)
    }
}
