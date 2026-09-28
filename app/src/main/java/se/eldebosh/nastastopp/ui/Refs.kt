package se.eldebosh.nastastopp.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.platform.testTag
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

/**
 * Reference numbers: a small number on every button, switch and piece of information, so the
 * driver can name a control by its number ("change 64"). The numbers are listed in the README;
 * they can be switched off in Settings (105).
 */
object RefNumbers {
    var enabled by mutableStateOf(true)
}

/**
 * Reference number [n] in a thin strip of its own above this element, so it never covers text.
 * [centered] adds the same space below (keeps the element centred in a row, e.g. a title).
 */
fun Modifier.ref(n: Int, centered: Boolean = false): Modifier =
    // The number is drawn over the strip's full size, so the draw part comes before the strip.
    this.testTag(refTag(n)) then RefDrawElement(n, corner = false) then RefStripElement(centered)

/** For icon buttons and switches: the number sits in the control's own empty top-start corner. */
fun Modifier.refCorner(n: Int): Modifier = this.testTag(refTag(n)) then RefDrawElement(n, corner = true)

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
        if (corner) {
            val text = m.measure(n.toString(), PILL_STYLE)
            val padX = 3.dp.toPx()
            val w = text.size.width + 2 * padX
            val h = text.size.height.toFloat()
            val inset = 1.dp.toPx()
            val x = if (layoutDirection == LayoutDirection.Ltr) inset else size.width - w - inset
            drawRoundRect(PILL, topLeft = Offset(x, inset), size = Size(w, h), cornerRadius = CornerRadius(h / 2))
            drawText(text, topLeft = Offset(x + padX, inset))
        } else {
            val text = m.measure(n.toString(), STRIP_STYLE)
            val x = if (layoutDirection == LayoutDirection.Ltr) 2.dp.toPx() else size.width - text.size.width - 2.dp.toPx()
            drawText(text, topLeft = Offset(x, (STRIP.toPx() - text.size.height) / 2))
        }
    }

    companion object {
        private val STRIP_STYLE = TextStyle(color = Color(0xFF7F8BA0), fontSize = 8.5.sp, fontWeight = FontWeight.Bold, lineHeight = 10.sp)
        private val PILL = Color(0xE60B0F17)
        private val PILL_STYLE = TextStyle(color = Color(0xFFB8C2D4), fontSize = 8.sp, fontWeight = FontWeight.Bold, lineHeight = 10.sp)
    }
}
