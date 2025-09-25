package com.bullfrog.iconfontviewer.ui

import com.bullfrog.iconfontviewer.FontModelListHolder
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBScalableIcon
import java.awt.Component
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.font.FontRenderContext
import java.awt.font.GlyphVector
import java.awt.geom.AffineTransform

class IconFromIconFontCharacter(
    private val stringToDraw: String,
    private var font: Font? = null
) : JBScalableIcon() {

    companion object {
        val FONT_SIZE = JBUIScale.scale(15f)
    }

    override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
        c ?: return
        g ?: return
        drawString(g, x.toFloat() , y.toFloat())
    }

    override fun getIconWidth(): Int {
        return scaleVal((FONT_SIZE).toDouble()).toInt()
    }

    override fun getIconHeight(): Int {
        return scaleVal((FONT_SIZE).toDouble()).toInt()
    }

    private fun drawString(g: Graphics, x: Float, y: Float) {
        font?.let {
            val graphics = g as Graphics2D
            graphics.color = JBColor.BLACK //Otherwise the text would be white
            graphics.font = it
            val gv = graphics.font.createGlyphVector(
                FontRenderContext(null, true, false),
                stringToDraw
            )
            correctCoordinates(gv)
            graphics.drawGlyphVector(gv, x, y)
        }
    }

    private fun correctCoordinates(gv: GlyphVector) {
        val xOffset = 0f - gv.outline.bounds2D.x.toFloat()
        val yOffset = 0f - gv.outline.bounds2D.y.toFloat()
        val transform = AffineTransform(
            1f, 0f,
            0f, 1f,
            xOffset, yOffset
        )
        for (i in 0 until  gv.numGlyphs) {
            gv.setGlyphTransform(i, transform)
        }
    }

}