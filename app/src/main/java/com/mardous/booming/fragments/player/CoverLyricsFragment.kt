package com.mardous.booming.fragments.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.mardous.booming.fragments.lyrics.LyricsViewModel
import com.mardous.booming.ui.screens.lyrics.CoverLyricsScreen
import com.mardous.booming.ui.theme.BoomingMusicTheme
import com.mardous.booming.viewmodels.PlaybackViewModel
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class CoverLyricsFragment : Fragment() {

    private val lyricsViewModel: LyricsViewModel by activityViewModel()
    private val playbackViewModel: PlaybackViewModel by activityViewModel()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                BoomingMusicTheme {
                    CoverLyricsScreen(lyricsViewModel, playbackViewModel)
                }
            }
        }
    }
}