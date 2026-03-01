package com.example.autoplaymate.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import kotlin.coroutines.resume

/**
 * BLE HID 键盘管理器
 * 用于连接 BLE HID 设备并发送键盘指令
 */
class BleHidManager(private val context: Context) {

    companion object {
        private const val TAG = "BleHidManager"

        // EmulStick 自定义服务 UUID
        val EMULSTICK_SERVICE_UUID: UUID = UUID.fromString("0000f800-0000-1000-8000-00805f9b34fb")
        val EMULSTICK_INPUT_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000f801-0000-1000-8000-00805f9b34fb")

        // 标准 BLE HID UUIDs (备用)
        val HID_SERVICE_UUID: UUID = UUID.fromString("00001812-0000-1000-8000-00805f9b34fb")
        val REPORT_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002a4d-0000-1000-8000-00805f9b34fb")
        val REPORT_MAP_UUID: UUID = UUID.fromString("00002a4b-0000-1000-8000-00805f9b34fb")
        val HID_INFORMATION_UUID: UUID = UUID.fromString("00002a4a-0000-1000-8000-00805f9b34fb")
        val CLIENT_CHARACTERISTIC_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        // HID 键盘报告 ID
        private const val KEYBOARD_REPORT_ID: Byte = 0x01

        // 报告长度：带 Report ID 为 8 字节
        private const val REPORT_LENGTH = 8
    }

