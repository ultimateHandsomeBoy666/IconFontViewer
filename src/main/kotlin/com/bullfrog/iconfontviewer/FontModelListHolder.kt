package com.bullfrog.iconfontviewer

import com.bullfrog.iconfontviewer.model.IconFontTtfFileModel
import com.bullfrog.iconfontviewer.ui.IconFromIconFontCharacter
import java.awt.Font
import java.io.File
import java.util.*

object FontModelListHolder {

    private val fontModelList = Collections.synchronizedList(mutableListOf<IconFontTtfFileModel>())

    fun getFontModelList(): List<IconFontTtfFileModel> = fontModelList

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
            IconFontTtfFileModel(
                selected = true,
                ttfFileName = fontFileName,
                ttfAbsolutePath = fontPath,
                font = font
            )
        )
        return true
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