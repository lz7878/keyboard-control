package com.example.autoplaymate.ui.screens

import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.autoplaymate.ble.BleHidManager
import com.example.autoplaymate.ble.KeyCodes
import com.example.autoplaymate.ble.KeyModifier
import com.example.autoplaymate.viewmodel.KeyboardViewModel

/**
 * 键盘控制主界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardControlScreen(
    viewModel: KeyboardViewModel,
    bluetoothPermissionsGranted: Boolean = true,
    onRequestBluetoothPermission: () -> Unit = {},
    onBackClick: () -> Unit = {}
) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val connectedDevice by viewModel.connectedDevice.collectAsStateWithLifecycle()
    val availableDevices by viewModel.availableDevices.collectAsStateWithLifecycle()
    val lastAction by viewModel.lastAction.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val isBluetoothEnabled by remember { derivedStateOf { viewModel.isBluetoothEnabled() } }

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("设备连接", "键盘控制", "定时脚本")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("键盘控制") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        // 监听权限授予后自动扫描
        LaunchedEffect(bluetoothPermissionsGranted) {
            if (bluetoothPermissionsGranted && isBluetoothEnabled) {
                viewModel.checkBluetoothAndScan()
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
        // 标签页
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 标签页内容
        when (selectedTab) {
            0 -> DeviceListContent(
                connectionState = connectionState,
                connectedDevice = connectedDevice,
                availableDevices = availableDevices,
                lastAction = lastAction,
                isScanning = isScanning,
                isBluetoothEnabled = isBluetoothEnabled,
                bluetoothPermissionsGranted = bluetoothPermissionsGranted,
                onScan = { viewModel.scanDevices() },
                onConnect = { device -> viewModel.connect(device) },
                onDisconnect = { viewModel.disconnect() },
                onRequestPermission = onRequestBluetoothPermission
            )
            1 -> KeyboardControlContent(
                connectionState = connectionState,
                onKeyPress = { keycode, modifier ->
                    viewModel.sendKeyPress(keycode, modifier)
                }
            )
            2 -> ScriptActionsContent(
                viewModel = viewModel,
                connectionState = connectionState,
                onRunScript = { steps, loop ->
                    viewModel.runScript(steps, loop)
                },
                onStopScript = { viewModel.stopScript() },
                isRunning = viewModel.isScriptRunning.collectAsStateWithLifecycle().value
            )
        }
        }
    }
}

/**
 * 状态卡片
 */
