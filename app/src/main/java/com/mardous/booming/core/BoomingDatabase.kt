package com.mardous.booming.core

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mardous.booming.data.local.room.*

@Database(
    entities = [
        PlaylistEntity::class,
        SongEntity::class,
        HistoryEntity::class,
        PlayCountEntity::class,
        InclExclEntity::class,
        LyricsEntity::class,
        SongClassificationEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class BoomingDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun playCountDao(): PlayCountDao
    abstract fun historyDao(): HistoryDao
    abstract fun inclExclDao(): InclExclDao
    abstract fun lyricsDao(): LyricsDao
    abstract fun songClassificationDao(): SongClassificationDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE PlaylistEntity ADD COLUMN custom_cover_uri TEXT")
                db.execSQL("ALTER TABLE PlaylistEntity ADD COLUMN description TEXT")
            }
        }
        
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS song_classifications (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        song_id INTEGER NOT NULL,
                        song_title TEXT NOT NULL,
                        artist_name TEXT NOT NULL,
                        classification_type TEXT NOT NULL,
                        confidence REAL NOT NULL,
                        christian_probability REAL NOT NULL,
                        secular_probability REAL NOT NULL,
                        classification_timestamp INTEGER NOT NULL,
                        model_version TEXT NOT NULL DEFAULT '1.0.0',
                        is_manual_override INTEGER NOT NULL DEFAULT 0,
                        manual_override_timestamp INTEGER
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_song_classifications_song_id ON song_classifications (song_id)")
            }
        }
    }
}