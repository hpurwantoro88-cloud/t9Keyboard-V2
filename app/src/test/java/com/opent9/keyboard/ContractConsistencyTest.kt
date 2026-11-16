package com.opent9.keyboard

import android.view.inputmethod.InputConnection
import com.opent9.keyboard.ui.EmojiAtlas
import com.opent9.keyboard.ui.FlickDirection
import com.opent9.keyboard.ui.FlickDisambiguator
import com.opent9.keyboard.ui.KeyAtlas
import com.opent9.keyboard.ui.KeyType
import com.opent9.keyboard.ui.KeyboardPage
import com.opent9.keyboard.ui.PageController
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ContractConsistencyTest {

    @Test
    fun testAllPreferencesKeysAreReferencedInSettingsObserverOrActivity() {
        val prefsXml = File("src/main/res/xml/preferences.xml").readText()
        val observerSrc = File("src/main/java/com/opent9/keyboard/settings/SettingsObserver.kt").readText()
        val activitySrc = File("src/main/java/com/opent9/keyboard/settings/SettingsActivity.kt").readText()

        val keyMatches = Regex("""app:key="([^"]+)"""").findAll(prefsXml).map { it.groupValues[1] }.toList()
        assertTrue("preferences.xml should define preference keys", keyMatches.isNotEmpty())

        for (key in keyMatches) {
            val isHandled = observerSrc.contains("\"$key\"") || activitySrc.contains("\"$key\"")
            assertTrue("Preference key '$key' must be handled in SettingsObserver.kt or SettingsActivity.kt", isHandled)
        }
    }

    @Test
    fun testFlickDisambiguatorMatchesPageControllerHandlers() {
        val atlas = KeyAtlas()
        atlas.computeGeometry(1080f, 780f, 3f)
        atlas.updatePageLayout(KeyboardPage.PAGE_0_TEXT)
        val controller = PageController(atlas)

        // Key 12 (?123) only supports DOWN flick, not UP
        val key12 = atlas.keys[12]
        assertTrue(FlickDisambiguator.isFlickSupported(key12, FlickDirection.DOWN, KeyboardPage.PAGE_0_TEXT))
        assertFalse(FlickDisambiguator.isFlickSupported(key12, FlickDirection.UP, KeyboardPage.PAGE_0_TEXT))

        // Key 13 (LANG) supports UP (switch language) and DOWN (switch to Emoji)
        val key13 = atlas.keys[13]
        assertTrue(FlickDisambiguator.isFlickSupported(key13, FlickDirection.UP, KeyboardPage.PAGE_0_TEXT))
        assertTrue(FlickDisambiguator.isFlickSupported(key13, FlickDirection.DOWN, KeyboardPage.PAGE_0_TEXT))

        val ic = mockk<InputConnection>(relaxed = true)
        var langSwitched = false
        var switchedPage: KeyboardPage? = null
        controller.handleKeyFlick(
            key = key13,
            direction = FlickDirection.UP,
            ic = ic,
            onSwitchLanguage = { langSwitched = true },
            onClearField = {},
            onDeletePrecedingWord = {},
            onForceSubmit = {},
            onSwitchPage = { switchedPage = it }
        )
        assertTrue(langSwitched)
        assertEquals(null, switchedPage)

        // Key 15 is hidden on Page 0 and must not report flick support
        assertFalse(FlickDisambiguator.isFlickSupported(atlas.keys[15], FlickDirection.UP, KeyboardPage.PAGE_0_TEXT))
        assertFalse(FlickDisambiguator.isFlickSupported(atlas.keys[15], FlickDirection.DOWN, KeyboardPage.PAGE_0_TEXT))

        // Page 1, 2, 3 non-system direct keys must not report flick support
        val page1Direct = atlas.page1Keys.first { it.type == KeyType.DIRECT_SYM }
        for (dir in FlickDirection.entries) {
            assertFalse(FlickDisambiguator.isFlickSupported(page1Direct, dir, KeyboardPage.PAGE_1_NUM_SYM))
        }
    }

    @Test
    fun testAllPagesHaveValidKeyGeometryAndUniqueIds() {
        val atlas = KeyAtlas()
        atlas.computeGeometry(1080f, 780f, 3f)

        assertTrue("Page 0 hidden key 15 must have empty bounds", atlas.keys[15].bounds.isEmpty)

        val pages = listOf(
            "Page 1" to atlas.page1Keys,
            "Page 2" to atlas.page2Keys,
            "Page 3" to atlas.page3Keys
        )
        for ((name, keys) in pages) {
            val ids = keys.map { it.id }
            assertEquals("$name key IDs must be unique", ids.size, ids.toSet().size)
            for (k in keys) {
                assertFalse("$name key ${k.primaryLabel} (${k.id}) must have non-empty bounds", k.bounds.isEmpty)
            }
        }
    }

    @Test
    fun testEmojiAtlasCategoryConsistency() {
        val emojiAtlas = EmojiAtlas()
        assertEquals(emojiAtlas.categories.size, emojiAtlas.categoryTabIcons.size)
        assertEquals(emojiAtlas.categories.size, emojiAtlas.categoryEmojiStrings.size)
        assertTrue(emojiAtlas.recentEmojis.isNotEmpty())
        assertTrue(emojiAtlas.recentEmojis.all { it.isNotBlank() })
    }
}
