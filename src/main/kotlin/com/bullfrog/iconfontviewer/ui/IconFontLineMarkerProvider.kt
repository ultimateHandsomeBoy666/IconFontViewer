
package com.bullfrog.iconfontviewer.ui

import com.bullfrog.iconfontviewer.util.*
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager

class IconFontLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        // 1. Kotlin/Java 代码中的 R.string.xxx 引用
        // 2. XML 布局中的 @string/xxx 属性值
        // 3. strings.xml 中的 <string name="xxx">&#xE88A;</string> 定义
        val matchResult = when {
            element.isValidExpression() -> getIconForElement(element)
            element.isValidLayoutXmlElement() -> getIconForElement(element)
            element.isStringResourceTagName() -> getIconForStringResourceTag(element)
            else -> null
        } ?: return null

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
