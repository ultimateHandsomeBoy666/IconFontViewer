package com.bullfrog.iconfontviewer.service

import com.bullfrog.iconfontviewer.model.TTFType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DisplayName("TTF Classifier Tests")
class TTFClassifierTest {

    private lateinit var classifier: TTFClassifier

    @BeforeEach
    fun setUp() {
        classifier = TTFClassifier()
    }

    @Nested
    @DisplayName("Icon Font Classification Tests")
    inner class IconFontClassificationTests {

        @ParameterizedTest
        @DisplayName("Should classify icon fonts by filename keywords")
        @ValueSource(strings = [
            "iconfont.ttf",
            "my-icon-set.ttf",
            "fontawesome-webfont.ttf",
            "material-icons.ttf",
            "symbol-font.ttf",
            "pictogram.ttf",
            "glyph-set.ttf",
            "webfont.ttf"
        ])
        fun shouldClassifyIconFontsByKeywords(fileName: String) {
            // When
            val result = classifier.classifyTTF(fileName, "/path/to/font/$fileName")

            // Then
            assertEquals(TTFType.ICON_FONT, result, "File '$fileName' should be classified as ICON_FONT")
        }

        @ParameterizedTest
        @DisplayName("Should classify icon fonts by case-insensitive keywords")
        @ValueSource(strings = [
            "ICONFONT.TTF",
            "My-ICON-Set.ttf",
            "FontAwesome-WebFont.TTF",
            "MATERIAL-ICONS.ttf"
        ])
        fun shouldClassifyIconFontsCaseInsensitive(fileName: String) {
            // When
            val result = classifier.classifyTTF(fileName, "/path/to/font/$fileName")

            // Then
            assertEquals(TTFType.ICON_FONT, result, "File '$fileName' should be classified as ICON_FONT (case insensitive)")
        }

        @ParameterizedTest
        @DisplayName("Should classify icon fonts by path patterns")
        @CsvSource(
            "regular.ttf, /project/src/main/assets/icons/regular.ttf",
            "font.ttf, /project/res/font/iconfont/font.ttf",
            "custom.ttf, /project/assets/icons/custom.ttf"
        )
        fun shouldClassifyIconFontsByPath(fileName: String, filePath: String) {
            // When
            val result = classifier.classifyTTF(fileName, filePath)

            // Then
            assertEquals(TTFType.ICON_FONT, result, "File at path '$filePath' should be classified as ICON_FONT")
        }

        @Test
        @DisplayName("Should classify complex icon font names")
        fun shouldClassifyComplexIconFontNames() {
            val testCases = mapOf(
                "font-awesome-5-free.ttf" to TTFType.ICON_FONT,
                "material-design-icons.ttf" to TTFType.ICON_FONT,
                "feather-icons-webfont.ttf" to TTFType.ICON_FONT,
                "custom-symbol-set.ttf" to TTFType.ICON_FONT
            )

            testCases.forEach { (fileName, expectedType) ->
                val result = classifier.classifyTTF(fileName, "/path/$fileName")
                assertEquals(expectedType, result, "File '$fileName' classification failed")
            }
        }
    }

    @Nested
    @DisplayName("Regular Font Classification Tests")
    inner class RegularFontClassificationTests {

        @ParameterizedTest
        @DisplayName("Should classify regular fonts")
        @ValueSource(strings = [
            "roboto-regular.ttf",
            "helvetica-neue.ttf",
            "arial.ttf",
            "times-new-roman.ttf",
            "noto-sans-cjk.ttf",
            "source-code-pro.ttf",
            "open-sans.ttf"
        ])
        fun shouldClassifyRegularFonts(fileName: String) {
            // When
            val result = classifier.classifyTTF(fileName, "/path/to/font/$fileName")

            // Then
            assertEquals(TTFType.REGULAR_FONT, result, "File '$fileName' should be classified as REGULAR_FONT")
        }

        @Test
        @DisplayName("Should handle edge cases for regular fonts")
        fun shouldHandleEdgeCasesForRegularFonts() {
            val edgeCases = listOf(
                "iconic-but-not-icon.ttf",  // Contains "icon" substring but not as keyword
                "fontastic.ttf",            // Contains "font" but not the full keyword
                "symbolic.ttf"              // Contains "symbol" substring
            )

            edgeCases.forEach { fileName ->
                val result = classifier.classifyTTF(fileName, "/regular/path/$fileName")
                assertEquals(TTFType.REGULAR_FONT, result, "Edge case '$fileName' should be REGULAR_FONT")
            }
        }
    }

