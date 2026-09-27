/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.lidesheng.hyperlyric.lyric.view

import com.lidesheng.hyperlyric.lyric.model.RichLyricLine
import com.lidesheng.hyperlyric.lyric.model.Song
import com.lidesheng.hyperlyric.lyric.model.interfaces.IRichLyricLine
import com.lidesheng.hyperlyric.lyric.model.lyricMetadataOf

internal class SongPreprocessor(private val placeholder: TitleSlot) {

    companion object {
        internal const val MIN_PLACEHOLDER_DURATION_MS = 3_000L

        private val LEADING_MUSIC_INFO_PATTERN = Regex(
            """^[\s\[\]【】()（）「」『』♪♫♬·•\-]*(?:(?:作词(?:人)?|作詞(?:者)?|作曲(?:者)?|词曲|詞曲|编曲|編曲|制作人|製作人|监制|監製|出品人|录音|錄音|混音|母带|母帶|和声|和聲|演唱|演奏|企划|企劃|统筹|統籌|词|詞|曲|lyricist|composer|arranger|producer)\s*[:：=／/]\s*|(?:lyrics|music|written|composed|arranged|produced)\s+by\b\s*)""",
            RegexOption.IGNORE_CASE
        )
    }

    fun prepare(song: Song): PreparedSongLyrics {
        val lyrics = timelineLyrics(song)
        val lines = mutableListOf<TimedLine>()
        var previous: TimedLine? = null
        lyrics.forEach { lyric ->
            val timedLine = TimedLine(lyric).also {
                it.previous = previous
                previous?.next = it
            }
            lines.add(timedLine)
            previous = timedLine
        }

        val firstSungLyric = song.lyrics.orEmpty().firstOrNull {
            hasRenderableContent(it) && !isLeadingMusicInfo(it)
        }
        val leadingPlaceholder = firstSungLyric
            ?.takeIf { it.begin >= MIN_PLACEHOLDER_DURATION_MS }
            ?.let { leadingPlaceholderLine(song, it) }
            ?.let(::TimedLine)

        return PreparedSongLyrics(
            lines = lines,
            leadingPlaceholder = leadingPlaceholder,
            hasRenderableLyrics = lines.isNotEmpty()
        )
    }

    /**
     * Builds a persistent placeholder for a metadata-only song. This is independent of the
     * timed prelude placeholder and does not make a lyric-less source pass the lyric gate.
     */
    internal fun noLyricsPlaceholder(song: Song): RichLyricLine? = when (placeholder) {
        TitleSlot.NONE -> null
        TitleSlot.COUNTDOWN -> countdownLine(Long.MAX_VALUE)
        TitleSlot.NAME_ARTIST,
        TitleSlot.NAME -> songTitle(song)?.let {
            titleLine(Long.MAX_VALUE, Long.MAX_VALUE, it)
        }
    }

    private fun timelineLyrics(song: Song): List<RichLyricLine> {
        return song.lyrics.orEmpty().filter(::hasRenderableContent)
    }

    private fun leadingPlaceholderLine(song: Song, firstLyric: RichLyricLine): RichLyricLine? {
        val end = firstLyric.begin
        return when (placeholder) {
            TitleSlot.NONE -> null
            TitleSlot.COUNTDOWN -> countdownLine(end)
            TitleSlot.NAME_ARTIST,
            TitleSlot.NAME -> songTitle(song)?.let {
                titleLine(end, end, it)
            }
        }
    }

    private fun isLeadingMusicInfo(line: RichLyricLine): Boolean {
        if (line.isTitleLine()) return true
        val text = line.text?.takeIf { it.isNotBlank() }
            ?: line.words?.joinToString("") { it.text.orEmpty() }
                ?.takeIf { it.isNotBlank() }
            ?: return false
        return LEADING_MUSIC_INFO_PATTERN.containsMatchIn(text.trim())
    }

    private fun hasRenderableContent(line: RichLyricLine): Boolean =
        !line.text.isNullOrBlank() || !line.words.isNullOrEmpty()

    private fun titleLine(end: Long, duration: Long, text: String) =
        RichLyricLine(end = end, duration = duration, text = text).apply {
            metadata = lyricMetadataOf(METADATA_TITLE_LINE to "true")
        }

    private fun countdownLine(end: Long) =
        RichLyricLine(end = end, duration = end).apply {
            metadata = lyricMetadataOf(
                METADATA_TITLE_LINE to "true",
                METADATA_COUNTDOWN_LINE to "true"
            )
        }

    private fun songTitle(song: Song): String? {
        val name = song.name
        val artist = song.artist
        return when (placeholder) {
            TitleSlot.NONE -> null
            TitleSlot.NAME_ARTIST -> when {
                !name.isNullOrBlank() && !artist.isNullOrBlank() -> "$name - $artist"
                !name.isNullOrBlank() -> name
                else -> null
            }

            TitleSlot.NAME -> name?.takeIf { it.isNotBlank() }
            TitleSlot.COUNTDOWN -> null
        }
    }
}

internal data class PreparedSongLyrics(
    val lines: List<TimedLine>,
    val leadingPlaceholder: TimedLine?,
    val hasRenderableLyrics: Boolean
)

internal class TimedLine(val line: IRichLyricLine) : IRichLyricLine by line {
    var previous: TimedLine? = null
    var next: TimedLine? = null
}
