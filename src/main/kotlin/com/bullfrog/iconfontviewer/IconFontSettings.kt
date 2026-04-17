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
    val fontCache = ConcurrentHashMap<String, Font?>()

    override fun getState(): IconFontState {
        return internalState
    }

    override fun loadState(state: IconFontState) {
        XmlSerializerUtil.copyBean(state, internalState)
    }

    override fun dispose() {
        fontCache.clear()
    }

    companion object {
        fun getInstance(project: Project): IconFontSettings {
            return project.getService(IconFontSettings::class.java)
        }
    }
}