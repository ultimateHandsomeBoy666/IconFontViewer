
package com.bullfrog.iconfontviewer.util

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceValue
import com.android.resources.ResourceType
import com.bullfrog.iconfontviewer.IconFontSettings
import com.bullfrog.iconfontviewer.ui.IconFromIconFontCharacter
import com.intellij.psi.PsiElement
import org.jetbrains.android.facet.AndroidFacet
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.bullfrog.iconfontviewer.model.IconFontPopupModel
import java.awt.Font
import java.io.File
import java.util.*
import javax.swing.Icon

/**
 * 图标匹配结果，包含渲染后的图标和匹配的字体信息
 */
data class IconMatchResult(
    val icon: Icon,
    val font: Font,
    val fontPath: String
)

fun getString(key: String): String {
    val bundle = ResourceBundle.getBundle("strings")
    return bundle.getString(key)
}

/**
 * 根据 PSI 元素获取 iconfont 图标。
 * 返回 IconMatchResult（包含图标和匹配的字体信息），或 null（未找到匹配字体）。
 */
fun getIconForElement(element: PsiElement): IconMatchResult? {
    val instance = IconFontSettings.getInstance(element.project)
    val enabledFontInfos = instance.state.fontInfos.filter { it.enabled }

    if (enabledFontInfos.isEmpty()) return null

    val resourceValue = getResourceValueForElement(element) ?: return null
    val charText = resourceValue.value ?: return null

    for (fontInfo in enabledFontInfos) {
        val font = instance.fontCache.computeIfAbsent(fontInfo.path) { path ->
            try {
                Font.createFont(Font.TRUETYPE_FONT, File(path))
                    .deriveFont(IconFromIconFontCharacter.FONT_SIZE)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        } ?: continue

        if (font.canDisplayUpTo(charText) == -1) {
            val icon = IconFromIconFontCharacter(charText, font)
            return IconMatchResult(icon, font, fontInfo.path)
        }
    }

    return null
}

/**
 * 为指定字体构建所有可用图标的列表（用于弹窗展示）。
 * 遍历项目中的所有 string 资源，筛选出该字体能渲染的字符。
 */
fun buildIconListForFont(font: Font, fontPath: String, element: PsiElement): List<IconFontPopupModel> {
    val androidFacet = AndroidFacet.getInstance(element) ?: return emptyList()
    val manager = StudioResourceRepositoryManager.getInstance(androidFacet)
    val namespace = ResourceNamespace.TODO()
    val allNames = manager.appResources.getResourceNames(namespace, ResourceType.STRING)

    val result = mutableListOf<IconFontPopupModel>()

    for (name in allNames) {
        val items = manager.appResources.getResources(namespace, ResourceType.STRING, name)
        if (items.isEmpty()) continue
        val resourceValue = items[0].resourceValue ?: continue
        val charText = resourceValue.value ?: continue

        // 只保留看起来像 iconfont 字符的 string 资源：
        // 1. 长度为 1-2（单个 Unicode 字符，包括 surrogate pair）
        // 2. 字符在 Private Use Area (U+E000-U+F8FF, U+F0000-U+FFFFD) 或其他非常规区域
        if (!looksLikeIconChar(charText)) continue

        if (font.canDisplayUpTo(charText) == -1) {
            val icon = IconFromIconFontCharacter(charText, font)
            result.add(IconFontPopupModel(icon, name, charText))
        }
    }

    return result
}

/**
 * 判断一个 string 值是否看起来像 iconfont 字符。
 * Icon font 的值通常是单个 Unicode 字符，且位于 Private Use Area 或非 ASCII 区域。
 */
private fun looksLikeIconChar(text: String): Boolean {
    if (text.isEmpty()) return false
    // 最多 2 个 char（支持 surrogate pair 表示的高位 Unicode）
    if (text.length > 2) return false
    val codePoint = text.codePointAt(0)
    // Private Use Area: U+E000-U+F8FF (BMP PUA)
    // Supplementary PUA-A: U+F0000-U+FFFFF
    // Supplementary PUA-B: U+100000-U+10FFFD
    // 也接受其他非 ASCII、非常见文字的字符（> U+2000）
    return codePoint >= 0x2000
}

private fun getResourceValueForElement(element: PsiElement): ResourceValue? {
    val androidFacet = AndroidFacet.getInstance(element) ?: return null

    val resourceName = element.removePrefix()
    if (resourceName.isBlank()) return null

    val manager = StudioResourceRepositoryManager.getInstance(androidFacet)
    val stringResource = manager.appResources.getResources(
        ResourceNamespace.TODO(), ResourceType.STRING, resourceName
    )

    return if (stringResource.isNotEmpty()) {
        stringResource[0].resourceValue
    } else {
        null
    }
}