    private val bluetoothManager: BluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }

    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager.adapter

    private var bluetoothGatt: BluetoothGatt? = null
    private var reportCharacteristic: BluetoothGattCharacteristic? = null

    // 用于追踪正在连接的 GATT 实例，避免旧连接断开影响新连接
    private var pendingConnectionGatt: BluetoothGatt? = null

    // 用于确保写入操作串行化的互斥锁
    private val writeMutex = Mutex()

    // 用于等待写入完成的回调
    private var writeCallback: ((Boolean) -> Unit)? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    val connectedDevice: StateFlow<BluetoothDevice?> = _connectedDevice.asStateFlow()

    sealed class ConnectionState {
        data object Disconnected : ConnectionState()
        data object Connecting : ConnectionState()
        data object Connected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    /**
     * 检查蓝牙是否启用
     */
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    /**
     * 开始扫描 BLE HID 设备
     * @param scanDuration 扫描持续时间（毫秒），默认 10 秒
     * @param callback 扫描完成回调，返回发现的 HID 设备列表（包括已配对和扫描到的新设备）
     */
    fun startScan(scanDuration: Long = 10000, callback: (List<BluetoothDevice>) -> Unit) {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            Log.e(TAG, "蓝牙适配器不可用")
            callback(emptyList())
            return
        }

        if (!adapter.isEnabled) {
            Log.e(TAG, "蓝牙未启用")
            callback(emptyList())
            return
        }

        // 使用 LinkedHashSet 去重，保持插入顺序
        val discoveredDevices = linkedSetOf<BluetoothDevice>()

        // 先添加已配对的 HID 设备
        val bondedDevices = getBondedHidDevices()
        discoveredDevices.addAll(bondedDevices)
        Log.d(TAG, "已配对 HID 设备: ${bondedDevices.map { "${it.name ?: "未知"}(${it.address})" }}")

        // 使用传统扫描方式
        val leScanCallback = android.bluetooth.BluetoothAdapter.LeScanCallback { device, rssi, scanRecord ->
            // 检查是否是 HID 设备（通过扫描记录中的服务 UUID）
            val isHidDevice = checkHidServiceInScanRecord(scanRecord)

            if (isHidDevice) {
                val isNewDevice = discoveredDevices.add(device)
                if (isNewDevice) {
                    Log.d(TAG, "发现新的 HID 设备: ${device.name ?: "未知"} - ${device.address}, RSSI: $rssi")
                }
            }
        }

        adapter.startLeScan(leScanCallback)
        Log.d(TAG, "开始扫描 BLE HID 设备，持续 ${scanDuration}ms...")

        // 停止扫描并返回所有发现的设备
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            adapter.stopLeScan(leScanCallback)
            val allDevices = discoveredDevices.toList()
            Log.d(TAG, "扫描完成，共发现 ${allDevices.size} 个 HID 设备")
            callback(allDevices)
        }, scanDuration)
    }

    /**
     * 检查扫描记录中是否包含 HID 服务或 EmulStick 服务
     */
    private fun checkHidServiceInScanRecord(scanRecord: ByteArray): Boolean {
        var index = 0
        while (index < scanRecord.size) {
            val length = scanRecord[index++].toInt() and 0xFF
            if (length == 0) break

            val type = scanRecord[index++].toInt() and 0xFF

            // 检查完整的 UUID 服务列表 (type = 0x02 or 0x03)
            if (type == 0x02 || type == 0x03) {
                val uuidLength = length - 1
                for (i in 0 until uuidLength step 2) {
                    if (index + i + 1 < scanRecord.size) {
                        val uuid = ((scanRecord[index + i].toInt() and 0xFF) shl 8) or
                                   (scanRecord[index + i + 1].toInt() and 0xFF)
                        // 标准 HID Service UUID (0x1812) 或 EmulStick 自定义服务 (0xF800)
                        if (uuid == 0x1812 || uuid == 0xF800) {
                            return true
                        }
                    }
                }
            }
            index += length - 1
        }
        return false
    }

    /**
     * 获取已配对的 HID 设备（包括 EmulStick）
     */
    fun getBondedHidDevices(): List<BluetoothDevice> {
        val adapter = bluetoothAdapter ?: return emptyList()

        // 获取已配对设备
        val bondedDevices = adapter.bondedDevices

        // 过滤出支持 BLE HID 的设备（包括 EmulStick 自定义服务）
        return bondedDevices.filter { device ->
            device.type == BluetoothDevice.DEVICE_TYPE_LE ||
            (device.uuids?.any {
                it == HID_SERVICE_UUID || it == EMULSTICK_SERVICE_UUID
            } == true)
        }.toList()
    }

    /**
     * 连接到 BLE HID 设备
     */
    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        _connectionState.value = ConnectionState.Connecting

        // 保存当前的 GATT（稍后会变成"旧的"）
        val oldGatt = bluetoothGatt

        // 先直接关闭旧连接，避免延迟关闭影响新连接
        try {
            oldGatt?.disconnect()
            oldGatt?.close()
        } catch (e: Exception) {
            Log.e(TAG, "关闭旧连接时出错", e)
        }
        bluetoothGatt = null
        reportCharacteristic = null

        // 短暂延迟，确保旧连接完全关闭
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            // 创建新连接并记录为待定连接
            val newGatt = device.connectGatt(context, false, gattCallback)
            pendingConnectionGatt = newGatt
            Log.d(TAG, "正在连接到设备: ${device.name ?: "未知"} (GATT: ${System.identityHashCode(newGatt)})")
        }, 100)
    }

    /**
     * 断开连接
     */
    @SuppressLint("MissingPermission")
    fun disconnect() {
        try {
            // 断开前先释放所有按键，防止残留状态
            releaseAllKeys()

            // 清除待定连接标记，允许断开完成
            pendingConnectionGatt = null

            // 短暂延迟确保释放报告发送成功
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                bluetoothGatt?.disconnect()
                bluetoothGatt?.close()
                bluetoothGatt = null
                reportCharacteristic = null
                _connectedDevice.value = null
                _connectionState.value = ConnectionState.Disconnected
                Log.d(TAG, "主动断开连接完成")
            }, 50)
        } catch (e: Exception) {
            Log.e(TAG, "断开连接时出错", e)
            bluetoothGatt = null
            reportCharacteristic = null
            pendingConnectionGatt = null
            _connectedDevice.value = null
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    /**
     * 发送键盘报告（等待写入完成）
     * @param modifier 修饰键 (Ctrl, Alt, Shift, Win) 的位掩码
     * @param keycode 按键码
     */
    @SuppressLint("MissingPermission")
    suspend fun sendKeyReport(modifier: Byte, keycode: Byte): Boolean = writeMutex.withLock {
        val characteristic = reportCharacteristic ?: run {
            Log.w(TAG, "报告特征值未找到")
            return false
        }

        val gatt = bluetoothGatt ?: run {
            Log.w(TAG, "设备未连接")
            return false
        }

        return suspendCancellableCoroutine { continuation ->
            try {
                // 构造 HID 键盘报告（带 Report ID 的8字节格式）
                // [Report ID(1), 修饰键(1), 保留(1), 按键码1(1), 按键码2(1), 按键码3(1), 按键码4(1), 按键码5(1), 按键码6(1)]
                val report = ByteArray(REPORT_LENGTH)
                report[0] = KEYBOARD_REPORT_ID
                report[1] = modifier
                report[2] = 0x00
                report[3] = keycode
                report[4] = 0x00
                report[5] = 0x00
                report[6] = 0x00
                report[7] = 0x00

                characteristic.value = report
                writeCallback = { success ->
                    if (success) {
                        Log.d(TAG, "发送键盘报告成功: modifier=0x${modifier.toString(16)}, keycode=0x${keycode.toString(16)}")
                    } else {
                        Log.e(TAG, "发送键盘报告失败")
                    }
                    continuation.resume(success)
                }

                val writeQueued = gatt.writeCharacteristic(characteristic)
                if (!writeQueued) {
                    writeCallback = null
                    Log.e(TAG, "写入请求排队失败")
                    continuation.resume(false)
                } else {
                    continuation.invokeOnCancellation {
                        writeCallback = null
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "发送键盘报告异常", e)
                writeCallback = null
                continuation.resume(false)
            }
        }
    }

    /**
     * 发送按键（按下后释放）
     */
    suspend fun sendKeyPress(keycode: Byte, modifier: Byte = 0): Boolean {
        // 按下
        if (!sendKeyReport(modifier, keycode)) {
            return false
        }
        // 短暂延迟确保设备处理按键按下事件
        kotlinx.coroutines.delay(20)
        // 释放所有键
        return sendKeyReport(0, 0)
    }

    /**
     * 释放所有按键（发送空报告）
     * 用于清理可能残留的按键状态
     */
    @SuppressLint("MissingPermission")
    fun releaseAllKeys() {
        try {
            val characteristic = reportCharacteristic ?: return
            val gatt = bluetoothGatt ?: return

            // 构造空的 HID 键盘报告（带 Report ID 的8字节格式）
            val report = ByteArray(REPORT_LENGTH)
            report[0] = KEYBOARD_REPORT_ID
            report[1] = 0x00  // 无修饰键
            report[2] = 0x00
            report[3] = 0x00
            report[4] = 0x00
            report[5] = 0x00
            report[6] = 0x00
            report[7] = 0x00

            characteristic.value = report
            gatt.writeCharacteristic(characteristic)
            Log.d(TAG, "已发送释放所有按键报告")
        } catch (e: Exception) {
            Log.e(TAG, "释放按键失败", e)
        }
    }

    /**
     * 初始化时发送释放报告（同步等待）
     * 这个方法只在服务发现完成后调用一次，用于确保初始状态正确
     */
    private fun sendInitialReleaseReportSync() {
        try {
            val characteristic = reportCharacteristic ?: run {
                Log.w(TAG, "报告特征值为空，跳过初始释放报告")
                return
            }
            val gatt = bluetoothGatt ?: run {
                Log.w(TAG, "蓝牙GATT为空，跳过初始释放报告")
                return
            }

            // 构造空的 HID 键盘报告（带 Report ID 的8字节格式）
            val report = ByteArray(REPORT_LENGTH)
            report[0] = KEYBOARD_REPORT_ID
            report[1] = 0x00  // 无修饰键
            report[2] = 0x00  // 保留
            report[3] = 0x00  // 无按键
            report[4] = 0x00
            report[5] = 0x00
            report[6] = 0x00
            report[7] = 0x00

            characteristic.value = report
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

            // 使用 CountDownLatch 等待写入完成
            val latch = java.util.concurrent.CountDownLatch(1)
            val successHolder = java.util.concurrent.atomic.AtomicBoolean(false)

            // 临时保存旧的回调（此时应该为 null，因为是首次写入）
            val oldCallback = writeCallback
            writeCallback = { success ->
                successHolder.set(success)
                if (success) {
                    Log.d(TAG, "初始释放报告发送成功")
                } else {
                    Log.e(TAG, "初始释放报告发送失败")
                }
                latch.countDown()
            }

            val writeQueued = gatt.writeCharacteristic(characteristic)
            if (!writeQueued) {
                Log.e(TAG, "初始释放报告写入请求排队失败")
                writeCallback = oldCallback
            } else {
                // 等待写入完成，最多等待 2 秒
                val completed = latch.await(2, java.util.concurrent.TimeUnit.SECONDS)
                if (!completed) {
                    Log.w(TAG, "初始释放报告发送超时")
                }
                // 恢复旧的回调
                writeCallback = oldCallback
            }
        } catch (e: Exception) {
            Log.e(TAG, "发送初始释放报告异常", e)
        }
    }

    /**
     * GATT 回调
     */
    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val device = gatt.device
            val gattHash = System.identityHashCode(gatt)
            val pendingHash = pendingConnectionGatt?.let { System.identityHashCode(it) }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "已连接到设备: ${device.name ?: "未知"} (GATT: $gattHash, 待定: $pendingHash)")

                    // 检查这是否是我们正在等待的新连接
                    if (pendingConnectionGatt == gatt) {
                        // 这是新连接，更新状态
                        _connectedDevice.value = device
                        bluetoothGatt = gatt
                        pendingConnectionGatt = null  // 连接成功，清除待定标记
                        // 发现服务
                        gatt.discoverServices()
                    } else {
                        // 这是旧连接的延迟回调，忽略
                        Log.d(TAG, "忽略旧连接的延迟 CONNECTED 回调")
                        // 关闭这个旧的 GATT
                        try {
                            gatt.disconnect()
                            gatt.close()
                        } catch (e: Exception) {
                            Log.e(TAG, "关闭旧 GATT 时出错", e)
                        }
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "设备已断开: ${device.name ?: "未知"} (GATT: $gattHash, 待定: $pendingHash, 当前: ${bluetoothGatt?.let { System.identityHashCode(it) }})")

                    // 检查这是否是当前活动连接的断开
                    if (bluetoothGatt == gatt) {
                        // 这是当前活动连接的断开
                        _connectionState.value = ConnectionState.Disconnected
                        _connectedDevice.value = null
                        bluetoothGatt = null
                        reportCharacteristic = null
                        Log.d(TAG, "当前连接已断开")
                    } else if (pendingConnectionGatt == gatt) {
                        // 这是正在建立的连接失败了
                        _connectionState.value = ConnectionState.Error("连接失败")
                        pendingConnectionGatt = null
                        Log.e(TAG, "连接失败: status=$status")
                    } else {
                        // 这是旧连接的延迟回调，忽略
                        Log.d(TAG, "忽略旧连接的延迟 DISCONNECTED 回调")
                        // 确保旧 GATT 被关闭
                        try {
                            gatt.close()
                        } catch (e: Exception) {
                            Log.e(TAG, "关闭旧 GATT 时出错", e)
                        }
                    }
                }
                BluetoothProfile.STATE_CONNECTING -> {
                    Log.d(TAG, "正在连接... (GATT: $gattHash)")
                    if (pendingConnectionGatt == gatt) {
                        _connectionState.value = ConnectionState.Connecting
                    }
                }
                BluetoothProfile.STATE_DISCONNECTING -> {
                    Log.d(TAG, "正在断开... (GATT: $gattHash)")
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "服务发现成功")

                // 打印所有可用的服务用于调试
                val services = gatt.services
                Log.d(TAG, "发现 ${services.size} 个服务:")
                services.forEach { service ->
                    Log.d(TAG, "  服务 UUID: ${service.uuid}")
                    service.characteristics.forEach { char ->
                        val props = char.properties
                        val propsStr = buildString {
                            if (props and BluetoothGattCharacteristic.PROPERTY_READ != 0) append("READ ")
                            if (props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) append("WRITE ")
                            if (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) append("WRITE_NO_RESPONSE ")
                            if (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) append("NOTIFY ")
                        }
                        Log.d(TAG, "    特征: ${char.uuid} [$propsStr]")
                    }
                }

                // 优先查找 EmulStick 自定义服务
                var targetService = gatt.getService(EMULSTICK_SERVICE_UUID)
                var targetCharacteristicUuid = EMULSTICK_INPUT_CHARACTERISTIC_UUID
                var serviceType = "EmulStick"

                // 如果没找到 EmulStick 服务，尝试标准 HID 服务
                if (targetService == null) {
                    targetService = gatt.getService(HID_SERVICE_UUID)
                    targetCharacteristicUuid = REPORT_CHARACTERISTIC_UUID
                    serviceType = "标准 HID"
                }

                if (targetService == null) {
                    Log.e(TAG, "未找到支持的服务")
                    _connectionState.value = ConnectionState.Error("未找到 HID 服务")
                    return
                }

                Log.d(TAG, "使用 $serviceType 服务: ${targetService.uuid}")

                // 查找 Report 特征值
                reportCharacteristic = targetService.getCharacteristic(targetCharacteristicUuid)
                if (reportCharacteristic == null) {
                    Log.e(TAG, "未找到 Report 特征值 (UUID: $targetCharacteristicUuid)")
                    _connectionState.value = ConnectionState.Error("未找到 Report 特征值")
                    return
                }

                // 检查是否可写
                val properties = reportCharacteristic!!.properties
                if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE == 0 &&
                    properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE == 0) {
                    Log.e(TAG, "Report 特征值不可写")
                    _connectionState.value = ConnectionState.Error("Report 特征值不可写")
                    return
                }

                _connectionState.value = ConnectionState.Connected
                Log.d(TAG, "HID 服务准备就绪")

                // 连接成功后立即发送一个"全释放"报告，确保设备没有残留的按键状态
                sendInitialReleaseReportSync()

            } else {
                Log.e(TAG, "服务发现失败: $status")
                _connectionState.value = ConnectionState.Error("服务发现失败")
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            val success = status == BluetoothGatt.GATT_SUCCESS
            if (!success) {
                Log.e(TAG, "特征值写入失败: $status")
            }
            // 通知等待中的协程写入已完成
            writeCallback?.invoke(success)
            writeCallback = null
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "特征值读取成功: ${characteristic.value?.joinToString(", ") { "0x${it.toString(16)}" }}")
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "描述符写入成功")
            }
        }
    }
}

