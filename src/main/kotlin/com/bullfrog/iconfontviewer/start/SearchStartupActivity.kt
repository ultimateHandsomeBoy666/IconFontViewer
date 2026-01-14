
package com.bullfrog.iconfontviewer.start

import com.bullfrog.iconfontviewer.IconFontSettings
import com.bullfrog.iconfontviewer.model.FontInfo
import com.bullfrog.iconfontviewer.model.FontSource
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

class SearchStartupActivity : StartupActivity.DumbAware {

    override fun runActivity(project: Project) {
        DumbService.getInstance(project).runWhenSmart {
            val task = object : Task.Backgroundable(project, "Scanning IconFont TTF Files", false) {
                override fun run(indicator: ProgressIndicator) {
                    ReadAction.run<Throwable> {
                        scanForFonts(project, indicator)
                    }
                }
            }
            ProgressManager.getInstance().run(task)
        }
    }

    companion object {
        fun scanForFonts(project: Project, indicator: ProgressIndicator) {
            indicator.text = "Searching for .ttf files..."
            val settings = IconFontSettings.getInstance(project)
            val existingPaths = settings.state.fontInfos.map { it.path }.toSet()

            val scope = GlobalSearchScope.allScope(project)
            val allTtfFiles = FilenameIndex.getAllFilenames(project)
                .filter { it.endsWith(".ttf", ignoreCase = true) }

            indicator.isIndeterminate = false
            var processed = 0.0

            val foundPaths = mutableSetOf<String>()

            allTtfFiles.forEach { filename ->
                indicator.checkCanceled()
                indicator.fraction = ++processed / allTtfFiles.size
                indicator.text2 = "Processing: $filename"

                val virtualFiles = FilenameIndex.getVirtualFilesByName(filename, scope)
                for (file in virtualFiles) {
                    foundPaths.add(file.path)
                    if (file.path !in existingPaths) {
                        val source = if (file.path.contains("/.gradle/caches/") || file.path.contains("/build/")) {
                            FontSource.AAR
                        } else {
                            FontSource.PROJECT
                        }

                        val isIconFontHeuristic = filename.contains("icon", ignoreCase = true)

                        val fontInfo = FontInfo(
                            path = file.path,
                            source = source,
                            enabled = isIconFontHeuristic
                        )
                        settings.state.fontInfos.add(fontInfo)
                    }
                }
            }

            // Clean up missing fonts (only for PROJECT and AAR sources, keep USER added fonts if possible or decide policy)
            // Current policy: Remove font if it was auto-detected (PROJECT/AAR) and no longer exists in search results.
            // USER fonts: We might want to keep them, or check if file exists.
            // For now, let's remove if file doesn't exist on disk to be safe, but FilenameIndex only returns existing files.

            // However, iterating over settings and checking if they exist in `foundPaths` is tricky because USER fonts might be outside project scope.
            // Let's just remove PROJECT/AAR source fonts that are not in `foundPaths`.

            val iterator = settings.state.fontInfos.iterator()
            while (iterator.hasNext()) {
                val info = iterator.next()
                if (info.source != FontSource.USER) {
                    if (info.path !in foundPaths) {
                        iterator.remove()
                    }
                } else {
                     // Check if file still exists
                     val file = java.io.File(info.path)
                     if (!file.exists()) {
                         iterator.remove()
                     }
                }
            }
        }
    }
}
