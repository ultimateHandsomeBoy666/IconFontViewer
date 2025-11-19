package com.bullfrog.iconfontviewer.start

import com.bullfrog.iconfontviewer.service.TTFRepository
import com.bullfrog.iconfontviewer.service.TTFScanService
import com.bullfrog.iconfontviewer.util.buildLogger
import com.bullfrog.iconfontviewer.util.isAndroidProject
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

/**
 * 插件启动活动
 * 在项目启动时初始化 TTF 扫描
 */
class SearchStartupActivity : StartupActivity.DumbAware {

    private val logger = buildLogger(SearchStartupActivity::class.java)

    /**
     * 判断是否需要为该项目扫描 TTF，只对 Android 项目启用
     */
    private fun shouldScanProject(project: Project): Boolean {
        return isAndroidProject(project)
    }

    override fun runActivity(project: Project) {
        if (!shouldScanProject(project)) {
            logger.info("TTF - Skipping TTF scan for project: ${project.name}")
            return
        }
        DumbService.getInstance(project).runWhenSmart {
            scanTTF(project)
        }
    }

    private fun scanTTF(project: Project) {
        val task = object : Task.Backgroundable(project, "Scanning IconFont TTF Files", false) {
            override fun run(indicator: ProgressIndicator) {
                ReadAction.run<Throwable> {
                    val allScannedTTF = TTFScanService.getInstance(project).scanTTFFiles(project)
                    TTFRepository.getInstance(project).putAll(allScannedTTF)
                }
            }
        }
        ProgressManager.getInstance().run(task)
    }
}
