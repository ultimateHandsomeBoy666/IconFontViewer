package com.bullfrog.iconfontviewer

import com.bullfrog.iconfontviewer.model.IconFontTtfFileModel
import com.bullfrog.iconfontviewer.model.TTFSource
import com.bullfrog.iconfontviewer.model.TTFType
import com.bullfrog.iconfontviewer.service.TTFRepository
import com.bullfrog.iconfontviewer.service.TTFClassifier
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DisplayName("Simple Integration Tests")
class SimpleIntegrationTest {

    private lateinit var repository: TTFRepository
    private lateinit var classifier: TTFClassifier

    @BeforeEach
    fun setUp() {
        repository = TTFRepository()
        classifier = TTFClassifier()
    }

    private fun createTestTTF(
        id: String,
        fileName: String,
        enabled: Boolean = true,
        source: TTFSource = TTFSource.ProjectAssets,
        type: TTFType = TTFType.ICON_FONT
    ): IconFontTtfFileModel {
        return IconFontTtfFileModel(
            id = id,
            enabled = enabled,
            ttfFileName = fileName,
            ttfAbsolutePath = "/test/path/$fileName",
            font = null,
            source = source,
            type = type,
            fileSize = 1024L
        )
    }

    @Test
    @DisplayName("Should integrate repository with classifier correctly")
    fun shouldIntegrateRepositoryWithClassifierCorrectly() {
        // Given
        val iconFontName = "material-icons.ttf"
        val regularFontName = "roboto.ttf"

        // When - Classify fonts
        val iconFontType = classifier.classifyTTF(iconFontName, "/path/to/$iconFontName")
        val regularFontType = classifier.classifyTTF(regularFontName, "/path/to/$regularFontName")

        // Create TTF models based on classification
        val iconTTF = createTestTTF("id1", iconFontName, type = iconFontType, enabled = iconFontType == TTFType.ICON_FONT)
        val regularTTF = createTestTTF("id2", regularFontName, type = regularFontType, enabled = regularFontType == TTFType.ICON_FONT)

        // Add to repository
        repository.put(iconTTF)
        repository.put(regularTTF)

        // Then - Verify integration
        val allTTFs = repository.getAll()
        val enabledTTFs = repository.getAllEnabledTTFs()

        assertEquals(2, allTTFs.size, "Should have 2 TTFs")
        assertEquals(1, enabledTTFs.size, "Should have 1 enabled TTF (icon font)")

        val iconFont = allTTFs.find { it.type == TTFType.ICON_FONT }
        val regularFont = allTTFs.find { it.type == TTFType.REGULAR_FONT }

        assertEquals(iconFont?.enabled, true, "Icon font should be enabled")
        assertEquals(regularFont?.enabled, false, "Regular font should be disabled")
    }

    @Test
    @DisplayName("Should handle different TTF sources correctly")
    fun shouldHandleDifferentTTFSourcesCorrectly() {
        // Given
        val projectTTF = createTestTTF("project1", "iconfont.ttf", source = TTFSource.ProjectAssets)
        val userTTF = createTestTTF("user1", "custom.ttf", source = TTFSource.UserAdded)
        val aarTTF = createTestTTF("aar1", "material.ttf", source = TTFSource.Dependency("com.google.android.material", "1.0"))

        // When
        repository.put(projectTTF)
        repository.put(userTTF)
        repository.put(aarTTF)

        // Then
        assertEquals(3, repository.size(), "Should have 3 TTFs")

        val projectTTFs = repository.getBySource(TTFSource.ProjectAssets::class.java)
        val userTTFs = repository.getBySource(TTFSource.UserAdded::class.java)
        val aarTTFs = repository.getBySource(TTFSource.Dependency::class.java)

        assertEquals(1, projectTTFs.size, "Should have 1 project TTF")
        assertEquals(1, userTTFs.size, "Should have 1 user TTF")
        assertEquals(1, aarTTFs.size, "Should have 1 AAR TTF")
    }

    @Test
    @DisplayName("Should perform CRUD operations correctly")
    fun shouldPerformCRUDOperationsCorrectly() {
        // Given
        val ttf1 = createTestTTF("id1", "font1.ttf", enabled = true)
        val ttf2 = createTestTTF("id2", "font2.ttf", enabled = false)

        // Create
        repository.put(ttf1)
        repository.put(ttf2)
        assertEquals(2, repository.size(), "Should have 2 TTFs after creation")

        // Read
        val retrieved1 = repository.findById("id1")
        val retrieved2 = repository.findById("id2")
        assertEquals(ttf1, retrieved1, "Should retrieve TTF1 correctly")
        assertEquals(ttf2, retrieved2, "Should retrieve TTF2 correctly")

        // Update
        val updatedTTF1 = ttf1.copy(enabled = false)
        repository.put(updatedTTF1)
        val afterUpdate = repository.findById("id1")
        assertEquals(afterUpdate?.enabled, false, "TTF1 should be disabled after update")

        // Delete
        val removed = repository.remove("id2")
        assertEquals(ttf2, removed, "Should return removed TTF")
        assertEquals(1, repository.size(), "Should have 1 TTF after deletion")
        assertEquals(null, repository.findById("id2"), "TTF2 should no longer exist")
    }

    @Test
    @DisplayName("Should classify various font names correctly")
    fun shouldClassifyVariousFontNamesCorrectly() {
        val testCases = mapOf(
            // Icon fonts
            "iconfont.ttf" to TTFType.ICON_FONT,
            "material-icons.ttf" to TTFType.ICON_FONT,
            "fontawesome.ttf" to TTFType.ICON_FONT,
            "symbol-set.ttf" to TTFType.ICON_FONT,

            // Regular fonts
            "roboto.ttf" to TTFType.REGULAR_FONT,
            "helvetica.ttf" to TTFType.REGULAR_FONT,
            "arial.ttf" to TTFType.REGULAR_FONT,
            "opensans.ttf" to TTFType.REGULAR_FONT
        )

        testCases.forEach { (fileName, expectedType) ->
            val actualType = classifier.classifyTTF(fileName, "/fonts/$fileName")
            assertEquals(expectedType, actualType, "Classification failed for $fileName")
        }
    }

    @Test
    @DisplayName("Should handle repository operations with enabled/disabled states")
    fun shouldHandleRepositoryOperationsWithStates() {
        // Given - Various TTFs with different states
        val ttfs = listOf(
            createTestTTF("enabled1", "icon1.ttf", enabled = true),
            createTestTTF("enabled2", "icon2.ttf", enabled = true),
            createTestTTF("disabled1", "regular1.ttf", enabled = false),
            createTestTTF("disabled2", "regular2.ttf", enabled = false)
        )

        // When
        ttfs.forEach { repository.put(it) }

        // Then
        assertEquals(4, repository.getAll().size, "Should have 4 TTFs")
        assertEquals(2, repository.getAllEnabledTTFs().size, "Should have 2 enabled TTFs")

        val enabledTTFs = repository.getAllEnabledTTFs()
        assertTrue(enabledTTFs.all { it.enabled }, "All returned TTFs should be enabled")
        assertTrue(enabledTTFs.map { it.id }.containsAll(listOf("enabled1", "enabled2")),
            "Should contain correct enabled TTFs")
    }
}