package com.opent9.keyboard

import android.view.inputmethod.InputMethodSubtype
import com.opent9.keyboard.jni.NativeEngineBridge
import com.opent9.keyboard.ui.T9KeyboardView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class SubtypeLanguageSyncTest {

    @Test
    fun testResolveLanguageFromSubtypeLocale() {
        assertEquals("EN", LanguageCycleHelper.resolveLanguageFromSubtypeLocale("en_US"))
        assertEquals("EN", LanguageCycleHelper.resolveLanguageFromSubtypeLocale("en-GB"))
        assertEquals("EN", LanguageCycleHelper.resolveLanguageFromSubtypeLocale("en"))
        assertEquals("ID", LanguageCycleHelper.resolveLanguageFromSubtypeLocale("in_ID"))
        assertEquals("ID", LanguageCycleHelper.resolveLanguageFromSubtypeLocale("id_ID"))
        assertEquals("ID", LanguageCycleHelper.resolveLanguageFromSubtypeLocale("id-ID"))
        assertEquals("ID", LanguageCycleHelper.resolveLanguageFromSubtypeLocale("in"))
        assertEquals("ID", LanguageCycleHelper.resolveLanguageFromSubtypeLocale("id"))
        assertNull(LanguageCycleHelper.resolveLanguageFromSubtypeLocale(null))
        assertNull(LanguageCycleHelper.resolveLanguageFromSubtypeLocale(""))
        assertNull(LanguageCycleHelper.resolveLanguageFromSubtypeLocale("   "))
        assertNull(LanguageCycleHelper.resolveLanguageFromSubtypeLocale("fr_FR"))
    }

    @Test
    fun testMethodXmlDeclaresEnUsAndInIdSubtypes() {
        val methodXml = File("src/main/res/xml/method.xml").readText()
        assertTrue(methodXml.contains("android:imeSubtypeLocale=\"en_US\""))
        assertTrue(methodXml.contains("android:imeSubtypeLocale=\"in_ID\""))
    }

    @Test
    fun testOnCurrentInputMethodSubtypeChangedSyncsLanguageSafely() {
        val controller = Robolectric.buildService(OpenT9InputMethodService::class.java)
        val service = controller.create().get()
        val inputView = service.onCreateInputView() as T9KeyboardView

        val idSubtype = InputMethodSubtype.InputMethodSubtypeBuilder()
            .setSubtypeLocale("in_ID")
            .setSubtypeMode("keyboard")
            .build()
        val enSubtype = InputMethodSubtype.InputMethodSubtypeBuilder()
            .setSubtypeLocale("en_US")
            .setSubtypeMode("keyboard")
            .build()
        val idTagSubtype = InputMethodSubtype.InputMethodSubtypeBuilder()
            .setLanguageTag("id-ID")
            .setSubtypeMode("keyboard")
            .build()

        val method = OpenT9InputMethodService::class.java.getDeclaredMethod(
            "onCurrentInputMethodSubtypeChanged",
            InputMethodSubtype::class.java
        ).apply { isAccessible = true }

        method.invoke(service, enSubtype)
        assertEquals("EN", NativeEngineBridge.getActiveLanguage())
        assertEquals("EN", inputView.activeLanguage)

        method.invoke(service, idSubtype)
        assertEquals("ID", NativeEngineBridge.getActiveLanguage())
        assertEquals("ID", inputView.activeLanguage)

        // Calling again with equivalent languageTag should remain idempotent and safe
        method.invoke(service, idTagSubtype)
        assertEquals("ID", NativeEngineBridge.getActiveLanguage())
        assertEquals("ID", inputView.activeLanguage)

        // Null subtype must not throw or change active language
        method.invoke(service, null)
        assertEquals("ID", NativeEngineBridge.getActiveLanguage())
    }
}
