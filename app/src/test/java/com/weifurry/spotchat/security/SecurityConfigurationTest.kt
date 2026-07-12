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
    fun sensitiveStateIsExcludedFromBackupAndDeviceTransfer() {
        val legacyRules = parseXml(appFile("src/main/res/xml/backup_rules.xml"))
        val extractionRules = parseXml(appFile("src/main/res/xml/data_extraction_rules.xml"))
        val sensitiveFiles =
            listOf(
                "sharedpref" to "spotchat_identity.xml",
                "sharedpref" to "spotchat_identity.xml.bak",
                "sharedpref" to "spotchat_trusted_peers.xml",
                "sharedpref" to "spotchat_trusted_peers.xml.bak",
                "sharedpref" to "spotchat_wear_state.xml",
                "sharedpref" to "spotchat_wear_state.xml.bak",
                "database" to "spotchat_replay.db",
                "database" to "spotchat_replay.db-journal",
                "database" to "spotchat_replay.db-wal",
                "database" to "spotchat_replay.db-shm"
            )

        sensitiveFiles.forEach { (domain, path) ->
            assertEquals(
                "Legacy backup rules must exclude $domain/$path",
                setOf("full-backup-content"),
                exclusionParents(legacyRules, domain, path)
            )
            assertEquals(
                "Cloud backup and device transfer must exclude $domain/$path",
                setOf("cloud-backup", "device-transfer"),
                exclusionParents(extractionRules, domain, path)
            )
        }
    }

    @Test
    fun persistentChatStateUsesNoBackupDirectoryAndApplicationDisablesBackup() {
        val manifest = parseXml(appFile("src/main/AndroidManifest.xml"))
        val application = manifest.getElementsByTagName("application").item(0) as Element
        assertEquals("false", application.getAttributeNS(ANDROID_NAMESPACE, "allowBackup"))

        val storeSource =
            appDirectory("src/main/java")
                .walkTopDown()
                .single { it.isFile && it.name == "EncryptedChatStateStore.kt" }
                .readText()
        assertTrue(
            "Persistent chat state must be rooted in Context.noBackupFilesDir",
            storeSource.contains("context.noBackupFilesDir")
        )
        assertTrue(
            "Persistent chat state must use its dedicated no-backup directory",
            storeSource.contains("\"spotchat-chat-state\"")
        )
        assertTrue(
            "Persistent chat state must use the documented encrypted snapshot filename",
            storeSource.contains("\"spotchat_state.bin\"")
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

    private fun appFile(relativePath: String): File =
        appPath(relativePath).also { path ->
            check(path.isFile) { "Unable to locate app file: $relativePath" }
        }

    private fun appDirectory(relativePath: String): File =
        appPath(relativePath).also { path ->
            check(path.isDirectory) { "Unable to locate app directory: $relativePath" }
        }

    private fun appPath(relativePath: String): File {
        val userDirectory = checkNotNull(System.getProperty("user.dir"))
        var directory: File? = File(userDirectory).canonicalFile
        while (directory != null) {
            val candidates =
                listOf(
                    File(directory, relativePath),
                    File(directory, "app/$relativePath")
                )
            candidates.firstOrNull(File::exists)?.let { return it }
            directory = directory.parentFile
        }
        error("Unable to locate app path: $relativePath")
    }

    companion object {
        private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