    @Nested
    @DisplayName("Regex Pattern Tests")
    inner class RegexPatternTests {

        @Test
        @DisplayName("Should match font awesome patterns")
        fun shouldMatchFontAwesomePatterns() {
            val fontAwesomeFiles = listOf(
                "fontawesome.ttf",
                "font-awesome.ttf",
                "font_awesome_5.ttf",
                "FontAwesome.ttf"
            )

            fontAwesomeFiles.forEach { fileName ->
                val result = classifier.classifyTTF(fileName, "/path/$fileName")
                assertEquals(TTFType.ICON_FONT, result, "FontAwesome pattern '$fileName' should match")
            }
        }

        @Test
        @DisplayName("Should match material icon patterns")
        fun shouldMatchMaterialIconPatterns() {
            val materialFiles = listOf(
                "materialicons.ttf",
                "material-icons.ttf",
                "MaterialIcons.ttf",
                "material_design_icons.ttf"
            )

            materialFiles.forEach { fileName ->
                val result = classifier.classifyTTF(fileName, "/path/$fileName")
                assertEquals(TTFType.ICON_FONT, result, "Material icon pattern '$fileName' should match")
            }
        }

        @Test
        @DisplayName("Should handle empty and null inputs gracefully")
        fun shouldHandleEmptyInputs() {
            // Test empty strings
            assertEquals(TTFType.REGULAR_FONT, classifier.classifyTTF("", ""))
            assertEquals(TTFType.REGULAR_FONT, classifier.classifyTTF("   ", "   "))
        }
    }

    @Nested
    @DisplayName("Real World Test Cases")
    inner class RealWorldTestCases {

        @Test
        @DisplayName("Should classify real-world font files correctly")
        fun shouldClassifyRealWorldFonts() {
            val realWorldTests = mapOf(
                // Icon fonts
                "fa-solid-900.ttf" to TTFType.ICON_FONT,
                "MaterialIcons-Regular.ttf" to TTFType.ICON_FONT,
                "feather.ttf" to TTFType.REGULAR_FONT,  // Feather without "icon" keyword
                "icomoon.ttf" to TTFType.ICON_FONT,
                "fontello.ttf" to TTFType.REGULAR_FONT,  // Fontello without explicit keywords

                // Regular fonts
                "NotoSansCJK-Regular.ttf" to TTFType.REGULAR_FONT,
                "Roboto-Medium.ttf" to TTFType.REGULAR_FONT,
                "SourceCodePro-Regular.ttf" to TTFType.REGULAR_FONT,
                "OpenSans-Bold.ttf" to TTFType.REGULAR_FONT,

                // Edge cases
                "iconic-font-set.ttf" to TTFType.REGULAR_FONT,  // "iconic" not "icon"
                "my-icon-collection.ttf" to TTFType.ICON_FONT,   // Contains "icon"
                "fonts-awesome.ttf" to TTFType.REGULAR_FONT       // "fonts" not "font"
            )

            realWorldTests.forEach { (fileName, expectedType) ->
                val result = classifier.classifyTTF(fileName, "/fonts/$fileName")
                assertEquals(expectedType, result, "Real-world font '$fileName' classification failed")
            }
        }

        @Test
        @DisplayName("Should handle Android project typical structure")
        fun shouldHandleAndroidProjectStructure() {
            val androidTests = mapOf(
                // Typical Android paths
                Pair("iconfont.ttf", "/app/src/main/assets/fonts/iconfont.ttf") to TTFType.ICON_FONT,
                Pair("roboto.ttf", "/app/src/main/res/font/roboto.ttf") to TTFType.REGULAR_FONT,
                Pair("custom.ttf", "/app/src/main/assets/icons/custom.ttf") to TTFType.ICON_FONT,  // icons path

                // AAR dependencies
                Pair("material-icons.ttf", "/gradle/caches/transforms-3/.../material/res/font/material-icons.ttf") to TTFType.ICON_FONT,
                Pair("noto-sans.ttf", "/gradle/caches/transforms-3/.../androidx-core/res/font/noto-sans.ttf") to TTFType.REGULAR_FONT
            )

            androidTests.forEach { (fileNamePathPair, expectedType) ->
                val (fileName, filePath) = fileNamePathPair
                val result = classifier.classifyTTF(fileName, filePath)
                assertEquals(expectedType, result, "Android project font '$fileName' at '$filePath' classification failed")
            }
        }
    }
}