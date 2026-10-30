
package com.bullfrog.iconfontviewer.start

import com.android.tools.idea.projectsystem.DependencyScopeType
import com.android.tools.idea.projectsystem.getModuleSystem
import com.bullfrog.iconfontviewer.IconFontSettings
import com.bullfrog.iconfontviewer.model.FontInfo
import com.bullfrog.iconfontviewer.model.FontSource
import com.bullfrog.iconfontviewer.util.FontFileAccess
import com.bullfrog.iconfontviewer.util.ICONFONT_KEYWORDS
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.android.facet.AndroidFacet
import java.io.File
import java.io.IOException

class SearchStartupActivity : StartupActivity.DumbAware {

    override fun runActivity(project: Project) {
        DumbService.getInstance(project).runWhenSmart {
            val task = object : Task.Backgroundable(project, "Scanning IconFont TTF Files", false) {
                override fun run(indicator: ProgressIndicator) {
                    scanForFonts(project, indicator)
                }
            }
            ProgressManager.getInstance().run(task)
        }
    }

    companion object {
        private val logger = Logger.getInstance(SearchStartupActivity::class.java)

        fun scanForFonts(project: Project, indicator: ProgressIndicator) {
            indicator.text = "Searching for .ttf files..."
            val settings = IconFontSettings.getInstance(project)
            val existingPaths = settings.getFontInfosSnapshot().associateBy { it.path }

            val newFontInfos = mutableListOf<FontInfo>()
            val discoveredPaths = mutableSetOf<String>()

            // ── 阶段 1：通过 FilenameIndex 扫描已索引的项目和依赖 TTF ──
            indicator.text2 = "Scanning project sources..."
            val scope = GlobalSearchScope.allScope(project)
            val allTtfFiles = ReadAction.compute<List<String>, Throwable> {
                FilenameIndex.getAllFilenames(project)
                    .filter { it.endsWith(".ttf", ignoreCase = true) }
            }

            indicator.isIndeterminate = false
            var processed = 0.0

            allTtfFiles.forEach { filename ->
                indicator.checkCanceled()
                indicator.fraction = processed++ / (allTtfFiles.size + 1).toDouble() * 0.5
                indicator.text2 = "Processing: $filename"

                val virtualFiles = ReadAction.compute<Collection<VirtualFile>, Throwable> {
                    FilenameIndex.getVirtualFilesByName(filename, scope)
                }
                for (file in virtualFiles) {
                    val path = file.path
                    val source = if (path.contains("/.gradle/caches/") || path.contains("/build/") || "!/" in path) {
                        FontSource.AAR
                    } else {
                        FontSource.PROJECT
                    }

                    recordFont(path, source, existingPaths, discoveredPaths, newFontInfos)
                }
            }

            // ── 阶段 2：扫描依赖目录、AAR 和 JAR 中所有位置的 TTF ──
            indicator.text = "Scanning library dependencies..."
            indicator.text2 = ""
            val modules = ReadAction.compute<Array<com.intellij.openapi.module.Module>, Throwable> {
                ModuleManager.getInstance(project).modules
            }

            val visitedLibraryPaths = mutableSetOf<String>()
            for (module in modules) {
                indicator.checkCanceled()
                val libraryRootPaths = ReadAction.compute<List<String>, Throwable> {
                    // 类根用于找到实际依赖的 JAR/目录，不再相对它们拼接 res 或 assets。
                    val roots = OrderEnumerator.orderEntries(module).librariesOnly().classesRoots.map { root ->
                        VfsUtilCore.getVirtualFileForJar(root)?.path ?: root.path
                    }.toMutableList()
                    val facet = AndroidFacet.getInstance(module) ?: return@compute roots
                    val moduleSystem = facet.getModuleSystem()
                    roots += DependencyScopeType.values().flatMap { scopeType ->
                        moduleSystem.getAndroidLibraryDependencies(scopeType).flatMap { library ->
                            listOfNotNull(
                                library.resFolder?.root?.portablePath,
                                library.assetsFolder?.portablePath,
                                // manifest 所在目录是解包后的 AAR 根，覆盖 jars 及其他子目录。
                                library.manifestFile?.parent?.portablePath,
                                // 原始 AAR 也要扫描，覆盖构建系统没有解包到磁盘的字体。
                                library.location?.portablePath
                            )
                        }
                    }
                    roots
                }

                for (path in libraryRootPaths) {
                    indicator.checkCanceled()
                    if (path in visitedLibraryPaths) continue
                    scanDependencyRoot(
                        File(path), visitedLibraryPaths, existingPaths,
                        discoveredPaths, newFontInfos, indicator
                    )
                }
            }

            indicator.fraction = 0.9
            indicator.text = "Finalizing..."

            // ── 清理已不存在的文件 ──
            // 归档条目的存在性检查可能涉及解压，不能在设置锁内执行。
            val removedPaths = mutableSetOf<String>()
            for (fontInfo in settings.getFontInfosSnapshot()) {
                indicator.checkCanceled()
                if (fontInfo.path !in discoveredPaths && !FontFileAccess.exists(fontInfo.path)) {
                    removedPaths.add(fontInfo.path)
                }
            }
            settings.mutateFontInfos { fontInfos ->
                fontInfos.removeAll { it.path in removedPaths }

                if (newFontInfos.isNotEmpty()) {
                    fontInfos.addAll(newFontInfos)
                }
            }
            for (removedPath in removedPaths) {
                settings.fontCache.remove(removedPath)
            }

            indicator.fraction = 1.0
        }

        /**
         * 直接扫描文件系统，不依赖 IDE 索引或 VFS 缓存；目录和归档 I/O 均在读锁外执行。
         */
        private fun scanDependencyRoot(
            root: File,
            visitedPaths: MutableSet<String>,
            existingPaths: Map<String, FontInfo>,
            discoveredPaths: MutableSet<String>,
            newFontInfos: MutableList<FontInfo>,
            indicator: ProgressIndicator
        ) {
            val files = root.walkTopDown()
                .onEnter { directory ->
                    indicator.checkCanceled()
                    try {
                        // 共享依赖和符号链接只遍历一次，避免重复扫描及目录循环。
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
                    file.extension.equals("ttf", ignoreCase = true) ->
                        recordFont(path, FontSource.AAR, existingPaths, discoveredPaths, newFontInfos)
                    FontFileAccess.isArchive(file.name) -> FontFileAccess.scanArchive(path, indicator) { fontPath ->
                        recordFont(fontPath, FontSource.AAR, existingPaths, discoveredPaths, newFontInfos)
                    }
                }
            }
        }

        private fun recordFont(
            path: String,
            source: FontSource,
            existingPaths: Map<String, FontInfo>,
            discoveredPaths: MutableSet<String>,
            newFontInfos: MutableList<FontInfo>
        ) {
            if (!discoveredPaths.add(path) || path in existingPaths) return
            newFontInfos.add(FontInfo(path, source, isLikelyIconFont(path.substringAfterLast('/'))))
        }

        private fun isLikelyIconFont(filename: String): Boolean {
            return ICONFONT_KEYWORDS.any { keyword ->
                filename.contains(keyword, ignoreCase = true)
            }
        }
    }
}
