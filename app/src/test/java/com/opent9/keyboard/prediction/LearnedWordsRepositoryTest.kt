package com.opent9.keyboard.prediction

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.opent9.keyboard.jni.NativeEngineBridge
import com.opent9.keyboard.settings.SettingsObserver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LearnedWordsRepositoryTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        NativeEngineBridge.resetUserDictionary()
    }

    @Test
    fun testLegacyLastPickedPrefsAreClearedOnInitAndReset() {
        val legacyPrefs = context.getSharedPreferences("opent9_last_picked", Context.MODE_PRIVATE)
        legacyPrefs.edit().putString("digits_EN_4663", "hoof").commit()
        assertFalse(legacyPrefs.all.isEmpty())

        val repo = LearnedWordsRepository(context)
        assertTrue("Legacy last-picked overrides must be cleared on init", legacyPrefs.all.isEmpty())

        repo.recordWordUsage("home", isIncognito = false)
        assertTrue("recordWordUsage must not write to legacy SharedPreferences", legacyPrefs.all.isEmpty())
        assertEquals(1, NativeEngineBridge.getWordUsageCount("home"))

        repo.clearAll()
        assertEquals(0, NativeEngineBridge.getWordUsageCount("home"))
        assertTrue(legacyPrefs.all.isEmpty())
    }

    @Test
    fun testIncognitoAndNumericWordsAreNotRecorded() {
        val repo = LearnedWordsRepository(context)
        repo.recordWordUsage("secret", isIncognito = true)
        repo.recordWordUsage("12345", isIncognito = false)

        assertEquals(0, NativeEngineBridge.getWordUsageCount("secret"))
        assertEquals(0, NativeEngineBridge.getWordUsageCount("12345"))
    }

    @Test
    fun testCandidateResolverOnlyUsesExplicitPreferredWord() {
        val guard = SuggestionGuard()
        guard.recordValidCandidates(listOf("good", "home", "gone"), listOf(4, 6, 6, 3))

        val defaultResolved = CandidateResolver.resolveCandidates(
            currentComposingDigits = listOf(4, 6, 6, 3),
            suggestionGuard = guard
        )
        assertEquals("good", defaultResolved[0])

        val restoredResolved = CandidateResolver.resolveCandidates(
            currentComposingDigits = listOf(4, 6, 6, 3),
            suggestionGuard = guard,
            preferredWord = "home"
        )
        assertEquals("home", restoredResolved[0])
    }

    @Test
    fun testSettingsObserverSigmaScalesWithDensity() {
        val observer = SettingsObserver(context)
        val density = context.resources.displayMetrics.density.coerceAtLeast(1.0f)
        assertEquals(SettingsObserver.DEFAULT_TOUCH_SIGMA_DP * density, observer.getTouchVarianceSigmaPx(), 0.01f)
    }
}
