package com.bullfrog.iconfontviewer.service

import com.bullfrog.iconfontviewer.model.IconFontTtfFileModel
import com.bullfrog.iconfontviewer.model.TTFSource
import com.bullfrog.iconfontviewer.service.TTFRepository
import com.bullfrog.iconfontviewer.util.buildLogger
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.gradleTooling.get
import java.io.File

/**
 * TTF 状态管理器
 * 管理 TTF 文件的状态变化和用户操作
 */
@Service(Service.Level.PROJECT)
class TTFStateManager(private val project: Project) : Disposable {

    companion object {
        @JvmStatic
        fun getInstance(project: Project): TTFStateManager = project.service()

        private val logger = buildLogger(TTFStateManager::class.java)
    }

    private val ttfRepository: TTFRepository by lazy { project.service() }
    private val configStorage: TTFConfigStorage by lazy { service() }
    private val listeners = mutableListOf<TTFStateChangeListener>()

    /**
     * 获取所有 TTF 状态
     */
    fun getAllTTFs(): List<IconFontTtfFileModel> {
        return ttfRepository.getAll()
    }

    /**
     * 获取启用的 TTF
     */
    fun getEnabledTTFs(): List<IconFontTtfFileModel> {
        return ttfRepository.getAllEnabledTTFs()
    }

    /**
     * 更新 TTF 启用状态
     */
    fun updateTTFState(ttfId: String, enabled: Boolean) {
        val ttf = ttfRepository.findById(ttfId) ?: return

        // 手动添加的 TTF 不允许禁用
        if (ttf.source is TTFSource.UserAdded && !enabled) {
            logger.warn("Cannot disable user-added TTF: ${ttf.ttfFileName}")
            return
        }

        val updatedTTF = ttf.copy(
            enabled = enabled,
        )

        ttfRepository.put(updatedTTF)
        configStorage.saveTTFState(ttfId, enabled)

        notifyStateChanged(updatedTTF)
    }

    /**
     * 添加用户 TTF
     */
    fun addUserTTF(filePath: String): IconFontTtfFileModel? {
        val file = File(filePath)
        if (!file.exists() || !file.name.endsWith(".ttf", ignoreCase = true)) {
            logger.warn("Invalid TTF file: $filePath")
            return null
        }

        // 检查是否已存在
        if (ttfRepository.containsPath(filePath)) {
            logger.info("TTF already exists: $filePath")
            return null
        }

        try {
            val scanService = service<TTFScanService>()
            val classifier = TTFClassifier()

            val ttf = IconFontTtfFileModel(
                id = generateUserTTFId(filePath),
                enabled = true,  // 强制启用
                ttfFileName = file.name,
                ttfAbsolutePath = filePath,
                font = scanService.createFont(filePath),
                source = TTFSource.UserAdded,
                type = classifier.classifyTTF(file.name, filePath),
                fileSize = file.length()
            )

            ttfRepository.put(ttf)
            configStorage.saveUserTTF(filePath)

            notifyTTFAdded(ttf)
            return ttf
        } catch (e: Exception) {
            logger.error("Failed to add user TTF: $filePath", e)
            return null
        }
    }

    /**
     * 删除用户 TTF
     */
    fun removeUserTTF(ttfId: String): Boolean {
        val ttf = ttfRepository.findById(ttfId) ?: return false

        // 只能删除用户添加的 TTF
        if (ttf.source !is TTFSource.UserAdded) {
            logger.warn("Cannot remove non-user TTF: ${ttf.ttfFileName}")
            return false
        }

        ttfRepository.remove(ttfId)
        configStorage.removeUserTTF(ttf.ttfAbsolutePath)
        configStorage.removeTTFState(ttfId)

        notifyTTFRemoved(ttf)
        return true
    }

    /**
     * 刷新扫描
     */
    fun refreshScan() {
        try {
            // 1. 保存用户配置
            val userStates = ttfRepository.getAll()
                .associate { it.id to it.enabled }

            // 2. 重新扫描项目
            val scanService = TTFScanService.getInstance(project)
            val projectTTFs = scanService.scanTTFFiles(project)

            // 3. 加载用户手动添加的 TTF
            val userTTFs = scanService.loadUserAddedTTFs()

            // 4. 合并数据并应用用户配置
            val allTTFs = (projectTTFs + userTTFs).map { ttf ->
                if (userStates.containsKey(ttf.id)) {
                    ttf.copy(
                        enabled = userStates[ttf.id] ?: ttf.enabled,
                    )
                } else {
                    ttf
                }
            }

            // 5. 更新仓库
            ttfRepository.replaceAll(allTTFs)

            notifyRefreshCompleted(allTTFs)
            logger.info("TTF refresh completed: ${allTTFs.size} files")
        } catch (e: Exception) {
            logger.error("TTF refresh failed", e)
        }
    }

    /**
     * 生成用户 TTF ID
     */
    private fun generateUserTTFId(filePath: String): String {
        return "user:${filePath.hashCode()}"
    }

    // 通知方法
    private fun notifyStateChanged(ttf: IconFontTtfFileModel) {
        listeners.forEach { it.onTTFStateChanged(ttf) }
    }

    private fun notifyTTFAdded(ttf: IconFontTtfFileModel) {
        listeners.forEach { it.onTTFAdded(ttf) }
    }

    private fun notifyTTFRemoved(ttf: IconFontTtfFileModel) {
        listeners.forEach { it.onTTFRemoved(ttf) }
    }

    private fun notifyRefreshCompleted(ttfs: List<IconFontTtfFileModel>) {
        listeners.forEach { it.onRefreshCompleted(ttfs) }
    }

    // 监听器管理
    fun addStateChangeListener(listener: TTFStateChangeListener) {
        listeners.add(listener)
    }

    fun removeStateChangeListener(listener: TTFStateChangeListener) {
        listeners.remove(listener)
    }

    override fun dispose() {
        listeners.clear()
    }
}

/**
 * TTF 状态变化监听器
 */
interface TTFStateChangeListener {
    fun onTTFStateChanged(ttf: IconFontTtfFileModel) {}
    fun onTTFAdded(ttf: IconFontTtfFileModel) {}
    fun onTTFRemoved(ttf: IconFontTtfFileModel) {}
    fun onRefreshCompleted(ttfs: List<IconFontTtfFileModel>) {}
}