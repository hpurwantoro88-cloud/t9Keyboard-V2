package com.opent9.keyboard.prediction

import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import com.opent9.keyboard.OpenT9InputMethodService
import com.opent9.keyboard.jni.NativeEngineBridge
import com.opent9.keyboard.ui.KeyType
import com.opent9.keyboard.ui.T9KeyboardView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PredictionContextHelperTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        NativeEngineBridge.resetUserDictionary()
    }

    @Test
    fun testExtractContextHandlesSentenceBoundariesAndTokens() {
        assertEquals(
            PredictionContextHelper.ContextInfo(null, hasTrailingSpace = false, isSentenceStart = true),
            PredictionContextHelper.extractFromText("")
        )
        assertEquals(
            PredictionContextHelper.ContextInfo("selamat", hasTrailingSpace = true, isSentenceStart = false),
            PredictionContextHelper.extractFromText("selamat ")
        )
        assertEquals(
            PredictionContextHelper.ContextInfo("I'm", hasTrailingSpace = true, isSentenceStart = false),
            PredictionContextHelper.extractFromText("Yes, I'm ")
        )
        assertEquals(
            PredictionContextHelper.ContextInfo("hello", hasTrailingSpace = true, isSentenceStart = false),
            PredictionContextHelper.extractFromText("hello,  ")
        )
        assertEquals(
            PredictionContextHelper.ContextInfo(null, hasTrailingSpace = true, isSentenceStart = true),
            PredictionContextHelper.extractFromText("Done. ")
        )
        assertEquals(
            PredictionContextHelper.ContextInfo(null, hasTrailingSpace = true, isSentenceStart = true),
            PredictionContextHelper.extractFromText("\"Halo.\" ")
        )
        assertEquals(
            PredictionContextHelper.ContextInfo("hello", hasTrailingSpace = true, isSentenceStart = false),
            PredictionContextHelper.extractFromText("He said \"hello\" ")
        )
        assertEquals(
            PredictionContextHelper.ContextInfo(null, hasTrailingSpace = false, isSentenceStart = false),
            PredictionContextHelper.extractFromText("selamat")
        )
        assertEquals(
            PredictionContextHelper.ContextInfo(null, hasTrailingSpace = true, isSentenceStart = false),
            PredictionContextHelper.extractFromText("2026 ")
        )
    }

    @Test
    fun testIdleNwpShowsSuggestionsAndSpaceDoesNotAutoCommitChip() {
        val service = Robolectric.buildService(OpenT9InputMethodService::class.java).create().get()
        val inputView = service.onCreateInputView() as T9KeyboardView

        val editText = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText("selamat ")
            setSelection(8)
        }
        val info = EditorInfo().apply { inputType = editText.inputType }
        val ic = editText.onCreateInputConnection(info)
        val icField = OpenT9InputMethodService::class.java.superclass.getDeclaredField("mInputConnection").apply {
            isAccessible = true
        }
        icField.set(service, ic)

        NativeEngineBridge.recordBigram("selamat", "pagi", 1000L)
        service.onStartInputView(info, false)

        assertTrue("Strip should enter idle NWP mode", inputView.isIdlePrediction)

        // Pressing Space while idle NWP is shown must insert a space, NOT commit "pagi"
        val spaceKey = inputView.keyAtlas.keys.first { it.type == KeyType.SPACE_0 }
        inputView.onKeyTapAction?.invoke(spaceKey, spaceKey.centerX, spaceKey.centerY)
        assertEquals("selamat  ", editText.text.toString())

        // Tapping the NWP chip inserts "pagi " cleanly
        inputView.onCandidateTapAction?.invoke(0)
        assertEquals("selamat  pagi ", editText.text.toString())
    }

    @Test
    fun testPasswordFieldSuppressesIdleNwp() {
        val service = Robolectric.buildService(OpenT9InputMethodService::class.java).create().get()
        val inputView = service.onCreateInputView() as T9KeyboardView

        val editText = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText("selamat ")
            setSelection(8)
        }
        val info = EditorInfo().apply { inputType = editText.inputType }
        val ic = editText.onCreateInputConnection(info)
        val icField = OpenT9InputMethodService::class.java.superclass.getDeclaredField("mInputConnection").apply {
            isAccessible = true
        }
        icField.set(service, ic)

        NativeEngineBridge.recordBigram("selamat", "pagi", 1000L)
        service.onStartInputView(info, false)

        assertFalse(inputView.isIdlePrediction)
        assertNull(NativeEngineBridge.getNextWordCandidates("").firstOrNull())
    }
}
