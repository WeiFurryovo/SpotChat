package com.weifurry.spotchat.transport

internal const val BLUETOOTH_CONNECT_PERMISSION = "android.permission.BLUETOOTH_CONNECT"

internal fun requiredBluetoothRuntimePermissions(sdkInt: Int): List<String> =
    if (sdkInt >= ANDROID_12_API_LEVEL) {
        listOf(BLUETOOTH_CONNECT_PERMISSION)
    } else {
        emptyList()
    }

private const val ANDROID_12_API_LEVEL = 31
