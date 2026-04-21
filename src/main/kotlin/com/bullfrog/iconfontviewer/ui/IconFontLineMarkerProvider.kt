
package com.bullfrog.iconfontviewer.ui

import com.bullfrog.iconfontviewer.util.*
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager

class IconFontLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        val matchResult: IconMatchResult
        val pointerTarget: PsiElement
        val markerRange: TextRange

        when {
            element.isLeafOfRStringExpression() -> {
                val expr = element.findParentRStringExpression() ?: return null
                matchResult = getIconForElement(expr) ?: return null
                pointerTarget = expr
                markerRange = expr.textRange
            }
            element.isLeafOfXmlStringAttribute() -> {
                val attrValue = element.parent as? com.intellij.psi.xml.XmlAttributeValue ?: return null
                matchResult = getIconForElement(attrValue) ?: return null
                pointerTarget = attrValue
                markerRange = attrValue.textRange
            }
            element.isStringResourceTagName() -> {
                matchResult = getIconForStringResourceTag(element) ?: return null
                pointerTarget = element
                markerRange = element.textRange
            }
            else -> return null
        }

        val smartPointer = SmartPointerManager.createPointer(pointerTarget)

        return LineMarkerInfo(
            element,
            markerRange,
            matchResult.icon,
            null,
            IconFontLineMarkerNavHandler(smartPointer, matchResult.font, matchResult.fontPath),
            GutterIconRenderer.Alignment.CENTER
        ) { getString("icv.screen.read") }
    }
}
