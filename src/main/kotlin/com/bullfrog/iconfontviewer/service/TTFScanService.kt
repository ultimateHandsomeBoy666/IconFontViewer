package com.bullfrog.iconfontviewer.service

import com.bullfrog.iconfontviewer.model.IconFontTtfFileModel
import com.bullfrog.iconfontviewer.model.MavenCoordinate
import com.bullfrog.iconfontviewer.model.TTFSource
import com.bullfrog.iconfontviewer.model.TTFType
import com.bullfrog.iconfontviewer.ui.IconFromIconFontCharacter
import com.bullfrog.iconfontviewer.util.buildLogger
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import java.awt.Font
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * TTF 扫描服务
 * 负责扫描项目和依赖中的 TTF 文件
 */
@Service(Service.Level.PROJECT)
class TTFScanService : Disposable {

    companion object {
        @JvmStatic
        fun getInstance(project: Project): TTFScanService = project.service()
    }

    private val logger = buildLogger(TTFScanService::class.java)
    private val classifier = TTFClassifier()
    private val excludedPaths = setOf(
        "/build/",
        "/.git/",
        "/.idea/",
        "/.gradle/",
        "/gradle/",
        "/tmp/",
        "/temp/",
        "/cache/",
        "/src/test"
    )

    // Gradle 缓存路径格式:
    // ~/.gradle/caches/modules-2/files-2.1/groupId/artifactId/version/hash/artifact.jar
    private val gradlePattern = Regex(
        """.gradle[/\\]caches[/\\]modules-2[/\\]files-2.1[/\\]([^/\\]+)[/\\]([^/\\]+)[/\\]([^/\\]+)"""
    )

    // Maven 本地仓库路径格式:
    // ~/.m2/repository/groupId/artifactId/version/artifact.jar
    val mavenPattern = Regex(
        """\.m2[/\\]repository[/\\](.+)[/\\]([^/\\]+)[/\\]([^/\\]+)[/\\][^/\\]+\.(jar|aar)"""
    )

    // 字体对象缓存
    private val fontCache = ConcurrentHashMap<String, Font?>()

    /**
     * 扫描项目中的所有 TTF 文件
     */
    fun scanTTFFiles(project: Project): List<IconFontTtfFileModel> {
        val results = mutableListOf<IconFontTtfFileModel>()

        try {
            results.addAll(scanProjectAssets(project) + scanDependencyFonts(project))
            logger.info("TTF scan completed: found ${results.size} files")
        } catch (e: Exception) {
            logger.error("TTF scan failed", e)
        }

        return results
    }

    /**
     * 扫描项目资源文件夹
     */
    private fun scanProjectAssets(project: Project): List<IconFontTtfFileModel> {
        val startTime = System.currentTimeMillis()
        logger.info("scanProjectAssets: start")
        val results = mutableListOf<IconFontTtfFileModel>()
        try {
            ProjectFileIndex.getInstance(project).iterateContent { fileOrDir ->
                logger.info("scanProjectAssets: current file ${fileOrDir.name}")
                if (fileOrDir.isDirectory && excludedPaths.contains(fileOrDir.name)) {
                    logger.info("scanProjectAssets: hit excludedPaths, skip")
                    return@iterateContent false // 不进入该目录
                }
                if (!fileOrDir.isDirectory && fileOrDir.name.endsWith(".ttf", ignoreCase = true)) {
                    val ttfModel = createTTFModel(
                        file = fileOrDir,
                        source = TTFSource.ProjectAssets
                    )
                    results.add(ttfModel)
                }
                true
            }
        } catch (e: Exception) {
            logger.error("Failed to scan project assets", e)
        }
        logger.info("scanProjectAssets: end, duration = ${System.currentTimeMillis() - startTime}")
        return results
    }

