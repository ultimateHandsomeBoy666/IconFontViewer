package com.bullfrog.iconfontviewer

import com.bullfrog.iconfontviewer.model.FontInfo
import com.bullfrog.iconfontviewer.model.IconFontPopupModel
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
    storages = [Storage("iconFontViewer.xml")] // 存储在项目配置目录 .idea/ 下
)
@Service(Service.Level.PROJECT)
class IconFontSettings : PersistentStateComponent<IconFontState>, Disposable {

    private var internalState = IconFontState()
    val fontCache = HashMap<String, Font?>()
    val iconPopupList = mutableListOf<IconFontPopupModel>()


    override fun getState(): IconFontState {
        return internalState
    }

    override fun loadState(state: IconFontState) {
        XmlSerializerUtil.copyBean(state, internalState)
    }

    override fun dispose() {
        fontCache.clear()
        iconPopupList.clear()
    }

    companion object {
        fun getInstance(project: Project): IconFontSettings {
            return project.getService(IconFontSettings::class.java)
        }
    }
}