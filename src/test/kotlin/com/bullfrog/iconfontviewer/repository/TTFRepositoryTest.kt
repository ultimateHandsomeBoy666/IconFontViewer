package com.bullfrog.iconfontviewer.repository

import com.bullfrog.iconfontviewer.model.IconFontTtfFileModel
import com.bullfrog.iconfontviewer.model.TTFSource
import com.bullfrog.iconfontviewer.model.TTFType
import com.bullfrog.iconfontviewer.service.TTFRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DisplayName("TTF Repository Tests")
class TTFRepositoryTest {

    private lateinit var repository: TTFRepository

    @BeforeEach
    fun setUp() {
        repository = TTFRepository()
    }

    private fun createTestTTF(
        id: String = "test-id-1",
        fileName: String = "test.ttf",
        path: String = "/path/to/test.ttf",
        enabled: Boolean = true,
        source: TTFSource = TTFSource.ProjectAssets,
        type: TTFType = TTFType.ICON_FONT
    ): IconFontTtfFileModel {
        return IconFontTtfFileModel(
            id = id,
            enabled = enabled,
            ttfFileName = fileName,
            ttfAbsolutePath = path,
            font = null,  // Mock font for testing
            source = source,
            type = type,
            fileSize = 1024L
        )
    }

    @Nested
    @DisplayName("Basic CRUD Operations")
    inner class BasicCrudOperations {

        @Test
        @DisplayName("Should add new TTF successfully")
        fun shouldAddNewTTF() {
            // Given
            val ttf = createTestTTF()

            // When
            repository.put(ttf)

            // Then
            assertEquals(1, repository.size(), "Repository size should be 1")
            assertEquals(ttf, repository.findById(ttf.id), "Should be able to retrieve added TTF")
        }

        @Test
        @DisplayName("Should not add duplicate TTF")
        fun shouldNotAddDuplicateTTF() {
            // Given
            val ttf = createTestTTF()
            repository.put(ttf)

            // When
            val result = repository.put(ttf)

            // Then
            assertEquals(1, repository.size(), "Repository size should remain 1")
        }

        @Test
        @DisplayName("Should retrieve TTF by ID")
        fun shouldRetrieveTTFById() {
            // Given
            val ttf1 = createTestTTF(id = "id1", fileName = "font1.ttf")
            val ttf2 = createTestTTF(id = "id2", fileName = "font2.ttf")
            repository.put(ttf1)
            repository.put(ttf2)

            // When & Then
            assertEquals(ttf1, repository.findById("id1"))
            assertEquals(ttf2, repository.findById("id2"))
            assertNull(repository.findById("nonexistent"))
        }

        @Test
        @DisplayName("Should update existing TTF")
        fun shouldUpdateExistingTTF() {
            // Given
            val originalTTF = createTestTTF(enabled = true)
            repository.put(originalTTF)

            // When
            val updatedTTF = originalTTF.copy(enabled = false)
            repository.put(updatedTTF)

            // Then
            val retrieved = repository.findById(originalTTF.id)
            assertNotNull(retrieved)
            assertFalse(retrieved.enabled, "TTF should be disabled")
        }

        @Test
        @DisplayName("Should remove TTF successfully")
        fun shouldRemoveTTF() {
            // Given
            val ttf = createTestTTF()
            repository.put(ttf)

            // When
            val removed = repository.remove(ttf.id)

            // Then
            assertEquals(ttf, removed, "Should return the removed TTF")
            assertEquals(0, repository.size(), "Repository should be empty")
            assertNull(repository.findById(ttf.id), "TTF should no longer exist")
        }

        @Test
        @DisplayName("Should return null when removing non-existent TTF")
        fun shouldReturnNullWhenRemovingNonExistentTTF() {
            // When
            val result = repository.remove("nonexistent")

            // Then
            assertNull(result, "Should return null for non-existent TTF")
        }
    }

