
package com.bullfrog.iconfontviewer.util

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceValue
import com.android.resources.ResourceType
import com.bullfrog.iconfontviewer.IconFontSettings
import com.bullfrog.iconfontviewer.ui.IconFromIconFontCharacter
import com.intellij.psi.PsiElement
import org.jetbrains.android.facet.AndroidFacet
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.ui.resourcemanager.model.getDependentModuleResources
import com.android.tools.idea.ui.resourcemanager.model.getLibraryResources
import com.android.tools.idea.ui.resourcemanager.model.getModuleResources
import com.bullfrog.iconfontviewer.model.IconFontPopupModel
import java.awt.Font
import java.io.File
import java.util.*
import javax.swing.Icon

fun getString(key: String): String {
    val bundle = ResourceBundle.getBundle("strings")
    return bundle.getString(key)
}

fun getIconForElement(element: PsiElement): Icon? {
    val instance = IconFontSettings.getInstance(element.project)
    val enabledFonts = instance.state.fontInfos
        .filter { it.enabled }
        .mapNotNull { fontInfo ->
            instance.fontCache.computeIfAbsent(fontInfo.path) { path ->
                try {
                    Font.createFont(Font.TRUETYPE_FONT, File(path)).deriveFont(IconFromIconFontCharacter.FONT_SIZE)
                } catch (e: Exception) {
                    // Log error or notify user
                    e.printStackTrace()
                    null
                }
            }
        }

    if (enabledFonts.isEmpty()) {
        return null
    }

//    return getResources(element, enabledFonts, instance)

    val resourceValue = getResourceValueForElement(element) ?: return null
    val charText = resourceValue.value
    val key = resourceValue.name

    for (font in enabledFonts) {
        if (font.canDisplayUpTo(charText) == -1) {
            val icon = IconFromIconFontCharacter(charText, font)
            instance.iconPopupList.add(IconFontPopupModel(icon, key, charText))
            return icon
        }
    }

    return null
}

private fun getResourceValueForElement(element: PsiElement): ResourceValue? {
    val androidFacet = AndroidFacet.getInstance(element) ?: return null

    val resourceName = element.removePrefix()
    if (resourceName.isBlank()) return null

    // 使用 ModuleResourceManagers 来准确查找字符串资源的值
    val manager = ResourceRepositoryManager.getInstance(androidFacet)
    val stringResource = manager.appResources.getResources(ResourceNamespace.TODO(), ResourceType.STRING, resourceName)


    return if (stringResource.isNotEmpty()) {
        stringResource[0].resourceValue
    } else {
        null
    }
}

private fun getResources(psiElement: PsiElement, enabledFonts: List<Font>, instance: IconFontSettings): Icon? {
    val resourceType = ResourceType.STRING
    val androidFacet = AndroidFacet.getInstance(psiElement) ?: return null
    val stringModuleAssets = getModuleResources(androidFacet, resourceType, emptyList()).assetSets
    val stringDependentAssets = getDependentModuleResources(androidFacet, resourceType, emptyList()).flatMap { it.assetSets }
    val stringLibraryAssets = getLibraryResources(androidFacet, resourceType, emptyList()).flatMap { it.assetSets }
    val assetList = stringModuleAssets + stringDependentAssets + stringLibraryAssets

    assetList.forEach {
        val asset = it.assets.first()
        val charText = asset.resourceItem.resourceValue.value
        val key = asset.name
        for (font in enabledFonts) {
            if (font.canDisplayUpTo(charText) == -1) {
                val icon = IconFromIconFontCharacter(charText, font)
                instance.iconPopupList.add(IconFontPopupModel(icon, key, charText))
                return icon
            }
        }
    }
    return null
}