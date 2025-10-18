package com.bullfrog.iconfontviewer.service

import com.bullfrog.iconfontviewer.model.IconFontTtfFileModel
import com.bullfrog.iconfontviewer.model.TTFSource
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap

/**
 * TTF 数据仓库
 * 管理项目中的 TTF 文件数据
 */
@Service(Service.Level.PROJECT)
class TTFRepository : Disposable {

    companion object {
        @JvmStatic
        fun getInstance(project: Project): TTFRepository = project.service()
    }

    private val ttfMap = ConcurrentHashMap<String, IconFontTtfFileModel>()

    fun put(ttf: IconFontTtfFileModel) {
        ttfMap[ttf.id] = ttf
    }

    fun putAll(ttf: List<IconFontTtfFileModel>) {
        ttfMap.putAll(ttfMap)
    }

    fun getAll(): List<IconFontTtfFileModel> {
        return ttfMap.values.toList()
    }

    fun findById(id: String): IconFontTtfFileModel? {
        return ttfMap[id]
    }

    fun remove(id: String): IconFontTtfFileModel? {
        return ttfMap.remove(id)
    }

    fun clear() {
        ttfMap.clear()
    }

    fun replaceAll(ttfs: List<IconFontTtfFileModel>) {
        ttfMap.clear()
        ttfs.forEach { ttfMap[it.id] = it }
    }

    fun getBySource(source: Class<out TTFSource>): List<IconFontTtfFileModel> {
        return ttfMap.values.filter { source.isInstance(it.source) }
    }

    fun getAllEnabledTTFs(): List<IconFontTtfFileModel> {
        return ttfMap.values.filter { it.enabled }
    }

    fun containsPath(path: String): Boolean {
        return ttfMap.values.any { it.ttfAbsolutePath == path }
    }

    fun size(): Int = ttfMap.size

    override fun dispose() {
        ttfMap.clear()
    }
}