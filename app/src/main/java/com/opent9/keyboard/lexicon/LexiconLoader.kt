package com.opent9.keyboard.lexicon

import android.content.res.AssetManager
import android.os.ParcelFileDescriptor
import android.util.Log
import com.opent9.keyboard.jni.NativeEngineBridge
import java.io.File

class LexiconLoader(
    private val assetManager: AssetManager,
    private val filesDir: File
) {

    companion object {
        private const val TAG = "OpenT9LexiconLoader"
    }

    fun loadLexicons(startupLang: String) {
        loadLexiconForLanguage("EN", "dictionaries/en_lexicon.dawg")
        loadLexiconForLanguage("ID", "dictionaries/id_lexicon.dawg")
        NativeEngineBridge.switchLanguage(startupLang)
    }

    fun loadLexiconForLanguage(langCode: String, assetPath: String): Boolean {
        var loaded = false
        try {
            assetManager.openFd(assetPath).use { afd ->
                loaded = NativeEngineBridge.loadLexiconFd(
                    langCode,
                    afd.parcelFileDescriptor.fd,
                    afd.startOffset,
                    afd.length
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct asset openFd failed for $assetPath: ${e.message}")
        }

        if (!loaded) {
            try {
                val outFile = File(filesDir, assetPath.substringAfterLast('/'))
                if (!outFile.exists() || outFile.length() == 0L) {
                    outFile.parentFile?.mkdirs()
                    assetManager.open(assetPath).use { input ->
                        outFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                ParcelFileDescriptor.open(outFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    loaded = NativeEngineBridge.loadLexiconFd(
                        langCode,
                        pfd.fd,
                        0L,
                        outFile.length()
                    )
                }
                Log.i(TAG, "Loaded $langCode lexicon via fallback file ($outFile): $loaded")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load $langCode lexicon via fallback: ${e.message}", e)
            }
        }
        return loaded
    }
}
