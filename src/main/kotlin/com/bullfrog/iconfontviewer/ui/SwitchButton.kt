
package com.bullfrog.iconfontviewer.ui

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.Timer
import javax.swing.event.EventListenerList
import kotlin.properties.Delegates

class SwitchButton : JComponent() {

    var isSelected: Boolean by Delegates.observable(false) { _, _, newValue ->
        animateSwitch(newValue)
    }

    private var animationProgress = 0.0f
    private val animationTimer: Timer
    private val listenerList = EventListenerList()

    private val trackColorOff = JBColor.namedColor("Slider.disabledBackground", Color(0x4c4f52))
    private val trackColorOn = JBColor.namedColor("Slider.background", Color(0x4c7fef))
    private val thumbColorOff = JBColor.namedColor("Slider.disabledForeground", Color(0xafb1b3))
    private val thumbColorOn = JBColor.namedColor("Slider.foreground", Color.WHITE)

    init {
        preferredSize = Dimension(36, 22)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        isFocusable = true

        animationTimer = Timer(15) { repaint() }

        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                if (isEnabled) {
                    isSelected = !isSelected
                    firePropertyChange("selected", !isSelected, isSelected)
                    fireActionPerformed(MouseEvent(this@SwitchButton, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, e?.x ?: 0, e?.y ?: 0, 1, false))
                }
            }
        })
    }

    fun addActionListener(l: ActionListener) {
        listenerList.add(ActionListener::class.java, l)
    }

    fun removeActionListener(l: ActionListener) {
        listenerList.remove(ActionListener::class.java, l)
    }

    private fun fireActionPerformed(event: MouseEvent) {
        val listeners = listenerList.listenerList
        var e: ActionEvent? = null
        for (i in listeners.indices step 2) {
            if (listeners[i] == ActionListener::class.java) {
                if (e == null) {
                    val actionCommand = "switchChanged"
                    e = ActionEvent(this, ActionEvent.ACTION_PERFORMED, actionCommand, event.`when`, event.modifiersEx)
                }
                (listeners[i + 1] as ActionListener).actionPerformed(e)
            }
        }
    }

    private fun animateSwitch(selected: Boolean) {
        val targetProgress = if (selected) 1.0f else 0.0f
        val duration = 120 // ms

        if (animationTimer.isRunning) {
            animationTimer.stop()
        }

        val startProgress = animationProgress
        val totalSteps = duration / 15
        if (totalSteps <= 0) {
            animationProgress = targetProgress
            repaint()
            return
        }

        val stepSize = (targetProgress - startProgress) / totalSteps

        val listener = java.awt.event.ActionListener { 
            animationProgress += stepSize
            if ((stepSize > 0 && animationProgress >= targetProgress) || (stepSize < 0 && animationProgress <= targetProgress)) {
                animationProgress = targetProgress
                (it.source as Timer).stop()
            }
            repaint()
        }
        animationTimer.actionListeners.forEach { animationTimer.removeActionListener(it) } // Clear old listeners
        animationTimer.addActionListener(listener)
        animationTimer.start()
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        cursor = if (enabled) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else Cursor.getDefaultCursor()
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2d = g.create() as Graphics2D

        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            val trackHeight = height
            val trackWidth = width
            val cornerRadius = trackHeight

            // Track
            val interpolatedTrackColor = interpolateColor(trackColorOff, trackColorOn, animationProgress)
            g2d.color = if (isEnabled) interpolatedTrackColor else trackColorOff.darker()
            g2d.fillRoundRect(0, 0, trackWidth, trackHeight, cornerRadius, cornerRadius)

            // Thumb
            val thumbSize = height - JBUI.scale(6)
            val thumbPadding = (trackHeight - thumbSize) / 2
            val thumbTravelDistance = trackWidth - thumbSize - thumbPadding * 2
            val thumbX = thumbPadding + (thumbTravelDistance * animationProgress).toInt()
            val thumbY = thumbPadding

            val interpolatedThumbColor = interpolateColor(thumbColorOff, thumbColorOn, animationProgress)
            g2d.color = if (isEnabled) interpolatedThumbColor else thumbColorOff.darker()
            g2d.fillOval(thumbX, thumbY, thumbSize, thumbSize)

        } finally {
            g2d.dispose()
        }
    }

    private fun interpolateColor(color1: Color, color2: Color, fraction: Float): Color {
        val f = fraction.coerceIn(0.0f, 1.0f)
        val red = (color1.red + f * (color2.red - color1.red)).toInt()
        val green = (color1.green + f * (color2.green - color1.green)).toInt()
        val blue = (color1.blue + f * (color2.blue - color1.blue)).toInt()
        return Color(red, green, blue)
    }
}
