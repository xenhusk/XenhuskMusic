/*
 * Copyright (C) 2014 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mardous.booming.providers.databases

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.MediaStore.Audio.AudioColumns
import com.mardous.booming.model.Song
import com.mardous.booming.repository.SongRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class PlaybackQueueStore private constructor(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, VERSION), KoinComponent {

    override fun onCreate(db: SQLiteDatabase) {
        createTable(db, PLAYING_QUEUE_TABLE_NAME)
        createTable(db, ORIGINAL_PLAYING_QUEUE_TABLE_NAME)
    }

    private fun createTable(db: SQLiteDatabase, tableName: String) {
        val builder = StringBuilder()
        builder.append("CREATE TABLE IF NOT EXISTS ")
        builder.append(tableName)
        builder.append("(")
        builder.append(AudioColumns._ID)
        builder.append(" LONG NOT NULL,")
        builder.append(AudioColumns.DATA)
        builder.append(" STRING NOT NULL,")
        builder.append(AudioColumns.TITLE)
        builder.append(" STRING NOT NULL,")
        builder.append(AudioColumns.TRACK)
        builder.append(" INT NOT NULL,")
        builder.append(AudioColumns.YEAR)
        builder.append(" INT NOT NULL,")
        builder.append(AudioColumns.SIZE)
        builder.append(" LONG NOT NULL,")
        builder.append(AudioColumns.DURATION)
        builder.append(" LONG NOT NULL,")
        builder.append(AudioColumns.DATE_ADDED)
        builder.append(" LONG NOT NULL,")
        builder.append(AudioColumns.DATE_MODIFIED)
        builder.append(" LONG NOT NULL,")
        builder.append(AudioColumns.ALBUM_ID)
        builder.append(" LONG NOT NULL,")
        builder.append(AudioColumns.ALBUM)
        builder.append(" STRING NOT NULL,")
        builder.append(AudioColumns.ARTIST_ID)
        builder.append(" LONG NOT NULL,")
        builder.append(AudioColumns.ARTIST)
        builder.append(" STRING NOT NULL,")
        builder.append(AudioColumns.ALBUM_ARTIST)
        builder.append(" STRING);")
        db.execSQL(builder.toString())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // not necessary yet
        db.execSQL("DROP TABLE IF EXISTS $PLAYING_QUEUE_TABLE_NAME")
        db.execSQL("DROP TABLE IF EXISTS $ORIGINAL_PLAYING_QUEUE_TABLE_NAME")
        onCreate(db)
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // If we ever have downgrade, drop the table to be safe
        db.execSQL("DROP TABLE IF EXISTS $PLAYING_QUEUE_TABLE_NAME")
        db.execSQL("DROP TABLE IF EXISTS $ORIGINAL_PLAYING_QUEUE_TABLE_NAME")
        onCreate(db)
    }

    @Synchronized
    fun saveQueues(playingQueue: List<Song>, originalPlayingQueue: List<Song>) {
        saveQueue(PLAYING_QUEUE_TABLE_NAME, playingQueue)
        saveQueue(ORIGINAL_PLAYING_QUEUE_TABLE_NAME, originalPlayingQueue)
    }

    @Synchronized
    private fun saveQueue(tableName: String, queue: List<Song>) {
        val database = writableDatabase
        database.beginTransaction()
        try {
            database.delete(tableName, null, null)
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }

        val numProcess = 20
        var position = 0
        while (position < queue.size) {
            database.beginTransaction()
            try {
                var i = position
                while (i < queue.size && i < position + numProcess) {
                    val song = queue[i]
                    val values = ContentValues(12)
                    values.put(AudioColumns._ID, song.id)
                    values.put(AudioColumns.DATA, song.data)
                    values.put(AudioColumns.TITLE, song.title)
                    values.put(AudioColumns.TRACK, song.trackNumber)
                    values.put(AudioColumns.YEAR, song.year)
                    values.put(AudioColumns.SIZE, song.size)
                    values.put(AudioColumns.DURATION, song.duration)
                    values.put(AudioColumns.DATE_ADDED, song.dateAdded)
                    values.put(AudioColumns.DATE_MODIFIED, song.dateModified)
                    values.put(AudioColumns.ALBUM_ID, song.albumId)
                    values.put(AudioColumns.ALBUM, song.albumName)
                    values.put(AudioColumns.ARTIST_ID, song.artistId)
                    values.put(AudioColumns.ARTIST, song.artistName)
                    values.put(AudioColumns.ALBUM_ARTIST, song.albumArtistName)
                    database.insert(tableName, null, values)
                    i++
                }
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
                position += numProcess
            }
        }
    }

    val savedPlayingQueue: List<Song>
        get() = getQueue(PLAYING_QUEUE_TABLE_NAME)

    val savedOriginalPlayingQueue: List<Song>
        get() = getQueue(ORIGINAL_PLAYING_QUEUE_TABLE_NAME)

    private fun getQueue(tableName: String): List<Song> {
        val songRepository = get<SongRepository>()
        val cursor = readableDatabase.query(tableName, null, null, null, null, null, null)
        return songRepository.songs(cursor)
    }

    companion object {
        private const val DATABASE_NAME = "music_playback_state.db"
        private const val PLAYING_QUEUE_TABLE_NAME = "playing_queue"
        private const val ORIGINAL_PLAYING_QUEUE_TABLE_NAME = "original_playing_queue"
        private const val VERSION = 2

        private var sInstance: PlaybackQueueStore? = null

        @Synchronized
        fun getInstance(context: Context): PlaybackQueueStore {
            if (sInstance == null) {
                sInstance = PlaybackQueueStore(context.applicationContext)
            }
            return sInstance!!
        }
    }
}
