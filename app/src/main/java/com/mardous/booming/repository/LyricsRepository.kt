package com.mardous.booming.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.mardous.booming.lyrics.parser.LyricsParser
import com.mardous.booming.appContext
import com.mardous.booming.database.LyricsDao
import com.mardous.booming.database.toLyricsEntity
import com.mardous.booming.extensions.files.getBestTag
import com.mardous.booming.extensions.files.getContentUri
import com.mardous.booming.extensions.files.toAudioFile
import com.mardous.booming.extensions.hasR
import com.mardous.booming.extensions.isAllowedToDownloadMetadata
import com.mardous.booming.extensions.media.isArtistNameUnknown
import com.mardous.booming.http.Result
import com.mardous.booming.http.lyrics.LyricsDownloadService
import com.mardous.booming.worker.TagEditorWorker
import com.mardous.booming.model.DownloadedLyrics
import com.mardous.booming.model.Song
import com.mardous.booming.viewmodels.lyrics.model.LyricsResult
import com.mardous.booming.viewmodels.lyrics.model.LyricsSource
import com.mardous.booming.viewmodels.lyrics.model.LyricsType
import com.mardous.booming.viewmodels.lyrics.model.SaveLyricsResult
import com.mardous.booming.util.LyricsUtil
import com.mardous.booming.util.UriUtil
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.util.EnumMap

interface LyricsRepository {
    suspend fun onlineLyrics(song: Song, searchTitle: String, searchArtist: String): Result<DownloadedLyrics>
    suspend fun allLyrics(song: Song, allowDownload: Boolean, fromEditor: Boolean): LyricsResult
    suspend fun embeddedLyrics(song: Song, requirePlainText: Boolean): LyricsResult
    suspend fun saveLyrics(song: Song, plainLyrics: String?, syncedLyrics: String?, plainLyricsModified: Boolean): SaveLyricsResult
    suspend fun saveSyncedLyricsFromUri(song: Song, uri: Uri?): Boolean
    suspend fun shareSyncedLyrics(song: Song): Uri?
    suspend fun deleteAllLyrics()
}

