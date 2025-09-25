package com.bullfrog.iconfontviewer

import com.bullfrog.iconfontviewer.ui.TTFInputDialog
import com.bullfrog.iconfontviewer.util.getString
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent


class MainAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        TTFInputDialog(event.project, getString("icv.dialog.title")).show()
    }

}

