package com.bullfrog.iconfontviewer.service

import com.bullfrog.iconfontviewer.model.TTFType

/**
 * TTF 文件智能分类器
 * 基于启发式规则判断 TTF 文件类型
 */
class TTFClassifier {

    companion object {
        private val ICON_FONT_KEYWORDS = setOf(
            "icon", "iconfont", "fontawesome", "material",
            "symbol", "glyph", "pictogram", "webfont"
        )

        private val ICON_FONT_PATTERNS = listOf(
            Regex(".*icon.*", RegexOption.IGNORE_CASE),
            Regex(".*font.*awesome.*", RegexOption.IGNORE_CASE),
            Regex(".*material.*icon.*", RegexOption.IGNORE_CASE),
            Regex(".*symbol.*", RegexOption.IGNORE_CASE)
        )
    }

    /**
     * 基于启发式规则分类 TTF 文件
     */
    fun classifyTTF(fileName: String, filePath: String): TTFType {
        val lowerName = fileName.lowercase()
        val lowerPath = filePath.lowercase()

        if (ICON_FONT_KEYWORDS.any { lowerName.contains(it) }) {
            return TTFType.ICON_FONT
        }

        if (ICON_FONT_PATTERNS.any { it.matches(lowerName) }) {
            return TTFType.ICON_FONT
        }

        if (lowerPath.contains("icons") || lowerPath.contains("iconfont")) {
            return TTFType.ICON_FONT
        }

        return TTFType.REGULAR_FONT
    }
}