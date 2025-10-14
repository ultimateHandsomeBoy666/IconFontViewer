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
import kotlin.properties.Delegates

class SwitchButton : JComponent() {

    var isSelected: Boolean by Delegates.observable(false) { _, oldValue, newValue ->
        if (oldValue != newValue) {
            animateSwitch(newValue)
        }
    }

    private var animationProgress = 0.0f
    private val animationTimer: Timer

    private val trackColorOff = JBColor.namedColor("ToggleButton.borderColor", Color(0x4c4f52))
    private val trackColorOn = JBColor.namedColor("ToggleButton.on.background", Color(0x4c7fef))
    private val thumbColor = JBColor.namedColor("ToggleButton.on.foreground", Color.WHITE)
    private val thumbColorOff = JBColor.namedColor("ToggleButton.off.foreground", Color(0xafb1b3))

    init {
        preferredSize = JBUI.size(36, 22)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        isFocusable = true

        animationTimer = Timer(15) { 
            repaint()
        }

        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                isSelected = !isSelected
                fireActionPerformed()
            }
        })
    }

    private fun animateSwitch(selected: Boolean) {
        val targetProgress = if (selected) 1.0f else 0.0f
        val duration = 120 // ms
        val steps = duration / 15
        if (steps == 0) {
            animationProgress = targetProgress
            repaint()
            return
        }
        val stepSize = (targetProgress - animationProgress) / steps

        if (animationTimer.isRunning) {
            animationTimer.stop()
        }

        val listener = ActionListener { 
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

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2d = g.create() as Graphics2D

        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            val trackHeight = height
            val trackWidth = width
            val cornerRadius = trackHeight

            val interpolatedColor = Color(
                (trackColorOff.red + animationProgress * (trackColorOn.red - trackColorOff.red)).toInt(),
                (trackColorOff.green + animationProgress * (trackColorOn.green - trackColorOff.green)).toInt(),
                (trackColorOff.blue + animationProgress * (trackColorOn.blue - trackColorOff.blue)).toInt()
            )
            g2d.color = interpolatedColor
            g2d.fillRoundRect(0, 0, trackWidth, trackHeight, cornerRadius, cornerRadius)

            val thumbSize = height - JBUI.scale(6)
            val thumbPadding = (trackHeight - thumbSize) / 2
            val thumbTravelDistance = trackWidth - thumbSize - thumbPadding * 2
            val thumbX = thumbPadding + (thumbTravelDistance * animationProgress).toInt()
            val thumbY = thumbPadding
            
            val interpolatedThumbColor = Color(
                (thumbColorOff.red + animationProgress * (thumbColor.red - thumbColorOff.red)).toInt(),
                (thumbColorOff.green + animationProgress * (thumbColor.green - thumbColorOff.green)).toInt(),
                (thumbColorOff.blue + animationProgress * (thumbColor.blue - thumbColorOff.blue)).toInt()
            )

            g2d.color = interpolatedThumbColor
            g2d.fillOval(thumbX, thumbY, thumbSize, thumbSize)

        } finally {
            g2d.dispose()
        }
    }

    fun addActionListener(listener: ActionListener) {
        listenerList.add(ActionListener::class.java, listener)
    }

    private fun fireActionPerformed() {
        val event = ActionEvent(this, ActionEvent.ACTION_PERFORMED, "selected")
        for (listener in listenerList.getListeners(ActionListener::class.java)) {
            listener.actionPerformed(event)
        }
    }
}