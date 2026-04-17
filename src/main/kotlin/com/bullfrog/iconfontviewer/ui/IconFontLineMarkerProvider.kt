
package com.bullfrog.iconfontviewer.ui

import com.bullfrog.iconfontviewer.util.getIconForElement
import com.bullfrog.iconfontviewer.util.getString
import com.bullfrog.iconfontviewer.util.isValidExpression
import com.bullfrog.iconfontviewer.util.isValidLayoutXmlElement
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager

class IconFontLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (!element.isValidExpression() && !element.isValidLayoutXmlElement()) {
            return null
        }

        val matchResult = getIconForElement(element) ?: return null

        val smartPointer = SmartPointerManager.createPointer(element)

        return LineMarkerInfo(
            element,
            element.textRange,
            matchResult.icon,
            null,
            IconFontLineMarkerNavHandler(smartPointer, matchResult.font, matchResult.fontPath),
            GutterIconRenderer.Alignment.CENTER
        ) { getString("icv.screen.read") }
    }
}
