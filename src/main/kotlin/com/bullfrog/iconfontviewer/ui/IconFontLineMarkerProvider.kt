
package com.bullfrog.iconfontviewer.ui

import com.bullfrog.iconfontviewer.util.getIconForElement
import com.bullfrog.iconfontviewer.util.getString
import com.bullfrog.iconfontviewer.util.isValidExpression
import com.bullfrog.iconfontviewer.util.isValidLayoutXmlElement
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement

class IconFontLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        // 首先，快速判断元素是否可能是我们关心的类型
        if (!element.isValidExpression() && !element.isValidLayoutXmlElement()) {
            return null
        }

        // 然后，调用新的、高效的工具方法来获取图标
        val icon = getIconForElement(element)

        return if (icon != null) {
            // 创建 LineMarkerInfo，导航处理器暂时保持不变
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
}
