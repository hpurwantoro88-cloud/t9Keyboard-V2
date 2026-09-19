package com.opent9.keyboard

import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import com.opent9.keyboard.prediction.EnglishOrthography
import com.opent9.keyboard.prediction.IndonesianOrthography
import com.opent9.keyboard.prediction.SuggestionGuard
import com.opent9.keyboard.ui.FlickDirection
import com.opent9.keyboard.ui.KeyInfo
import com.opent9.keyboard.ui.KeyType
import com.opent9.keyboard.ui.T9KeyboardView
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ServiceController

@RunWith(RobolectricTestRunner::class)
class SimulatedSpeedTypingJvmTest {

    private lateinit var controller: ServiceController<OpenT9InputMethodService>
    private lateinit var service: OpenT9InputMethodService
    private lateinit var inputView: T9KeyboardView
    private lateinit var editText: EditText

    @Before
    fun setup() {
        controller = Robolectric.buildService(OpenT9InputMethodService::class.java)
        service = controller.create().get()
        inputView = service.onCreateInputView() as T9KeyboardView

        val context = ApplicationProvider.getApplicationContext<Context>()
        editText = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val info = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val ic = editText.onCreateInputConnection(info)
        val icField = OpenT9InputMethodService::class.java.superclass.getDeclaredField("mInputConnection").apply {
            isAccessible = true
        }
        icField.set(service, ic)

        service.onStartInputView(info, false)
        inputView.setT9Mode(true)
    }

    private fun charToT9(c: Char): Int {
        val lower = c.lowercaseChar()
        return when (lower) {
            'a', 'b', 'c' -> 2
            'd', 'e', 'f' -> 3
            'g', 'h', 'i' -> 4
            'j', 'k', 'l' -> 5
            'm', 'n', 'o' -> 6
            'p', 'q', 'r', 's' -> 7
            't', 'u', 'v' -> 8
            'w', 'x', 'y', 'z' -> 9
            else -> 0
        }
    }

    private fun typeWord(word: String, withCommit: Boolean = true, commitWithCandidateTap: Boolean = false) {
        val keySpace = inputView.keyAtlas.keys.first { it.type == KeyType.SPACE_0 }

        for (c in word) {
            val d = charToT9(c)
            if (d in 2..9) {
                val key = inputView.keyAtlas.keys.first { it.digitValue == d }
                inputView.onKeyTapAction?.invoke(key, key.centerX, key.centerY)
            }
        }

        if (withCommit) {
            if (commitWithCandidateTap) {
                inputView.onCandidateTapAction?.invoke(0)
            } else {
                inputView.onKeyTapAction?.invoke(keySpace, keySpace.centerX, keySpace.centerY)
            }
        }
    }

    @Test
    fun testJvmSimulatedSpeedTypingMemoryAndStability() {
        System.gc()
        Thread.sleep(50)
        val runtime = Runtime.getRuntime()
        val initialUsedMemory = runtime.totalMemory() - runtime.freeMemory()

        println("================================================================")
        println("JVM SPEED TYPING STRESS & RAM RETENTION TEST")
        println("================================================================")
        println("Initial JVM Used Memory: ${initialUsedMemory / 1024} KB")

        val englishSentences = listOf(
            "the quick brown fox jumps over the lazy dog",
            "artificial intelligence is transforming mobile communication",
            "please let me know if you need additional information tomorrow",
            "we are building high performance software with minimal memory footprint"
        )

        val indonesianSentences = listOf(
            "selamat pagi semoga hari ini menyenangkan dan membawa berkah",
            "terima kasih banyak atas bantuan dan kerja sama yang sangat baik",
            "pemerintah sedang menyiapkan kebijakan baru untuk masyarakat indonesia",
            "kemandirian bangsa sangat penting untuk kemajuan generasi penerus"
        )

        val keyLang = inputView.keyAtlas.keys[13]
        val keyDel = inputView.keyAtlas.keys.first { it.type == KeyType.DEL }

        var totalKeystrokes = 0
        val iterations = 50 // 50 full cycles = ~400 sentences, ~2,400 words, ~15,000 keystrokes

        val candidatesField = OpenT9InputMethodService::class.java.getDeclaredField("activeCandidates").apply {
            isAccessible = true
        }

        val tStart = System.currentTimeMillis()

        for (iter in 0 until iterations) {
            // 1. English round
            inputView.onKeyTapAction?.invoke(keyLang, keyLang.centerX, keyLang.centerY)
            for (sentence in englishSentences) {
                val words = sentence.split(" ")
                for (w in words) {
                    typeWord(w, withCommit = true, commitWithCandidateTap = (totalKeystrokes % 3 == 0))
                    totalKeystrokes += w.length + 1

                    // Verify candidates never contain raw digits
                    @Suppress("UNCHECKED_CAST")
                    val candidates = candidatesField.get(service) as List<String>
                    assertTrue("Candidates must never contain raw digits", candidates.none { cand -> cand.any { it.isDigit() } })
                }
            }

            // Simulate some backspaces and overtyping
            typeWord("longovertypedwordthatdoesnotexist", withCommit = false)
            totalKeystrokes += 33
            for (b in 0 until 10) {
                inputView.onKeyTapAction?.invoke(keyDel, keyDel.centerX, keyDel.centerY)
                totalKeystrokes++
            }
            // Commit remaining
            val keySpace = inputView.keyAtlas.keys.first { it.type == KeyType.SPACE_0 }
            inputView.onKeyTapAction?.invoke(keySpace, keySpace.centerX, keySpace.centerY)
            totalKeystrokes++

            // 2. Indonesian round
            inputView.setLanguage("ID")
            for (sentence in indonesianSentences) {
                val words = sentence.split(" ")
                for (w in words) {
                    typeWord(w, withCommit = true, commitWithCandidateTap = (totalKeystrokes % 4 == 0))
                    totalKeystrokes += w.length + 1
                }
            }

            // Periodically clear EditText to prevent Android text view buffer itself from dominating
            if (iter % 10 == 0) {
                editText.setText("")
                editText.setSelection(0)
            }
        }

        val tEnd = System.currentTimeMillis()
        val totalMs = tEnd - tStart

        System.gc()
        Thread.sleep(50)
        val finalUsedMemory = runtime.totalMemory() - runtime.freeMemory()
        val memoryDelta = finalUsedMemory - initialUsedMemory

        println("Simulation Results:")
        println("  Total Keystrokes:   $totalKeystrokes")
        println("  Total Duration:     $totalMs ms")
        println("  Average Rate:       ${(totalKeystrokes.toDouble() / (totalMs.toDouble() / 1000.0)).toInt()} keystrokes/second")
        println("  Initial Memory:     ${initialUsedMemory / 1024} KB")
        println("  Final Memory:       ${finalUsedMemory / 1024} KB")
        println("  Memory Delta:       ${memoryDelta / 1024} KB")

        // In a properly managed JVM without leaks, retained memory delta after GC should be well below 20MB
        assertTrue("Memory growth must not be runaway (delta was ${memoryDelta / 1024} KB)", memoryDelta < 25 * 1024 * 1024)
        println("================================================================")
        println("JVM SPEED TYPING TEST PASSED WITH NO RUNAWAY RAM!")
        println("================================================================")
    }

