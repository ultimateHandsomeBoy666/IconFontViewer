package com.bullfrog.iconfontviewer

import com.bullfrog.iconfontviewer.model.IconFontTtfFileModel
import com.bullfrog.iconfontviewer.model.TTFSource
import com.bullfrog.iconfontviewer.model.TTFType
import com.bullfrog.iconfontviewer.service.TTFClassifier
import com.bullfrog.iconfontviewer.ui.IconFromIconFontCharacter
import java.awt.Font
import java.io.File
import java.util.*

/**
 * 旧版本的字体模型持有者
 * 保留用于向后兼容和数据迁移
 */
object FontModelListHolder {

    // 用于向后兼容的旧数据结构
    data class LegacyIconFontTtfFileModel(
        var selected: Boolean,
        var ttfFileName: String,
        var ttfAbsolutePath: String,
        var font: Font
    )

    private val fontModelList = Collections.synchronizedList(mutableListOf<LegacyIconFontTtfFileModel>())

    fun getFontModelList(): List<LegacyIconFontTtfFileModel> = fontModelList

    fun getFontPaths(): List<String> {
        return fontModelList.map { it.ttfAbsolutePath }
    }

    fun getFonts(): Collection<Font> {
        return fontModelList.map { it.font }
    }

    fun putFont(
        fontPath: String,
        fontFileName: String = fontPath.split(File.separator).last()
    ): Boolean {
        if (fontModelList.firstOrNull { it.ttfAbsolutePath == fontPath } != null) {
            return false
        }
        val font = createFont(fontPath) ?: return false
        fontModelList.add(
            LegacyIconFontTtfFileModel(
                selected = true,
                ttfFileName = fontFileName,
                ttfAbsolutePath = fontPath,
                font = font
            )
        )
        return true
    }

    fun clear() {
        fontModelList.clear()
    }

    private fun createFont(fontPath: String): Font? {
        return try {
            Font.createFont(Font.TRUETYPE_FONT, File(fontPath)).deriveFont(IconFromIconFontCharacter.FONT_SIZE)
        } catch (e: Throwable) {
            println("fontpath = ${fontPath}, font created failed, $e")
            null
        }
    }
}