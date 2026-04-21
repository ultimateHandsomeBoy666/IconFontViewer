
package com.bullfrog.iconfontviewer.start

import com.bullfrog.iconfontviewer.IconFontSettings
import com.bullfrog.iconfontviewer.model.FontInfo
import com.bullfrog.iconfontviewer.model.FontSource
import com.bullfrog.iconfontviewer.util.ICONFONT_KEYWORDS
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import java.io.File

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
        fun scanForFonts(project: Project, indicator: ProgressIndicator) {
            indicator.text = "Searching for .ttf files..."
            val settings = IconFontSettings.getInstance(project)
            val existingPaths = settings.getFontInfosSnapshot().associateBy { it.path }

            val newFontInfos = mutableListOf<FontInfo>()
            val discoveredPaths = mutableSetOf<String>()

            // ── 阶段 1：通过 FilenameIndex 扫描项目源码中的 TTF ──
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
                    if (!discoveredPaths.add(path)) continue
                    if (path in existingPaths) continue

                    val source = if (path.contains("/.gradle/caches/") || path.contains("/build/")) {
                        FontSource.AAR
                    } else {
                        FontSource.PROJECT
                    }

                    newFontInfos.add(FontInfo(
                        path = path,
                        source = source,
                        enabled = isLikelyIconFont(filename)
                    ))
                }
            }

            // ── 阶段 2：通过 OrderEnumerator 扫描依赖库中的 TTF（补充 AAR） ──
            indicator.text = "Scanning library dependencies..."
            indicator.text2 = ""
            val modules = ReadAction.compute<Array<com.intellij.openapi.module.Module>, Throwable> {
                ModuleManager.getInstance(project).modules
            }

            for (module in modules) {
                indicator.checkCanceled()
                val libraryRoots = ReadAction.compute<Array<VirtualFile>, Throwable> {
                    OrderEnumerator.orderEntries(module)
                        .librariesOnly()
                        .classesRoots
                }

                for (root in libraryRoots) {
                    indicator.checkCanceled()
                    // 只扫描 res/font 目录和 assets 目录（AAR 中字体的常见位置）
                    ReadAction.run<Throwable> {
                        scanDirForTtf(root, "res/font", existingPaths, discoveredPaths, newFontInfos)
                    }
                    ReadAction.run<Throwable> {
                        scanDirForTtf(root, "assets", existingPaths, discoveredPaths, newFontInfos)
                    }
                }
            }

            indicator.fraction = 0.9
            indicator.text = "Finalizing..."

            // ── 清理已不存在的文件（用户手动添加的除外）──
            val removedPaths = mutableListOf<String>()
            settings.mutateFontInfos { fontInfos ->
                fontInfos.removeAll { fi ->
                    val shouldRemove = fi.source != FontSource.USER &&
                        fi.path !in discoveredPaths &&
                        !File(fi.path).exists()
                    if (shouldRemove) {
                        removedPaths.add(fi.path)
                    }
                    shouldRemove
                }

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
         * 在指定根目录的子目录中搜索 .ttf 文件
         */
        private fun scanDirForTtf(
            root: VirtualFile,
            subPath: String,
            existingPaths: Map<String, FontInfo>,
            discoveredPaths: MutableSet<String>,
            newFontInfos: MutableList<FontInfo>
        ) {
            val dir = root.findFileByRelativePath(subPath) ?: return
            VfsUtilCore.visitChildrenRecursively(dir, object : VirtualFileVisitor<Unit>() {
                override fun visitFile(file: VirtualFile): Boolean {
                    if (!file.isDirectory && file.extension.equals("ttf", ignoreCase = true)) {
                        val path = file.path
                        if (!discoveredPaths.add(path)) {
                            return true
                        }
                        if (path !in existingPaths) {
                            newFontInfos.add(FontInfo(
                                path = path,
                                source = FontSource.AAR,
                                enabled = isLikelyIconFont(file.name)
                            ))
                        }
                    }
                    return true
                }
            })
        }

        private fun isLikelyIconFont(filename: String): Boolean {
            return ICONFONT_KEYWORDS.any { keyword ->
                filename.contains(keyword, ignoreCase = true)
            }
        }
    }
}
