package com.mardous.booming.data.local.lyrics.lrc

import android.util.Log
import com.mardous.booming.data.LyricsParser
import com.mardous.booming.data.model.lyrics.Lyrics
import com.mardous.booming.data.model.lyrics.LyricsActor
import com.mardous.booming.data.model.lyrics.LyricsFile
import java.io.IOException
import java.io.Reader
import java.util.Locale

class LrcLyricsParser : LyricsParser {

    override fun handles(file: LyricsFile): Boolean {
        return file.format == LyricsFile.Format.LRC
    }

    override fun handles(reader: Reader): Boolean {
        val content = reader.buffered().use { it.readText() }
        return content
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .any { line ->
                if (ATTRIBUTE_PATTERN.matches(line)) {
                    false
                } else {
                    val hasTime = LINE_TIME_PATTERN.containsMatchIn(line)
                    val hasContent = LINE_PATTERN.matchEntire(line)?.groupValues
                        ?.getOrNull(2)
                        ?.isNotBlank() == true

                    hasTime && hasContent
                }
            }
    }

    override fun parse(reader: Reader, trackLength: Long): Lyrics? {
        val attributes = hashMapOf<String, String>()
        val rawLines = mutableListOf<LrcNode>()
        try {
            reader.buffered().use { br ->
                while (true) {
                    val line = br.readLine() ?: break
                    if (line.isBlank()) continue

                    val attrMatcher = ATTRIBUTE_PATTERN.find(line)
                    if (attrMatcher != null) {
                        val attr = attrMatcher.groupValues[1].lowercase(Locale.getDefault()).trim()
                        val value = attrMatcher.groupValues[2].lowercase(Locale.getDefault())
                            .trim()
                            .takeUnless { it.isEmpty() } ?: continue

                        attributes[attr] = value
                    } else {
                        val lineResult = LINE_PATTERN.find(line)
                        if (lineResult != null) {
                            val time = lineResult.groupValues[1].trim()
                                .takeUnless { it.isEmpty() } ?: continue
                            val text = lineResult.groupValues[2].trim()
                            val bgText = lineResult.groupValues[3]
                                .takeIf { it.isNotEmpty() }

                            val timeResult = LINE_TIME_PATTERN.find(time)
                            if (timeResult != null) {
                                val timeMs = parseTime(timeResult)
                                if (timeMs > LrcNode.INVALID_DURATION) {
                                    rawLines.add(LrcNode(timeMs, text, bgText, line))
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return parse(attributes, rawLines, trackLength)
    }

    private fun parse(
        attributes: Map<String, String>,
        rawLines: List<LrcNode>,
        trackLength: Long
    ): Lyrics? {
        val lines = mutableListOf<Lyrics.Line?>()
        val length = attributes["length"]
            ?.let { parseTime(it) }
            ?.takeIf { it > LrcNode.INVALID_DURATION }
            ?: trackLength

        try {
            for (i in 0 until rawLines.size) {
                val entry = rawLines[i]

                val nextEntry = rawLines.getOrNull(i + 1)
                entry.end = nextEntry?.start ?: length

                if (entry.text.isNullOrBlank()) {
                    // we still allow empty lines
                    lines.add(entry.toLine())
                } else {
                    val previousEntry = rawLines.getOrNull(i - 1)
                    if (previousEntry != null && previousEntry.start == entry.start) {
                        addChildren(entry, previousEntry.actor)
                        previousEntry.setTranslation(entry)
                        lines[i - 1] = previousEntry.toLine()
                    } else {
                        addChildren(entry, null)
                        lines.add(entry.toLine())
                    }
                }
            }

            val linesWithOffset = lines.filterNotNull()
                .distinctBy { it.id }
                .toMutableList().apply {
                    sortBy { it.startAt }
                }

            if (linesWithOffset.isNotEmpty()) {
                val firstLine = linesWithOffset.first()
                if (firstLine.startAt > Lyrics.MIN_OFFSET_TIME) {
                    linesWithOffset.add(0,
                        Lyrics.Line(
                            startAt = 0,
                            end = firstLine.startAt,
                            content = Lyrics.EmptyContent,
                            translation = null,
                            actor = firstLine.actor
                        )
                    )
                }
            }

            return Lyrics(
                title = attributes["ti"],
                artist = attributes["ar"],
                album = attributes["al"],
                durationMillis = length,
                lines = linesWithOffset
            )
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }

    private fun addChildren(entry: LrcNode, actor: LyricsActor?) {
        check(!entry.text.isNullOrBlank())

        val matchResult = LINE_ACTOR_PATTERN.find(entry.text)
        entry.actor = actor ?: LyricsActor.getActorFromValue(matchResult?.groupValues?.get(1))

        val text = matchResult?.groupValues?.get(2) ?: entry.text
        LINE_WORD_PATTERN.findAll(text).forEach { match ->
            entry.addChild(
                start = parseTime(match),
                text = match.groupValues.getOrNull(3),
                actor = entry.actor
            )
        }

        entry.bgText?.let {
            LINE_WORD_PATTERN.findAll(it).forEach { match ->
                entry.addChild(
                    start = parseTime(match),
                    text = match.groupValues.getOrNull(3),
                    actor = entry.actor?.asBackground(true)
                )
            }
        }
    }

    private fun parseTime(str: String): Long {
        val result = TIME_PATTERN.find(str)
        if (result != null) {
            return parseTime(result)
        }
        return LrcNode.INVALID_DURATION
    }

    private fun parseTime(result: MatchResult): Long {
        try {
            val m = result.groupValues.getOrNull(1)?.toInt()
            val s = result.groupValues.getOrNull(2)?.toFloat()
            return if (m != null && s != null) {
                (s * LRC_SECONDS_TO_MS_MULTIPLIER).toLong() + m * LRC_MINUTES_TO_MS_MULTIPLIER
            } else LrcNode.INVALID_DURATION
        } catch (e: Exception) {
            Log.d("LrcLyricsParser", "LRC timestamp format is incorrect: ${result.value}", e)
        }
        return LrcNode.INVALID_DURATION
    }

    companion object {
        private const val LRC_SECONDS_TO_MS_MULTIPLIER = 1000f
        private const val LRC_MINUTES_TO_MS_MULTIPLIER = 60 * 1000

        private val TIME_PATTERN = Regex("(\\d+):(\\d{2}(?:\\.\\d+)?)")
        private val LINE_PATTERN = Regex("((?:\\[.*?])+)(.*?)(?:\\[bg:(.*?)])?$")
        private val LINE_TIME_PATTERN = Regex("\\[${TIME_PATTERN.pattern}]")
        private val LINE_ACTOR_PATTERN = Regex("^([vV]\\d+|D|M|F)\\s*:\\s*(.*)")
        private val LINE_WORD_PATTERN = Regex("<${TIME_PATTERN.pattern}>([^<]*)")
        private val ATTRIBUTE_PATTERN = Regex("\\[(offset|ti|ar|al|length|by):(.+)]", RegexOption.IGNORE_CASE)
    }
}