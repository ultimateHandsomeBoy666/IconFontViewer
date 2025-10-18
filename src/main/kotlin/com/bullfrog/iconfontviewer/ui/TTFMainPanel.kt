package com.bullfrog.iconfontviewer.ui

import com.bullfrog.iconfontviewer.model.IconFontTtfFileModel
import com.bullfrog.iconfontviewer.model.TTFSource
import com.bullfrog.iconfontviewer.model.TTFType
import com.bullfrog.iconfontviewer.service.TTFStateChangeListener
import com.bullfrog.iconfontviewer.service.TTFStateManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.runBlocking
import java.awt.*
import java.awt.event.ActionListener
import javax.swing.*

class TTFMainPanel(private val project: Project) : JPanel(), TTFStateChangeListener {

    private val stateManager: TTFStateManager by lazy { project.service() }
    private val containerPanel = JPanel()

    init {
        setupUI()
        stateManager.addStateChangeListener(this)
        loadTTFs()
    }

    private fun setupUI() {
        layout = BorderLayout()
        background = JBColor(0x2b2d30, 0x2b2d30)

        // 使用说明
        add(createLegendPanel(), BorderLayout.NORTH)

        // TTF 列表容器
        containerPanel.layout = BoxLayout(containerPanel, BoxLayout.Y_AXIS)
        containerPanel.background = JBColor(0x2b2d30, 0x2b2d30)

        val scrollPane = JBScrollPane(containerPanel).apply {
            border = BorderFactory.createEmptyBorder()
            preferredSize = Dimension(700, 400)
            background = JBColor(0x2b2d30, 0x2b2d30)
        }
        add(scrollPane, BorderLayout.CENTER)

        // 操作按钮
        add(createActionPanel(), BorderLayout.SOUTH)

        isVisible = true
    }

    private fun createLegendPanel(): JPanel {
        return JPanel().apply {
            layout = BorderLayout()
            background = JBColor(0x313438, 0x313438)
            border = JBUI.Borders.compound(
                JBUI.Borders.customLine(JBColor(0x43454a, 0x43454a)),
                JBUI.Borders.empty(20, 20)
            )

            add(JLabel("使用说明").apply {
                font = font.deriveFont(Font.BOLD, 15f)
                foreground = JBColor.WHITE
            }, BorderLayout.NORTH)

            val legendText = """
                • 插件会自动扫描项目及依赖中的字体文件，基于文件名（如 "icon"）启发式规则自动启用部分字体进行预览
                • 手动添加的TTF文件将总是保持启用状态，用于图标预览
                • 您可以随时通过开关手动启用或关闭扫描到的字体
            """.trimIndent()

            add(JLabel("<html>${legendText.replace("\n", "<br>")}</html>").apply {
                font = font.deriveFont(13f)
                foreground = JBColor(0xbcbec4, 0xbcbec4)
                border = JBUI.Borders.emptyTop(16)
            }, BorderLayout.CENTER)
        }
    }

