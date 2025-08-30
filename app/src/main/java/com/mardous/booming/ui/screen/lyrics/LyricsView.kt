package com.mardous.booming.ui.screen.lyrics

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.mardous.booming.data.model.lyrics.Lyrics
import com.mardous.booming.data.model.lyrics.LyricsActor
import com.mardous.booming.ui.component.compose.decoration.FadingEdges
import com.mardous.booming.ui.component.compose.decoration.fadingEdges
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

@Composable
fun LyricsView(
    state: LyricsViewState,
    settings: LyricsViewSettings,
    textStyle: TextStyle,
    fadingEdges: FadingEdges,
    modifier: Modifier = Modifier,
    onLineClick: (Lyrics.Line) -> Unit
) {
    val density = LocalDensity.current
    val listState = rememberLazyListState()

    LaunchedEffect(state.currentLineIndex) {
        if (state.currentLineIndex >= 0) {
            if (!listState.isScrollInProgress) {
                if (settings.isCenterCurrentLine) {
                    listState.animateScrollToItem(
                        index = state.currentLineIndex,
                        scrollOffset = settings.calculateCenterOffset(
                            currentIndex = state.currentLineIndex,
                            listState = listState,
                            density = density
                        )
                    )
                } else {
                    listState.animateScrollToItem(state.currentLineIndex)
                }
            }
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = settings.contentPadding,
        modifier = modifier
            .nestedScroll(rememberNestedScrollInteropConnection())
            .fadingEdges(edges = fadingEdges)
            .fillMaxSize()
    ) {
        val lines = state.lyrics?.lines ?: emptyList()
        itemsIndexed(lines, key = { _, line -> line.id }) { index, line ->
            val isActive = index == state.currentLineIndex
            val textAlign = when (line.actor) {
                LyricsActor.Voice2,
                LyricsActor.Voice2Background -> TextAlign.End

                LyricsActor.Group,
                LyricsActor.GroupBackground,
                LyricsActor.Duet,
                LyricsActor.DuetBackground -> TextAlign.Center

                else -> TextAlign.Start
            }
            if (line.isWordByWord) {
                LyricsLineWordByWord(
                    line = line,
                    index = state.currentWordIndex,
                    backgroundIndex = state.currentBackgroundIndex,
                    position = state.position,
                    isActive = isActive,
                    isBackgroundActive = isActive && state.currentBackgroundIndex > -1,
                    style = textStyle,
                    align = textAlign,
                    onClick = { onLineClick(line) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem(placementSpec = tween(durationMillis = 1000))
                )
            } else {
                LyricsViewLine(
                    isActive = isActive,
                    progressiveColoring = settings.progressiveColoring,
                    position = state.position,
                    line = line,
                    style = textStyle,
                    align = textAlign,
                    onClick = { onLineClick(line) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem(placementSpec = tween(durationMillis = 1000))
                )
            }
        }
    }
}


@Composable
private fun LyricsViewLine(
    isActive: Boolean,
    progressiveColoring: Boolean,
    position: Long,
    line: Lyrics.Line,
    style: TextStyle,
    align: TextAlign,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val localContentColor = LocalContentColor.current

    val animatedAlpha by animateFloatAsState(
        targetValue = if (isActive) 1f else .5f,
        animationSpec = tween(durationMillis = 400),
        label = "current-line-alpha-animation"
    )

    val color = localContentColor.copy(alpha = animatedAlpha)

    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "current-line-scale-animation"
    )

    var textHeight by remember { mutableFloatStateOf(0f) }
    val textStyle by remember(isActive, progressiveColoring, position) {
        derivedStateOf {
            if (progressiveColoring) {
                val progressFraction = ((position.toFloat() - line.startAt) / (line.end - line.startAt))
                    .coerceIn(0f, 1f)

                val gradientOrigin = progressFraction * textHeight

                style.copy(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            color,
                            color.copy(alpha = color.alpha / 2)
                        ),
                        startY = gradientOrigin - 10f,
                        endY = gradientOrigin + 10f
                    )
                )
            } else {
                style.copy(color = color)
            }
        }
    }

    LyricsLineBox(
        modifier = modifier,
        style = style,
        align = align,
        onClick = onClick,
    ) { transformOrigin ->
        Text(
            text = line.content.trim(),
            style = textStyle,
            textAlign = align,
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned {
                    textHeight = it.size.height.toFloat()
                }
                .graphicsLayer {
                    this.transformOrigin = transformOrigin
                    scaleX = scale
                    scaleY = scale
                }
        )
    }
}

