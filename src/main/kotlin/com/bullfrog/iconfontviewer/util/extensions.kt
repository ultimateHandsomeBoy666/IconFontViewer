package com.bullfrog.iconfontviewer.util

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.xml.*
import com.intellij.ui.scale.JBUIScale
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression

fun PsiElement.isValidExpression(): Boolean {
    val isExpression = this is KtDotQualifiedExpression || this is PsiReferenceExpression
    val containsPrefix = this.text.contains(R_PREFIX)
    return isExpression && containsPrefix
}

fun PsiElement.isValidLayoutXmlElement(): Boolean {
    return this is XmlAttributeValue && this.text.contains(XML_PREFIX)
}

fun PsiElement.isValidResXmlToken(): Boolean {
    return this is XmlTag
}

fun PsiElement.removePrefix(): String {
    when {
        this.isValidExpression() -> {
            return this.text.removePrefix(R_PREFIX)
        }
        this.isValidLayoutXmlElement() -> {
            return (this as? XmlAttributeValue)?.value?.removePrefix(XML_PREFIX) ?:
                    this.text.removePrefix(QUOTE).removeSuffix(QUOTE).removePrefix(XML_PREFIX)
        }
        this.isValidResXmlToken() -> {
            return (this as? XmlTag)
                    ?.children?.firstOrNull { it is XmlAttribute }
                    ?.children?.firstOrNull { it is XmlAttributeValue }
                    ?.text?.removePrefix(QUOTE)?.removeSuffix(QUOTE) ?: this.text
        }
    }
    return this.text
}

fun Int.jbScale(): Int {
    return JBUIScale.scale(this)
}

fun Float.jbScale(): Float {
    return JBUIScale.scale(this)
}

fun Float.jbFontSizeScale(): Int {
    return JBUIScale.scaleFontSize(this)
}