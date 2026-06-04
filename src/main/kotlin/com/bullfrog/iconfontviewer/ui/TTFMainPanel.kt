package com.bullfrog.iconfontviewer.ui

import com.bullfrog.iconfontviewer.IconFontSettings
import com.bullfrog.iconfontviewer.model.FontInfo
import com.bullfrog.iconfontviewer.model.FontSource
import com.bullfrog.iconfontviewer.start.SearchStartupActivity
import com.bullfrog.iconfontviewer.util.getString
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import javax.swing.*

class TTFMainPanel(private val project: Project) : JPanel() {

    private val settings = IconFontSettings.getInstance(project)
    private val listModel = DefaultListModel<FontInfo>()
    private val fontList = JBList(listModel)

    // 尺寸
    private val itemH = JBUI.scale(68)
    private val indSize = JBUI.scale(38)
    private val tglW = JBUI.scale(36)
    private val tglH = JBUI.scale(20)
    private val delSize = JBUI.scale(28)
    private val pad = JBUI.scale(24)

    // 颜色
    private val accent = JBColor(Color(0x4285F4), Color(0x4c7fef))
    private val indOff = JBColor(Color(0xC8C8C8), Color(0x43454a))
    private val tglOff = JBColor(Color(0xC0C0C0), Color(0x4e5157))
    private val nameOn = JBColor(Color(0x1A1A1A), Color(0xdfe1e5))
    private val nameOff = JBColor(Color(0x999999), Color(0x6e7078))
    private val pathC = JBColor(Color(0x999999), Color(0x6e7078))
    private val borderC = JBColor(Color(0xE8E8E8), Color(0x35373b))
    private val hoverBg = JBColor(Color(0xEDEDED), Color(0x2f3136))
    private val delNormal = JBColor(Color(0xBBBBBB), Color(0x6e7078))
    private val delHover = JBColor(Color(0xE55C5C), Color(0xe55c5c))
    private val sumText = JBColor(Color(0x888888), Color(0x6e7078))
    private val sumBold = JBColor(Color(0x333333), Color(0xbcbec4))
    private val greenC = JBColor(Color(0x3B8F55), Color(0x5a9e6f))

    private var hoverIdx = -1
    private var hoverDel = false

    // 开关动画状态：fontPath → 动画进度 (0.0=off, 1.0=on)
    private val toggleProgress = HashMap<String, Float>()
    private val animTimer = Timer(12) {
        var needRepaint = false
        for ((path, progress) in toggleProgress.toMap()) {
            val enabled = settings.isFontEnabled(path)
            if (enabled == null) {
                toggleProgress.remove(path)
                continue
            }
            val target = if (enabled) 1.0f else 0.0f
            if (progress != target) {
                val step = 0.12f
                val next = if (target > progress) (progress + step).coerceAtMost(1.0f) else (progress - step).coerceAtLeast(0.0f)
                toggleProgress[path] = next
                needRepaint = true
            }
        }
        if (needRepaint) fontList.repaint()
        else (it.source as Timer).stop()
    }

