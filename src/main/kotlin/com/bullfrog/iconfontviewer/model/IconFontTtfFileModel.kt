package com.bullfrog.iconfontviewer.model

import java.awt.Font

data class IconFontTtfFileModel(
    val id: String,                           // 唯一标识
    var enabled: Boolean,                     // 是否启用
    val ttfFileName: String,                  // 文件名
    val ttfAbsolutePath: String,              // 绝对路径
    val font: Font?,                          // 字体对象（懒加载，可为null）
    val source: TTFSource,                    // 来源信息
    val type: TTFType,                        // 分类类型
    val fileSize: Long = 0                    // 文件大小
)

/**
 * TTF 文件类型枚举
 */
enum class TTFType {
    ICON_FONT,      // 图标字体（命中启发式规则）
    REGULAR_FONT    // 普通字体（未命中规则）
}

/**
 * TTF 文件来源密封类
 */
sealed class TTFSource {
    object ProjectAssets : TTFSource()
    object UserAdded : TTFSource()
    data class Dependency(
        val depName: String,
        val version: String,
        val groupId: String = "",
        val artifactId: String = ""
    ) : TTFSource()
}