/**
 * 修饰键位掩码
 */
object KeyModifier {
    const val LEFT_CTRL: Byte = 0x01
    const val LEFT_SHIFT: Byte = 0x02
    const val LEFT_ALT: Byte = 0x04
    const val LEFT_META: Byte = 0x08
    const val RIGHT_CTRL: Byte = 0x10
    const val RIGHT_SHIFT: Byte = 0x20
    const val RIGHT_ALT: Byte = 0x40
    const val RIGHT_META: Byte = -0x80
}

/**
 * 常用按键码 (HID Usage Tables)
 */
object KeyCodes {
    const val KEY_A: Byte = 0x04
    const val KEY_B: Byte = 0x05
    const val KEY_C: Byte = 0x06
    const val KEY_D: Byte = 0x07
    const val KEY_E: Byte = 0x08
    const val KEY_F: Byte = 0x09
    const val KEY_G: Byte = 0x0A
    const val KEY_H: Byte = 0x0B
    const val KEY_I: Byte = 0x0C
    const val KEY_J: Byte = 0x0D
    const val KEY_K: Byte = 0x0E
    const val KEY_L: Byte = 0x0F
    const val KEY_M: Byte = 0x10
    const val KEY_N: Byte = 0x11
    const val KEY_O: Byte = 0x12
    const val KEY_P: Byte = 0x13
    const val KEY_Q: Byte = 0x14
    const val KEY_R: Byte = 0x15
    const val KEY_S: Byte = 0x16
    const val KEY_T: Byte = 0x17
    const val KEY_U: Byte = 0x18
    const val KEY_V: Byte = 0x19
    const val KEY_W: Byte = 0x1A
    const val KEY_X: Byte = 0x1B
    const val KEY_Y: Byte = 0x1C
    const val KEY_Z: Byte = 0x1D

