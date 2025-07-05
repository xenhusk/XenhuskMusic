/*
 * Copyright (c) 2024 Christians Martínez Alvarado
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.mardous.booming.service.queue

import android.content.Context
import com.mardous.booming.model.Song
import com.mardous.booming.model.SongProvider
import com.mardous.booming.providers.databases.PlaybackQueueStore
import com.mardous.booming.service.playback.Playback
import com.mardous.booming.util.Preferences
import com.mardous.booming.util.sort.SortOrder
import com.mardous.booming.util.sort.sortedSongs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext

private typealias MutablePlayQueue = MutableList<QueueSong>

class QueueManager(private val context: Context) {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val shuffleManager = ShuffleManager()
    private val defaultShuffleMode: Playback.ShuffleMode
        get() = if (Preferences.rememberShuffleMode) {
            Playback.ShuffleMode.On
        } else {
            Playback.ShuffleMode.Off
        }

    private val lastUpcomingPosition: Int
        get() = playingQueue.indexOfLast { it.isUpcoming }

    private val mutableRepeatModeFlow = MutableStateFlow(Playback.RepeatMode.Off)
    val repeatModeFlow = mutableRepeatModeFlow.asStateFlow()
    val repeatMode get() = repeatModeFlow.value

    private val mutableShuffleModeFlow = MutableStateFlow(Playback.ShuffleMode.Off)
    val shuffleModeFlow = mutableShuffleModeFlow.asStateFlow()
    val shuffleMode get() = shuffleModeFlow.value

    private val mutablePositionFlow = MutableStateFlow(QueuePosition.Unspecified)
    val positionFlow = mutablePositionFlow.asStateFlow()
    val position get() = positionFlow.value.value

    private val mutablePlayingQueueFlow = MutableStateFlow(emptyList<QueueSong>())
    val playingQueueFlow = mutablePlayingQueueFlow.asStateFlow()
    val playingQueue get() = playingQueueFlow.value

    private val mutableStopPositionFlow = MutableStateFlow(StopPosition.Unspecified)
    val stopPositionFlow = mutableStopPositionFlow.asStateFlow()
    val stopPosition get() = stopPositionFlow.value.value

    val currentSongFlow = mutablePositionFlow.map {
        getSongAt(it.value)
    }.stateIn(
        scope = coroutineScope,
        started = SharingStarted.Eagerly,
        initialValue = Song.emptySong
    )
    val currentSong get() = currentSongFlow.value

    val nextSongFlow = mutablePositionFlow.map {
        getSongAt(getNextPosition(true))
    }.stateIn(
        scope = coroutineScope,
        started = SharingStarted.Eagerly,
        initialValue = Song.emptySong
    )
    val nextSong get() = nextSongFlow.value

    var nextPosition = -1
    var originalPlayingQueue: MutableList<QueueSong> = ArrayList()

    val isEmpty get() = playingQueue.isEmpty()
    val isFirstTrack get() = position == 0
    val isLastTrack get() = position == playingQueue.lastIndex
    val isStopPosition get() = stopPosition == position

    init {
        setSequentialMode(Preferences.queueNextSequentially)
    }

    var isSequentialQueue: Boolean = false
    fun setSequentialMode(isSequentialQueue: Boolean) {
        this.isSequentialQueue = isSequentialQueue
        if (!isSequentialQueue) {
            removeAllRanges()
        }
    }

    suspend fun open(
        queue: List<Song>,
        startPosition: Int,
        startPlaying: Boolean,
        shuffleMode: Playback.ShuffleMode?
    ) {
        var position = startPosition
        val result = createQueue(queue, startPosition, shuffleMode ?: defaultShuffleMode) {
            if (shuffleMode == Playback.ShuffleMode.On) {
                position = 0
                makeShuffleList(this, startPosition)
            }
            this
        }
        if (result) {
            setPositionTo(QueuePosition.initial(position, startPlaying))
        }
    }

    suspend fun specialShuffleQueue(songs: List<Song>, shuffleMode: SpecialShuffleMode): Boolean {
        val result = createQueue(songs, 0, Playback.ShuffleMode.On) {
            shuffleManager.applySmartShuffle(this, shuffleMode).toQueueSongs()
        }
        if (result) {
            setPositionTo(QueuePosition.play())
        }
        return false
    }

    suspend fun shuffleUsingProviders(
        providers: List<SongProvider>,
        shuffleMode: GroupShuffleMode,
        defaultSortKey: String? = null
    ): Boolean {
        val songs = providers.flatMap { it.songs }.sortedSongs(SortOrder.songSortOrder)
        val result = createQueue(songs, 0, Playback.ShuffleMode.On) {
            shuffleManager.shuffleByProvider(providers, shuffleMode, defaultSortKey).toQueueSongs()
        }
        if (result) {
            setPositionTo(QueuePosition.play())
        }
        return result
    }

    fun getSongAt(position: Int): Song {
        return playingQueue.getOrElse(position) { Song.emptySong }
    }

    fun getDuration(position: Int) = playingQueue.drop(position).sumOf { it.duration }

    fun getNextPosition(force: Boolean): Int {
        var position = this.position + 1
        when (repeatMode) {
            Playback.RepeatMode.All -> if (isLastTrack) {
                position = 0
            }

            Playback.RepeatMode.One -> if (force) {
                if (isLastTrack) {
                    position = 0
                }
            } else {
                position -= 1
            }

            Playback.RepeatMode.Off -> if (isLastTrack) {
                position -= 1
            }
        }
        return position
    }

    fun getPreviousPosition(force: Boolean): Int {
        var newPosition = this.position - 1
        when (repeatMode) {
            Playback.RepeatMode.All -> if (newPosition < 0) {
                newPosition = playingQueue.size - 1
            }

            Playback.RepeatMode.One -> if (force) {
                if (newPosition < 0) {
                    newPosition = playingQueue.size - 1
                }
            } else {
                newPosition = this.position
            }

            Playback.RepeatMode.Off -> if (newPosition < 0) {
                newPosition = 0
            }
        }
        return newPosition
    }

    fun queueNext(song: Song) {
        if (!isSequentialQueue) {
            addSong(position + 1, song)
        } else {
            modifyQueue { playingQueue, originalPlayingQueue ->
                val queueSong = song.toQueueSong(true)
                if (position == playingQueue.lastIndex) {
                    playingQueue.add(queueSong)
                    originalPlayingQueue.add(queueSong)
                } else for (i in (position + 1) until playingQueue.size) {
                    val item = playingQueue[i]
                    if (item.isUpcoming) {
                        if (i == playingQueue.lastIndex) {
                            playingQueue.add(queueSong)
                            originalPlayingQueue.add(queueSong)
                            break
                        }
                    } else {
                        playingQueue.add(i, queueSong)
                        originalPlayingQueue.add(i, queueSong)
                        break
                    }
                }
            }
        }
    }

    fun queueNext(songs: List<Song>) {
        if (!isSequentialQueue) {
            addSongs(position + 1, songs)
        } else {
            modifyQueue { playingQueue, originalPlayingQueue ->
                val queueSong = songs.toQueueSongs(true)
                if (position == playingQueue.lastIndex) {
                    playingQueue.addAll(queueSong)
                    originalPlayingQueue.addAll(queueSong)
                } else for (i in (position + 1) until playingQueue.size) {
                    val item = playingQueue[i]
                    if (item.isUpcoming) {
                        if (i == playingQueue.lastIndex) {
                            playingQueue.addAll(queueSong)
                            originalPlayingQueue.addAll(queueSong)
                            break
                        }
                    } else {
                        playingQueue.addAll(i, queueSong)
                        originalPlayingQueue.addAll(i, queueSong)
                        break
                    }
                }
            }
        }
    }

    fun addSong(position: Int, song: Song) {
        modifyQueue { playingQueue, originalPlayingQueue ->
            val isUpcomingRange = isInUpcomingRange(position)
            val queueSong = song.toQueueSong(isUpcomingRange)
            playingQueue.add(position, queueSong)
            originalPlayingQueue.add(position, queueSong)
        }
    }

    fun addSong(song: Song) {
        modifyQueue { playingQueue, originalPlayingQueue ->
            val queueSong = song.toQueueSong()
            playingQueue.add(queueSong)
            originalPlayingQueue.add(queueSong)
        }
    }

    fun addSongs(position: Int, songs: List<Song>) {
        modifyQueue { playingQueue, originalPlayingQueue ->
            val isUpcomingRange = isInUpcomingRange(position)
            val queueSongs = songs.toQueueSongs(isUpcomingRange)
            playingQueue.addAll(position, queueSongs)
            originalPlayingQueue.addAll(position, queueSongs)
        }
    }

    fun addSongs(songs: List<Song>) {
        modifyQueue { playingQueue, originalPlayingQueue ->
            val queueSongs = songs.toQueueSongs()
            playingQueue.addAll(queueSongs)
            originalPlayingQueue.addAll(queueSongs)
        }
    }

    fun moveSong(from: Int, to: Int) {
        if (from == to)
            return

        modifyQueue { playingQueue, originalPlayingQueue ->
            val playingQueue = playingQueue.toMutableList()
            val lastUpcomingIndex = lastUpcomingPosition
            val currPosition = this.position
            val songToMove = playingQueue.removeAt(from)
            songToMove.isUpcoming = isInUpcomingRange(to)
            playingQueue.add(to, songToMove)
            if (shuffleMode == Playback.ShuffleMode.Off) {
                val tmpSong = originalPlayingQueue.removeAt(from)
                originalPlayingQueue.add(to, tmpSong)
            }
            when {
                currPosition in to until from -> {
                    setPositionTo(QueuePosition.passive(currPosition + 1))
                }
                currPosition in (from + 1)..to -> {
                    setPositionTo(QueuePosition.passive(currPosition - 1))
                }
                from == currPosition -> {
                    setPositionTo(QueuePosition.passive(to))
                    if (to < currPosition) {
                        realignUpcomingRange(lastIndex = lastUpcomingIndex)
                    } else if (to >= lastUpcomingIndex) {
                        removeAllRanges()
                    }
                }
            }
        }
    }

    fun removeSong(position: Int) {
        modifyQueue { playingQueue, originalPlayingQueue ->
            if (shuffleMode == Playback.ShuffleMode.Off) {
                playingQueue.removeAt(position)
                originalPlayingQueue.removeAt(position)
            } else {
                originalPlayingQueue.remove(playingQueue.removeAt(position))
            }
            rePosition(position, playingQueue)
        }
    }

    fun removeSong(song: Song) {
        modifyQueue { playingQueue, originalPlayingQueue ->
            playingQueue.removeSong(song)
            originalPlayingQueue.removeSong(song)
        }
    }

    fun removeSongs(songs: List<Song>) {
        modifyQueue { playingQueue, originalPlayingQueue ->
            for (song in songs) {
                playingQueue.removeSong(song)
                originalPlayingQueue.removeSong(song)
            }
        }
    }

    fun playSongAt(newPosition: Int) {
        setPositionTo(QueuePosition.play(newPosition), realign = true)
    }

    fun prepareSongAt(newPosition: Int) {
        setPositionTo(QueuePosition.prepare(newPosition), realign = true)
    }

    fun syncNextPosition() {
        setPositionTo(QueuePosition.passive(nextPosition), realign = true)
    }

    fun setPositionTo(position: QueuePosition, realign: Boolean = false) {
        val oldPosition = this.position
        if (realign) {
            val lastUpcomingPosition = this.lastUpcomingPosition
            val isUpcomingRange = isInUpcomingRange(
                targetIndex = position.value,
                firstIndex = oldPosition,
                lastIndex = lastUpcomingPosition
            )
            if (position.value < oldPosition || isUpcomingRange) {
                // First we check if the new position is further back than the
                // current one or further forward but within the allowed range.
                // If so, it will only be necessary to realign the upcoming tracks.
                realignUpcomingRange(firstIndex = position.value, lastIndex = lastUpcomingPosition)
            } else if (position.value > lastUpcomingPosition) {
                // In case the new position has exceeded the allowed range,
                // we discard it completely.
                removeAllRanges()
            }
        }
        mutablePositionFlow.value = position
    }

    fun setStopPosition(stopPosition: Int, fromUser: Boolean = false) {
        mutableStopPositionFlow.value = StopPosition(stopPosition, fromUser)
    }

    fun syncStopPosition() {
        if (stopPosition < position) {
            setStopPosition(StopPosition.INFINITE)
        }
    }

    fun shuffleQueue() {
        val currentPosition = position
        val endPosition = playingQueue.lastIndex
        if (currentPosition == endPosition)
            return

        modifyQueue { playingQueue, _ ->
            // Check that there are at least two tracks to make a shuffle list
            if (endPosition - currentPosition >= 2) {
                playingQueue.subList(currentPosition + 1, endPosition + 1).shuffle()
                removeAllRanges()
            }
        }
    }

    suspend fun restoreQueues(
        restoredQueue: List<Song>,
        restoredOriginalQueue: List<Song>,
        restoredPosition: Int
    ): Boolean {
        return createQueue(restoredOriginalQueue, restoredPosition, shuffleMode) {
            restoredQueue.toQueueSongs().toMutableList()
        }.also { restored ->
            if (restored) {
                setPositionTo(QueuePosition.passive(restoredPosition))
            }
        }
    }

    fun setRepeatMode(mode: Playback.RepeatMode) {
        mutableRepeatModeFlow.value = mode
    }

    fun setShuffleMode(mode: Playback.ShuffleMode) {
        removeAllRanges()
        val newPlayingQueue = when (mode) {
            Playback.ShuffleMode.On -> {
                playingQueue.toMutableList().also {
                    makeShuffleList(it, position)
                    setPositionTo(QueuePosition.passive(0))
                }
            }

            Playback.ShuffleMode.Off -> {
                val currentSongId = currentSong.id
                val playingQueue = originalPlayingQueue.toMutableList()
                var newPosition = 0
                for (song in playingQueue) {
                    if (song.id == currentSongId) {
                        newPosition = playingQueue.indexOf(song)
                    }
                }
                setPositionTo(QueuePosition.passive(newPosition))
                playingQueue
            }
        }
        mutableShuffleModeFlow.value = mode
        mutablePlayingQueueFlow.value = newPlayingQueue
    }

    fun clearQueue() {
        setPositionTo(QueuePosition.prepare(-1))
        originalPlayingQueue = mutableListOf()
        mutablePlayingQueueFlow.value = emptyList()
    }

    fun disconnect() {
        setStopPosition(StopPosition.INFINITE)
        mutablePositionFlow.value = positionFlow.value.copy(passive = true, play = false)
    }

    private fun modifyQueue(
        dispatch: Boolean = true,
        modifier: (playingQueue: MutablePlayQueue, originalPlayingQueue: MutablePlayQueue) -> Unit
    ) {
        val playingQueue = playingQueue.toMutableList()
        val originalPlayingQueue = originalPlayingQueue.toMutableList()
        modifier(playingQueue, originalPlayingQueue)
        this.originalPlayingQueue = originalPlayingQueue
        if (dispatch) {
            mutablePlayingQueueFlow.value = playingQueue
        }
    }

    private suspend fun createQueue(
        songs: List<Song>,
        startPosition: Int,
        shuffleMode: Playback.ShuffleMode,
        onCreated: suspend MutableList<QueueSong>.() -> List<QueueSong>
    ): Boolean {
        if (songs.isNotEmpty() && startPosition >= 0 && startPosition < songs.size) {
            // it is important to copy the playing queue here first as we might add/remove songs later
            originalPlayingQueue = songs.toQueueSongs().toMutableList()
            mutablePlayingQueueFlow.value = onCreated(originalPlayingQueue.toMutableList())
            mutableShuffleModeFlow.value = shuffleMode
            return true
        }
        return false
    }

    private fun rePosition(deletedPosition: Int, playingQueue: List<QueueSong>) {
        val currentPosition = this.position
        if (deletedPosition < currentPosition) {
            setPositionTo(QueuePosition.passive(currentPosition + 1))
        } else if (deletedPosition == currentPosition) {
            if (playingQueue.size > deletedPosition) {
                setPositionTo(QueuePosition.prepare(position), realign = true)
            } else {
                setPositionTo(QueuePosition.prepare(position + 1), realign = true)
            }
        }
    }

    private fun MutableList<QueueSong>.removeSong(song: Song): Int {
        val deletePosition = indexOf(song)
        if (deletePosition != -1) {
            removeAt(deletePosition)
            rePosition(deletePosition, playingQueue)
        }
        return deletePosition
    }

    private fun <T : Song> makeShuffleList(listToShuffle: MutableList<T>, current: Int) {
        if (current >= 0) {
            val song = listToShuffle.removeAt(current)
            listToShuffle.shuffle()
            listToShuffle.add(0, song)
        } else {
            listToShuffle.shuffle()
        }
    }

    suspend fun saveQueues() = withContext(IO) {
        PlaybackQueueStore.getInstance(context)
            .saveQueues(playingQueue, originalPlayingQueue)
    }

    private fun isInUpcomingRange(targetIndex: Int, firstIndex: Int = position, lastIndex: Int = lastUpcomingPosition): Boolean {
        if (!isSequentialQueue)
            return false

        if (lastIndex == -1) return false
        return targetIndex in (firstIndex + 1)..lastIndex
    }

    /**
     * Keeps the status of the upcoming tracks consistent. Basically it removes the
     * value from those tracks that are out of range (which starts from the position
     * immediately following the current one, and ends at the last track that has
     * been added).
     *
     * It is valid to specify a different range depending on the need of the situation.
     */
    private fun realignUpcomingRange(firstIndex: Int = position, lastIndex: Int = lastUpcomingPosition) {
        if (!isSequentialQueue)
            return

        if (lastIndex == -1) return // there is nothing to realign
        if (lastIndex == firstIndex) {
            playingQueue[firstIndex].isUpcoming = false
            return
        }
        for ((i, song) in playingQueue.withIndex()) {
            song.isUpcoming = !(i <= firstIndex || i > lastIndex)
        }
    }

    /**
     * Remove all upcoming tracks. However, this does not actually remove the items
     * from the list, it just removes the [QueueSong.isUpcoming] value from them.
     */
    private fun removeAllRanges() {
        if (!isSequentialQueue)
            return

        playingQueue.forEach { it.isUpcoming = false }
    }
}