    private fun createActionPanel(): JPanel {
        return JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
            background = JBColor(0x2b2d30, 0x2b2d30)
            border = JBUI.Borders.compound(
                JBUI.Borders.customLineTop(JBColor(0x43454a, 0x43454a)),
                JBUI.Borders.empty(20, 15)
            )

            add(createActionButton("重新扫描", false) { refreshScan() })
            add(createActionButton("添加 TTF 文件", true) { addUserTTF() })
        }
    }

    private fun createActionButton(text: String, isPrimary: Boolean, action: ActionListener): JButton {
        return JButton(text).apply {
            font = font.deriveFont(Font.BOLD, 13f)
            border = BorderFactory.createEmptyBorder(10, 20, 10, 20)
            isFocusPainted = false

            if (isPrimary) {
                background = JBColor(0x4c7fef, 0x6366f1)
                foreground = Color.WHITE
            } else {
                background = JBColor(0x43454a, 0x43454a)
                foreground = JBColor(0xbcbec4, 0xbcbec4)
            }

            addActionListener(action)
        }
    }

    private fun loadTTFs() {
        containerPanel.removeAll()
        val ttfs = stateManager.getAllTTFs()
        ttfs.forEach { ttf ->
            containerPanel.add(buildItemPanel(ttf))
        }
        revalidate()
        repaint()
    }

    private fun buildItemPanel(ttf: IconFontTtfFileModel): JPanel {
        return JPanel().apply {
            layout = BorderLayout()
            background = JBColor(0x313438, 0x313438)
            border = JBUI.Borders.compound(
                JBUI.Borders.customLineBottom(JBColor(0x43454a, 0x43454a)),
                JBUI.Borders.empty(16, 20)
            )

            // 设置启用/禁用状态样式
            if (!ttf.enabled) {
                // 禁用状态：半透明
                components.forEach { it.foreground = JBColor.GRAY }
            }

            // 左侧信息区域
            val infoPanel = JPanel(BorderLayout()).apply {
                background = JBColor(0x313438, 0x313438)

                // 图标区域
                val iconPanel = JPanel().apply {
                    layout = FlowLayout(FlowLayout.LEFT, 0, 0)
                    background = JBColor(0x313438, 0x313438)
                    preferredSize = Dimension(50, 40)

                    val iconLabel = JLabel().apply {
                        text = if (ttf.type == TTFType.ICON_FONT) "★" else "T"
                        font = font.deriveFont(Font.BOLD, 20f)
                        foreground = if (ttf.type == TTFType.ICON_FONT) {
                            JBColor(0x4c7fef, 0x6366f1)
                        } else {
                            JBColor.GRAY
                        }
                        horizontalAlignment = SwingConstants.CENTER
                        preferredSize = Dimension(36, 36)
                        isOpaque = true
                        background = if (ttf.type == TTFType.ICON_FONT) {
                            JBColor(0x43454a, 0x43454a)
                        } else {
                            JBColor(0x43454a, 0x43454a)
                        }
                        border = JBUI.Borders.empty(8)

                        // 对于扫描类型的 TTF，点击图标可以切换开关
                        if (ttf.source !is TTFSource.UserAdded) {
                            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                            addMouseListener(object : java.awt.event.MouseAdapter() {
                                override fun mouseClicked(e: java.awt.event.MouseEvent?) {
                                    stateManager.updateTTFState(ttf.id, !ttf.enabled)
                                }
                            })
                        }
                    }
                    add(iconLabel)
                }

                // 文件信息
                val detailsPanel = JPanel().apply {
                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                    background = JBColor(0x313438, 0x313438)

                    add(JLabel(ttf.ttfFileName).apply {
                        font = font.deriveFont(Font.BOLD, 14f)
                        foreground = if (ttf.enabled) JBColor.WHITE else JBColor.GRAY
                    })

                    add(JLabel(ttf.ttfAbsolutePath).apply {
                        font = Font("JetBrains Mono", Font.PLAIN, 11)
                        foreground = if (ttf.enabled) JBColor(0x8c9196, 0x8c9196) else JBColor.GRAY
                        border = JBUI.Borders.emptyTop(4)
                    })

                    // AAR 信息
                    if (ttf.source is TTFSource.Dependency) {
                        add(JLabel("AAR: ${ttf.source.depName}").apply {
                            font = font.deriveFont(10f)
                            foreground = JBColor.GRAY
                            border = JBUI.Borders.emptyTop(2)
                        })
                    }
                }

                add(iconPanel, BorderLayout.WEST)
                add(detailsPanel, BorderLayout.CENTER)
            }

            // 右侧控制区域
            val controlPanel = JPanel(FlowLayout()).apply {
                background = JBColor(0x313438, 0x313438)

                // 来源标签
                val sourceLabel = JLabel(getSourceText(ttf.source)).apply {
                    font = font.deriveFont(Font.BOLD, 11f)
                    foreground = Color.WHITE
                    isOpaque = true
                    background = getSourceColor(ttf.source)
                    border = JBUI.Borders.empty(4, 10)
                }
                add(sourceLabel)

                // 控制组件
                when (ttf.source) {
                    is TTFSource.UserAdded -> {
                        // 用户添加的 TTF 显示删除按钮
                        val deleteBtn = JButton("×").apply {
                            font = font.deriveFont(Font.BOLD, 18f)
                            foreground = JBColor(0x8c9196, 0x8c9196)
                            background = JBColor(0x313438, 0x313438)
                            border = BorderFactory.createEmptyBorder(6, 6, 6, 6)
                            isFocusPainted = false
                            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

                            addMouseListener(object : java.awt.event.MouseAdapter() {
                                override fun mouseEntered(e: java.awt.event.MouseEvent?) {
                                    foreground = JBColor(0xff8380, 0xff8380)
                                    background = JBColor(0x3a3c41, 0x3a3c41)
                                }

                                override fun mouseExited(e: java.awt.event.MouseEvent?) {
                                    foreground = JBColor(0x8c9196, 0x8c9196)
                                    background = JBColor(0x313438, 0x313438)
                                }
                            })

                            addActionListener {
                                val result = Messages.showYesNoDialog(
                                    "确定要移除 ${ttf.ttfFileName} 吗？",
                                    "确认删除",
                                    Messages.getQuestionIcon()
                                )
                                if (result == Messages.YES) {
                                    stateManager.removeUserTTF(ttf.id)
                                }
                            }
                        }
                        add(deleteBtn)
                    }
                    else -> {
                        // 扫描类型的 TTF 显示开关
                        val toggle = JBCheckBox().apply {
                            isSelected = ttf.enabled
                            background = JBColor(0x313438, 0x313438)
                            addActionListener {
                                stateManager.updateTTFState(ttf.id, isSelected)
                            }
                        }
                        add(toggle)
                    }
                }
            }

            add(infoPanel, BorderLayout.CENTER)
            add(controlPanel, BorderLayout.EAST)

            // 应用禁用状态样式
            if (!ttf.enabled) {
                components.forEach { component ->
                    if (component is Container) {
                        setComponentsEnabled(component, false)
                    }
                }
            }
        }
    }

    private fun getSourceText(source: TTFSource): String = when (source) {
        is TTFSource.ProjectAssets -> "项目扫描"
        is TTFSource.Dependency -> "AAR依赖"
        is TTFSource.UserAdded -> "手动添加"
    }

    private fun getSourceColor(source: TTFSource): Color = when (source) {
        is TTFSource.ProjectAssets -> JBColor(0x2a4e32, 0x81c784)
        is TTFSource.Dependency -> JBColor(0x1a365d, 0x63b3ed)
        is TTFSource.UserAdded -> JBColor(0x7c2d12, 0xffa726)
    }

    private fun setComponentsEnabled(container: Container, enabled: Boolean) {
        container.components.forEach { component ->
            component.foreground = if (enabled) JBColor.WHITE else JBColor.GRAY
            if (component is Container) {
                setComponentsEnabled(component, enabled)
            }
        }
    }

    private fun refreshScan() {
        ApplicationManager.getApplication().executeOnPooledThread {
            runBlocking {
                stateManager.refreshScan()
            }
        }
    }

    private fun addUserTTF() {
        val chooser = FileChooserDescriptorFactory.createSingleFileDescriptor("ttf")
        chooser.title = "选择 TTF 文件"

        val file = FileChooser.chooseFile(chooser, project, null)
        file?.let {
            stateManager.addUserTTF(it.path)
        }
    }

    // TTFStateChangeListener 实现
    override fun onTTFStateChanged(ttf: IconFontTtfFileModel) {
        SwingUtilities.invokeLater { loadTTFs() }
    }

    override fun onTTFAdded(ttf: IconFontTtfFileModel) {
        SwingUtilities.invokeLater { loadTTFs() }
    }

    override fun onTTFRemoved(ttf: IconFontTtfFileModel) {
        SwingUtilities.invokeLater { loadTTFs() }
    }

    override fun onRefreshCompleted(ttfs: List<IconFontTtfFileModel>) {
        SwingUtilities.invokeLater { loadTTFs() }
    }
}
