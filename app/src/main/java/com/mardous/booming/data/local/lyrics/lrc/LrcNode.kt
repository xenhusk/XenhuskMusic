package com.mardous.booming.data.local.lyrics.lrc

import com.mardous.booming.data.model.lyrics.Lyrics
import com.mardous.booming.data.model.lyrics.LyricsActor

internal class LrcNode(
    val start: Long,
    val text: String,
    val bgText: String?,
    val rawLine: String?,
    actor: LyricsActor? = null
) {
    private val children = mutableListOf<LrcNode>()

    var end: Long = INVALID_DURATION
        private set

    var actor: LyricsActor? = actor
        private set

    fun setEnd(end: Long) {
        this.end = end
    }

    fun setActor(agent: LyricsActor?) {
        this.actor = agent
    }

    fun addChild(start: Long, text: String?, actor: LyricsActor?): Boolean {
        if (start > INVALID_DURATION && !text.isNullOrBlank()) {
            return children.add(LrcNode(
                start = start,
                text = text,
                bgText = null,
                rawLine = null,
                actor = actor
            ))
        }
        return false
    }

    fun toWord(startIndex: Int): Lyrics.Word {
        return Lyrics.Word(
            content = text,
            startMillis = start,
            startIndex = startIndex,
            endMillis = end,
            endIndex = startIndex + (text.length - 1),
            durationMillis = (end - start),
            actor = actor
        )
    }

    fun toLine(): Lyrics.Line? {
        if (start <= INVALID_DURATION && end <= INVALID_DURATION) {
            return null
        }
        return if (children.isNotEmpty()) {
            children.sortBy { it.start }
            for (i in 0 until children.lastIndex) {
                children[i].end = children[i + 1].start
            }
            children[children.lastIndex].end = end

            val words = mutableListOf<Lyrics.Word>()
            for (child in children) {
                val startIndex = words.sumOf { it.content.length }
                words.add(child.toWord(startIndex))
            }

            Lyrics.Line(
                startAt = start,
                end = end,
                durationMillis = (end - start),
                content = words.filterNot { it.isBackground }
                    .joinToString(separator = "") { it.content }.trim(),
                backgroundContent = words.filter { it.isBackground }
                    .joinToString(separator = "") { it.content }.trim(),
                rawContent = rawLine.orEmpty(),
                words = words,
                actor = actor
            )
        } else {
            Lyrics.Line(
                startAt = start,
                end = end,
                durationMillis = (end - start),
                content = text,
                backgroundContent = null,
                rawContent = rawLine.orEmpty(),
                words = emptyList(),
                actor = actor
            )
        }
    }

    companion object {
        const val INVALID_DURATION = -1L
    }
}