    @Nested
    @DisplayName("Bulk Operations")
    inner class BulkOperations {

        @Test
        @DisplayName("Should get all TTFs")
        fun shouldGetAllTTFs() {
            // Given
            val ttf1 = createTestTTF(id = "id1", fileName = "font1.ttf")
            val ttf2 = createTestTTF(id = "id2", fileName = "font2.ttf")
            val ttf3 = createTestTTF(id = "id3", fileName = "font3.ttf")

            repository.put(ttf1)
            repository.put(ttf2)
            repository.put(ttf3)

            // When
            val allTTFs = repository.getAll()

            // Then
            assertEquals(3, allTTFs.size, "Should return all TTFs")
            assertTrue(allTTFs.contains(ttf1), "Should contain ttf1")
            assertTrue(allTTFs.contains(ttf2), "Should contain ttf2")
            assertTrue(allTTFs.contains(ttf3), "Should contain ttf3")
        }

        @Test
        @DisplayName("Should replace all TTFs")
        fun shouldReplaceAllTTFs() {
            // Given
            val originalTTF = createTestTTF(id = "original")
            repository.put(originalTTF)

            val newTTFs = listOf(
                createTestTTF(id = "new1", fileName = "new1.ttf"),
                createTestTTF(id = "new2", fileName = "new2.ttf")
            )

            // When
            repository.replaceAll(newTTFs)

            // Then
            assertEquals(2, repository.size(), "Repository should contain only new TTFs")
            assertNull(repository.findById("original"), "Original TTF should be removed")
            assertNotNull(repository.findById("new1"), "New TTF 1 should exist")
            assertNotNull(repository.findById("new2"), "New TTF 2 should exist")
        }

        @Test
        @DisplayName("Should clear all TTFs")
        fun shouldClearAllTTFs() {
            // Given
            repository.put(createTestTTF(id = "id1"))
            repository.put(createTestTTF(id = "id2"))
            repository.put(createTestTTF(id = "id3"))

            // When
            repository.clear()

            // Then
            assertEquals(0, repository.size(), "Repository should be empty")
            assertTrue(repository.getAll().isEmpty(), "GetAll should return empty list")
        }
    }

