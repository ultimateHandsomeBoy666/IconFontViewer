package com.bullfrog.iconfontviewer.util

import org.jetbrains.kotlin.utils.PrintingLogger

class TagPrintLogger(private val cls: Class<*>): PrintingLogger(System.out) {
    override fun debug(message: String?) {
        super.debug("#${cls.simpleName} -- $message")
    }

}