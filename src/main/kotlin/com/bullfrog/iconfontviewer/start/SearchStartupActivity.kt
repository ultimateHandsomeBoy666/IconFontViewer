
package com.bullfrog.iconfontviewer.start

import com.bullfrog.iconfontviewer.IconFontSettings
import com.bullfrog.iconfontviewer.model.FontInfo
import com.bullfrog.iconfontviewer.model.FontSource
import com.bullfrog.iconfontviewer.util.ICONFONT_KEYWORDS
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
            val existingPaths = settings.state.fontInfos.associateBy { it.path }

            val scope = GlobalSearchScope.allScope(project)
            val allTtfFiles = FilenameIndex.getAllFilenames(project)
                .filter { it.endsWith(".ttf", ignoreCase = true) }

            indicator.isIndeterminate = false
            var processed = 0.0

            val newFontInfos = mutableListOf<FontInfo>()

            allTtfFiles.forEach { filename ->
                indicator.checkCanceled()
                indicator.fraction = ++processed / allTtfFiles.size
                indicator.text2 = "Processing: $filename"

                val virtualFiles = FilenameIndex.getVirtualFilesByName(filename, scope)
                for (file in virtualFiles) {
                    val existing = existingPaths[file.path]
                    if (existing != null) {
                        // 已有记录：保留用户的 enabled 选择，不覆盖
                        continue
                    }

                    val source = if (file.path.contains("/.gradle/caches/") || file.path.contains("/build/")) {
                        FontSource.AAR
                    } else {
                        FontSource.PROJECT
                    }

                    // 智能检测：文件名包含关键词的默认启用
                    val isIconFont = ICONFONT_KEYWORDS.any { keyword ->
                        filename.contains(keyword, ignoreCase = true)
                    }

                    newFontInfos.add(FontInfo(
                        path = file.path,
                        source = source,
                        enabled = isIconFont
                    ))
                }
            }

            if (newFontInfos.isNotEmpty()) {
                settings.state.fontInfos.addAll(newFontInfos)
            }
        }
    }
}
