
package com.bullfrog.iconfontviewer.util

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceValue
import com.android.resources.ResourceType
import com.bullfrog.iconfontviewer.IconFontSettings
import com.bullfrog.iconfontviewer.model.FontInfo
import com.bullfrog.iconfontviewer.ui.IconFromIconFontCharacter
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiModificationTracker
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

private data class IconListCacheEntry(
    val modificationCount: Long,
    val icons: List<IconFontPopupModel>
)

private val FAILED_FONT_SENTINEL = Font("Dialog", Font.PLAIN, 1)

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
    val enabledFontInfos = instance.getFontInfosSnapshot().filter { it.enabled }

    if (enabledFontInfos.isEmpty()) return null

    val resourceValue = getResourceValueForElement(element) ?: return null
    val charText = resourceValue.value ?: return null

    return findMatchForChar(instance, enabledFontInfos, charText)
}

/**
 * 为指定字体构建所有可用图标的列表（用于弹窗展示）。
 * 遍历项目中的所有 string 资源，筛选出该字体能渲染的字符。
 */
fun buildIconListForFont(font: Font, fontPath: String, element: PsiElement): List<IconFontPopupModel> {
    val settings = IconFontSettings.getInstance(element.project)
    val modificationCount = PsiModificationTracker.getInstance(element.project).modificationCount
    @Suppress("UNCHECKED_CAST")
    val cached = settings.iconListCache[fontPath] as? IconListCacheEntry
    cached?.takeIf { it.modificationCount == modificationCount }?.let {
        return it.icons
    }

    val androidFacet = AndroidFacet.getInstance(element) ?: return emptyList()
    val manager = StudioResourceRepositoryManager.getInstance(androidFacet)
    val namespace = ResourceNamespace.RES_AUTO
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

    val immutable = result.toList()
    settings.iconListCache[fontPath] = IconListCacheEntry(modificationCount, immutable)
    return immutable
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

/**
 * 为 strings.xml 中的 <string> 标签获取图标。
 * 直接读取标签的文本内容作为 Unicode 字符，无需查找资源。
 */
fun getIconForStringResourceTag(element: PsiElement): IconMatchResult? {
    val charText = element.getStringTagValue() ?: return null
    if (!looksLikeIconChar(charText)) return null

    val instance = IconFontSettings.getInstance(element.project)
    val enabledFontInfos = instance.getFontInfosSnapshot().filter { it.enabled }
    return findMatchForChar(instance, enabledFontInfos, charText)
}

private fun getResourceValueForElement(element: PsiElement): ResourceValue? {
    val androidFacet = AndroidFacet.getInstance(element) ?: return null

    val resourceName = element.removePrefix()
    if (resourceName.isBlank()) return null

    val manager = StudioResourceRepositoryManager.getInstance(androidFacet)
    val stringResource = manager.appResources.getResources(
        ResourceNamespace.RES_AUTO, ResourceType.STRING, resourceName
    )

    return if (stringResource.isNotEmpty()) {
        stringResource[0].resourceValue
    } else {
        null
    }
}

private fun findMatchForChar(
    settings: IconFontSettings,
    enabledFontInfos: List<FontInfo>,
    charText: String
): IconMatchResult? {
    for (fontInfo in enabledFontInfos) {
        val font = loadFontFromCache(settings, fontInfo.path) ?: continue
        if (font.canDisplayUpTo(charText) == -1) {
            val icon = IconFromIconFontCharacter(charText, font)
            return IconMatchResult(icon, font, fontInfo.path)
        }
    }
    return null
}

private fun loadFontFromCache(settings: IconFontSettings, fontPath: String): Font? {
    val cached = settings.fontCache.computeIfAbsent(fontPath) { path ->
        try {
            Font.createFont(Font.TRUETYPE_FONT, File(path))
                .deriveFont(IconFromIconFontCharacter.FONT_SIZE)
        } catch (_: Exception) {
            FAILED_FONT_SENTINEL
        }
    } ?: return null

    return if (cached === FAILED_FONT_SENTINEL) null else cached
}
