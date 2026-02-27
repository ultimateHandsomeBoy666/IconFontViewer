package com.bullfrog.iconfontviewer.ui

import com.bullfrog.iconfontviewer.IconFontSettings
import com.bullfrog.iconfontviewer.model.FontInfo
import com.bullfrog.iconfontviewer.model.FontSource
import com.bullfrog.iconfontviewer.start.SearchStartupActivity
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.sun.java.accessibility.util.AWTEventMonitor.addActionListener
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

class TTFMainPanel(private val project: Project) : JPanel() {

    private val settings = IconFontSettings.getInstance(project)
    private val fontListPanel = JPanel()

    init {
        layout = BorderLayout()
        background = JBColor.namedColor("Panel.background", Color(0x2b2d30))

        // Title
        val titleLabel = JLabel("IconFont TTF 文件管理").apply {
            font = font.deriveFont(20f).deriveFont(Font.BOLD)
            border = JBUI.Borders.empty(16, 24)
        }
        val titlePanel = JPanel(BorderLayout()).apply {
            add(titleLabel, BorderLayout.WEST)
            border = BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.namedColor("Borders.color", Color(0x43454a)))
        }

        // Font List
        fontListPanel.layout = BoxLayout(fontListPanel, BoxLayout.Y_AXIS)
        fontListPanel.background = JBColor.namedColor("List.background", Color(0x313438))
        buildFontList()

        val scrollPane = JScrollPane(fontListPanel).apply {
            border = BorderFactory.createEmptyBorder()
            verticalScrollBar.unitIncrement = 16
        }

