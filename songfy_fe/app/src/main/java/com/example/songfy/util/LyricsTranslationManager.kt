package com.lagu.songfy.util

import android.content.Context
import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.lagu.songfy.presentation.player.LyricLine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsTranslationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val memoryCache = ConcurrentHashMap<String, Map<Int, String>>()
    private val languageCache = ConcurrentHashMap<String, String>()

    fun getCachedLanguage(videoId: String): String? {
        if (videoId.isBlank()) return null
        return languageCache[videoId] ?: readDiskLanguage(videoId)?.also {
            languageCache[videoId] = it
        }
    }

    fun getCachedTranslation(videoId: String): Map<Int, String>? {
        if (videoId.isBlank()) return null
        return memoryCache[videoId] ?: readDiskCache(videoId).takeIf { it.isNotEmpty() }?.also {
            memoryCache[videoId] = it
        }
    }

    private val languageIdentifier by lazy {
        LanguageIdentification.getClient(
            LanguageIdentificationOptions.Builder()
                .setConfidenceThreshold(0.5f)
                .build()
        )
    }

    /**
     * Deteksi bahasa dari gabungan teks lirik.
     * Mengembalikan kode bahasa (contoh: "id", "en", "ja", "ko", "es") atau null jika tidak terdeteksi.
     */
    suspend fun detectLanguage(
        lyrics: List<LyricLine>,
        songTitle: String = "",
        artistName: String = "",
        videoId: String = ""
    ): String? = withContext(Dispatchers.Default) {
        if (videoId.isNotBlank()) {
            val cached = getCachedLanguage(videoId)
            if (cached != null) return@withContext cached
        }
        if (lyrics.isEmpty()) return@withContext null

        // Gabungkan seluruh baris lirik (bukan judul)
        val validLines = lyrics
            .filter { !it.isInstrumental && it.text.isNotBlank() && it.text != "♪" && it.text != "•••" }
            .map { it.text }
        if (validLines.isEmpty()) return@withContext null

        val fullText = validLines.joinToString("\n")

        // 1. Cek aksara Hangul (Korea)
        val hangulCount = fullText.count { it in '\uAC00'..'\uD7AF' }
        if (hangulCount >= 3) {
            if (videoId.isNotBlank()) {
                languageCache[videoId] = "ko"
                writeDiskLanguage(videoId, "ko")
            }
            return@withContext "ko"
        }

        // 2. Cek aksara Hiragana & Katakana (Jepang)
        val kanaCount = fullText.count { it in '\u3040'..'\u30FF' }
        if (kanaCount >= 3) {
            if (videoId.isNotBlank()) {
                languageCache[videoId] = "ja"
                writeDiskLanguage(videoId, "ja")
            }
            return@withContext "ja"
        }

        // 3. Cek Kanji CJK
        val cjkCount = fullText.count { it in '\u4E00'..'\u9FFF' }
        if (cjkCount >= 5) {
            if (videoId.isNotBlank()) {
                languageCache[videoId] = "ja"
                writeDiskLanguage(videoId, "ja")
            }
            return@withContext "ja"
        }

        // 4. Deteksi bahasa via Google ML Kit Language Identification
        val sampleText = fullText.take(1500)
        val detected = try {
            languageIdentifier.identifyLanguage(sampleText).await()
        } catch (e: Exception) {
            Log.w("LyricsTranslation", "Language identification failed: ${e.message}")
            "und"
        }

        val normalized = when {
            detected.equals("und", ignoreCase = true) -> null
            detected.startsWith("ja", ignoreCase = true) -> "ja"
            detected.startsWith("ko", ignoreCase = true) -> "ko"
            detected.startsWith("zh", ignoreCase = true) -> "zh"
            else -> detected.lowercase()
        }

        if (normalized != null) {
            if (videoId.isNotBlank()) {
                languageCache[videoId] = normalized
                writeDiskLanguage(videoId, normalized)
            }
            return@withContext normalized
        }

        // 5. Fallback Romaji untuk J-Pop / K-Pop:
        // Jika ML Kit mengembalikan "und", cek metadata judul, artis, atau kata serapan romaji umum.
        val metadataCombined = "$songTitle $artistName $fullText".lowercase()
        val hasAsianMetadata = songTitle.any { it in '\u3040'..'\u9FFF' || it in '\uAC00'..'\uD7AF' } ||
                artistName.any { it in '\u3040'..'\u9FFF' || it in '\uAC00'..'\uD7AF' }
        val romajiKeywords = listOf("watashi", "anata", "kono", "kore", "sore", "shinu", "sando", "mou", "saranghae", "neoreul", "haru", "geunyang")
        val hasRomajiKeywords = romajiKeywords.any { metadataCombined.contains(it) }

        if (hasAsianMetadata || hasRomajiKeywords) {
            val fallbackLang = if (metadataCombined.contains("saranghae") || metadataCombined.contains("neoreul")) "ko" else "ja"
            if (videoId.isNotBlank()) {
                languageCache[videoId] = fallbackLang
                writeDiskLanguage(videoId, fallbackLang)
            }
            return@withContext fallbackLang
        }

        null
    }

    /**
     * Terjemahkan lirik ke Bahasa Indonesia (on-device ML Kit).
     * Hasil disimpan di in-memory cache dan file cache per videoId.
     */
    suspend fun translateLyrics(
        videoId: String,
        sourceLangCode: String,
        lyrics: List<LyricLine>
    ): Map<Int, String> = withContext(Dispatchers.IO) {
        // Cek in-memory cache
        memoryCache[videoId]?.let { return@withContext it }

        // Cek disk cache
        val diskCached = readDiskCache(videoId)
        if (diskCached.isNotEmpty()) {
            memoryCache[videoId] = diskCached
            return@withContext diskCached
        }

        val sourceLang = mapToMlKitLanguage(sourceLangCode)
        val targetLang = TranslateLanguage.INDONESIAN

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLang)
            .setTargetLanguage(targetLang)
            .build()
        val translator = Translation.getClient(options)

        try {
            // Unduh model offline jika belum ada
            val conditions = DownloadConditions.Builder().build()
            translator.downloadModelIfNeeded(conditions).await()

            val resultMap = mutableMapOf<Int, String>()
            for ((index, line) in lyrics.withIndex()) {
                val rawText = line.text.trim()
                if (line.isInstrumental || rawText.isBlank() || rawText == "♪" || rawText == "•••") {
                    continue
                }
                try {
                    val translated = translator.translate(rawText).await()
                    if (translated.isNotBlank() && !translated.equals(rawText, ignoreCase = true)) {
                        resultMap[index] = translated
                    } else if (translated.isNotBlank()) {
                        resultMap[index] = translated
                    }
                } catch (e: Exception) {
                    Log.d("LyricsTranslation", "Line translation skipped: ${e.message}")
                }
            }

            if (resultMap.isNotEmpty()) {
                memoryCache[videoId] = resultMap
                writeDiskCache(videoId, resultMap)
            }
            resultMap
        } catch (e: Exception) {
            Log.e("LyricsTranslation", "Translation failed: ${e.message}", e)
            emptyMap()
        } finally {
            translator.close()
        }
    }

    private fun mapToMlKitLanguage(code: String): String {
        return when (code.lowercase().take(2)) {
            "ja" -> TranslateLanguage.JAPANESE
            "ko" -> TranslateLanguage.KOREAN
            "es" -> TranslateLanguage.SPANISH
            "fr" -> TranslateLanguage.FRENCH
            "de" -> TranslateLanguage.GERMAN
            "zh" -> TranslateLanguage.CHINESE
            "ar" -> TranslateLanguage.ARABIC
            "hi" -> TranslateLanguage.HINDI
            "th" -> TranslateLanguage.THAI
            "ru" -> TranslateLanguage.RUSSIAN
            "it" -> TranslateLanguage.ITALIAN
            "pt" -> TranslateLanguage.PORTUGUESE
            else -> TranslateLanguage.fromLanguageTag(code) ?: TranslateLanguage.JAPANESE
        }
    }

    private fun readDiskCache(videoId: String): Map<Int, String> {
        return try {
            val file = File(context.cacheDir, "lyrics_trans_$videoId.json")
            if (!file.exists()) return emptyMap()
            val json = JSONObject(file.readText())
            val map = mutableMapOf<Int, String>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k.toInt()] = json.getString(k)
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun writeDiskCache(videoId: String, data: Map<Int, String>) {
        try {
            val file = File(context.cacheDir, "lyrics_trans_$videoId.json")
            val json = JSONObject()
            for ((k, v) in data) {
                json.put(k.toString(), v)
            }
            file.writeText(json.toString())
        } catch (_: Exception) {}
    }

    private fun readDiskLanguage(videoId: String): String? {
        return try {
            val file = File(context.cacheDir, "lyrics_lang_$videoId.txt")
            if (file.exists()) file.readText().trim().ifBlank { null } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun writeDiskLanguage(videoId: String, lang: String) {
        try {
            val file = File(context.cacheDir, "lyrics_lang_$videoId.txt")
            file.writeText(lang)
        } catch (_: Exception) {}
    }
}
