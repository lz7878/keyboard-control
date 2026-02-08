package com.example.autoplaymate.data

import android.content.Context
import android.content.SharedPreferences
import com.example.autoplaymate.viewmodel.KeyboardViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 脚本数据存储管理器
 * 使用 SharedPreferences 持久化保存脚本数据
 */
class ScriptDataManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    private val gson = Gson()

    /**
     * 保存脚本步骤列表
     */
    fun saveScriptSteps(steps: List<ScriptStepData>) {
        val json = gson.toJson(steps)
        prefs.edit().putString(KEY_SCRIPT_STEPS, json).apply()
    }

    /**
     * 加载脚本步骤列表
     */
    fun loadScriptSteps(): List<ScriptStepData> {
        val json = prefs.getString(KEY_SCRIPT_STEPS, null)
        return if (json != null) {
            val type = object : TypeToken<List<ScriptStepData>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } else {
            emptyList()
        }
    }

    /**
     * 清空脚本步骤
     */
    fun clearScriptSteps() {
        prefs.edit().remove(KEY_SCRIPT_STEPS).apply()
    }

    /**
     * 保存循环模式设置
     */
    fun saveLoopEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOOP_ENABLED, enabled).apply()
    }

    /**
     * 加载循环模式设置
     */
    fun loadLoopEnabled(): Boolean {
        return prefs.getBoolean(KEY_LOOP_ENABLED, false)
    }

    companion object {
        private const val PREFS_NAME = "script_config"
        private const val KEY_SCRIPT_STEPS = "script_steps"
        private const val KEY_LOOP_ENABLED = "loop_enabled"
    }
}

/**
 * 脚本步骤数据类（用于持久化存储）
 */
data class ScriptStepData(
    val delayMs: Long,
    val keyName: String?
)
