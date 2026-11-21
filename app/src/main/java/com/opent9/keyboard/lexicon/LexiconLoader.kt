package com.opent9.keyboard.lexicon

import android.content.res.AssetManager
import android.os.ParcelFileDescriptor
import android.util.Log
import com.opent9.keyboard.jni.NativeEngineBridge
import java.io.File

class LexiconLoader(
    private val assetManager: AssetManager,
    private val filesDir: File,
    private val appVersionCode: Long = -1L
) {

    companion object {
        private const val TAG = "OpenT9LexiconLoader"
        private const val VERSION_FILE = "lexicon_cache_version.txt"
    }

    private val isCacheStale: Boolean by lazy {
        if (appVersionCode <= 0L) return@lazy false
        val vf = File(filesDir, VERSION_FILE)
        val cached = try {
            if (vf.exists()) vf.readText().trim().toLongOrNull() else null
        } catch (_: Exception) {
            null
        }
        cached != appVersionCode
    }

    fun loadLexicons(startupLang: String) {
        loadLexiconForLanguage("EN", "dictionaries/en_lexicon.dawg")
        loadLexiconForLanguage("ID", "dictionaries/id_lexicon.dawg")
        loadNgramForLanguage("EN", "dictionaries/en_bigrams.bin")
        loadNgramForLanguage("ID", "dictionaries/id_bigrams.bin")
        if (appVersionCode > 0L && isCacheStale) {
            try {
                File(filesDir, VERSION_FILE).writeText(appVersionCode.toString())
            } catch (_: Exception) {}
        }
        NativeEngineBridge.switchLanguage(startupLang)
    }

    fun loadLexiconForLanguage(langCode: String, assetPath: String): Boolean {
        return loadAssetViaMmap(langCode, assetPath) { code, fd, offset, length ->
            NativeEngineBridge.loadLexiconFd(code, fd, offset, length)
        }
    }

    fun loadNgramForLanguage(langCode: String, assetPath: String): Boolean {
        return loadAssetViaMmap(langCode, assetPath) { code, fd, offset, length ->
            NativeEngineBridge.loadNgramFd(code, fd, offset, length)
        }
    }

    private inline fun loadAssetViaMmap(
        langCode: String,
        assetPath: String,
        crossinline nativeLoader: (String, Int, Long, Long) -> Boolean
    ): Boolean {
        var loaded = false
        try {
            assetManager.openFd(assetPath).use { afd ->
                loaded = nativeLoader(
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
                if (!outFile.exists() || outFile.length() == 0L || isCacheStale) {
                    outFile.parentFile?.mkdirs()
                    val tmpFile = File(filesDir, "${outFile.name}.tmp")
                    assetManager.open(assetPath).use { input ->
                        tmpFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (!tmpFile.renameTo(outFile)) {
                        tmpFile.copyTo(outFile, overwrite = true)
                        tmpFile.delete()
                    }
                }
                ParcelFileDescriptor.open(outFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    loaded = nativeLoader(
                        langCode,
                        pfd.fd,
                        0L,
                        outFile.length()
                    )
                }
                Log.i(TAG, "Loaded $langCode asset via fallback file ($outFile): $loaded")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load $langCode asset via fallback: ${e.message}", e)
            }
        }
        return loaded
    }
}