        // Footer Buttons
        val addButton = JButton("添加 TTF 文件").apply {
            isFocusable = false
            addActionListener { addFontFile() }
        }
        val rescanButton = JButton("重新扫描").apply {
            isFocusable = false
            addActionListener { rescanProject() }
        }
        val footerPanel = JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
            border = JBUI.Borders.empty(12)
            add(rescanButton)
            add(addButton)
        }

        add(titlePanel, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)
        add(footerPanel, BorderLayout.SOUTH)
    }

    private fun buildFontList() {
        fontListPanel.removeAll()
        val fontInfos = settings.state.fontInfos
        if (fontInfos.isEmpty()) {
            val emptyLabel = JLabel("未发现或添加任何字体文件。请点击“重新扫描”或“添加”").apply {
                horizontalAlignment = SwingConstants.CENTER
                foreground = JBColor.GRAY
                border = JBUI.Borders.empty(20)
            }
            fontListPanel.add(emptyLabel)
        } else {
            fontInfos.forEach { fontInfo ->
                fontListPanel.add(createFontItemPanel(fontInfo))
                fontListPanel.add(JSeparator(JSeparator.HORIZONTAL))
            }
        }
        fontListPanel.revalidate()
        fontListPanel.repaint()
    }

    private fun createFontItemPanel(fontInfo: FontInfo): JPanel {
        val itemPanel = JPanel(BorderLayout(20, 0)).apply {
            border = JBUI.Borders.empty(16, 20)
            background = JBColor.namedColor("List.background", Color(0x313438))
            isEnabled = fontInfo.enabled
        }

        // Icon
        val iconLabel = JLabel(if (fontInfo.enabled) "⭐" else "T").apply {
            font = font.deriveFont(20f)
            horizontalAlignment = SwingConstants.CENTER
            isOpaque = true
            background = if (fontInfo.enabled) JBColor.namedColor("ToggleButton.on.background", Color(0x4c7fef)) else JBColor.GRAY
            foreground = Color.WHITE
            preferredSize = Dimension(36, 36)
            border = JBUI.Borders.empty(4)
        }

        // Details
        val nameLabel = JLabel(fontInfo.fileName).apply {
            font = font.deriveFont(14f).deriveFont(Font.BOLD)
        }
        val pathLabel = JLabel(fontInfo.path).apply {
            font = font.deriveFont(12f)
            foreground = JBColor.GRAY
        }
        val detailsPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(nameLabel)
            add(Box.createRigidArea(Dimension(0, 4)))
            add(pathLabel)
        }

        val infoPanel = JPanel(BorderLayout(16, 0)).apply {
            add(iconLabel, BorderLayout.WEST)
            add(detailsPanel, BorderLayout.CENTER)
        }

        // Controls
        val sourceTag = JLabel(fontInfo.source.name).apply {
            font = font.deriveFont(11f)
            isOpaque = true
            border = JBUI.Borders.empty(4, 10)
            background = when (fontInfo.source) {
                FontSource.PROJECT -> JBColor.namedColor("VersionControl.Log.tag.background.green", Color(0x2a4e32))
                FontSource.AAR -> JBColor.namedColor("VersionControl.Log.tag.background.blue", Color(0x1a365d))
                FontSource.USER -> JBColor.namedColor("VersionControl.Log.tag.background.orange", Color(0x7c2d12))
            }
            foreground = when (fontInfo.source) {
                FontSource.PROJECT -> JBColor.namedColor("VersionControl.Log.tag.foreground.green", Color(0x81c784))
                FontSource.AAR -> JBColor.namedColor("VersionControl.Log.tag.foreground.blue", Color(0x63b3ed))
                FontSource.USER -> JBColor.namedColor("VersionControl.Log.tag.foreground.orange", Color(0xffa726))
            }
        }

        val switchButton = SwitchButton().apply {
            isSelected = fontInfo.enabled
            addActionListener {
                fontInfo.enabled = isSelected
                itemPanel.isEnabled = isSelected
                updateComponentTreeUI(itemPanel)
            }
        }

        val deleteButton = JButton("×").apply {
            font = font.deriveFont(18f)
            isBorderPainted = false
            isContentAreaFilled = false
            isFocusable = false
            foreground = JBColor.GRAY
            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent?) {
                    foreground = JBColor.RED
                }
                override fun mouseExited(e: MouseEvent?) {
                    foreground = JBColor.GRAY
                }
            })
            addActionListener {
                if (Messages.showYesNoDialog(
                        "确定要从列表中移除 ${fontInfo.fileName} 吗?",
                        "确认删除",
                        Messages.getQuestionIcon()
                    ) == Messages.YES
                ) {
                    settings.getState().fontInfos.remove(fontInfo)
                    buildFontList()
                }
            }
        }

        val controlsPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 15, 0)).apply {
            add(sourceTag)
            add(switchButton)
            add(deleteButton)
        }

        itemPanel.add(infoPanel, BorderLayout.CENTER)
        itemPanel.add(controlsPanel, BorderLayout.EAST)

        // Set opacity for disabled state
        updateComponentTreeUI(itemPanel)

        return itemPanel
    }

    private fun addFontFile() {
        val descriptor = FileChooserDescriptor(true, false, false, false, false, true)
            .withTitle("选择 TTF 字体文件")
            .withFileFilter { it.extension?.equals("ttf", ignoreCase = true) == true }
        FileChooser.chooseFiles(descriptor, null, null) { files ->
            files.forEach { file ->
                if (settings.state.fontInfos.none { it.path == file.path }) {
                    val fontInfo = FontInfo(
                        path = file.path,
                        source = FontSource.USER,
                        enabled = true // Manually added fonts are enabled by default
                    )
                    settings.state.fontInfos.add(fontInfo)
                }
            }
            buildFontList()
        }
    }

    private fun rescanProject() {
        val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: return
        val task = object : Task.Backgroundable(project, "Rescanning Project for TTF Files", true) {
            override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                SearchStartupActivity.scanForFonts(project, indicator)
            }

            override fun onSuccess() {
                super.onSuccess()
                SwingUtilities.invokeLater {
                    buildFontList()
                }
            }
        }
        ProgressManager.getInstance().run(task)
    }

    private fun updateComponentTreeUI(c: Component) {
        c.isEnabled = c.parent?.isEnabled ?: true
        if (c is JComponent) {
            c.foreground = if (c.isEnabled) UIManager.getColor("Label.foreground") else UIManager.getColor("Label.disabledForeground")
            if (c is JLabel && c.parent is JPanel && c.parent.background != null) {
                 // Special handling for labels on colored backgrounds
            }
        }
        if (c is Container) {
            for (child in c.components) {
                updateComponentTreeUI(child)
            }
        }
    }
}