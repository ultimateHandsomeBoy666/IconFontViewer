package com.bullfrog.iconfontviewer

import com.bullfrog.iconfontviewer.model.IconFontPopupModel
import java.util.*

// TODO 命令修改，这个类的职责不止popup可以用，画line marker也都可以用
object FontPopupModelListHolder {

    private val fontPopupModelList = Collections.synchronizedList<IconFontPopupModel>(mutableListOf())

    fun get(): List<IconFontPopupModel> {
        return fontPopupModelList
    }

    fun add(fontPopupModel: IconFontPopupModel) {
        fontPopupModelList.add(fontPopupModel)
    }

    fun remove(fontPopupModel: IconFontPopupModel) {
        fontPopupModelList.remove(fontPopupModel)
    }

}