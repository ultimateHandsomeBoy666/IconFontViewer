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

/**
 * 检查是否是 strings.xml 中 <string> 标签的开始标签名 token。
 * 匹配的是叶子元素 XmlToken（"string"），而非复合元素 XmlTag。
 * IntelliJ 的 LineMarkerPass 只对叶子元素可靠地调用 getLineMarkerInfo。
 */
fun PsiElement.isStringResourceTagName(): Boolean {
    if (this !is XmlToken) return false
    if (this.tokenType != XmlTokenType.XML_NAME) return false
    if (this.text != "string") return false
    // 确保是开始标签的名称（不是闭合标签 </string> 的名称）
    val prevSibling = this.prevSibling
    if (prevSibling !is XmlToken || prevSibling.tokenType != XmlTokenType.XML_START_TAG_START) return false
    // 父元素必须是 XmlTag，且祖父标签是 <resources>
    val tag = this.parent as? XmlTag ?: return false
    val parentTag = tag.parentTag ?: return false
    return parentTag.name == "resources"
}

/**
 * 从 string resource 标签名 token 获取其所属的 XmlTag
 */
fun PsiElement.getParentStringResourceTag(): XmlTag? {
    if (!this.isStringResourceTagName()) return null
    return this.parent as? XmlTag
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
        this.isStringResourceTagName() -> {
            val tag = this.parent as? XmlTag ?: return ""
            return tag.getAttributeValue("name") ?: ""
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

/**
 * 获取 strings.xml 中 <string> 标签的文本内容（Unicode 字符值）。
 * 从叶子 token 元素向上找到父 XmlTag 来读取。
 */
fun PsiElement.getStringTagValue(): String? {
    val tag = when {
        this is XmlTag -> this
        this.isStringResourceTagName() -> this.parent as? XmlTag
        else -> null
    } ?: return null
    val text = tag.value.text
    if (text.isBlank()) return null
    return decodeIconFontValue(text)
}

private fun decodeIconFontValue(text: String): String {
    var result = text.trim()
    result = result.replace(Regex("&#x([0-9a-fA-F]+);")) {
        try { String(Character.toChars(it.groupValues[1].toInt(16))) } catch (_: Exception) { it.value }
    }
    result = result.replace(Regex("&#(\\d+);")) {
        try { String(Character.toChars(it.groupValues[1].toInt())) } catch (_: Exception) { it.value }
    }
    result = result.replace(Regex("\\\\u([0-9a-fA-F]{4})")) {
        try { String(Character.toChars(it.groupValues[1].toInt(16))) } catch (_: Exception) { it.value }
    }
    return result
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
