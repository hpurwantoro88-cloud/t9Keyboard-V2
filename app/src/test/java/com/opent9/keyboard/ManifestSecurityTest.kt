package com.opent9.keyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ManifestSecurityTest {

    @Test
    fun testNoInternetPermission() {
        val manifestFile = File("src/main/AndroidManifest.xml")
        val content = manifestFile.readText()
        assertFalse("AndroidManifest.xml must NOT declare INTERNET permission!",
            content.contains("android.permission.INTERNET"))
    }

    @Test
    fun testHardwareAccelerationEnabled() {
        val manifestFile = File("src/main/AndroidManifest.xml")
        val content = manifestFile.readText()
        assertTrue("AndroidManifest.xml must declare android:hardwareAccelerated=\"true\"!",
            content.contains("android:hardwareAccelerated=\"true\""))
    }
}
