package com.bullfrog.iconfontviewer.util

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import java.lang.reflect.Proxy

/** The scanner needs only cancellation checks; JVM tests do not bootstrap an IDE Application. */
internal fun testIndicator(canceled: Boolean = false): ProgressIndicator =
    Proxy.newProxyInstance(ProgressIndicator::class.java.classLoader, arrayOf(ProgressIndicator::class.java)) { _, method, _ ->
        when {
            method.name == "checkCanceled" -> {
                if (canceled) throw ProcessCanceledException()
                null
            }
            method.name == "isCanceled" -> canceled
            method.returnType == java.lang.Boolean.TYPE -> false
            method.returnType == java.lang.Double.TYPE -> 0.0
            else -> null
        }
    } as ProgressIndicator
