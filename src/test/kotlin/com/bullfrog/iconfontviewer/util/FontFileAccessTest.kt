package com.bullfrog.iconfontviewer.util

import com.intellij.openapi.progress.ProcessCanceledException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FontFileAccessTest {
    @get:Rule val temporary = TemporaryFolder()
    private val indicator get() = testIndicator()

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun put(path: String, bytes: ByteArray): File = File(temporary.root, path).apply {
        parentFile.mkdirs()
        writeBytes(bytes)
    }

    @Test fun `finds fonts in arbitrary directories and all jar files`() {
        val expected = listOf("res/raw/a.ttf", "assets/custom/a/b/B.TTF", "jars/direct.ttf").map {
            put(it, it.toByteArray()).absolutePath
        }.toMutableSet()
        val first = put("jars/classes.jar", zip("package/custom/c.ttf" to byteArrayOf(1)))
        val second = put("jars/libs/other.jar", zip("root.ttf" to byteArrayOf(2)))
        expected += first.absolutePath + "!/package/custom/c.ttf"
        expected += second.absolutePath + "!/root.ttf"
        put("assets/custom/ignored.otf", byteArrayOf(3))
        val actual = mutableListOf<String>()
        FontFileAccess.scan(temporary.root, indicator, mutableSetOf(), actual::add)
        assertEquals(expected, actual.toSet())
        assertEquals(expected.size, actual.size)
    }

    @Test fun `nested archive scan preserves entries after the nested jar`() {
        val inner = zip("deep/first.ttf" to byteArrayOf(1, 2))
        val outer = zip("lib/inner.jar" to inner, "after/second.TTF" to byteArrayOf(3, 4))
        val aar = put("container.aar", zip("assets/outer.jar" to outer, "last.ttf" to byteArrayOf(5)))
        val actual = mutableListOf<String>()
        FontFileAccess.scan(aar, indicator, mutableSetOf(), actual::add)
        assertEquals(listOf(
            aar.absolutePath + "!/assets/outer.jar!/lib/inner.jar!/deep/first.ttf",
            aar.absolutePath + "!/assets/outer.jar!/after/second.TTF",
            aar.absolutePath + "!/last.ttf"
        ), actual)
        assertArrayEquals(byteArrayOf(1, 2), FontFileAccess.read(actual[0]) { it.readBytes() })
        assertArrayEquals(byteArrayOf(3, 4), FontFileAccess.read(actual[1]) { it.readBytes() })
    }

    @Test fun `overlapping roots and symlink cycles do not duplicate results`() {
        val root = temporary.newFolder("library")
        val font = File(root, "assets/deep/icon.ttf").apply { parentFile.mkdirs(); writeText("font") }
        Files.createSymbolicLink(File(root, "assets/back").toPath(), root.toPath())
        val actual = mutableListOf<String>()
        val visited = mutableSetOf<String>()
        FontFileAccess.scan(File(root, "assets"), indicator, visited, actual::add)
        FontFileAccess.scan(root, indicator, visited, actual::add)
        assertEquals(listOf(font.absolutePath), actual)
    }

    @Test fun `exists checks archive entries rather than just the outer jar`() {
        val jar = put("with spaces/图标.jar", zip("custom/中文.ttf" to byteArrayOf(7)))
        val path = jar.absolutePath + "!/custom/中文.ttf"
        assertTrue(FontFileAccess.exists(path))
        assertFalse(FontFileAccess.exists(jar.absolutePath + "!/missing.ttf"))
        jar.writeBytes(zip("different.ttf" to byteArrayOf(8)))
        assertFalse(FontFileAccess.exists(path))
    }

    @Test fun `bad nested jar does not hide valid sibling fonts`() {
        val file = put("mixed.aar", zip("assets/bad.jar" to byteArrayOf(1), "assets/good.ttf" to byteArrayOf(2)))
        val actual = mutableListOf<String>()
        FontFileAccess.scan(file, indicator, mutableSetOf(), actual::add)
        assertEquals(listOf(file.absolutePath + "!/assets/good.ttf"), actual)
    }

    @Test(expected = ProcessCanceledException::class) fun `cancellation is not swallowed by archive scan`() {
        val jar = put("cancel.jar", zip("icon.ttf" to byteArrayOf(1)))
        val canceled = testIndicator(canceled = true)
        FontFileAccess.scan(jar, canceled, mutableSetOf()) { fail("Canceled scan emitted a font") }
    }

    @Test(expected = FileNotFoundException::class) fun `missing nested font fails explicitly`() {
        val jar = put("outer.jar", zip("inner.jar" to zip("real.ttf" to byteArrayOf(1))))
        FontFileAccess.read(jar.absolutePath + "!/inner.jar!/missing.ttf") { it.readBytes() }
    }
}