    @Nested
    @DisplayName("Filtering Operations")
    inner class FilteringOperations {

        @Test
        @DisplayName("Should get TTFs by source type")
        fun shouldGetTTFsBySourceType() {
            // Given
            val projectTTF1 = createTestTTF(id = "project1", source = TTFSource.ProjectAssets)
            val projectTTF2 = createTestTTF(id = "project2", source = TTFSource.ProjectAssets)
            val userTTF = createTestTTF(id = "user1", source = TTFSource.UserAdded)
            val aarTTF = createTestTTF(id = "aar1", source = TTFSource.Dependency("test-aar", "1.0"))

            repository.put(projectTTF1)
            repository.put(projectTTF2)
            repository.put(userTTF)
            repository.put(aarTTF)

            // When
            val projectTTFs = repository.getBySource(TTFSource.ProjectAssets::class.java)
            val userTTFs = repository.getBySource(TTFSource.UserAdded::class.java)
            val aarTTFs = repository.getBySource(TTFSource.Dependency::class.java)

            // Then
            assertEquals(2, projectTTFs.size, "Should return 2 project TTFs")
            assertEquals(1, userTTFs.size, "Should return 1 user TTF")
            assertEquals(1, aarTTFs.size, "Should return 1 AAR TTF")

            assertTrue(projectTTFs.all { it.source is TTFSource.ProjectAssets }, "All should be project assets")
            assertTrue(userTTFs.all { it.source is TTFSource.UserAdded }, "All should be user added")
            assertTrue(aarTTFs.all { it.source is TTFSource.Dependency }, "All should be AAR dependencies")
        }

        @Test
        @DisplayName("Should get enabled TTFs only")
        fun shouldGetEnabledTTFsOnly() {
            // Given
            val enabledTTF1 = createTestTTF(id = "enabled1", enabled = true)
            val enabledTTF2 = createTestTTF(id = "enabled2", enabled = true)
            val disabledTTF1 = createTestTTF(id = "disabled1", enabled = false)
            val disabledTTF2 = createTestTTF(id = "disabled2", enabled = false)

            repository.put(enabledTTF1)
            repository.put(enabledTTF2)
            repository.put(disabledTTF1)
            repository.put(disabledTTF2)

            // When
            val enabledTTFs = repository.getAllEnabledTTFs()

            // Then
            assertEquals(2, enabledTTFs.size, "Should return only enabled TTFs")
            assertTrue(enabledTTFs.all { it.enabled }, "All returned TTFs should be enabled")
            assertTrue(enabledTTFs.map { it.id }.containsAll(listOf("enabled1", "enabled2")), "Should contain correct TTFs")
        }

        @Test
        @DisplayName("Should check if contains path")
        fun shouldCheckIfContainsPath() {
            // Given
            val ttf1 = createTestTTF(id = "id1", path = "/path/to/font1.ttf")
            val ttf2 = createTestTTF(id = "id2", path = "/path/to/font2.ttf")
            repository.put(ttf1)
            repository.put(ttf2)

            // When & Then
            assertTrue(repository.containsPath("/path/to/font1.ttf"), "Should contain existing path")
            assertTrue(repository.containsPath("/path/to/font2.ttf"), "Should contain existing path")
            assertFalse(repository.containsPath("/path/to/nonexistent.ttf"), "Should not contain non-existent path")
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    inner class EdgeCasesAndErrorHandling {

        @Test
        @DisplayName("Should handle empty repository operations")
        fun shouldHandleEmptyRepositoryOperations() {
            // When & Then
            assertEquals(0, repository.size(), "Empty repository size should be 0")
            assertTrue(repository.getAll().isEmpty(), "GetAll should return empty list")
            assertTrue(repository.getAllEnabledTTFs().isEmpty(), "GetEnabled should return empty list")
            assertNull(repository.findById("any-id"), "FindById should return null")
            assertNull(repository.remove("any-id"), "Remove should return null")
            assertFalse(repository.containsPath("/any/path"), "ContainsPath should return false")
        }

        @Test
        @DisplayName("Should handle concurrent access safely")
        fun shouldHandleConcurrentAccessSafely() {
            // Given
            val ttfs = (1..100).map { createTestTTF(id = "id$it", fileName = "font$it.ttf") }

            // When - Simulate concurrent access
            ttfs.parallelStream().forEach { repository.put(it) }

            // Then
            assertEquals(100, repository.size(), "All TTFs should be added")
            assertEquals(100, repository.getAll().size, "GetAll should return all TTFs")
        }

        @Test
        @DisplayName("Should maintain data consistency after operations")
        fun shouldMaintainDataConsistencyAfterOperations() {
            // Given
            val ttf1 = createTestTTF(id = "id1", enabled = true)
            val ttf2 = createTestTTF(id = "id2", enabled = false)
            val ttf3 = createTestTTF(id = "id3", enabled = true)

            // When
            repository.put(ttf1)
            repository.put(ttf2)
            repository.put(ttf3)

            val originalSize = repository.size()
            val originalEnabled = repository.getAllEnabledTTFs().size

            // Update one TTF
            repository.put(ttf2.copy(enabled = true))

            // Then
            assertEquals(originalSize, repository.size(), "Size should remain consistent")
            assertEquals(originalEnabled + 1, repository.getAllEnabledTTFs().size, "Enabled count should increase by 1")
        }

        @Test
        @DisplayName("Should handle TTFs with different source types correctly")
        fun shouldHandleDifferentSourceTypesCorrectly() {
            // Given
            val projectTTF = createTestTTF(id = "project", source = TTFSource.ProjectAssets)
            val userTTF = createTestTTF(id = "user", source = TTFSource.UserAdded)
            val aarTTF = createTestTTF(
                id = "aar",
                source = TTFSource.Dependency(
                    depName = "com.example:library",
                    version = "1.2.3",
                    groupId = "com.example",
                    artifactId = "library"
                )
            )

            // When
            repository.put(projectTTF)
            repository.put(userTTF)
            repository.put(aarTTF)

            // Then
            assertEquals(3, repository.size(), "Should store all different source types")

            val retrievedProject = repository.findById("project")
            val retrievedUser = repository.findById("user")
            val retrievedAAR = repository.findById("aar")

            assertNotNull(retrievedProject)
            assertNotNull(retrievedUser)
            assertNotNull(retrievedAAR)

            assertTrue(retrievedProject.source is TTFSource.ProjectAssets)
            assertTrue(retrievedUser.source is TTFSource.UserAdded)
            assertTrue(retrievedAAR.source is TTFSource.Dependency)

            val aarSource = retrievedAAR.source
            assertEquals("com.example:library", aarSource.depName)
            assertEquals("1.2.3", aarSource.version)
        }
    }

    @Nested
    @DisplayName("Dispose and Cleanup")
    inner class DisposeAndCleanup {

        @Test
        @DisplayName("Should clear all data when disposed")
        fun shouldClearAllDataWhenDisposed() {
            // Given
            repository.put(createTestTTF(id = "id1"))
            repository.put(createTestTTF(id = "id2"))
            assertEquals(2, repository.size(), "Repository should have data")

            // When
            repository.dispose()

            // Then
            assertEquals(0, repository.size(), "Repository should be empty after dispose")
            assertTrue(repository.getAll().isEmpty(), "GetAll should return empty list after dispose")
        }
    }
}