    @Test
    fun testSuggestionQualityEnglishAndIndonesian() {
        val guard = SuggestionGuard()

        // 1. English Orthography & Contractions check
        val testEnglishWords = listOf(
            "dont" to "don't",
            "cant" to "can't",
            "wont" to "won't",
            "didnt" to "didn't",
            "isnt" to "isn't",
            "arent" to "aren't",
            "im" to "I'm",
            "youre" to "you're",
            "theyre" to "they're",
            "weve" to "we've",
            "thats" to "that's"
        )

        println("\nTesting English Orthography Restorations:")
        for ((raw, expected) in testEnglishWords) {
            val processed = EnglishOrthography.processCandidates(listOf(raw), "EN")
            assertTrue("Expected '$raw' to become '$expected'", processed.contains(expected))
            println("  EN raw: '$raw' -> processed: ${processed[0]}")
        }

        // Test English Homograph Promotion (e.g. don't over foot, aren't over brent)
        val footDontList = listOf("foot", "dont")
        val promoted = EnglishOrthography.processCandidates(footDontList, "EN")
        assertEquals("don't must be promoted over foot", "don't", promoted[0])

        // 2. SuggestionGuard Phonotactic Fallback Check (Out-of-lexicon words)
        println("\nTesting SuggestionGuard Phonotactic Projections:")
        // Word "purwantoro" (digits: 7, 8, 7, 9, 2, 6, 8, 6, 7, 6)
        val purwantoroDigits = listOf(7, 8, 7, 9, 2, 6, 8, 6, 7, 6)
        val idProjected = guard.projectWordFromDigits(purwantoroDigits, alt = false, lang = "ID")
        println("  ID Projected for 7879268676: '$idProjected'")
        assertTrue("Projected word must contain only letters", idProjected.all { it.isLetter() })
        assertEquals("Projected length must match digit length", purwantoroDigits.size, idProjected.length)

        // Word "helloworld" (digits: 4, 3, 5, 5, 6, 9, 6, 7, 5, 3)
        val enDigits = listOf(4, 3, 5, 5, 6, 9, 6, 7, 5, 3)
        val enProjected = guard.projectWordFromDigits(enDigits, alt = false, lang = "EN")
        println("  EN Projected for 4355696753: '$enProjected'")
        assertTrue("Projected word must contain only letters", enProjected.all { it.isLetter() })
        assertEquals("Projected length must match digit length", enDigits.size, enProjected.length)
    }

    @Test
    fun testIndonesianSuffixCompoundingEndToEnd() {
        inputView.setLanguage("ID")
        editText.setText("")
        editText.setSelection(0)

        // Suffix synthesis for 14 digits of "kemandiriannya"
        val kemandiriannyaDigits = listOf(5, 3, 6, 2, 6, 3, 4, 7, 4, 2, 6, 6, 9, 2)
        val raw = listOf("kemandirian", "kemandirianku")
        val processed = IndonesianOrthography.processCandidates(raw, kemandiriannyaDigits)
        assertEquals("kemandiriannya must be top candidate", "kemandiriannya", processed[0])

        val candidatesField = OpenT9InputMethodService::class.java.getDeclaredField("activeCandidates").apply {
            isAccessible = true
        }
        candidatesField.set(service, processed)

        val hasActiveField = OpenT9InputMethodService::class.java.getDeclaredField("hasActiveComposing").apply {
            isAccessible = true
        }
        hasActiveField.set(service, true)

        // Space commits synthesized word
        val keySpace = inputView.keyAtlas.keys.first { it.type == KeyType.SPACE_0 }
        inputView.onKeyTapAction?.invoke(keySpace, keySpace.centerX, keySpace.centerY)

        val committedText = editText.text.toString()
        assertEquals("Kemandiriannya ", committedText)
    }
}