@Composable
fun StatusCard(
    connectionState: BleHidManager.ConnectionState,
    connectedDevice: BluetoothDevice?,
    lastAction: String?,
    isBluetoothEnabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (connectionState) {
                is BleHidManager.ConnectionState.Connected -> MaterialTheme.colorScheme.primaryContainer
                is BleHidManager.ConnectionState.Error -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            if (!isBluetoothEnabled) {
                Text(
                    text = "蓝牙未启用",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "请开启蓝牙后使用",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Text(
                    text = when (connectionState) {
                        is BleHidManager.ConnectionState.Connected -> "已连接"
                        is BleHidManager.ConnectionState.Connecting -> "连接中..."
                        is BleHidManager.ConnectionState.Disconnected -> "未连接"
                        is BleHidManager.ConnectionState.Error -> "连接错误"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                connectedDevice?.let {
                    Text(
                        text = "设备: ${it.name ?: "未知设备"}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        text = "地址: ${it.address}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                (connectionState as? BleHidManager.ConnectionState.Error)?.message?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            lastAction?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 设备列表内容
 */
@Composable
fun DeviceListContent(
    connectionState: BleHidManager.ConnectionState,
    connectedDevice: BluetoothDevice?,
    availableDevices: List<BluetoothDevice>,
    lastAction: String?,
    isScanning: Boolean,
    isBluetoothEnabled: Boolean,
    bluetoothPermissionsGranted: Boolean,
    onScan: () -> Unit,
    onConnect: (BluetoothDevice) -> Unit,
    onDisconnect: () -> Unit,
    onRequestPermission: () -> Unit
) {
    // 使用可滚动的内容区域
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 状态卡片
        StatusCard(
            connectionState = connectionState,
            connectedDevice = connectedDevice,
            lastAction = lastAction,
            isBluetoothEnabled = isBluetoothEnabled
        )

        // 扫描和断开按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (bluetoothPermissionsGranted) {
                        onScan()
                    } else {
                        onRequestPermission()
                    }
                },
                enabled = isBluetoothEnabled && !isScanning,
                modifier = Modifier.weight(1f)
            ) {
                if (isScanning) {
                    Text("扫描中...")
                } else {
                    Text(if (bluetoothPermissionsGranted) "扫描设备" else "请求权限")
                }
            }

            if (connectionState is BleHidManager.ConnectionState.Connected) {
                Button(
                    onClick = onDisconnect,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("断开连接")
                }
            }
        }

        // 设备列表
        if (!bluetoothPermissionsGranted) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "需要蓝牙权限才能扫描设备",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onRequestPermission) {
                        Text("授予权限")
                    }
                }
            }
        } else if (!isBluetoothEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                ) {
                Text(
                    text = "请先开启蓝牙",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
        } else if (availableDevices.isEmpty() && !isScanning) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
            ) {
                Text(
                    text = "没有找到 BLE HID 设备\n请确保 EmulStick 已开启并配对",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (availableDevices.isNotEmpty()) {
            Text(
                text = if (isScanning) "正在扫描..." else "找到 ${availableDevices.size} 个设备",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            availableDevices.forEach { device ->
                DeviceListItem(
                    device = device,
                    onConnect = { onConnect(device) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/**
 * 设备列表项
 */
@Composable
fun DeviceListItem(
    device: BluetoothDevice,
    onConnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = device.name ?: "未知设备",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = onConnect
            ) {
                Text("连接")
            }
        }
    }
}

/**
 * 键盘控制内容
 */
@Composable
fun KeyboardControlContent(
    connectionState: BleHidManager.ConnectionState,
    onKeyPress: (Byte, Byte) -> Unit
) {
    val isEnabled = connectionState is BleHidManager.ConnectionState.Connected

    // 使用可滚动的内容区域
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        KeyboardGrid(
            keys = listOf(
                "Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P",
                "A", "S", "D", "F", "G", "H", "J", "K", "L",
                "Z", "X", "C", "V", "B", "N", "M"
            ),
            onKeyPress = { key ->
                val keycode = getLetterKeycode(key)
                val modifier = if (key.first().isUpperCase()) KeyModifier.LEFT_SHIFT else 0
                if (keycode != null) {
                    onKeyPress(keycode, modifier)
                }
            },
            enabled = isEnabled
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 数字和功能键
        Text(
            text = "数字键",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("1", "2", "3", "4", "5").forEach { key ->
                KeyButton(
                    key = key,
                    onClick = {
                        val keycode = getNumberKeycode(key)
                        if (keycode != null) {
                            onKeyPress(keycode, 0)
                        }
                    },
                    enabled = isEnabled,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("6", "7", "8", "9", "0").forEach { key ->
                KeyButton(
                    key = key,
                    onClick = {
                        val keycode = getNumberKeycode(key)
                        if (keycode != null) {
                            onKeyPress(keycode, 0)
                        }
                    },
                    enabled = isEnabled,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 特殊键
        Text(
            text = "特殊键",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KeyButton(
                key = "Enter",
                onClick = { onKeyPress(KeyCodes.KEY_ENTER, 0) },
                enabled = isEnabled,
                modifier = Modifier.weight(1f)
            )
            KeyButton(
                key = "Space",
                onClick = { onKeyPress(KeyCodes.KEY_SPACE, 0) },
                enabled = isEnabled,
                modifier = Modifier.weight(1f)
            )
            KeyButton(
                key = "Tab",
                onClick = { onKeyPress(KeyCodes.KEY_TAB, 0) },
                enabled = isEnabled,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KeyButton(
                key = "↑",
                onClick = { onKeyPress(KeyCodes.KEY_ARROW_UP, 0) },
                enabled = isEnabled,
                modifier = Modifier.weight(1f)
            )
            KeyButton(
                key = "↓",
                onClick = { onKeyPress(KeyCodes.KEY_ARROW_DOWN, 0) },
                enabled = isEnabled,
                modifier = Modifier.weight(1f)
            )
            KeyButton(
                key = "←",
                onClick = { onKeyPress(KeyCodes.KEY_ARROW_LEFT, 0) },
                enabled = isEnabled,
                modifier = Modifier.weight(1f)
            )
            KeyButton(
                key = "→",
                onClick = { onKeyPress(KeyCodes.KEY_ARROW_RIGHT, 0) },
                enabled = isEnabled,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 定时脚本内容
 */
@Composable
fun ScriptActionsContent(
    viewModel: KeyboardViewModel,
    connectionState: BleHidManager.ConnectionState,
    onRunScript: (List<com.example.autoplaymate.viewmodel.KeyboardViewModel.ScriptStep>, Boolean) -> Unit,
    onStopScript: () -> Unit = {},
    isRunning: Boolean = false
) {
    val isEnabled = connectionState is BleHidManager.ConnectionState.Connected && !isRunning

    // 从 ViewModel 获取脚本数据（持久化）
    val scriptSteps by viewModel.scriptSteps.collectAsStateWithLifecycle()
    val loopEnabled by viewModel.loopEnabled.collectAsStateWithLifecycle()

    // 当前编辑的步骤
    var currentDelay by remember { mutableStateOf("1.0") }
    var currentKey by remember { mutableStateOf("F12") }
    var delayOnly by remember { mutableStateOf(false) }

    // 滚动状态
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = "定时脚本",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 可滚动内容区域
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 添加步骤区域
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "添加步骤",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    // 延迟时间输入
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "延迟(s):",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedTextField(
                            value = currentDelay,
                            onValueChange = { currentDelay = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("1.0") }
                        )
                    }

                    // 按键选择
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "按键:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        KeyDropdown(
                            selectedKey = currentKey,
                            onKeySelected = { currentKey = it },
                            modifier = Modifier.weight(1f),
                            enabled = !delayOnly
                        )
                    }

                    // 纯延时选项
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = delayOnly,
                            onCheckedChange = { delayOnly = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "纯延时（不按键）",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    // 添加按钮
                    Button(
                        onClick = {
                            val delaySec = currentDelay.toDoubleOrNull() ?: 1.0
                            val delayMs = (delaySec * 1000).toLong()
                            val keyName = if (delayOnly) null else currentKey
                            viewModel.addScriptStep(com.example.autoplaymate.data.ScriptStepData(delayMs, keyName))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("添加步骤")
                    }
                }
            }

            // 步骤列表
            if (scriptSteps.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "脚本步骤 (${scriptSteps.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = { viewModel.clearScriptSteps() }) {
                                Text("清空")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        scriptSteps.forEachIndexed { index, step ->
                            ScriptStepItem(
                                index = index + 1,
                                step = ScriptStepUi(step.delayMs, step.keyName),
                                onDelete = {
                                    viewModel.removeScriptStep(index)
                                }
                            )
                            if (index < scriptSteps.size - 1) {
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 运行按钮（固定在底部）
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 循环开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "循环模式",
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = loopEnabled,
                    onCheckedChange = { viewModel.setLoopEnabled(it) }
                )
                if (loopEnabled) {
                    Text(
                        text = "已启用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // 运行按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (scriptSteps.isNotEmpty()) {
                            val steps = scriptSteps.map { step ->
                                val keycode = if (step.keyName != null) {
                                    keyNameToKeycode(step.keyName) ?: 0
                                } else {
                                    0 // 纯延时使用 keycode 0
                                }
                                com.example.autoplaymate.viewmodel.KeyboardViewModel.ScriptStep(
                                    delayMs = step.delayMs,
                                    keycode = keycode.toByte(),
                                    modifier = 0
                                )
                            }
                            onRunScript(steps, loopEnabled)
                        }
                    },
                    enabled = isEnabled && scriptSteps.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isRunning) "运行中..." else "运行脚本")
                }

                if (isRunning) {
                    Button(
                        onClick = onStopScript,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("停止")
                    }
                }
            }
        }
    }
}

/**
 * 脚本步骤UI数据类
 */
data class ScriptStepUi(
    val delayMs: Long,
    val keyName: String?
)

/**
 * 脚本步骤项
 */
@Composable
fun ScriptStepItem(
    index: Int,
    step: ScriptStepUi,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "步骤 $index",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (step.keyName != null) {
                    "${step.delayMs / 1000.0}s 后按下 ${step.keyName}"
                } else {
                    "纯延时 ${step.delayMs / 1000.0}s"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete) {
            Text(
                text = "×",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * 按键下拉选择
 */
@Composable
fun KeyDropdown(
    selectedKey: String,
    onKeySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    val commonKeys = listOf(
        "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12",
        "Space", "Enter", "Tab", "Esc", "Backspace",
        "Up", "Down", "Left", "Right",
        "0", "1", "2", "3", "4", "5", "6", "7", "8", "9"
    )

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled
        ) {
            Text(selectedKey)
            Spacer(modifier = Modifier.weight(1f))
            Text("▼")
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.height(300.dp)
        ) {
            commonKeys.forEach { key ->
                DropdownMenuItem(
                    text = { Text(key) },
                    onClick = {
                        onKeySelected(key)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * 按键名称转按键码
 */
fun keyNameToKeycode(keyName: String): Byte? {
    return when (keyName) {
        "A" -> com.example.autoplaymate.ble.KeyCodes.KEY_A
        "B" -> com.example.autoplaymate.ble.KeyCodes.KEY_B
        "C" -> com.example.autoplaymate.ble.KeyCodes.KEY_C
        "D" -> com.example.autoplaymate.ble.KeyCodes.KEY_D
        "E" -> com.example.autoplaymate.ble.KeyCodes.KEY_E
        "F" -> com.example.autoplaymate.ble.KeyCodes.KEY_F
        "G" -> com.example.autoplaymate.ble.KeyCodes.KEY_G
        "H" -> com.example.autoplaymate.ble.KeyCodes.KEY_H
        "I" -> com.example.autoplaymate.ble.KeyCodes.KEY_I
        "J" -> com.example.autoplaymate.ble.KeyCodes.KEY_J
        "K" -> com.example.autoplaymate.ble.KeyCodes.KEY_K
        "L" -> com.example.autoplaymate.ble.KeyCodes.KEY_L
        "M" -> com.example.autoplaymate.ble.KeyCodes.KEY_M
        "N" -> com.example.autoplaymate.ble.KeyCodes.KEY_N
        "O" -> com.example.autoplaymate.ble.KeyCodes.KEY_O
        "P" -> com.example.autoplaymate.ble.KeyCodes.KEY_P
        "Q" -> com.example.autoplaymate.ble.KeyCodes.KEY_Q
        "R" -> com.example.autoplaymate.ble.KeyCodes.KEY_R
        "S" -> com.example.autoplaymate.ble.KeyCodes.KEY_S
        "T" -> com.example.autoplaymate.ble.KeyCodes.KEY_T
        "U" -> com.example.autoplaymate.ble.KeyCodes.KEY_U
        "V" -> com.example.autoplaymate.ble.KeyCodes.KEY_V
        "W" -> com.example.autoplaymate.ble.KeyCodes.KEY_W
        "X" -> com.example.autoplaymate.ble.KeyCodes.KEY_X
        "Y" -> com.example.autoplaymate.ble.KeyCodes.KEY_Y
        "Z" -> com.example.autoplaymate.ble.KeyCodes.KEY_Z
        "0" -> com.example.autoplaymate.ble.KeyCodes.KEY_0
        "1" -> com.example.autoplaymate.ble.KeyCodes.KEY_1
        "2" -> com.example.autoplaymate.ble.KeyCodes.KEY_2
        "3" -> com.example.autoplaymate.ble.KeyCodes.KEY_3
        "4" -> com.example.autoplaymate.ble.KeyCodes.KEY_4
        "5" -> com.example.autoplaymate.ble.KeyCodes.KEY_5
        "6" -> com.example.autoplaymate.ble.KeyCodes.KEY_6
        "7" -> com.example.autoplaymate.ble.KeyCodes.KEY_7
        "8" -> com.example.autoplaymate.ble.KeyCodes.KEY_8
        "9" -> com.example.autoplaymate.ble.KeyCodes.KEY_9
        "F1" -> com.example.autoplaymate.ble.KeyCodes.KEY_F1
        "F2" -> com.example.autoplaymate.ble.KeyCodes.KEY_F2
        "F3" -> com.example.autoplaymate.ble.KeyCodes.KEY_F3
        "F4" -> com.example.autoplaymate.ble.KeyCodes.KEY_F4
        "F5" -> com.example.autoplaymate.ble.KeyCodes.KEY_F5
        "F6" -> com.example.autoplaymate.ble.KeyCodes.KEY_F6
        "F7" -> com.example.autoplaymate.ble.KeyCodes.KEY_F7
        "F8" -> com.example.autoplaymate.ble.KeyCodes.KEY_F8
        "F9" -> com.example.autoplaymate.ble.KeyCodes.KEY_F9
        "F10" -> com.example.autoplaymate.ble.KeyCodes.KEY_F10
        "F11" -> com.example.autoplaymate.ble.KeyCodes.KEY_F11
        "F12" -> com.example.autoplaymate.ble.KeyCodes.KEY_F12
        "Enter" -> com.example.autoplaymate.ble.KeyCodes.KEY_ENTER
        "Space" -> com.example.autoplaymate.ble.KeyCodes.KEY_SPACE
        "Tab" -> com.example.autoplaymate.ble.KeyCodes.KEY_TAB
        "Esc" -> com.example.autoplaymate.ble.KeyCodes.KEY_ESCAPE
        "Backspace" -> com.example.autoplaymate.ble.KeyCodes.KEY_BACKSPACE
        "Up" -> com.example.autoplaymate.ble.KeyCodes.KEY_ARROW_UP
        "Down" -> com.example.autoplaymate.ble.KeyCodes.KEY_ARROW_DOWN
        "Left" -> com.example.autoplaymate.ble.KeyCodes.KEY_ARROW_LEFT
        "Right" -> com.example.autoplaymate.ble.KeyCodes.KEY_ARROW_RIGHT
        else -> null
    }
}

/**
 * 键盘网格
 */
@Composable
fun KeyboardGrid(
    keys: List<String>,
    onKeyPress: (String) -> Unit,
    enabled: Boolean
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 第一行 Q-P
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            keys.take(10).forEach { key ->
                KeyButton(
                    key = key,
                    onClick = { onKeyPress(key) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 第二行 A-L
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            keys.drop(10).take(9).forEach { key ->
                KeyButton(
                    key = key,
                    onClick = { onKeyPress(key) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 第三行 Z-M
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            keys.drop(19).forEach { key ->
                KeyButton(
                    key = key,
                    onClick = { onKeyPress(key) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * 按键按钮
 */
@Composable
fun KeyButton(
    key: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        contentPadding = PaddingValues(4.dp)
    ) {
        Text(
            text = key,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * 获取字母按键码
 */
fun getLetterKeycode(letter: String): Byte? {
    val lower = letter.lowercase()
    return when (lower) {
        "a" -> KeyCodes.KEY_A
        "b" -> KeyCodes.KEY_B
        "c" -> KeyCodes.KEY_C
        "d" -> KeyCodes.KEY_D
        "e" -> KeyCodes.KEY_E
        "f" -> KeyCodes.KEY_F
        "g" -> KeyCodes.KEY_G
        "h" -> KeyCodes.KEY_H
        "i" -> KeyCodes.KEY_I
        "j" -> KeyCodes.KEY_J
        "k" -> KeyCodes.KEY_K
        "l" -> KeyCodes.KEY_L
        "m" -> KeyCodes.KEY_M
        "n" -> KeyCodes.KEY_N
        "o" -> KeyCodes.KEY_O
        "p" -> KeyCodes.KEY_P
        "q" -> KeyCodes.KEY_Q
        "r" -> KeyCodes.KEY_R
        "s" -> KeyCodes.KEY_S
        "t" -> KeyCodes.KEY_T
        "u" -> KeyCodes.KEY_U
        "v" -> KeyCodes.KEY_V
        "w" -> KeyCodes.KEY_W
        "x" -> KeyCodes.KEY_X
        "y" -> KeyCodes.KEY_Y
        "z" -> KeyCodes.KEY_Z
        else -> null
    }
}

/**
 * 获取数字按键码
 */
fun getNumberKeycode(number: String): Byte? {
    return when (number) {
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
        else -> null
    }
}
