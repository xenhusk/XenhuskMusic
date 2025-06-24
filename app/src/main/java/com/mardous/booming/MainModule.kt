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

package com.mardous.booming

import androidx.preference.PreferenceManager
import androidx.room.Room
import com.mardous.booming.activities.tageditor.TagEditorViewModel
import com.mardous.booming.androidauto.AutoMusicProvider
import com.mardous.booming.audio.SoundSettings
import com.mardous.booming.database.BoomingDatabase
import com.mardous.booming.fragments.LibraryViewModel
import com.mardous.booming.fragments.albums.AlbumDetailViewModel
import com.mardous.booming.fragments.artists.ArtistDetailViewModel
import com.mardous.booming.fragments.equalizer.EqualizerViewModel
import com.mardous.booming.fragments.folders.FolderDetailViewModel
import com.mardous.booming.fragments.genres.GenreDetailViewModel
import com.mardous.booming.fragments.info.InfoViewModel
import com.mardous.booming.fragments.lyrics.LyricsViewModel
import com.mardous.booming.fragments.playlists.PlaylistDetailViewModel
import com.mardous.booming.fragments.search.SearchViewModel
import com.mardous.booming.fragments.sound.SoundSettingsViewModel
import com.mardous.booming.fragments.years.YearDetailViewModel
import com.mardous.booming.helper.UriSongResolver
import com.mardous.booming.http.deezer.DeezerService
import com.mardous.booming.http.github.GitHubService
import com.mardous.booming.http.jsonHttpClient
import com.mardous.booming.http.lastfm.LastFmService
import com.mardous.booming.http.lyrics.LyricsDownloadService
import com.mardous.booming.http.provideDefaultCache
import com.mardous.booming.http.provideOkHttp
import com.mardous.booming.lyrics.parser.LrcLyricsParser
import com.mardous.booming.lyrics.parser.LyricsParser
import com.mardous.booming.model.Genre
import com.mardous.booming.providers.MediaStoreWriter
import com.mardous.booming.repository.*
import com.mardous.booming.service.equalizer.EqualizerManager
import com.mardous.booming.service.queue.ShuffleManager
import com.mardous.booming.viewmodels.PlaybackViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

val networkModule = module {
    factory {
        jsonHttpClient(get())
    }
    factory {
        provideDefaultCache()
    }
    factory {
        provideOkHttp(get(), get())
    }
    single {
        GitHubService(androidContext(), get())
    }
    single {
        DeezerService(get())
    }
    single {
        LastFmService(get())
    }
    single {
        LyricsDownloadService(get())
    }
}

private val autoModule = module {
    single {
        AutoMusicProvider(androidContext(), get())
    }
}

private val mainModule = module {
    single {
        androidContext().contentResolver
    }
    single {
        EqualizerManager(androidContext())
    }
    single {
        SoundSettings(androidContext())
    }
    single {
        MediaStoreWriter(androidContext(), get())
    }
    single {
        PreferenceManager.getDefaultSharedPreferences(androidContext())
    }
    single {
        LrcLyricsParser()
    } bind LyricsParser::class
}

private val roomModule = module {
    single {
        Room.databaseBuilder(androidContext(), BoomingDatabase::class.java, "music_database.db")
            .build()
    }

    factory {
        get<BoomingDatabase>().playlistDao()
    }

    factory {
        get<BoomingDatabase>().playCountDao()
    }

    factory {
        get<BoomingDatabase>().historyDao()
    }

    factory {
        get<BoomingDatabase>().inclExclDao()
    }

    factory {
        get<BoomingDatabase>().lyricsDao()
    }
}

private val dataModule = module {
    single {
        RealRepository(
            androidContext(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get()
        )
    } bind Repository::class

    single {
        RealSongRepository(get())
    } bind SongRepository::class

    single {
        RealAlbumRepository(get())
    } bind AlbumRepository::class

    single {
        RealArtistRepository(get(), get())
    } bind ArtistRepository::class

    single {
        RealPlaylistRepository(androidContext(), get(), get())
    } bind PlaylistRepository::class

    single {
        RealGenreRepository(get(), get())
    } bind GenreRepository::class

    single {
        RealSearchRepository(get(), get(), get(), get(), get(), get())
    } bind SearchRepository::class

    single {
        RealSmartRepository(androidContext(), get(), get(), get(), get(), get())
    } bind SmartRepository::class

    single {
        RealSpecialRepository(get())
    } bind SpecialRepository::class

    single {
        RealLyricsRepository(androidContext(), get(), get(), get(), get())
    } bind LyricsRepository::class

    single {
        UriSongResolver(androidContext(), get(), get())
    }

    single {
        ShuffleManager(get())
    }
}

private val viewModule = module {
    viewModel {
        LibraryViewModel(get(), get(), get(), get(), get())
    }

    viewModel {
        PlaybackViewModel()
    }

    viewModel {
        EqualizerViewModel(get(), get(), get())
    }

    viewModel { (albumId: Long) ->
        AlbumDetailViewModel(get(), albumId)
    }

    viewModel { (artistId: Long, artistName: String?) ->
        ArtistDetailViewModel(get(), artistId, artistName)
    }

    viewModel { (playlistId: Long) ->
        PlaylistDetailViewModel(get(), playlistId)
    }

    viewModel { (genre: Genre) ->
        GenreDetailViewModel(get(), genre)
    }

    viewModel { (year: Int) ->
        YearDetailViewModel(get(), year)
    }

    viewModel { (path: String) ->
        FolderDetailViewModel(get(), path)
    }

    viewModel {
        SearchViewModel(get())
    }

    viewModel { (id: Long, name: String?) ->
        TagEditorViewModel(get(), id, name)
    }

    viewModel {
        LyricsViewModel(get())
    }

    viewModel {
        InfoViewModel(get())
    }

    viewModel {
        SoundSettingsViewModel(get())
    }
}

val appModules = listOf(networkModule, autoModule, mainModule, roomModule, dataModule, viewModule)