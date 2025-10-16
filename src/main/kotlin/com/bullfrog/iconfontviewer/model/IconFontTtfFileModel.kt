package com.bullfrog.iconfontviewer.model

import java.awt.Font

enum class FontSource {
    PROJECT, // 项目扫描
    AAR,     // AAR依赖
    USER     // 用户手动添加
}

data class FontInfo(
    var path: String = "",
    var source: FontSource = FontSource.PROJECT,
    var enabled: Boolean = false
) {
    val fileName: String
        get() = path.substringAfterLast('/')
}