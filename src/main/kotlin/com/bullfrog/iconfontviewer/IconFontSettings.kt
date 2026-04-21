package com.bullfrog.iconfontviewer

import com.bullfrog.iconfontviewer.model.FontInfo
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import java.awt.Font
import java.util.concurrent.ConcurrentHashMap

class IconFontState {
    var fontInfos: MutableList<FontInfo> = mutableListOf()
}

@State(
    name = "com.bullfrog.iconfontviewer.IconFontSettings",
    storages = [Storage("iconFontViewer.xml")]
)
@Service(Service.Level.PROJECT)
class IconFontSettings : PersistentStateComponent<IconFontState>, Disposable {

    private var internalState = IconFontState()
    private val fontInfosLock = Any()
    val fontCache = ConcurrentHashMap<String, Font?>()

    val iconListCache = ConcurrentHashMap<String, Any>()

    override fun getState(): IconFontState {
        return internalState
    }

    override fun loadState(state: IconFontState) {
        synchronized(fontInfosLock) {
            XmlSerializerUtil.copyBean(state, internalState)
        }
    }

    override fun dispose() {
        synchronized(fontInfosLock) {
            internalState.fontInfos.clear()
        }
        fontCache.clear()
        iconListCache.clear()
    }

    fun getFontInfosSnapshot(): List<FontInfo> {
        return synchronized(fontInfosLock) {
            // Return copies to avoid sharing mutable items across threads.
            internalState.fontInfos.map { it.copy() }
        }
    }

    fun mutateFontInfos(mutator: (MutableList<FontInfo>) -> Unit) {
        synchronized(fontInfosLock) {
            mutator(internalState.fontInfos)
        }
    }

    fun isFontEnabled(path: String): Boolean? {
        return synchronized(fontInfosLock) {
            internalState.fontInfos.firstOrNull { it.path == path }?.enabled
        }
    }

    fun setFontEnabled(path: String, enabled: Boolean): Boolean {
        return synchronized(fontInfosLock) {
            val target = internalState.fontInfos.firstOrNull { it.path == path } ?: return@synchronized false
            target.enabled = enabled
            true
        }
    }

    fun removeFontByPath(path: String): Boolean {
        val removed = synchronized(fontInfosLock) {
            internalState.fontInfos.removeIf { it.path == path }
        }
        if (removed) {
            fontCache.remove(path)
        }
        return removed
    }

    fun addFontIfAbsent(fontInfo: FontInfo): Boolean {
        return synchronized(fontInfosLock) {
            if (internalState.fontInfos.any { it.path == fontInfo.path }) {
                false
            } else {
                internalState.fontInfos.add(fontInfo)
                true
            }
        }
    }

    companion object {
        fun getInstance(project: Project): IconFontSettings {
            return project.getService(IconFontSettings::class.java)
        }
    }
}