    const val KEY_1: Byte = 0x1E
    const val KEY_2: Byte = 0x1F
    const val KEY_3: Byte = 0x20
    const val KEY_4: Byte = 0x21
    const val KEY_5: Byte = 0x22
    const val KEY_6: Byte = 0x23
    const val KEY_7: Byte = 0x24
    const val KEY_8: Byte = 0x25
    const val KEY_9: Byte = 0x26
    const val KEY_0: Byte = 0x27

    const val KEY_ENTER: Byte = 0x28
    const val KEY_ESCAPE: Byte = 0x29
    const val KEY_BACKSPACE: Byte = 0x2A
    const val KEY_TAB: Byte = 0x2B
    const val KEY_SPACE: Byte = 0x2C

    const val KEY_F1: Byte = 0x3A
    const val KEY_F2: Byte = 0x3B
    const val KEY_F3: Byte = 0x3C
    const val KEY_F4: Byte = 0x3D
    const val KEY_F5: Byte = 0x3E
    const val KEY_F6: Byte = 0x3F
    const val KEY_F7: Byte = 0x40
    const val KEY_F8: Byte = 0x41
    const val KEY_F9: Byte = 0x42
    const val KEY_F10: Byte = 0x43
    const val KEY_F11: Byte = 0x44
    const val KEY_F12: Byte = 0x45

    const val KEY_ARROW_UP: Byte = 0x52
    const val KEY_ARROW_LEFT: Byte = 0x50
    const val KEY_ARROW_DOWN: Byte = 0x51
    const val KEY_ARROW_RIGHT: Byte = 0x4F
}
