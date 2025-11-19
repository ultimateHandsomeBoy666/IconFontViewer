package com.bullfrog.iconfontviewer.ui

import com.bullfrog.iconfontviewer.util.getString
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import javax.swing.JComponent
import javax.swing.JLabel

class TTFInputDialog(
    private val project: Project?,
    dialogTitle: String
) : DialogWrapper(
    project, null, false, IdeModalityType.MODELESS, false
) {

    init {
        title = dialogTitle
        init()
    }

    override fun createCenterPanel(): JComponent {
        return project?.let { TTFMainPanel(it) } ?: JLabel(getString("icv.dialog.error.msg.1"))
    }
}