package com.bullfrog.iconfontviewer.ui

import com.bullfrog.iconfontviewer.FontModelListHolder
import com.bullfrog.iconfontviewer.model.IconFontTtfFileModel
import com.bullfrog.iconfontviewer.util.getString
import com.bullfrog.iconfontviewer.util.jbScale
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.rowWithIndent
import com.intellij.ui.layout.titledRow
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.*

class TTFMainPanel : JPanel() {
    private val containerPanel = JPanel()
    init {

        layout = BorderLayout()

        containerPanel.layout = BoxLayout(containerPanel, BoxLayout.Y_AXIS)

        FontModelListHolder.getFontModelList().forEach {
            containerPanel.add(buildItemPanel(it))
        }

        add(JScrollPane().apply {
            viewport.view = containerPanel
            border = BorderFactory.createEmptyBorder()
        }, BorderLayout.NORTH)

        add(Button().apply {
            label = getString("icv.add.font")
            addActionListener {

            }
        }, BorderLayout.LINE_END)

        isVisible = true
    }

    private fun buildItemPanel(ttfModel: IconFontTtfFileModel): JPanel {

        return JPanel().apply {
            layout = BorderLayout()

            val topPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                add(
                    JLabel().apply {
                        text = ttfModel.ttfFileName
                        //TODO 展示尺寸有问题，不知道换成 svg 会不会正常
                        //icon = SizedIcon(IconLoader.getIcon("/icon/ttf_icon.png", TTFMainPanel::class.java), 14, 14)
                        font = font.deriveFont(14f.jbScale())
                    }
                )
                add(
                    Box.createHorizontalGlue()
                )
                    JBCheckBox("", ttfModel.selected).apply {
                        addActionListener {
                            val cb = it.source as? JBCheckBox ?: return@addActionListener
                            ttfModel.selected = cb.isSelected
                        }
                    }
                border = JBUI.Borders.emptyBottom(4.jbScale())
            }
            add(topPanel, BorderLayout.NORTH)

            add(
                JLabel().apply {
                    text = ttfModel.ttfAbsolutePath
                    font = font.deriveFont(11f.jbScale())
                    foreground = Color(255, 255, 255, 80)
                }, BorderLayout.SOUTH
            )

            border = JBUI.Borders.empty(10.jbScale(), 5.jbScale())
        }
    }

}
