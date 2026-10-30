package com.bullfrog.iconfontviewer.util

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.io.FileNotFoundException
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/** 磁盘字体和归档内字体共用的路径格式：archive.jar!/directory/font.ttf。 */
internal object FontFileAccess {
    private const val ARCHIVE_SEPARATOR = "!/"
    private val logger = Logger.getInstance(FontFileAccess::class.java)

    fun isArchive(name: String): Boolean =
        name.endsWith(".jar", ignoreCase = true) || name.endsWith(".aar", ignoreCase = true)

    /** Traverse real dependency directories, including unindexed files and symlinks. */
    fun scan(root: File, indicator: ProgressIndicator, visitedPaths: MutableSet<String>, onFont: (String) -> Unit) {
        val files = root.walkTopDown()
            .onEnter { directory ->
                indicator.checkCanceled()
                try {
                    visitedPaths.add(FileUtil.toSystemIndependentName(directory.canonicalPath))
                } catch (e: IOException) {
                    logger.warn("Cannot resolve font directory: $directory", e)
                    false
                }
            }
            .onFail { file, error -> logger.warn("Cannot scan font directory: $file", error) }
        for (file in files) {
            indicator.checkCanceled()
            if (!file.isFile) continue
            val path = FileUtil.toSystemIndependentName(file.absolutePath)
            if (!visitedPaths.add(path)) continue
            when {
                file.extension.equals("ttf", ignoreCase = true) -> onFont(path)
                isArchive(file.name) -> scanArchive(path, indicator, onFont)
            }
        }
    }

    fun scanArchive(path: String, indicator: ProgressIndicator, onFont: (String) -> Unit) {
        try {
            ZipFile(path).use { archive ->
                val entries = archive.entries()
                while (entries.hasMoreElements()) {
                    indicator.checkCanceled()
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    val entryPath = path + ARCHIVE_SEPARATOR + entry.name
                    when {
                        entry.name.endsWith(".ttf", ignoreCase = true) -> onFont(entryPath)
                        isArchive(entry.name) -> archive.getInputStream(entry).use {
                            scanNestedArchive(it, entryPath, indicator, onFont)
                        }
                    }
                }
            }
        } catch (e: IOException) {
            logger.warn("Cannot scan font archive: $path", e)
        }
    }

    private fun scanNestedArchive(
        input: InputStream,
        path: String,
        indicator: ProgressIndicator,
        onFont: (String) -> Unit
    ) {
        try {
            // 关闭当前 ZIP 的解压器，但保留父归档流，继续扫描后续条目。
            val nonClosingInput = object : FilterInputStream(input) {
                override fun close() = Unit
            }
            ZipInputStream(nonClosingInput).use { archive ->
                while (true) {
                    indicator.checkCanceled()
                    val entry = archive.nextEntry ?: break
                    if (!entry.isDirectory) {
                        val entryPath = path + ARCHIVE_SEPARATOR + entry.name
                        when {
                            entry.name.endsWith(".ttf", ignoreCase = true) -> onFont(entryPath)
                            isArchive(entry.name) -> scanNestedArchive(archive, entryPath, indicator, onFont)
                        }
                    }
                    archive.closeEntry()
                }
            }
        } catch (e: IOException) {
            logger.warn("Cannot scan nested font archive: $path", e)
        }
    }

    fun exists(path: String): Boolean {
        if (ARCHIVE_SEPARATOR !in path) return File(path).exists()
        return try {
            read(path) { true }
        } catch (_: IOException) {
            false
        }
    }

    fun <T> read(path: String, action: (InputStream) -> T): T {
        val separator = path.indexOf(ARCHIVE_SEPARATOR)
        if (separator < 0) return File(path).inputStream().use(action)

        val archivePath = path.substring(0, separator)
        val entryPath = path.substring(separator + ARCHIVE_SEPARATOR.length)
        val entryName = entryPath.substringBefore(ARCHIVE_SEPARATOR)
        return ZipFile(archivePath).use { archive ->
            val entry = archive.getEntry(entryName) ?: throw FileNotFoundException(path)
            archive.getInputStream(entry).use { input ->
                if (ARCHIVE_SEPARATOR in entryPath) {
                    readNestedEntry(input, entryPath.substringAfter(ARCHIVE_SEPARATOR), action)
                } else {
                    action(input)
                }
            }
        }
    }

    private fun <T> readNestedEntry(input: InputStream, path: String, action: (InputStream) -> T): T {
        val entryName = path.substringBefore(ARCHIVE_SEPARATOR)
        ZipInputStream(input).use { archive ->
            while (true) {
                val entry = archive.nextEntry ?: break
                if (!entry.isDirectory && entry.name == entryName) {
                    return if (ARCHIVE_SEPARATOR in path) {
                        readNestedEntry(archive, path.substringAfter(ARCHIVE_SEPARATOR), action)
                    } else {
                        action(archive)
                    }
                }
            }
        }
        throw FileNotFoundException(path)
    }
}
