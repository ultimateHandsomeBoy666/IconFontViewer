package com.bullfrog.iconfontviewer.ui

import com.bullfrog.iconfontviewer.util.*
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import javax.swing.Icon

class IconFontLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        var icon: Icon? = null
        if (element.isValidForIconFont()) {
            icon = getIconFromIconFont(element)
        }
        return if (icon != null) {
            LineMarkerInfo(
                element,
                element.textRange,
                icon,
                null,
                IconFontLineMarkerNavHandler().apply { setPsiElement(element) },
                GutterIconRenderer.Alignment.CENTER
            ) { getString("icv.screen.read") }
        } else {
            null
        }
    }

    private fun PsiElement.isValidForIconFont(): Boolean {
        return this.isValidExpression() || this.isValidLayoutXmlElement() || this.isValidResXmlToken()
    }

}