    /**
     * 扫描依赖的 AAR 和 jar 中的字体文件
     */
    private fun scanDependencyFonts(project: Project): List<IconFontTtfFileModel> {
        val startTime = System.currentTimeMillis()
        logger.info("scanDependencyFonts: start")
        val results = mutableListOf<IconFontTtfFileModel>()
        try {
            val librariesScope = GlobalSearchScope.allScope(project)
                .intersectWith(GlobalSearchScope.notScope(GlobalSearchScope.projectScope(project)))

            val fileTypeManager = FileTypeManager.getInstance()
            val fileType = fileTypeManager.getFileTypeByExtension("ttf")
            FileTypeIndex.getFiles(fileType, librariesScope).forEach { fileOrDir ->
                logger.info("scanDependencyFonts: current file ${fileOrDir.name}")
                if (!fileOrDir.isDirectory && fileOrDir.name.endsWith(".ttf", ignoreCase = true)) {
                    val coordinate = parseCoordinateFromPath(fileOrDir)
                    val ttfModel = createTTFModel(
                        file = fileOrDir,
                        source = TTFSource.Dependency(
                            depName = coordinate.buildName(),
                            groupId = coordinate.groupId,
                            artifactId = coordinate.artifactId,
                            version = coordinate.version
                        )
                    )
                    results.add(ttfModel)
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to scan dependency assets", e)
        }
        logger.info("scanDependencyFonts: end, duration = ${System.currentTimeMillis() - startTime}")
        return results
    }



    /**
     * 加载用户手动添加的 TTF 文件
     */
    fun loadUserAddedTTFs(): List<IconFontTtfFileModel> {
        val results = mutableListOf<IconFontTtfFileModel>()
        val configStorage = service<TTFConfigStorage>()
        val userTTFs = configStorage.getUserTTFs()

        userTTFs.forEach { filePath ->
            try {
                val file = File(filePath)
                if (file.exists() && file.name.endsWith(".ttf", ignoreCase = true)) {
                    val virtualFile = VfsUtil.findFileByIoFile(file, true)
                    if (virtualFile != null) {
                        val ttfModel = createTTFModel(
                            file = virtualFile,
                            source = TTFSource.UserAdded
                        )
                        results.add(ttfModel)
                    }
                }
            } catch (e: Exception) {
                logger.warn("Failed to load user TTF: $filePath", e)
            }
        }

        return results
    }

    /**
     * 创建 TTF 模型对象
     */
    private fun createTTFModel(file: VirtualFile, source: TTFSource): IconFontTtfFileModel {
        val type = classifier.classifyTTF(file.name, file.path)
        val enabled = when (source) {
            is TTFSource.UserAdded -> true  // 用户添加的强制启用
            else -> type == TTFType.ICON_FONT  // 其他根据分类决定
        }

        return IconFontTtfFileModel(
            id = generateTTFId(file.path, source),
            enabled = enabled,
            ttfFileName = file.name,
            ttfAbsolutePath = file.path,
            font = createFont(file.path),
            source = source,
            type = type,
            fileSize = file.length
        )
    }

    /**
     * 生成 TTF 唯一标识
     */
    private fun generateTTFId(path: String, source: TTFSource): String {
        val sourcePrefix = when (source) {
            is TTFSource.ProjectAssets -> "project"
            is TTFSource.UserAdded -> "user"
            is TTFSource.Dependency -> "aar_${source.depName}"
        }
        return "$sourcePrefix:${path.hashCode()}"
    }

    /**
     * 创建字体对象（带缓存）
     */
    fun createFont(ttfPath: String): Font? {
        return fontCache.computeIfAbsent(ttfPath) {
            try {
                Font.createFont(Font.TRUETYPE_FONT, File(ttfPath))
                    .deriveFont(IconFromIconFontCharacter.FONT_SIZE)
            } catch (e: Exception) {
                logger.warn("Failed to create font: $ttfPath", e)
                null
            }
        }
    }

    private fun parseCoordinateFromPath(virtualFile: VirtualFile): MavenCoordinate {
        val path = virtualFile.path
        gradlePattern.find(path)?.let { match ->
            return MavenCoordinate(
                groupId = match.groupValues[1],
                artifactId = match.groupValues[2],
                version = match.groupValues[3]
            )
        }
        mavenPattern.find(path)?.let { match ->
            val groupPath = match.groupValues[1]
            val groupId = groupPath.replace(Regex("[/\\\\]"), ".")

            return MavenCoordinate(
                groupId = groupId,
                artifactId = match.groupValues[2],
                version = match.groupValues[3]
            )
        }
        return MavenCoordinate()
    }


    override fun dispose() {
        fontCache.clear()
    }
}