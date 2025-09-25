package com.bullfrog.iconfontviewer.start

import com.bullfrog.iconfontviewer.FontModelListHolder
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import java.io.File


class SearchStartupActivity : StartupActivity.DumbAware {

    override fun runActivity(project: Project) {
        DumbService.getInstance(project).runWhenSmart {
            searchMatchedFiles(project)
        }
    }

    private fun searchMatchedFiles(project: Project) {
        val task = object : Task.Backgroundable(project, "searching files by regex", false) {
            override fun run(indicator: ProgressIndicator) {
                ReadAction.run<Throwable> {
                    // build FontModelListHolder，only search iconfont file in current project, do not search in dependent module
                    buildFontModelListHolder(project)
                }
            }

        }
        ProgressManager.getInstance().run(task)
    }

    private fun buildFontModelListHolder(project: Project) {
        val scope = GlobalSearchScope.allScope(project)
        val startTime = System.currentTimeMillis()
        val allFilenames = FilenameIndex.getAllFilenames(project)
        println("filenames count = ${allFilenames.size}")
        for (filename in allFilenames) {
            if (filename.endsWith(".ttf")) {
                val files = FilenameIndex.getVirtualFilesByName(filename, scope)
                println("files size = ${files.size}, current thread is ${Thread.currentThread()}")
                for (file in files) {
                    if (!file.path.contains("/build/")) {
                        println("ttf = ${file.path}")
                        FontModelListHolder.putFont(file.path, filename)
                    }
                }
            }
        }
        println("search time cost = ${System.currentTimeMillis() - startTime}")
        println("font path = ${FontModelListHolder.getFontModelList()}")
    }

    // 暂时不用，排除掉build目录下的文件就好了，重复基本只会存在于项目目录和build目录中
    // 计算MD5值需要读取整个文件流，还是蛮耗时的
    private fun deDuplicate(fileList: List<String>) {
        fileList
            .mapNotNull { fileName ->
                val file = File(fileName)
                if (file.exists()) file else null
            }
            .forEach {
                // no need for now
            }
    }
}