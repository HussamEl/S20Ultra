package se.eldebosh.nastastopp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Reference numbers: a small translucent number in the top-start corner of every button, switch
 * and piece of information, so the driver can name a control by its number ("change 64").
 * The numbers are listed in the README; they can be switched off in Settings.
 */
object RefNumbers {
    var enabled by mutableStateOf(true)
}

/** Draws reference number [n] over this element (see [RefNumbers]). */
fun Modifier.ref(n: Int): Modifier = this then RefElement(n)

private data class RefElement(val n: Int) : ModifierNodeElement<RefNode>() {
    override fun create() = RefNode(n)

    override fun update(node: RefNode) {
        node.n = n
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "ref"
        properties["n"] = n
    }
}

private class RefNode(var n: Int) : Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {
    private var measurer: TextMeasurer? = null

    override fun ContentDrawScope.draw() {
        drawContent()
        if (!RefNumbers.enabled) return
        val m = measurer ?: TextMeasurer(currentValueOf(LocalFontFamilyResolver), this, layoutDirection).also { measurer = it }
        val text = m.measure(n.toString(), STYLE)
        val padX = 3.dp.toPx()
        val padY = 1.dp.toPx()
        val w = text.size.width + 2 * padX
        val h = text.size.height + 2 * padY
        val inset = 2.dp.toPx()
        val x = if (layoutDirection == LayoutDirection.Ltr) inset else size.width - w - inset
        drawRoundRect(BADGE, topLeft = Offset(x, inset), size = Size(w, h), cornerRadius = CornerRadius(4.dp.toPx()))
        drawText(text, topLeft = Offset(x + padX, inset + padY))
    }

    companion object {
        private val BADGE = Color.Black.copy(alpha = 0.35f)
        private val STYLE = TextStyle(color = Color.White.copy(alpha = 0.8f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}
