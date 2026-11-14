/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * LyricsProviders.kt — model lirik tersinkron (SyncLine, SyncWord), parser LRC, dan ekstensi converter.
 */

package com.metrolist.music.lyrics

// ───────────────────────────────────────────────────────────────────────────────
// Model
// ───────────────────────────────────────────────────────────────────────────────

data class SyncWord(val text: String, val startMs: Long, val endMs: Long)

data class SyncLine(
    val startMs: Long,                       // -1 = lirik polos tanpa timing
    val endMs: Long,
    val text: String,                        // kosong = jeda instrumental
    val words: List<SyncWord> = emptyList(), // kosong = tingkat baris
    val translation: String? = null,
) {
    val isWordSynced: Boolean get() = words.isNotEmpty()
}

// ───────────────────────────────────────────────────────────────────────────────
// Converters to / from Metrolist's LyricsEntry
// ───────────────────────────────────────────────────────────────────────────────

fun LyricsEntry.toSyncLine(nextStartMs: Long? = null): SyncLine {
    val end = nextStartMs ?: (time + 4000L)
    val syncWords = words?.map { w ->
        val s = (w.startTime * 1000).toLong()
        val e = (w.endTime * 1000).toLong()
        SyncWord(text = w.text, startMs = s, endMs = maxOf(s, e))
    } ?: emptyList()
    return SyncLine(
        startMs = time,
        endMs = end,
        text = text,
        words = syncWords,
        translation = translatedTextFlow.value,
    )
}

// ───────────────────────────────────────────────────────────────────────────────
// Parser LRC (baris + kata)
// ───────────────────────────────────────────────────────────────────────────────

object LrcParser {
    private val lineTag = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val wordTag = Regex("""<(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?>""")
    private val offsetTag = Regex("""^\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)

    private fun toMs(m: MatchResult): Long {
        val (min, sec, frac) = m.destructured
        val ms = if (frac.isEmpty()) 0L else frac.padEnd(3, '0').take(3).toLong()
        return min.toLong() * 60_000 + sec.toLong() * 1_000 + ms
    }

    fun parse(raw: String, lastLineMs: Long = 5_000): List<SyncLine> {
        var offset = 0L
        val rows = ArrayList<Pair<Long, String>>()

        for (row in raw.lineSequence()) {
            val trimmed = row.trim()
            val off = offsetTag.find(trimmed)
            if (off != null) {
                offset = off.groupValues[1].toLong()
                continue
            }

            // satu baris boleh punya beberapa timestamp: [00:12.00][01:30.00]teks
            var pos = 0
            val starts = ArrayList<Long>()
            while (true) {
                val m = lineTag.matchAt(trimmed, pos) ?: break
                starts += toMs(m)
                pos = m.range.last + 1
            }
            if (starts.isEmpty()) continue

            val body = trimmed.substring(pos).trim()
            starts.forEach { rows += (it - offset).coerceAtLeast(0L) to body }
        }

        rows.sortBy { it.first }
        return rows.mapIndexed { i, (start, body) ->
            val end = rows.getOrNull(i + 1)?.first ?: (start + lastLineMs)
            buildLine(start, maxOf(start, end), body, offset)
        }
    }

    private fun buildLine(start: Long, end: Long, body: String, offset: Long): SyncLine {
        val tags = wordTag.findAll(body).toList()
        if (tags.isEmpty()) return SyncLine(start, end, body)

        val words = ArrayList<SyncWord>()
        tags.forEachIndexed { i, t ->
            val next = tags.getOrNull(i + 1)
            val text = body.substring(t.range.last + 1, next?.range?.first ?: body.length).trim()
            if (text.isEmpty()) return@forEachIndexed
            val s = (toMs(t) - offset).coerceAtLeast(0L)
            val e = next?.let { (toMs(it) - offset).coerceAtLeast(0L) } ?: end
            words += SyncWord(text, s, maxOf(s, e))
        }
        return SyncLine(start, end, words.joinToString(" ") { it.text }, words)
    }
}
