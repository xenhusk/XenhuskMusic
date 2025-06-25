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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mardous.booming.R
import com.mardous.booming.viewmodels.lyrics.LyricsViewModel
import com.mardous.booming.ui.components.decoration.FadingEdges
import com.mardous.booming.viewmodels.PlaybackViewModel

@Composable
fun LyricsScreen(
    lyricsViewModel: LyricsViewModel,
    playbackViewModel: PlaybackViewModel,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier
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
                        onLineClick = { playbackViewModel.seekTo(it.startAt) },
                        fadingEdges = FadingEdges(top = 56.dp, bottom = 32.dp),
                        contentPadding = PaddingValues(
                            top = 72.dp,
                            bottom = dimensionResource(R.dimen.fab_size_padding)
                        )
                    )
                }

                !lyricsResult.plainLyrics.isNullOrBlank() -> {
                    PlainLyricsView(content = lyricsResult.plainLyrics!!)
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
fun CoverLyricsScreen(
    lyricsViewModel: LyricsViewModel,
    playbackViewModel: PlaybackViewModel,
    modifier: Modifier = Modifier
) {
    val lyricsResult by lyricsViewModel.lyricsResult.collectAsState()
    val songProgress by playbackViewModel.progressFlow.collectAsState()
    val lyricsViewState = remember(lyricsResult.syncedLyrics) {
        LyricsViewState(lyricsResult.syncedLyrics)
    }

    LaunchedEffect(songProgress) {
        lyricsViewState.updatePosition(songProgress)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(all = 8.dp)
    ) {
        if (lyricsResult.loading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            when {
                lyricsViewState.lyrics != null -> {
                    LyricsView(
                        state = lyricsViewState,
                        onLineClick = { playbackViewModel.seekTo(it.startAt) },
                        fadingEdges = FadingEdges(top = 72.dp, bottom = 64.dp),
                        fontSize = 24.sp,
                        contentPadding = PaddingValues(
                            top = 72.dp,
                            bottom = dimensionResource(R.dimen.fab_size_padding)
                        )
                    )
                }

                !lyricsResult.plainLyrics.isNullOrBlank() -> {
                    PlainLyricsView(
                        content = lyricsResult.plainLyrics!!,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
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
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 20.sp,
    textAlign: TextAlign? = null,
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
            textAlign = textAlign,
            fontSize = fontSize,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )
    }
}