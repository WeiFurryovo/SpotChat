package com.weifurry.spotchat.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BluetoothPermissionPolicyTest {
    @Test
    fun preAndroid12NeedsNoRuntimeBluetoothPermission() {
        assertEquals(emptyList<String>(), requiredBluetoothRuntimePermissions(26))
        assertEquals(emptyList<String>(), requiredBluetoothRuntimePermissions(30))
    }

    @Test
    fun android12AndLaterRequestOnlyConnect() {
        listOf(31, 35, 36).forEach { sdkInt ->
            val permissions = requiredBluetoothRuntimePermissions(sdkInt)

            assertEquals(listOf(BLUETOOTH_CONNECT_PERMISSION), permissions)
            assertFalse(permissions.contains("android.permission.BLUETOOTH_SCAN"))
        }
    }
}
