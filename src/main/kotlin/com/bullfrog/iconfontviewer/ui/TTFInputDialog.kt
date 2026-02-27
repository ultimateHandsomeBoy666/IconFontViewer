
package com.bullfrog.iconfontviewer.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import javax.swing.JComponent

class TTFInputDialog(
    private val project: Project,
    dialogTitle: String
) : DialogWrapper(
    project, null, false, IdeModalityType.MODELESS, false
) {

    init {
        title = dialogTitle
        isResizable = true
        init()
    }

    override fun createCenterPanel(): JComponent {
        return TTFMainPanel(project).apply {
            preferredSize = Dimension(JBUI.scale(600), JBUI.scale(400))
        }
    }

    // We are using a custom footer in TTFMainPanel, so we don't need the default OK/Cancel buttons.
    override fun createActions(): Array<out javax.swing.Action> {
        return emptyArray()
    }
}
