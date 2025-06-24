package com.mardous.booming.ui.screens.lyrics

import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mardous.booming.lyrics.Lyrics
import com.mardous.booming.ui.components.decoration.FadingEdges
import com.mardous.booming.ui.components.decoration.fadingEdges
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

@Composable
fun LyricsView(
    state: LyricsViewState,
    onLineClick: (Lyrics.Line) -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.secondary,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    fadingEdges: FadingEdges = FadingEdges.None,
    fontSize: TextUnit = 32.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    lineHeight: TextUnit = 1.2.em,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(state.currentLineIndex) {
        if (state.currentLineIndex >= 0) {
            if (!listState.isScrollInProgress) {
                listState.animateScrollToItem(state.currentLineIndex)
            }
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        modifier = modifier
            .fadingEdges(edges = fadingEdges)
            .fillMaxSize()
    ) {
        val lines = state.lyrics?.lines ?: emptyList()
        itemsIndexed(lines, key = { _, line -> line.startAt }) { index, line ->
            val isActive = index == state.currentLineIndex
            if (line.isWordByWord) {
                val activeWordIndex = if (isActive) state.currentWordIndex else -1
                LyricsLineWordByWord(
                    words = line.words,
                    isActive = isActive,
                    isOppositeTurn = line.isOppositeTurn,
                    activeWordIndex = activeWordIndex,
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    lineHeight = lineHeight,
                    contentColor = contentColor,
                    onClick = { onLineClick(line) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem(placementSpec = tween(durationMillis = 1000))
                )
            } else {
                LyricsViewLine(
                    isActive = isActive,
                    isOppositeTurn = line.isOppositeTurn,
                    content = line.content,
                    contentColor = contentColor,
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    lineHeight = lineHeight,
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
    content: String,
    contentColor: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    lineHeight: TextUnit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    activeScale: Float = 1.1f,
    inactiveScale: Float = 1f,
    activeAlpha: Float = 1f,
    inactiveAlpha: Float = 0.35f,
) {
    var scale by remember { mutableFloatStateOf(if (isActive) activeScale else inactiveScale) }
    var alpha by remember { mutableFloatStateOf(if (isActive) activeAlpha else inactiveAlpha) }

    LaunchedEffect(isActive) {
        launch {
            animate(
                initialValue = scale,
                targetValue = if (isActive) activeScale else inactiveScale,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow,
                )
            ) { value, _ ->
                scale = value
            }
        }
        launch {
            // Composable could suddenly go invisible for one frame (or few frame?) when
            // isActive changes to false and the alpha animation starts. Delay may help
            // to reduce these glitches
            repeat(10) { awaitFrame() }
            animate(
                initialValue = alpha,
                targetValue = if (isActive) activeAlpha else inactiveAlpha,
            ) { value, _ ->
                alpha = value
            }
        }
    }

    LyricsLineBox(
        modifier = modifier,
        isOppositeTurn = isOppositeTurn,
        contentColor = contentColor,
        onClick = onClick,
    ) { pivotFractionX: Float, pivotFractionY: Float ->
        Text(
            text = content,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(pivotFractionX, pivotFractionY)
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                },
            textAlign = if (isOppositeTurn) TextAlign.End else TextAlign.Start,
            color = contentColor,
            fontSize = fontSize,
            fontWeight = fontWeight,
            lineHeight = lineHeight,
        )
    }
}

@Composable
fun LyricsLineWordByWord(
    words: List<Lyrics.Word>,
    isActive: Boolean,
    isOppositeTurn: Boolean,
    activeWordIndex: Int,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    lineHeight: TextUnit,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    activeScale: Float = 1.1f,
    inactiveScale: Float = 1f,
    activeAlpha: Float = 1f,
    inactiveAlpha: Float = 0.35f,
) {
    var scale by remember { mutableFloatStateOf(if (isActive) activeScale else inactiveScale) }

    LaunchedEffect(isActive) {
        launch {
            animate(
                initialValue = scale,
                targetValue = if (isActive) activeScale else inactiveScale,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow,
                )
            ) { value, _ ->
                scale = value
            }
        }
    }

    LyricsLineBox(
        modifier = modifier,
        isOppositeTurn = isOppositeTurn,
        contentColor = contentColor,
        onClick = onClick
    ) { pivotFractionX: Float, pivotFractionY: Float ->
        val annotatedText = remember(words, activeWordIndex, isActive) {
            buildAnnotatedString {
                words.forEachIndexed { i, word ->
                    val alpha = if (isActive && i <= activeWordIndex) activeAlpha else inactiveAlpha
                    withStyle(SpanStyle(color = contentColor.copy(alpha = alpha))) {
                        append(word.content)
                    }
                }
            }
        }

        Text(
            text = annotatedText,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(pivotFractionX, pivotFractionY)
                    scaleX = scale
                    scaleY = scale
                },
            textAlign = if (isOppositeTurn) TextAlign.End else TextAlign.Start,
            fontSize = fontSize,
            fontWeight = fontWeight,
            lineHeight = lineHeight,
        )
    }
}

@Composable
fun LyricsLineBox(
    isOppositeTurn: Boolean,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (pivotFractionX: Float, pivotFractionY: Float) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val indication = ripple(color = contentColor)

    val paddingValues = if (isOppositeTurn) {
        PaddingValues(start = 32.dp, top = 8.dp, end = 16.dp, bottom = 8.dp)
    } else {
        PaddingValues(start = 16.dp, top = 8.dp, end = 32.dp, bottom = 8.dp)
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