    private val summaryBar = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0))

    init {
        layout = BorderLayout()
        add(buildSummaryBar(), BorderLayout.NORTH)
        add(buildList(), BorderLayout.CENTER)
        add(buildFooter(), BorderLayout.SOUTH)
        refreshList()
    }

    // ── Summary ──
    private fun buildSummaryBar(): JPanel {
        summaryBar.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, borderC),
            JBUI.Borders.empty(14, pad, 14, pad)
        )
        return summaryBar
    }

    private fun updateSummary() {
        summaryBar.removeAll()
        val fontInfos = settings.getFontInfosSnapshot()
        val total = fontInfos.size
        val enabled = fontInfos.count { it.enabled }
        summaryBar.add(JBLabel("$total").apply { font = font.deriveFont(Font.BOLD, 13f); foreground = sumBold })
        summaryBar.add(JBLabel("fonts").apply { font = font.deriveFont(13f); foreground = sumText })
        summaryBar.add(JBLabel("  ·  ").apply { foreground = sumText })
        summaryBar.add(JBLabel("$enabled enabled").apply { font = font.deriveFont(12f); foreground = greenC })
        summaryBar.revalidate()
        summaryBar.repaint()
    }

    // ── List ──
    private fun buildList(): JComponent {
        fontList.cellRenderer = PaintRenderer()
        fontList.fixedCellHeight = itemH
        fontList.selectionMode = ListSelectionModel.SINGLE_SELECTION

        // hover 时显示完整路径 tooltip
        fontList.toolTipText = ""
        fontList.addMouseMotionListener(object : MouseAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                val i = indexAtPoint(e.point)
                fontList.toolTipText = if (i >= 0 && i < listModel.size) listModel.getElementAt(i).path else null
            }
        })

        fontList.addMouseMotionListener(object : MouseAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                val i = indexAtPoint(e.point)
                val b = if (i >= 0) fontList.getCellBounds(i, i) else null
                val d = b != null && (e.x - b.x) > b.width - pad - delSize
                if (i != hoverIdx || d != hoverDel) { hoverIdx = i; hoverDel = d; fontList.repaint() }
            }
        })
        fontList.addMouseListener(object : MouseAdapter() {
            override fun mouseExited(e: MouseEvent?) { hoverIdx = -1; hoverDel = false; fontList.repaint() }
            override fun mouseClicked(e: MouseEvent) {
                val i = indexAtPoint(e.point)
                if (i < 0 || i >= listModel.size) return
                val b = fontList.getCellBounds(i, i) ?: return
                val rx = e.x - b.x
                val w = b.width
                val fi = listModel.getElementAt(i)

                if (rx > w - pad - delSize) { handleDelete(fi); return }
                if (rx > w - pad - delSize - JBUI.scale(12) - tglW) {
                    val enabled = !fi.enabled
                    if (settings.setFontEnabled(fi.path, enabled)) {
                        fi.enabled = enabled
                        listModel.set(i, fi)
                        updateSummary()
                        notifyFontsChanged()
                        if (!animTimer.isRunning) animTimer.start()
                    } else {
                        refreshList()
                    }
                }
            }
        })
        return JBScrollPane(fontList).apply { border = JBUI.Borders.empty() }
    }

    // ── Footer ──
    private fun buildFooter(): JPanel {
        return JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(10), 0)).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, borderC),
                JBUI.Borders.empty(12, pad)
            )
            add(JButton(getString("icv.btn.rescan")).apply { addActionListener { rescanProject() } })
            add(JButton("+ ${getString("icv.btn.add")}").apply { addActionListener { addFontFile() } })
        }
    }

    // ── Data ──
    private fun refreshList() {
        listModel.clear()
        val fontInfos = settings.getFontInfosSnapshot()
        val validPaths = HashSet<String>(fontInfos.size)
        fontInfos.forEach {
            listModel.addElement(it)
            validPaths.add(it.path)
            // 初始化动画进度（无动画，直接到位）
            toggleProgress.putIfAbsent(it.path, if (it.enabled) 1.0f else 0.0f)
        }
        toggleProgress.keys.retainAll(validPaths)
        if (listModel.isEmpty) fontList.emptyText.text = getString("icv.empty.state")
        updateSummary()
    }

    private fun handleDelete(fi: FontInfo) {
        val msg = getString("icv.btn.delete.confirm.message").replace("{0}", fi.fileName)
        if (Messages.showYesNoDialog(msg, getString("icv.btn.delete.confirm.title"), Messages.getQuestionIcon()) == Messages.YES) {
            settings.removeFontByPath(fi.path)
            toggleProgress.remove(fi.path)
            refreshList()
            notifyFontsChanged()
        }
    }

    private fun addFontFile() {
        val d = FileChooserDescriptor(true, false, false, false, false, true)
            .withTitle(getString("icv.chooser.title"))
            .withFileFilter { it.extension?.equals("ttf", true) == true }
        FileChooser.chooseFiles(d, project, null) { files ->
            files.forEach { f ->
                settings.addFontIfAbsent(FontInfo(f.path, FontSource.USER, true))
            }
            refreshList()
            notifyFontsChanged()
        }
    }

    private fun rescanProject() {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Rescanning…", true) {
            override fun run(ind: com.intellij.openapi.progress.ProgressIndicator) {
                SearchStartupActivity.scanForFonts(project, ind)
            }
            override fun onSuccess() = SwingUtilities.invokeLater { refreshList(); notifyFontsChanged() }
        })
    }

    private fun notifyFontsChanged() {
        settings.iconListCache.clear()
        restartDaemonCodeAnalyzer()
    }

    private fun restartDaemonCodeAnalyzer() {
        val analyzer = DaemonCodeAnalyzer.getInstance(project)
        runCatching {
            DaemonCodeAnalyzer::class.java
                .getMethod("restart", Any::class.java)
                .invoke(analyzer, "IconFontViewer fonts changed")
        }.getOrElse {
            DaemonCodeAnalyzer::class.java
                .getMethod("restart")
                .invoke(analyzer)
        }
    }

    override fun removeNotify() {
        super.removeNotify()
        animTimer.stop()
    }

    private fun indexAtPoint(point: Point): Int {
        val idx = fontList.locationToIndex(point)
        if (idx < 0) return -1
        val bounds = fontList.getCellBounds(idx, idx) ?: return -1
        return if (bounds.contains(point)) idx else -1
    }

    private fun truncatePath(path: String, fm: FontMetrics, max: Int): String {
        if (max <= 0) return ""
        if (fm.stringWidth(path) <= max) return path
        val parts = path.split("/")
        if (parts.size > 3) {
            val short = ".../" + parts.takeLast(3).joinToString("/")
            if (fm.stringWidth(short) <= max) return short
        }
        val ellipsisWidth = fm.stringWidth("...")
        if (max <= ellipsisWidth) return "..."
        var end = path.length
        while (end > 0 && fm.stringWidth(path.substring(0, end)) + ellipsisWidth > max) end--
        return path.substring(0, end) + "..."
    }

    // ── Custom Paint Renderer ──
    private inner class PaintRenderer : ListCellRenderer<FontInfo> {

        override fun getListCellRendererComponent(
            list: JList<out FontInfo>, v: FontInfo, idx: Int, sel: Boolean, focus: Boolean
        ): Component = object : JComponent() {
            init { preferredSize = Dimension(list.width, itemH) }

            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                val w = width; val h = height; val cy = h / 2
                val hover = idx == hoverIdx

                // 背景
                if (sel) { g2.color = list.selectionBackground; g2.fillRect(0, 0, w, h) }
                else if (hover) { g2.color = hoverBg; g2.fillRect(0, 0, w, h) }

                // 分隔线
                g2.color = borderC; g2.fillRect(pad, h - 1, w - pad * 2, 1)

                var x = pad

                // ── 指示器 ──
                val iy = cy - indSize / 2
                val r = JBUI.scale(10).toFloat()
                g2.color = if (v.enabled) accent else indOff
                g2.fill(RoundRectangle2D.Float(x.toFloat(), iy.toFloat(), indSize.toFloat(), indSize.toFloat(), r, r))
                g2.color = Color.WHITE
                g2.font = g2.font.deriveFont(Font.BOLD, JBUI.scale(14).toFloat())
                val tfm = g2.fontMetrics
                val tt = v.fileName.firstOrNull()?.uppercaseChar()?.toString() ?: "T"
                g2.drawString(tt, x + (indSize - tfm.stringWidth(tt)) / 2, iy + (indSize + tfm.ascent - tfm.descent) / 2)
                x += indSize + JBUI.scale(14)
                val textX = x

                // ── 文件名 ──
                g2.font = g2.font.deriveFont(Font.BOLD, JBUI.scale(13).toFloat())
                g2.color = if (v.enabled) nameOn else nameOff
                val nfm = g2.fontMetrics
                val nameY = cy - JBUI.scale(4)
                g2.drawString(v.fileName, x, nameY)
                x += nfm.stringWidth(v.fileName) + JBUI.scale(8)

                // ── 来源标签 ──
                paintTag(g2, v.source, x, nameY - nfm.ascent + JBUI.scale(2))

                // ── 路径 ──
                g2.font = g2.font.deriveFont(Font.PLAIN, JBUI.scale(11).toFloat())
                g2.color = pathC
                val pfm = g2.fontMetrics
                val maxPW = w - textX - JBUI.scale(120)
                g2.drawString(truncatePath(v.path, pfm, maxPW), textX, cy + JBUI.scale(14))

                // ── 开关（带动画）──
                val tx = w - pad - delSize - JBUI.scale(12) - tglW
                val ty = cy - tglH / 2
                val prog = toggleProgress.getOrPut(v.path) { if (v.enabled) 1.0f else 0.0f }
                paintToggle(g2, tx, ty, prog)

                // ── 删除 ──
                val dx = w - pad - delSize; val dy = cy - delSize / 2
                val dh = hover && hoverDel
                if (dh) {
                    g2.color = Color(0xFF, 0x50, 0x50, 0x1A)
                    g2.fill(RoundRectangle2D.Float(dx.toFloat(), dy.toFloat(), delSize.toFloat(), delSize.toFloat(), JBUI.scale(6).toFloat(), JBUI.scale(6).toFloat()))
                }
                g2.color = if (dh) delHover else delNormal
                g2.font = g2.font.deriveFont(Font.PLAIN, JBUI.scale(13).toFloat())
                val dfm = g2.fontMetrics; val dt = "\u2715"
                g2.drawString(dt, dx + (delSize - dfm.stringWidth(dt)) / 2, dy + (delSize + dfm.ascent - dfm.descent) / 2)

                g2.dispose()
            }
        }

        private fun paintTag(g2: Graphics2D, src: FontSource, x: Int, y: Int) {
            val txt = src.name
            g2.font = g2.font.deriveFont(Font.BOLD, JBUI.scale(9).toFloat())
            val fm = g2.fontMetrics
            val p = JBUI.scale(6); val tw = fm.stringWidth(txt) + p * 2; val th = fm.height + JBUI.scale(4)
            val (bg, fg) = when (src) {
                FontSource.PROJECT -> Color(0x4A, 0x9E, 0x62, 0x26) to greenC
                FontSource.AAR -> Color(0x4C, 0x7F, 0xEF, 0x26) to JBColor(Color(0x4E82D6), Color(0x6b9ef0))
                FontSource.USER -> Color(0xD9, 0x9E, 0x3C, 0x26) to JBColor(Color(0xB8862D), Color(0xc9923c))
            }
            g2.color = bg
            g2.fill(RoundRectangle2D.Float(x.toFloat(), y.toFloat(), tw.toFloat(), th.toFloat(), JBUI.scale(4).toFloat(), JBUI.scale(4).toFloat()))
            g2.color = fg
            g2.drawString(txt, x + p, y + (th + fm.ascent - fm.descent) / 2)
        }

        private fun paintToggle(g2: Graphics2D, x: Int, y: Int, progress: Float) {
            // 轨道颜色插值
            val p = progress.coerceIn(0f, 1f)
            val offC = tglOff; val onC = accent
            val trackR = ((offC.red + p * (onC.red - offC.red)).toInt()).coerceIn(0, 255)
            val trackG = ((offC.green + p * (onC.green - offC.green)).toInt()).coerceIn(0, 255)
            val trackB = ((offC.blue + p * (onC.blue - offC.blue)).toInt()).coerceIn(0, 255)
            g2.color = Color(trackR, trackG, trackB)
            g2.fill(RoundRectangle2D.Float(x.toFloat(), y.toFloat(), tglW.toFloat(), tglH.toFloat(), tglH.toFloat(), tglH.toFloat()))

            // 滑块位置插值
            val ts = tglH - JBUI.scale(4); val tp = JBUI.scale(2)
            val minX = x + tp; val maxX = x + tglW - ts - tp
            val thumbX = (minX + p * (maxX - minX)).toInt()
            g2.color = Color.WHITE
            g2.fillOval(thumbX, y + tp, ts, ts)
        }

    }
}