@Composable
fun LyricsLineWordByWord(
    line: Lyrics.Line,
    index: Int,
    backgroundIndex: Int,
    position: Long,
    isActive: Boolean,
    isBackgroundActive: Boolean,
    style: TextStyle,
    align: TextAlign,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasBackground = line.background.isNotEmpty()

    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.1f else 1f,
        label = "current-line-scale-animation"
    )

    val bgScale by animateFloatAsState(
        targetValue = if (isBackgroundActive) 1.1f else 1f,
        label = "current-line-scale-animation"
    )

    LyricsLineBox(
        modifier = modifier,
        style = style,
        align = align,
        onClick = onClick
    ) { transformOrigin ->
        if (!hasBackground) {
            WordByWordText(
                words = line.main,
                index = index,
                position = position,
                isActive = isActive,
                style = style,
                align = align,
                modifier = Modifier.graphicsLayer {
                    this.transformOrigin = transformOrigin
                    scaleX = scale
                    scaleY = scale
                }
            )
        } else {
            Column(
                modifier = Modifier.wrapContentSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WordByWordText(
                    words = line.main,
                    index = index,
                    position = position,
                    isActive = isActive,
                    style = style,
                    align = align,
                    modifier = Modifier.graphicsLayer {
                        this.transformOrigin = transformOrigin
                        scaleX = scale
                        scaleY = scale
                    }
                )

                WordByWordText(
                    words = line.background,
                    index = backgroundIndex,
                    position = position,
                    isActive = isBackgroundActive,
                    style = style.copy(fontSize = style.fontSize / 1.65),
                    align = align,
                    modifier = Modifier.graphicsLayer {
                        this.transformOrigin = transformOrigin
                        scaleX = bgScale
                        scaleY = bgScale
                    }
                )
            }
        }
    }
}

@Composable
fun WordByWordText(
    words: List<Lyrics.Word>,
    index: Int,
    position: Long,
    isActive: Boolean,
    style: TextStyle,
    align: TextAlign,
    modifier: Modifier = Modifier
) {
    val contentColor = LocalContentColor.current
    val annotatedText = remember(words, index, isActive, position) {
        buildAnnotatedString {
            words.forEachIndexed { i, word ->
                val alpha = if (isActive && i <= index) 1f else .5f
                withStyle(SpanStyle(color = contentColor.copy(alpha = alpha))) {
                    if (i == words.lastIndex) {
                        append(word.content.trimEnd())
                    } else {
                        append(word.content)
                    }
                }
            }
        }
    }

    Text(
        text = annotatedText,
        style = style,
        textAlign = align,
        modifier = modifier
            .fillMaxWidth()
    )
}

@Composable
fun LyricsLineBox(
    style: TextStyle,
    align: TextAlign,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (transformOrigin: TransformOrigin) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val indication = ripple(color = LocalContentColor.current)

    val transformOrigin = when (align) {
        TextAlign.End -> TransformOrigin(1f, 1f)
        TextAlign.Start -> TransformOrigin(0f, 1f)
        else -> TransformOrigin.Center
    }

    val verticalPadding = (8f * style.lineHeight.value).dp
    val paddingValues = when (align) {
        TextAlign.End -> PaddingValues(
            start = 32.dp,
            top = verticalPadding,
            end = 8.dp,
            bottom = verticalPadding
        )
        TextAlign.Start -> PaddingValues(
            start = 8.dp,
            top = verticalPadding,
            end = 32.dp,
            bottom = verticalPadding
        )
        else -> PaddingValues(horizontal = 32.dp, vertical = verticalPadding)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .indication(interactionSource, indication)
            .pointerInput(interactionSource) {
                detectTapGestures(
                    onPress = {
                        val press = PressInteraction.Press(it)
                        try {
                            // Do not show indications (ripples) if the tap is done in 100ms since
                            // ripple animations will impact the performance of other animations
                            withTimeout(timeMillis = 100) {
                                tryAwaitRelease()
                            }
                        } catch (_: TimeoutCancellationException) {
                            interactionSource.emit(press)
                            tryAwaitRelease()
                        }
                        interactionSource.emit(PressInteraction.Release(press))
                    },
                    onTap = { onClick() },
                )
            }
            .padding(paddingValues)
    ) {
        content(transformOrigin)
    }
}