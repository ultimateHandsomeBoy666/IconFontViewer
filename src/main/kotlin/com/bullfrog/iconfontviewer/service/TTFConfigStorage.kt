package com.bullfrog.iconfontviewer.service

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service

/**
 * TTF 配置存储服务
 * 负责持久化用户的 TTF 偏好设置
 */
@Service(Service.Level.APP)
class TTFConfigStorage : Disposable {

    companion object {
        private const val TTF_STATES_KEY = "iconfonts.ttf.states"
        private const val USER_TTFS_KEY = "iconfonts.user.ttfs"

        @JvmStatic
        fun getInstance(): TTFConfigStorage = service()
    }

    private val applicationConfig = PropertiesComponent.getInstance()

    /**
     * 保存 TTF 启用状态
     */
    fun saveTTFState(ttfId: String, enabled: Boolean) {
        val states = getTTFStates().toMutableMap()
        states[ttfId] = enabled

        val stateJson = GsonBuilder().create().toJson(states)
        applicationConfig.setValue(TTF_STATES_KEY, stateJson)
    }

    /**
     * 获取 TTF 启用状态
     */
    fun getTTFStates(): Map<String, Boolean> {
        val stateJson = applicationConfig.getValue(TTF_STATES_KEY, "{}")
        return try {
            GsonBuilder().create().fromJson(stateJson, object : TypeToken<Map<String, Boolean>>() {}.type)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * 保存用户添加的 TTF
     */
    fun saveUserTTF(filePath: String) {
        val userTTFs = getUserTTFs().toMutableSet()
        userTTFs.add(filePath)

        val ttfsJson = GsonBuilder().create().toJson(userTTFs.toList())
        applicationConfig.setValue(USER_TTFS_KEY, ttfsJson)
    }

    /**
     * 获取用户添加的 TTF 列表
     */
    fun getUserTTFs(): Set<String> {
        val ttfsJson = applicationConfig.getValue(USER_TTFS_KEY, "[]")
        return try {
            val list: List<String> = GsonBuilder().create().fromJson(ttfsJson, object : TypeToken<List<String>>() {}.type)
            list.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    /**
     * 移除用户 TTF
     */
    fun removeUserTTF(filePath: String) {
        val userTTFs = getUserTTFs().toMutableSet()
        userTTFs.remove(filePath)

        val ttfsJson = GsonBuilder().create().toJson(userTTFs.toList())
        applicationConfig.setValue(USER_TTFS_KEY, ttfsJson)
    }

    /**
     * 批量移除TTF状态
     */
    fun removeTTFState(ttfId: String) {
        val states = getTTFStates().toMutableMap()
        states.remove(ttfId)

        val stateJson = GsonBuilder().create().toJson(states)
        applicationConfig.setValue(TTF_STATES_KEY, stateJson)
    }

    override fun dispose() {
        // 配置存储不需要特殊清理
    }
}