@file:Suppress("RedundantIf")

package com.bullfrog.iconfontviewer.util

import com.android.resources.ResourceType
import com.android.tools.idea.ui.resourcemanager.model.*
import com.bullfrog.iconfontviewer.FontPopupModelListHolder
import com.bullfrog.iconfontviewer.model.IconFontPopupModel
import com.bullfrog.iconfontviewer.service.TTFStateManager
import com.bullfrog.iconfontviewer.start.SearchStartupActivity
import com.bullfrog.iconfontviewer.ui.IconFromIconFontCharacter
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.utils.PrintingLogger
import java.io.File
import java.util.*
import javax.swing.Icon

fun buildLogger(cls: Class<*>): Logger {
    return if (isPluginInDebugMode()) {
        TagPrintLogger(cls)
    } else {
        Logger.getInstance(cls)
    }
}

fun isPluginInDebugMode(): Boolean {
    return ApplicationManager.getApplication().isInternal
}

fun isAndroidProject(project: Project): Boolean {
    val modules = ModuleManager.getInstance(project).modules
    return modules.any { module ->
        AndroidFacet.getInstance(module) != null
    }
}

fun getString(key: String): String {
    val bundle = ResourceBundle.getBundle("strings")
    return bundle.getString(key)
}

fun getIconFromIconFont(psiElement: PsiElement): Icon? {
    println("get icon from iconfont on ${Thread.currentThread()}, text = ${psiElement.text}, $psiElement")
    if (FontPopupModelListHolder.get().isEmpty()) {
        buildFontPopupModelListHolder(psiElement)
    }
    val key = psiElement.removePrefix()
    return FontPopupModelListHolder.get().firstOrNull { it.key == key }?.icon // TODO 优化成map，不要list遍历查询
}

// lazy build, cuz this is just the proper time that you can get non-empty assetList
fun buildFontPopupModelListHolder(psiElement: PsiElement) {
    println("build popup list ${Thread.currentThread()}")
    val startTime = System.currentTimeMillis()

    val resourceType = ResourceType.STRING
    val androidFacet = AndroidFacet.getInstance(psiElement) ?: return
    val stringModuleAssets = getModuleResources(androidFacet, resourceType, emptyList()).assetSets
    val stringDependentAssets = getDependentModuleResources(androidFacet, resourceType, emptyList()).flatMap { it.assetSets }
    val stringLibraryAssets = getLibraryResources(androidFacet, resourceType, emptyList()).flatMap { it.assetSets }
    val assetList = stringModuleAssets + stringDependentAssets + stringLibraryAssets

    assetList.forEach {
        val asset = it.assets.first()
        val text = asset.resourceItem.resourceValue.value
        val key = asset.name
        var icon: Icon? = null
        // 从新的 TTF 状态管理器获取启用的字体
        val project = psiElement.project
        val stateManager = project.service<TTFStateManager>()
        val enabledTTFs = stateManager.getEnabledTTFs()

        if (enabledTTFs.isNotEmpty()) {
            enabledTTFs.forEach ttfLoop@ { ttf ->
                ttf.font?.let { font ->
                    if (font.canDisplayUpTo(text) == -1) {
                        icon = IconFromIconFontCharacter(text, font)
                        return@ttfLoop
                    }
                }
            }
        }
        icon?.let {
            FontPopupModelListHolder.add(IconFontPopupModel(it, key, text))
            println("icon = $icon, key = $key")
        }
    }
    println("buildFontPopupModelListHolder time cost = ${System.currentTimeMillis() - startTime}")
}

fun isTTF(path: String): Boolean {
    val file = File(path)
    if (!file.exists()) return false
    if (!file.isFile) return false
    if (!file.name.endsWith(".ttf")) return false
    return true
}

fun getCurrentPsiFile(project: Project): PsiFile? {
    val document = FileEditorManager.getInstance(project).selectedTextEditor?.document ?: return null
    return PsiDocumentManager.getInstance(project).getPsiFile(document)
}

fun getIconFontString(psiElement: PsiElement): Asset? {
    val resourceType = ResourceType.STRING
    val androidFacet = AndroidFacet.getInstance(psiElement) ?: return null
    val stringModuleAssets = getModuleResources(androidFacet, resourceType, emptyList()).assetSets
    val stringDependentAssets = getDependentModuleResources(androidFacet, resourceType, emptyList()).flatMap { it.assetSets }
    val stringLibraryAssets = getLibraryResources(androidFacet, resourceType, emptyList()).flatMap { it.assetSets }

    val key = psiElement.removePrefix()
    stringModuleAssets.forEach { asset ->
        if (asset.name == key && asset.assets.isNotEmpty()) {
            return asset.assets.first()
        }
    }
    stringDependentAssets.forEach { asset ->
        if (asset.name == key && asset.assets.isNotEmpty()) {
            return asset.assets.first()
        }
    }
    stringLibraryAssets.forEach { asset ->
        if (asset.name == key && asset.assets.isNotEmpty()) {
            return asset.assets.first()
        }
    }
    return null
}

fun getIconFontCharacter(list: List<PsiElement>): List<Pair<Asset, PsiElement>>? {
    if (list.isEmpty()) return null
    val firstElement = list.first()
    val androidFacet = AndroidFacet.getInstance(firstElement) ?: return null
    val stringAssets = getModuleResources(androidFacet, ResourceType.STRING, emptyList()).assetSets
    val result = mutableListOf<Pair<Asset, PsiElement>>()
    list.forEach { psiElement ->
        val key = psiElement.removePrefix()
        stringAssets.forEach { asset ->
            if (asset.name == key && asset.assets.isNotEmpty()) {
                val first = asset.assets.first()
                result.add(Pair(first, psiElement))
            }
        }
    }
    return result
}