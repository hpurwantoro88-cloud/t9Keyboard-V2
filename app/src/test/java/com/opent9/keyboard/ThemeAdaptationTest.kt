package com.opent9.keyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.opent9.keyboard.settings.SettingsActivity
import com.opent9.keyboard.settings.SettingsObserver
import com.opent9.keyboard.ui.KeyboardColors
import com.opent9.keyboard.ui.KeyboardPage
import com.opent9.keyboard.ui.KeyboardTheme
import com.opent9.keyboard.ui.T9KeyboardView
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ThemeAdaptationTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    private fun getPaintField(view: T9KeyboardView, fieldName: String): Paint {
        val field = T9KeyboardView::class.java.getDeclaredField(fieldName).apply {
            isAccessible = true
        }
        return field.get(view) as Paint
    }

    @Test
    fun testDefaultFollowSystemNightModeResolution() {
        val nightConfig = Configuration().apply {
            uiMode = Configuration.UI_MODE_NIGHT_YES or (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv())
        }
        val dayConfig = Configuration().apply {
            uiMode = Configuration.UI_MODE_NIGHT_NO or (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv())
        }

        assertTrue(KeyboardTheme.isSystemInDarkMode(nightConfig))
        assertFalse(KeyboardTheme.isSystemInDarkMode(dayConfig))

        val darkColors = KeyboardTheme.resolveColors(nightConfig, "system")
        assertEquals(KeyboardTheme.DARK, darkColors)
        assertTrue(darkColors.isDark)

        val lightColors = KeyboardTheme.resolveColors(dayConfig, "system")
        assertEquals(KeyboardTheme.LIGHT, lightColors)
        assertFalse(lightColors.isDark)
    }

    @Test
    fun testExplicitThemeSettingOverrides() {
        val nightConfig = Configuration().apply {
            uiMode = Configuration.UI_MODE_NIGHT_YES
        }
        val dayConfig = Configuration().apply {
            uiMode = Configuration.UI_MODE_NIGHT_NO
        }

        // When "light" is chosen, force Light even on a phone in night mode
        val forcedLight = KeyboardTheme.resolveColors(nightConfig, "light")
        assertEquals(KeyboardTheme.LIGHT, forcedLight)
        assertFalse(forcedLight.isDark)

        // When "dark" is chosen, force Dark even on a phone in day mode
        val forcedDark = KeyboardTheme.resolveColors(dayConfig, "dark")
        assertEquals(KeyboardTheme.DARK, forcedDark)
        assertTrue(forcedDark.isDark)
    }

    @Test
    fun testT9KeyboardViewAppliesSystemThemeOnConfigurationChange() {
        val view = T9KeyboardView(context)

        // 1. Set to Day / Light Mode
        val lightConfig = Configuration().apply {
            uiMode = Configuration.UI_MODE_NIGHT_NO
        }
        view.onConfigurationChanged(lightConfig)
        assertFalse("Keyboard should be in light mode", view.isDarkMode)
        assertEquals(KeyboardTheme.LIGHT, view.currentColors)

        // Verify key paints match light theme
        assertEquals(KeyboardTheme.LIGHT.background, getPaintField(view, "backgroundPaint").color)
        assertEquals(KeyboardTheme.LIGHT.keyBackground, getPaintField(view, "keyBackgroundPaint").color)
        assertEquals(KeyboardTheme.LIGHT.keyPressed, getPaintField(view, "keyPressedPaint").color)
        assertEquals(KeyboardTheme.LIGHT.primaryText, getPaintField(view, "primaryTextPaint").color)
        assertEquals(KeyboardTheme.LIGHT.subText, getPaintField(view, "subTextPaint").color)
        assertEquals(KeyboardTheme.LIGHT.dualSymText, getPaintField(view, "dualSymTextPaint").color)
        assertEquals(KeyboardTheme.LIGHT.stripBackground, getPaintField(view, "stripBackgroundPaint").color)
        assertEquals(KeyboardTheme.LIGHT.pillBackground, getPaintField(view, "pillPaint").color)
        assertEquals(KeyboardTheme.LIGHT.pillText, getPaintField(view, "pillTextPaint").color)
        assertEquals(KeyboardTheme.LIGHT.candidateText, getPaintField(view, "candidateTextPaint").color)
        assertEquals(KeyboardTheme.LIGHT.candidatePrefixText, getPaintField(view, "candidatePrefixPaint").color)
        assertEquals(KeyboardTheme.LIGHT.iconColor, getPaintField(view, "iconPaint").color)
        assertEquals(KeyboardTheme.LIGHT.iconColor, getPaintField(view, "iconFillPaint").color)
        assertEquals(KeyboardTheme.LIGHT.page1DigitText, getPaintField(view, "page1DigitTextPaint").color)
        assertEquals(KeyboardTheme.LIGHT.page1OpText, getPaintField(view, "page1OpTextPaint").color)

        // 2. Switch to Night / Dark Mode
        val darkConfig = Configuration().apply {
            uiMode = Configuration.UI_MODE_NIGHT_YES
        }
        view.onConfigurationChanged(darkConfig)
        assertTrue("Keyboard should adapt to dark mode", view.isDarkMode)
        assertEquals(KeyboardTheme.DARK, view.currentColors)

        // Verify key paints match dark theme
        assertEquals(KeyboardTheme.DARK.background, getPaintField(view, "backgroundPaint").color)
        assertEquals(KeyboardTheme.DARK.keyBackground, getPaintField(view, "keyBackgroundPaint").color)
        assertEquals(KeyboardTheme.DARK.keyPressed, getPaintField(view, "keyPressedPaint").color)
        assertEquals(KeyboardTheme.DARK.primaryText, getPaintField(view, "primaryTextPaint").color)
        assertEquals(KeyboardTheme.DARK.subText, getPaintField(view, "subTextPaint").color)
        assertEquals(KeyboardTheme.DARK.dualSymText, getPaintField(view, "dualSymTextPaint").color)
        assertEquals(KeyboardTheme.DARK.stripBackground, getPaintField(view, "stripBackgroundPaint").color)
        assertEquals(KeyboardTheme.DARK.pillBackground, getPaintField(view, "pillPaint").color)
        assertEquals(KeyboardTheme.DARK.pillText, getPaintField(view, "pillTextPaint").color)
        assertEquals(KeyboardTheme.DARK.candidateText, getPaintField(view, "candidateTextPaint").color)
        assertEquals(KeyboardTheme.DARK.candidatePrefixText, getPaintField(view, "candidatePrefixPaint").color)
        assertEquals(KeyboardTheme.DARK.iconColor, getPaintField(view, "iconPaint").color)
        assertEquals(KeyboardTheme.DARK.iconColor, getPaintField(view, "iconFillPaint").color)
    }

    @Test
    fun testAllKeyboardPagesRenderInBothThemes() {
        val view = T9KeyboardView(context)
        view.layout(0, 0, 1080, 780)
        view.updateCandidates(listOf("hello", "world", "testing"))

        val bitmap = Bitmap.createBitmap(1080, 780, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val pages = listOf(
            KeyboardPage.PAGE_0_TEXT,
            KeyboardPage.PAGE_1_NUM_SYM,
            KeyboardPage.PAGE_2_EXT_SYM,
            KeyboardPage.PAGE_3_EMOJI
        )

        val themes = listOf(KeyboardTheme.DARK, KeyboardTheme.LIGHT)

        for (theme in themes) {
            view.applyTheme(theme)
            assertEquals(theme, view.currentColors)

            for (page in pages) {
                view.keyAtlas.updatePageLayout(page)
                // Drawing on canvas must succeed without throwing any exception
                view.draw(canvas)
            }
        }
    }

    @Test
    fun testSettingsObserverListensToThemePreferenceChanges() {
        val observer = SettingsObserver(context)
        observer.start()

        var themeChangedNotified = false
        observer.onThemeConfigChanged = {
            themeChangedNotified = true
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putString("app_theme", "light").commit()

        assertTrue("SettingsObserver should notify when app_theme changes", themeChangedNotified)
        assertEquals("light", observer.getAppTheme())

        observer.stop()
    }

    @Test
    fun testSettingsActivityNightModeMapping() {
        SettingsActivity.applyNightMode("light")
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.getDefaultNightMode())

        SettingsActivity.applyNightMode("dark")
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode())

        SettingsActivity.applyNightMode("system")
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.getDefaultNightMode())
    }
}
