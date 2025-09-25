package com.bullfrog.iconfontviewer.model

import java.awt.Font

data class IconFontTtfFileModel(
    var selected: Boolean,
    var ttfFileName: String,
    var ttfAbsolutePath: String,
    var font: Font
)