package com.example.autoplaymate.viewmodel

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider
import android.app.Application
import com.example.autoplaymate.ble.BleHidManager
import com.example.autoplaymate.ble.KeyCodes
import com.example.autoplaymate.ble.KeyModifier
import com.example.autoplaymate.data.ScriptDataManager
import com.example.autoplaymate.data.ScriptStepData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 键盘控制 ViewModel
 */
class KeyboardViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val bleManager = BleHidManager(application)
    private val scriptDataManager = ScriptDataManager(application)

    val connectionState = bleManager.connectionState
    val connectedDevice = bleManager.connectedDevice

    private val _availableDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val availableDevices: StateFlow<List<BluetoothDevice>> = _availableDevices.asStateFlow()

    private val _lastAction = MutableStateFlow<String?>(null)
    val lastAction: StateFlow<String?> = _lastAction.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isScriptRunning = MutableStateFlow(false)
    val isScriptRunning: StateFlow<Boolean> = _isScriptRunning.asStateFlow()

    // 脚本数据持久化
    private val _scriptSteps = MutableStateFlow<List<ScriptStepData>>(emptyList())
    val scriptSteps: StateFlow<List<ScriptStepData>> = _scriptSteps.asStateFlow()

    private val _loopEnabled = MutableStateFlow(false)
    val loopEnabled: StateFlow<Boolean> = _loopEnabled.asStateFlow()

    init {
        // 加载保存的脚本数据
        _scriptSteps.value = scriptDataManager.loadScriptSteps()
        _loopEnabled.value = scriptDataManager.loadLoopEnabled()
    }

    /**
     * 脚本步骤数据类
     */
    data class ScriptStep(
        val delayMs: Long,
        val keycode: Byte,
        val modifier: Byte = 0
    )

    /**
     * 检查蓝牙状态并扫描设备
     */
    fun checkBluetoothAndScan() {
        if (!bleManager.isBluetoothEnabled()) {
            _lastAction.value = "蓝牙未启用，请开启蓝牙"
            _availableDevices.value = emptyList()
            return
        }
        scanDevices()
    }

    /**
     * 扫描可用的 BLE HID 设备
     */
    fun scanDevices() {
        _isScanning.value = true
        _lastAction.value = "正在扫描设备..."

        bleManager.startScan { devices ->
            _availableDevices.value = devices
            _isScanning.value = false
            _lastAction.value = if (devices.isEmpty()) {
                "未找到 HID 设备"
            } else {
                "找到 ${devices.size} 个 HID 设备"
            }
        }
    }

    /**
     * 连接到设备
     */
    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        viewModelScope.launch {
            bleManager.connect(device)
        }
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        bleManager.disconnect()
        _lastAction.value = "已断开连接"
    }

    /**
     * 发送单个按键
     */
    fun sendKeyPress(keycode: Byte, modifier: Byte = 0) {
        viewModelScope.launch {
            val success = bleManager.sendKeyPress(keycode, modifier)
            _lastAction.value = if (success) {
                "发送按键成功"
            } else {
                "发送按键失败"
            }
        }
    }

    /**
     * 发送文本（逐字符发送）
     */
    fun sendText(text: String) {
        viewModelScope.launch {
            var success = true
            text.forEach { char ->
                val keycode = charToKeycode(char)
                val modifier = getModifierForChar(char)
                if (keycode != null) {
                    if (!bleManager.sendKeyPress(keycode, modifier)) {
                        success = false
                    }
                    kotlinx.coroutines.delay(50)
                }
            }
            _lastAction.value = if (success) {
                "发送文本成功: \"$text\""
            } else {
                "发送文本部分失败"
            }
        }
    }

    /**
     * 发送组合键
     */
    fun sendKeyCombo(keycode: Byte, modifiers: Byte) {
        viewModelScope.launch {
            // 按下修饰键和目标键
            if (!bleManager.sendKeyReport(modifiers, keycode)) {
                _lastAction.value = "发送组合键失败"
                return@launch
            }
            kotlinx.coroutines.delay(50)
            // 释放所有键
            if (!bleManager.sendKeyReport(0, 0)) {
                _lastAction.value = "发送组合键失败"
                return@launch
            }
            _lastAction.value = "发送组合键成功"
        }
    }

    /**
     * 检查蓝牙是否启用
     */
    fun isBluetoothEnabled(): Boolean {
        return bleManager.isBluetoothEnabled()
    }

    /**
     * 将字符转换为 HID 按键码
     */
    private fun charToKeycode(char: Char): Byte? {
        return when (char.lowercaseChar()) {
            'a' -> KeyCodes.KEY_A
            'b' -> KeyCodes.KEY_B
            'c' -> KeyCodes.KEY_C
            'd' -> KeyCodes.KEY_D
            'e' -> KeyCodes.KEY_E
            'f' -> KeyCodes.KEY_F
            'g' -> KeyCodes.KEY_G
            'h' -> KeyCodes.KEY_H
            'i' -> KeyCodes.KEY_I
            'j' -> KeyCodes.KEY_J
            'k' -> KeyCodes.KEY_K
            'l' -> KeyCodes.KEY_L
            'm' -> KeyCodes.KEY_M
            'n' -> KeyCodes.KEY_N
            'o' -> KeyCodes.KEY_O
            'p' -> KeyCodes.KEY_P
            'q' -> KeyCodes.KEY_Q
            'r' -> KeyCodes.KEY_R
            's' -> KeyCodes.KEY_S
            't' -> KeyCodes.KEY_T
            'u' -> KeyCodes.KEY_U
            'v' -> KeyCodes.KEY_V
            'w' -> KeyCodes.KEY_W
            'x' -> KeyCodes.KEY_X
            'y' -> KeyCodes.KEY_Y
            'z' -> KeyCodes.KEY_Z
            '1' -> KeyCodes.KEY_1
            '2' -> KeyCodes.KEY_2
            '3' -> KeyCodes.KEY_3
            '4' -> KeyCodes.KEY_4
            '5' -> KeyCodes.KEY_5
            '6' -> KeyCodes.KEY_6
            '7' -> KeyCodes.KEY_7
            '8' -> KeyCodes.KEY_8
            '9' -> KeyCodes.KEY_9
            '0' -> KeyCodes.KEY_0
            ' ' -> KeyCodes.KEY_SPACE
            '\n' -> KeyCodes.KEY_ENTER
            '\t' -> KeyCodes.KEY_TAB
            else -> null
        }
    }

    /**
     * 获取字符所需的修饰键
     */
    private fun getModifierForChar(char: Char): Byte {
        return when {
            char.isUpperCase() -> KeyModifier.LEFT_SHIFT
            else -> 0
        }
    }

    /**
     * 运行定时脚本
     * @param steps 脚本步骤列表，每步包含延迟时间、按键码和修饰键
     * @param loop 是否循环执行
     */
    fun runScript(steps: List<ScriptStep>, loop: Boolean = false) {
        viewModelScope.launch {
            _isScriptRunning.value = true
            try {
                do {
                    for (step in steps) {
                        if (!_isScriptRunning.value) break

                        // 延迟指定时间
                        kotlinx.coroutines.delay(step.delayMs)

                        // 如果 keycode 不为 0，发送按键（keycode 为 0 表示纯延时）
                        if (step.keycode != 0.toByte()) {
                            bleManager.sendKeyPress(step.keycode, step.modifier)
                        }
                    }
                    // 如果不是循环模式，执行完一次就退出
                    if (!loop) break

                    // 循环模式，等待一段时间再开始下一轮
                    if (_isScriptRunning.value) {
                        kotlinx.coroutines.delay(1000) // 每轮间隔1秒
                    }
                } while (_isScriptRunning.value && loop)
                _lastAction.value = "脚本执行完成"
            } catch (e: Exception) {
                _lastAction.value = "脚本执行出错: ${e.message}"
            } finally {
                _isScriptRunning.value = false
            }
        }
    }

    /**
     * 停止脚本执行
     */
    fun stopScript() {
        _isScriptRunning.value = false
        _lastAction.value = "脚本已停止"
    }

    /**
     * 添加脚本步骤
     */
    fun addScriptStep(step: ScriptStepData) {
        _scriptSteps.value = _scriptSteps.value + step
        saveScriptData()
    }

    /**
     * 删除指定位置的脚本步骤
     */
    fun removeScriptStep(index: Int) {
        if (index in _scriptSteps.value.indices) {
            _scriptSteps.value = _scriptSteps.value.toMutableList().apply { removeAt(index) }
            saveScriptData()
        }
    }

    /**
     * 清空所有脚本步骤
     */
    fun clearScriptSteps() {
        _scriptSteps.value = emptyList()
        saveScriptData()
    }

    /**
     * 设置循环模式
     */
    fun setLoopEnabled(enabled: Boolean) {
        _loopEnabled.value = enabled
        scriptDataManager.saveLoopEnabled(enabled)
    }

    /**
     * 保存脚本数据到持久化存储
     */
    private fun saveScriptData() {
        scriptDataManager.saveScriptSteps(_scriptSteps.value)
    }

    /**
     * ViewModel 销毁前保存数据
     */
    override fun onCleared() {
        super.onCleared()
        saveScriptData()
        scriptDataManager.saveLoopEnabled(_loopEnabled.value)
    }

    /**
     * 字符串转按键码
     */
    fun stringToKeycode(keyName: String): Byte? {
        return when (keyName) {
            "A" -> KeyCodes.KEY_A
            "B" -> KeyCodes.KEY_B
            "C" -> KeyCodes.KEY_C
            "D" -> KeyCodes.KEY_D
            "E" -> KeyCodes.KEY_E
            "F" -> KeyCodes.KEY_F
            "G" -> KeyCodes.KEY_G
            "H" -> KeyCodes.KEY_H
            "I" -> KeyCodes.KEY_I
            "J" -> KeyCodes.KEY_J
            "K" -> KeyCodes.KEY_K
            "L" -> KeyCodes.KEY_L
            "M" -> KeyCodes.KEY_M
            "N" -> KeyCodes.KEY_N
            "O" -> KeyCodes.KEY_O
            "P" -> KeyCodes.KEY_P
            "Q" -> KeyCodes.KEY_Q
            "R" -> KeyCodes.KEY_R
            "S" -> KeyCodes.KEY_S
            "T" -> KeyCodes.KEY_T
            "U" -> KeyCodes.KEY_U
            "V" -> KeyCodes.KEY_V
            "W" -> KeyCodes.KEY_W
            "X" -> KeyCodes.KEY_X
            "Y" -> KeyCodes.KEY_Y
            "Z" -> KeyCodes.KEY_Z
            "0" -> KeyCodes.KEY_0
            "1" -> KeyCodes.KEY_1
            "2" -> KeyCodes.KEY_2
            "3" -> KeyCodes.KEY_3
            "4" -> KeyCodes.KEY_4
            "5" -> KeyCodes.KEY_5
            "6" -> KeyCodes.KEY_6
            "7" -> KeyCodes.KEY_7
            "8" -> KeyCodes.KEY_8
            "9" -> KeyCodes.KEY_9
            "F1" -> KeyCodes.KEY_F1
            "F2" -> KeyCodes.KEY_F2
            "F3" -> KeyCodes.KEY_F3
            "F4" -> KeyCodes.KEY_F4
            "F5" -> KeyCodes.KEY_F5
            "F6" -> KeyCodes.KEY_F6
            "F7" -> KeyCodes.KEY_F7
            "F8" -> KeyCodes.KEY_F8
            "F9" -> KeyCodes.KEY_F9
            "F10" -> KeyCodes.KEY_F10
            "F11" -> KeyCodes.KEY_F11
            "F12" -> KeyCodes.KEY_F12
            "Enter" -> KeyCodes.KEY_ENTER
            "Space" -> KeyCodes.KEY_SPACE
            "Tab" -> KeyCodes.KEY_TAB
            "Esc" -> KeyCodes.KEY_ESCAPE
            "Backspace" -> KeyCodes.KEY_BACKSPACE
            "Up" -> KeyCodes.KEY_ARROW_UP
            "Down" -> KeyCodes.KEY_ARROW_DOWN
            "Left" -> KeyCodes.KEY_ARROW_LEFT
            "Right" -> KeyCodes.KEY_ARROW_RIGHT
            else -> null
        }
    }
}
