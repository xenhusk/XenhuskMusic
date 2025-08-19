package com.mardous.booming.lyrics.parser

import com.mardous.booming.lyrics.Lyrics

internal class LrcNode(val start: Long, val text: String) {
    private val children = mutableListOf<LrcNode>()

    var end: Long = INVALID_DURATION
        private set

    var actor: String? = null
        private set

    fun setEnd(end: Long) {
        this.end = end
    }

    fun setActor(actor: String?) {
        this.actor = actor
    }

    fun addChildren(start: Long, text: String?): Boolean {
        if (start > INVALID_DURATION && !text.isNullOrBlank()) {
            return children.add(LrcNode(start, text))
        }
        return false
    }

    fun toWord(): Lyrics.Word {
        return Lyrics.Word(text, start, end, (end - start))
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

            val words = children.map { it.toWord() }

            Lyrics.Line(
                startAt = start,
                end = end,
                durationMillis = (end - start),
                content = words.joinToString(separator = "") { it.content }.trim(),
                rawContent = text,
                words = words,
                actor = actor
            )
        } else {
            Lyrics.Line(
                startAt = start,
                end = end,
                durationMillis = (end - start),
                content = text,
                rawContent = text,
                words = emptyList(),
                actor = actor
            )
        }
    }

    companion object {
        const val INVALID_DURATION = -1L
    }
}