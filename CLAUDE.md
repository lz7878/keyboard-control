# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AutoPlayMate is an Android app that connects to BLE HID devices and automates keyboard input. It allows users to send keystrokes, text, and timed scripts to connected devices via Bluetooth Low Energy.

## Build Commands

```bash
# Build
./gradlew assembleDebug    # Build debug APK
./gradlew assembleRelease  # Build release APK
./gradlew build            # Full build with checks

# Tests (not yet implemented)
./gradlew test             # Run unit tests
./gradlew connectedCheck   # Run instrumented tests

# Lint
./gradlew lint             # Run lint checks

# Install & Run
./gradlew installDebug     # Install debug APK to connected device
```

## Architecture

**MVVM Pattern** with Jetpack Compose:

```
┌─────────────────────────────────────────────────────────┐
│                    MainActivity.kt                       │
│                  (Single Activity App)                   │
└─────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│              KeyboardControlScreen.kt                    │
│         (Compose UI - 3 Tabs: Device, Keyboard, Script)  │
└─────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│               KeyboardViewModel.kt                       │
│     (StateFlow-based state, script execution logic)      │
└─────────────────────────────────────────────────────────┘
                     │                        │
                     ▼                        ▼
    ┌────────────────────────┐   ┌─────────────────────────┐
    │   BleHidManager.kt     │   │ ScriptDataManager.kt    │
    │  (BLE HID operations)  │   │ (SharedPreferences JSON) │
    └────────────────────────┘   └─────────────────────────┘
```

## Key Components

### BleHidManager (`ble/BleHidManager.kt`)
Core BLE HID keyboard implementation:
- Scans for BLE HID devices (supports custom EmulStick service UUID and standard HID)
- Manages connection lifecycle with state flow
- Sends HID keyboard reports (keypress, key combos)
- Character-to-keycode mapping for text input

### KeyboardViewModel (`viewmodel/KeyboardViewModel.kt`)
Central business logic and state management:
- Exposes `connectionState`, `availableDevices`, `scriptSteps`, `loopEnabled` as StateFlow
- Methods: `scanDevices()`, `connect()`, `sendKeyPress()`, `runScript()`
- Script execution with coroutine-based delays and loop support
- Persistence integration via `ScriptDataManager`

### ScriptDataManager (`data/ScriptDataManager.kt`)
SharedPreferences-based persistence:
- Stores script steps as JSON using Gson
- Auto-loads on ViewModel init, auto-saves on changes
- Preserves loop mode setting across app restarts

### KeyboardControlScreen (`ui/screens/KeyboardControlScreen.kt`)
Main Compose UI with three tabs:
1. **设备连接** - Device scanning and connection
2. **键盘控制** - Manual keyboard controls and text input
3. **定时脚本** - Script builder and executor

## Data Flow

1. UI events → ViewModel method calls
2. ViewModel updates StateFlow
3. UI collects StateFlow with `collectAsStateWithLifecycle()`
4. Script data automatically persisted to SharedPreferences on any change

## Script Data Model

```kotlin
data class ScriptStepData(
    val delayMs: Long,      // Delay before this action (milliseconds)
    val keyName: String?    // Human-readable key name (e.g., "F12") or null for delay-only
)
```

## BLE HID Services

The app supports two BLE HID service UUIDs:
- Custom EmulStick: `00001812-0000-1000-8000-00805f9b34fb`
- Standard HID: `00001812-0000-1000-8000-00805f9b34fb`

HID Report UUID: `00002a4b-0000-1000-8000-00805f9b34fb`

## Important Implementation Notes

1. **Data Persistence**: When modifying script-related UI, always use ViewModel methods (`addScriptStep()`, `removeScriptStep()`, etc.) rather than direct state manipulation to ensure automatic persistence.

2. **ViewModel Lifecycle**: The `onCleared()` method saves all script data before destruction. This handles app exits and process death.

3. **State Management**: All UI state flows through StateFlow. Use `collectAsStateWithLifecycle()` in Composables for lifecycle-aware collection.

4. **Permission Handling**: The app requires BLUETOOTH_SCAN, BLUETOOTH_CONNECT, and location permissions for BLE scanning (Android 12+).

5. **Language**: The UI and code comments are in Chinese. This is intentional for the target user base.
