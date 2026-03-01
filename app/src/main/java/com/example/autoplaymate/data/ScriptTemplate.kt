package com.example.autoplaymate.data

/**
 * 脚本模板（保存的完整脚本）
 */
data class ScriptTemplate(
    val id: String,                    // 唯一标识
    val name: String,                  // 脚本名称
    val steps: List<ScriptStepData>,   // 脚本步骤列表
    val loopEnabled: Boolean = false,  // 是否启用循环
    val createdTime: Long = System.currentTimeMillis(),  // 创建时间
    val modifiedTime: Long = System.currentTimeMillis()   // 修改时间
)