class RealLyricsRepository(
    private val context: Context,
    private val contentResolver: ContentResolver,
    private val lyricsDownloadService: LyricsDownloadService,
    private val lyricsParser: LyricsParser,
    private val lyricsDao: LyricsDao
) : LyricsRepository {

    override suspend fun onlineLyrics(
        song: Song,
        searchTitle: String,
        searchArtist: String
    ): Result<DownloadedLyrics> {
        return if (song.id == Song.emptySong.id) {
            Result.Error(IllegalArgumentException("Song is not valid"))
        } else {
            if (searchArtist.isArtistNameUnknown()) {
                Result.Error(IllegalArgumentException("Artist name is <unknown>"))
            } else {
                try {
                    Result.Success(lyricsDownloadService.getLyrics(song, searchTitle, searchArtist))
                } catch (e: Exception) {
                    Result.Error(e)
                }
            }
        }
    }

    override suspend fun allLyrics(
        song: Song,
        allowDownload: Boolean,
        fromEditor: Boolean
    ): LyricsResult {
        if (song.id == Song.emptySong.id) {
            return LyricsResult(song.id)
        }

        val embeddedLyrics = embeddedLyrics(song, requirePlainText = false).plainLyrics.orEmpty()
        val embeddedSynced = lyricsParser.parse(embeddedLyrics)

        val localFileLyrics = LyricsUtil.getSyncedLyricsFile(song)?.let {
            lyricsParser.parse(it)
        }
        if (localFileLyrics?.hasContent == true) {
            val embeddedSource = if (embeddedSynced?.hasContent == true) {
                LyricsSource.EmbeddedSynced
            } else {
                LyricsSource.Embedded
            }
            return LyricsResult(
                song.id,
                embeddedLyrics,
                localFileLyrics,
                sources = hashMapOf(
                    LyricsType.Embedded to embeddedSource,
                    LyricsType.External to LyricsSource.Lrc
                )
            )
        }

        val storedSynced = lyricsDao.getLyrics(song.id)?.let {
            lyricsParser.parse(it.syncedLyrics)
        }
        if (embeddedSynced?.hasContent == true) {
            return if (fromEditor) {
                val lrcData = if (storedSynced?.hasContent == true) storedSynced else null
                LyricsResult(
                    song.id,
                    plainLyrics = embeddedLyrics,
                    syncedLyrics = lrcData,
                    sources = hashMapOf(LyricsType.Embedded to LyricsSource.EmbeddedSynced)
                )
            } else {
                LyricsResult(song.id, syncedLyrics = embeddedSynced)
            }
        }

        if (storedSynced?.hasContent == true) {
            return LyricsResult(song.id, plainLyrics = embeddedLyrics, syncedLyrics = storedSynced)
        }

        if (allowDownload && appContext().isAllowedToDownloadMetadata()) {
            val downloaded = runCatching { lyricsDownloadService.getLyrics(song) }.getOrNull()
            if (downloaded?.isSynced == true) {
                val syncedData = lyricsParser.parse(downloaded.syncedLyrics!!)
                if (syncedData?.hasContent == true) {
                    lyricsDao.insertLyrics(song.toLyricsEntity(syncedData.rawText, autoDownload = true))
                    return LyricsResult(song.id, plainLyrics = embeddedLyrics, syncedLyrics = syncedData)
                }
            }
        }

        return LyricsResult(song.id, embeddedLyrics)
    }

    override suspend fun embeddedLyrics(song: Song, requirePlainText: Boolean): LyricsResult {
        if (song.id != Song.emptySong.id) {
            val result = runCatching {
                File(song.data).toAudioFile()
                    ?.getBestTag(false)
                    ?.getFirst(FieldKey.LYRICS)
            }
            val content = result.getOrNull()
            if (requirePlainText && !content.isNullOrBlank()) {
                val syncedData = lyricsParser.parse(content)
                if (syncedData?.hasContent == true) {
                    return LyricsResult(song.id, plainLyrics = syncedData.plainText)
                }
            }
            return LyricsResult(song.id, plainLyrics = result.getOrNull())
        }
        return LyricsResult(song.id)
    }

    override suspend fun saveLyrics(
        song: Song,
        plainLyrics: String?,
        syncedLyrics: String?,
        plainLyricsModified: Boolean
    ): SaveLyricsResult {
        val pendingLrcFile = try {
            saveSyncedLyrics(context, song, syncedLyrics)
        } catch (e: Exception) {
            null
        }
        return if (!plainLyricsModified) {
            if (pendingLrcFile != null) {
                SaveLyricsResult(isPending = true, isSuccess = false, pendingWrite = listOf(pendingLrcFile))
            } else {
                SaveLyricsResult(isPending = false, isSuccess = true)
            }
        } else {
            val fieldKeyValueMap = EnumMap<FieldKey, String>(FieldKey::class.java).apply {
                put(FieldKey.LYRICS, plainLyrics)
            }
            val writeInfo = TagEditorWorker.WriteInfo(listOf(song.data), fieldKeyValueMap, null)
            if (hasR()) {
                val pending = runCatching {
                    TagEditorWorker.writeTagsToFilesR(context, writeInfo).first() to song.mediaStoreUri
                }
                if (pending.isSuccess) {
                    SaveLyricsResult(
                        isPending = true,
                        isSuccess = false,
                        pendingWrite = listOfNotNull(pendingLrcFile, pending.getOrThrow())
                    )
                } else {
                    SaveLyricsResult(isPending = false, isSuccess = false)
                }
            } else {
                val result = runCatching {
                    TagEditorWorker.writeTagsToFiles(context, writeInfo)
                }
                if (result.isSuccess) {
                    SaveLyricsResult(isPending = false, isSuccess = true)
                } else {
                    SaveLyricsResult(isPending = false, isSuccess = false)
                }
            }
        }
    }

    override suspend fun saveSyncedLyricsFromUri(song: Song, uri: Uri?): Boolean {
        val path = uri?.path
        if (path != null && path.endsWith(".lrc")) {
            return contentResolver.openInputStream(uri).use { stream ->
                val result = runCatching { stream?.reader()?.readText() }
                if (result.isSuccess) {
                    val fileContent = result.getOrThrow()
                    if (fileContent != null && lyricsParser.isValid(fileContent)) {
                        lyricsDao.insertLyrics(song.toLyricsEntity(fileContent))
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
        } else {
            return false
        }
    }

    override suspend fun shareSyncedLyrics(song: Song): Uri? {
        if (song.id == Song.emptySong.id) {
            return null
        } else {
            val lyrics = lyricsDao.getLyrics(song.id)
            if (lyrics != null) {
                val tempFile = context.externalCacheDir
                    ?.resolve("${song.artistName} - ${song.title}.lrc")
                if (tempFile == null) {
                    return null
                } else {
                    val result = runCatching {
                        tempFile.bufferedWriter().use {
                            it.write(lyrics.syncedLyrics)
                        }
                        tempFile.getContentUri(context)
                    }
                    return if (result.isSuccess) {
                        result.getOrThrow()
                    } else null
                }
            } else {
                return null
            }
        }
    }

    override suspend fun deleteAllLyrics() {
        lyricsDao.removeLyrics()
    }

    private suspend fun saveSyncedLyrics(context: Context, song: Song, syncedLyrics: String?): Pair<File, Uri>? {
        val localLrcFile = LyricsUtil.getSyncedLyricsFile(song)
        if (localLrcFile != null) {
            if (hasR()) {
                val destinationUri = UriUtil.getUriFromPath(context, localLrcFile.absolutePath)
                val cacheFile = File(context.cacheDir, localLrcFile.name).also {
                    it.writeText(syncedLyrics ?: "")
                }
                return cacheFile to destinationUri
            } else {
                LyricsUtil.writeLrc(song, syncedLyrics ?: "")
            }
        } else {
            if (syncedLyrics.isNullOrEmpty()) {
                val lyrics = lyricsDao.getLyrics(song.id)
                if (lyrics != null) {
                    if (lyrics.autoDownload) {
                        // The user has deleted an automatically downloaded lyrics, perhaps
                        // because it was incorrect. In this case we do not delete the
                        // registry, we simply clean it, this way it will prevent us from
                        // trying to download it again in the future.
                        lyricsDao.insertLyrics(song.toLyricsEntity("", userCleared = true))
                    } else if (!lyrics.userCleared) {
                        lyricsDao.removeLyrics(song.id)
                    }
                }
            } else {
                val parsedLyrics = lyricsParser.parse(syncedLyrics)
                if (parsedLyrics?.hasContent == true) {
                    lyricsDao.insertLyrics(song.toLyricsEntity(parsedLyrics.rawText))
                }
            }
        }
        return null
    }
}