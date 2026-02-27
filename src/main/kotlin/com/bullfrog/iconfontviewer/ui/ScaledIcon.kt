package com.bullfrog.iconfontviewer.ui

import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.Icon


class ScaledIcon(private val originalIcon: Icon?, private val width: Int, private val height: Int) : Icon {
    override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
        if (originalIcon != null) {
            val g2d = g.create() as Graphics2D
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            // Scale the icon to fit the defined width and height
            g2d.scale(width.toDouble() / originalIcon.iconWidth, height.toDouble() / originalIcon.iconHeight)
            originalIcon.paintIcon(c, g2d, x, y)
            g2d.dispose()
        }
    }

    override fun getIconWidth(): Int {
        return width
    }

    override fun getIconHeight(): Int {
        return height
    }
}

