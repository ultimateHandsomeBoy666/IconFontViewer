package com.bullfrog.iconfontviewer.util

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.File

class LibraryFontCorpusTest {
    @Test fun `every packaged case loads renders and cannot be satisfied by another case`() {
        val root = File(requireNotNull(System.getProperty("iconfontviewer.fixtures")))
        val manifest = JsonParser.parseString(File(root, "fixtures/library-font-cases.json").readText()).asJsonObject
        val cases = manifest.getAsJsonArray("cases").map { it.asJsonObject }
        val fonts = cases.associate { case ->
            val path = File(root, case["path"].asString).path
            val font = FontFileAccess.read(path) { Font.createFont(Font.TRUETYPE_FONT, it).deriveFont(48f) }
            assertEquals(case["family"].asString, font.getFamily(java.util.Locale.ENGLISH))
            case["id"].asString to font
        }
        for (case in cases) {
            val id = case["id"].asString
            for (point in case.getAsJsonArray("codepoints")) {
                val text = String(Character.toChars(point.asInt))
                assertEquals("Own glyph missing: $id", -1, fonts.getValue(id).canDisplayUpTo(text))
                fonts.filterKeys { it != id }.forEach { (other, font) ->
                    assertNotEquals("False-positive font $other renders $id", -1, font.canDisplayUpTo(text))
                }
                val image = BufferedImage(80, 80, BufferedImage.TYPE_INT_ARGB)
                image.createGraphics().apply { font = fonts.getValue(id); drawString(text, 4, 60); dispose() }
                assertTrue("Empty rendered glyph: $id", (0 until 80).any { x -> (0 until 80).any { y -> image.getRGB(x, y) ushr 24 != 0 } })
            }
        }
        manifest.getAsJsonArray("legacy_fonts").forEach { path ->
            val font = Font.createFont(Font.TRUETYPE_FONT, File(root, path.asString))
            cases.forEach { case -> case.getAsJsonArray("codepoints").forEach { cp ->
                assertFalse("Legacy font can mask a probe", font.canDisplay(cp.asInt))
            } }
        }
    }

    @Test fun `actual dependency archives expose every expected font entry`() {
        val root = File(requireNotNull(System.getProperty("iconfontviewer.fixtures")))
        val manifest = JsonParser.parseString(File(root, "fixtures/library-font-cases.json").readText()).asJsonObject
        val found = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        manifest.getAsJsonArray("artifacts").forEach { artifact ->
            FontFileAccess.scan(File(root, artifact.asJsonObject["path"].asString), testIndicator(), visited) { found += it }
        }
        manifest.getAsJsonArray("cases").map { it.asJsonObject }.filter { it["artifact"].asString != "source" }.forEach { case ->
            assertTrue("Undiscovered packaged font: ${case["id"]}", File(root, case["path"].asString).path in found)
        }
    }
}
