package com.weifurry.spotchat.security

import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

class SecurityConfigurationTest {
    @Test
    fun wearStateIsExcludedFromBackupAndDeviceTransfer() {
        val legacyRules = parseXml(appFile("src/main/res/xml/backup_rules.xml"))
        val extractionRules = parseXml(appFile("src/main/res/xml/data_extraction_rules.xml"))

        assertEquals(
            setOf("full-backup-content"),
            exclusionParents(legacyRules, "sharedpref", "spotchat_wear_state.xml")
        )
        assertEquals(
            setOf("cloud-backup", "device-transfer"),
            exclusionParents(extractionRules, "sharedpref", "spotchat_wear_state.xml")
        )
    }

    @Test
    fun manifestUsesOnlyPermissionsNeededForPairedBluetoothConnections() {
        val manifest = parseXml(appFile("src/main/AndroidManifest.xml"))
        val permissionNodes = manifest.getElementsByTagName("uses-permission")
        val permissions = buildSet {
            for (index in 0 until permissionNodes.length) {
                val element = permissionNodes.item(index) as Element
                add(element.getAttributeNS(ANDROID_NAMESPACE, "name"))
            }
        }

        assertTrue(permissions.contains("android.permission.BLUETOOTH"))
        assertTrue(permissions.contains("android.permission.BLUETOOTH_CONNECT"))
        assertFalse(permissions.contains("android.permission.BLUETOOTH_ADMIN"))
        assertFalse(permissions.contains("android.permission.BLUETOOTH_SCAN"))
    }

    private fun exclusionParents(
        document: Document,
        domain: String,
        path: String
    ): Set<String> {
        val nodes = document.getElementsByTagName("exclude")
        return buildSet {
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as Element
                if (element.getAttribute("domain") == domain && element.getAttribute("path") == path) {
                    add(element.parentNode.nodeName)
                }
            }
        }
    }

    private fun parseXml(file: File): Document {
        val factory =
            DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            }
        return factory.newDocumentBuilder().parse(file)
    }

    private fun appFile(relativePath: String): File {
        val userDirectory = checkNotNull(System.getProperty("user.dir"))
        var directory: File? = File(userDirectory).canonicalFile
        while (directory != null) {
            val candidates =
                listOf(
                    File(directory, relativePath),
                    File(directory, "app/$relativePath")
                )
            candidates.firstOrNull(File::isFile)?.let { return it }
            directory = directory.parentFile
        }
        error("Unable to locate app file: $relativePath")
    }

    companion object {
        private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
