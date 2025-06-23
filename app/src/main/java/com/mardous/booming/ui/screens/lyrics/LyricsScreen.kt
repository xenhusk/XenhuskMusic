package com.mardous.booming.ui.screens.lyrics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mardous.booming.R
import com.mardous.booming.fragments.lyrics.LyricsViewModel
import com.mardous.booming.lyrics.Lyrics
import com.mardous.booming.ui.components.decoration.FadingEdges
import com.mardous.booming.viewmodels.PlaybackViewModel

@Composable
fun LyricsScreen(
    lyricsViewModel: LyricsViewModel,
    playbackViewModel: PlaybackViewModel,
    onEditClick: () -> Unit,
    onSeekToLine: (Lyrics.Line) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lyricsResult by lyricsViewModel.lyricsResult.collectAsState()
    val songProgress by playbackViewModel.progressFlow.collectAsState()
    val lyricsViewState = remember(lyricsResult.syncedLyrics) {
        LyricsViewState(lyricsResult.syncedLyrics)
    }

    LaunchedEffect(songProgress) {
        lyricsViewState.updatePosition(songProgress)
    }

    Scaffold(
        modifier = modifier.padding(bottom = dimensionResource(R.dimen.mini_player_height)),
        floatingActionButton = {
            FloatingActionButton(
                onClick = onEditClick,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_edit_note_24dp),
                    contentDescription = stringResource(R.string.open_lyrics_editor)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (lyricsResult.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else when {
                lyricsViewState.lyrics != null -> {
                    LyricsView(
                        state = lyricsViewState,
                        onLineClick = onSeekToLine,
                        fadingEdges = FadingEdges(top = 56.dp, bottom = 32.dp),
                        contentPadding = PaddingValues(
                            top = 72.dp,
                            bottom = dimensionResource(R.dimen.fab_size_padding)
                        )
                    )
                }

                !lyricsResult.plainLyrics.isNullOrBlank() -> {
                    PlainLyricsView(lyricsResult.plainLyrics!!)
                }

                else -> {
                    Text(
                        text = stringResource(R.string.no_lyrics_found),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .align(Alignment.Center)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlainLyricsView(
    content: String,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp)
            .padding(
                top = 72.dp,
                bottom = dimensionResource(R.dimen.fab_size_padding)
            )
    ) {
        Text(
            text = content,
            fontSize = 20.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )
    }
}