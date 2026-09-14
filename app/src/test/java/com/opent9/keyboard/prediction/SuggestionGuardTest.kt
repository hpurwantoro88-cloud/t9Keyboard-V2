package com.opent9.keyboard.prediction

import com.opent9.keyboard.jni.NativeEngineBridge
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SuggestionGuardTest {

    private lateinit var guard: SuggestionGuard

    @Before
    fun setUp() {
        NativeEngineBridge.resetUserDictionary()
        guard = SuggestionGuard()
    }

    @Test
    fun testEmptyStateReturnsEmpty() {
        assertTrue(guard.getGuardedCandidates(emptyList()).isEmpty())
        assertFalse(guard.isGuarded())
        assertEquals("", guard.lastValidTopWord)
    }

    @Test
    fun testOneDigitOvertypeHoldsBaseWord() {
        val validDigits = listOf(4, 6, 6, 3) // "good"
        val validCandidates = listOf("good", "home", "gone")

        guard.recordValidCandidates(validCandidates, validDigits)
        assertTrue(guard.isGuarded())
        assertEquals("good", guard.lastValidTopWord)

        // User typed 1 extra digit (typo or suffix key)
        val overtypeDigits = listOf(4, 6, 6, 3, 7)
        val guarded = guard.getGuardedCandidates(overtypeDigits)

        assertFalse(guarded.isEmpty())
        // 1-digit overtype must hold the base word as the top candidate
        assertEquals("good", guarded[0])
        // Extended phonotactic word must be second candidate
        assertEquals("goods", guarded[1])
        // Must preserve alternate candidates from valid state
        assertTrue(guarded.contains("home"))
        assertTrue(guarded.contains("gone"))
        // Must NOT contain literal digits in candidates
        assertTrue("Candidates must not contain any digits", guarded.none { c -> c.any { it.isDigit() } })
        // Top candidate must NOT be digits
        assertFalse(guarded[0].all { it.isDigit() })
    }

    @Test
    fun testTwoDigitsOvertypePrioritizesExtendedWord() {
        val validDigits = listOf(4, 6, 6, 3) // "good"
        val validCandidates = listOf("good", "home")

        guard.recordValidCandidates(validCandidates, validDigits)

        // User typed 2 extra digits (deliberately typing an out-of-vocab word)
        val overtypeDigits = listOf(4, 6, 6, 3, 7, 7)
        val guarded = guard.getGuardedCandidates(overtypeDigits)

        assertFalse(guarded.isEmpty())
        // 2-digit overtype must prioritize the extended word
        assertEquals("goodss", guarded[0])
        // Held word is retained as fallback
        assertEquals("good", guarded[1])
        // Must NOT contain literal digits in candidates
        assertTrue("Candidates must not contain any digits", guarded.none { c -> c.any { it.isDigit() } })
    }

    @Test
    fun testUnknownPrefixFromStartProjectsPhonotacticWord() {
        // No valid prefix was ever recorded
        val digits = listOf(7, 3, 8)
        val guarded = guard.getGuardedCandidates(digits)

        assertFalse(guarded.isEmpty())
        // Top candidate must be a projected word, NOT numbers
        val top = guarded[0]
        assertFalse("Top candidate must not be numbers", top.all { it.isDigit() })
        assertTrue("Top candidate must contain alphabetic characters", top.all { it.isLetter() })
        // Must NOT contain literal digits in candidates
        assertTrue("Candidates must not contain any digits", guarded.none { c -> c.any { it.isDigit() } })
    }

    @Test
    fun testResetClearsState() {
        guard.recordValidCandidates(listOf("hello"), listOf(4, 3, 5, 5, 6))
        assertTrue(guard.isGuarded())

        guard.reset()
        assertFalse(guard.isGuarded())
        assertEquals("", guard.lastValidTopWord)
    }

    @Test
    fun testPhonotacticTransitions() {
        // Test English common affixes / digraphs
        // "th": 't' + digit 4 -> 'h'
        assertEquals("th", guard.extendWordPhonotactically("t", listOf(4)))
        // "-ed": "play" + digit 3 + digit 3 -> "played"
        assertEquals("played", guard.extendWordPhonotactically("play", listOf(3, 3)))
        // "-er": "hunt" + digit 3 + digit 7 -> "hunter"
        assertEquals("hunter", guard.extendWordPhonotactically("hunt", listOf(3, 7)))
        // "-ly": "real" + digit 5 + digit 9 -> "really"
        assertEquals("really", guard.extendWordPhonotactically("real", listOf(5, 9)))
        // "-ing": "go" + digit 4 + digit 6 + digit 4 -> "going"
        assertEquals("going", guard.extendWordPhonotactically("go", listOf(4, 6, 4)))
    }

    @Test
    fun testIndonesianPhonotacticExtension() {
        val validDigits = listOf(7, 8, 7, 9, 2, 6, 8, 6) // "Purwanto"
        val validCandidates = listOf("Purwanto")
        guard.recordValidCandidates(validCandidates, validDigits)

        val fullDigits = listOf(7, 8, 7, 9, 2, 6, 8, 6, 7, 6)
        val candidates = guard.getGuardedCandidates(fullDigits, lang = "ID")

        assertTrue("Candidates must not be empty", candidates.isNotEmpty())
        assertEquals("Purwantoro", candidates[0])
        assertTrue("Must contain Purwantoso as alternate", candidates.contains("Purwantoso"))
        assertTrue("Must contain base Purwanto", candidates.contains("Purwanto"))
    }

    @Test
    fun testSuppressedCandidateFiltering() {
        NativeEngineBridge.resetUserDictionary()
        val validDigits = listOf(7, 8, 7, 9, 2, 6, 8, 6)
        guard.recordValidCandidates(listOf("Purwanto"), validDigits)

        // Delete "purwantoso"
        NativeEngineBridge.removeWord("purwantoso")
        assertTrue(NativeEngineBridge.isWordDeleted("purwantoso"))

        val fullDigits = listOf(7, 8, 7, 9, 2, 6, 8, 6, 7, 6)
        val candidatesEn = guard.getGuardedCandidates(fullDigits, lang = "EN")

        assertFalse("Purwantoso must be filtered out when deleted", candidatesEn.contains("Purwantoso"))
        assertEquals("Purwantoro", candidatesEn[0])
    }
}
