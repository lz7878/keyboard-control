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
        private const val KEY_SCRIPT_TEMPLATES = "script_templates"
    }

    // ========== 脚本模板管理 ==========

    /**
     * 获取所有保存的脚本模板
     */
    fun getScriptTemplates(): List<ScriptTemplate> {
        val json = prefs.getString(KEY_SCRIPT_TEMPLATES, null)
        return if (json != null) {
            val type = object : TypeToken<List<ScriptTemplate>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } else {
            emptyList()
        }
    }

    /**
     * 保存脚本模板
     */
    fun saveScriptTemplate(template: ScriptTemplate) {
        val templates = getScriptTemplates().toMutableList()
        val existingIndex = templates.indexOfFirst { it.id == template.id }

        val updatedTemplate = template.copy(modifiedTime = System.currentTimeMillis())

        if (existingIndex >= 0) {
            templates[existingIndex] = updatedTemplate
        } else {
            templates.add(updatedTemplate)
        }

        val json = gson.toJson(templates)
        prefs.edit().putString(KEY_SCRIPT_TEMPLATES, json).apply()
    }

    /**
     * 删除脚本模板
     */
    fun deleteScriptTemplate(templateId: String) {
        val templates = getScriptTemplates().toMutableList()
        templates.removeIf { it.id == templateId }
        val json = gson.toJson(templates)
        prefs.edit().putString(KEY_SCRIPT_TEMPLATES, json).apply()
    }

    /**
     * 获取单个脚本模板
     */
    fun getScriptTemplate(templateId: String): ScriptTemplate? {
        return getScriptTemplates().firstOrNull { it.id == templateId }
    }
}

/**
 * 脚本步骤类型
 */
enum class StepType {
    NORMAL,        // 普通步骤：延时后执行一次按键
    ASYNC_REPEAT   // 异步重复：后台重复执行按键，不阻塞后续步骤
}

/**
 * 脚本步骤数据类（用于持久化存储）
 */
data class ScriptStepData(
    val delayMs: Long,
    val keyName: String?,
    val stepType: String = StepType.NORMAL.name,  // 步骤类型
    val repeatCount: Int = 0,                     // 重复次数（仅 ASYNC_REPEAT 使用）
    val repeatIntervalMs: Long = 0                // 重复间隔毫秒（仅 ASYNC_REPEAT 使用）
)
