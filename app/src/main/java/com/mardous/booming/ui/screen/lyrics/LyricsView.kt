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
            if (line.isWordByWord) {
                LyricsLineWordByWord(
                    line = line,
                    index = state.currentWordIndex,
                    backgroundIndex = state.currentBackgroundIndex,
                    position = state.position,
                    isActive = isActive,
                    isBackgroundActive = isActive && state.currentBackgroundIndex > -1,
                    isOppositeTurn = line.isOppositeTurn,
                    style = textStyle,
                    onClick = { onLineClick(line) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem(placementSpec = tween(durationMillis = 1000))
                )
            } else {
                LyricsViewLine(
                    isActive = isActive,
                    isOppositeTurn = line.isOppositeTurn,
                    progressiveColoring = settings.progressiveColoring,
                    position = state.position,
                    line = line,
                    style = textStyle,
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
    isOppositeTurn: Boolean,
    progressiveColoring: Boolean,
    position: Long,
    line: Lyrics.Line,
    style: TextStyle,
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
        isOppositeTurn = isOppositeTurn,
        onClick = onClick,
    ) { pivotFractionX: Float, pivotFractionY: Float ->
        Text(
            text = line.content.trim(),
            style = textStyle,
            textAlign = if (isOppositeTurn) TextAlign.End else TextAlign.Start,
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned {
                    textHeight = it.size.height.toFloat()
                }
                .graphicsLayer {
                    transformOrigin = TransformOrigin(pivotFractionX, pivotFractionY)
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
    isOppositeTurn: Boolean,
    style: TextStyle,
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
        isOppositeTurn = isOppositeTurn,
        onClick = onClick
    ) { pivotFractionX: Float, pivotFractionY: Float ->
        if (!hasBackground) {
            WordByWordText(
                words = line.main,
                index = index,
                position = position,
                isOppositeTurn = isOppositeTurn,
                isActive = isActive,
                style = style,
                pivotFractionX = pivotFractionX,
                pivotFractionY = pivotFractionY,
                scale = scale
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
                    isOppositeTurn = isOppositeTurn,
                    isActive = isActive,
                    style = style,
                    pivotFractionX = pivotFractionX,
                    pivotFractionY = pivotFractionY,
                    scale = scale
                )

                WordByWordText(
                    words = line.background,
                    index = backgroundIndex,
                    position = position,
                    isOppositeTurn = isOppositeTurn,
                    isActive = isBackgroundActive,
                    style = style.copy(fontSize = style.fontSize / 1.65),
                    pivotFractionX = pivotFractionX,
                    pivotFractionY = pivotFractionY,
                    scale = bgScale
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
    isOppositeTurn: Boolean,
    style: TextStyle,
    pivotFractionX: Float,
    pivotFractionY: Float,
    scale: Float,
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
        textAlign = if (isOppositeTurn) TextAlign.End else TextAlign.Start,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                transformOrigin = TransformOrigin(pivotFractionX, pivotFractionY)
                scaleX = scale
                scaleY = scale
            }
    )
}

@Composable
fun LyricsLineBox(
    isOppositeTurn: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (pivotFractionX: Float, pivotFractionY: Float) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val indication = ripple(color = LocalContentColor.current)

    val paddingValues = if (isOppositeTurn) {
        PaddingValues(start = 32.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)
    } else {
        PaddingValues(start = 8.dp, top = 8.dp, end = 32.dp, bottom = 8.dp)
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
                        } catch (e: TimeoutCancellationException) {
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
        content(if (isOppositeTurn) 1f else 0f, 1f)
    }
}