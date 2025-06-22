package com.mardous.booming.lyrics

import androidx.compose.runtime.Immutable

@Immutable
data class Lyrics(
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationMillis: Long?,
    val lines: List<Line>,
) {
    val hasContent = lines.isNotEmpty()
    val optimalDurationMillis = optimalDurationMillis()

    val plainText: String
        get() = lines.joinToString("\n") { it.content }

    val rawText: String
        get() = lines.joinToString("\n") { it.rawContent }

    init {
        for (line in lines) {
            require(line.startAt >= 0) { "startAt in the LyricsLine must >= 0" }
            require(line.durationMillis >= 0) { "durationMillis in the LyricsLine >= 0" }
        }
    }

    private fun optimalDurationMillis(): Long {
        return durationMillis ?: lines.maxOfOrNull { it.startAt + it.durationMillis } ?: 0L
    }

    @Immutable
    data class Line(
        val startAt: Long,
        val durationMillis: Long,
        val content: String,
        val rawContent: String,
        val words: List<Word>
    ) {
        val isWordByWord: Boolean = words.isNotEmpty()
    }

    @Immutable
    data class Word(val content: String, val startAt: Long)
}