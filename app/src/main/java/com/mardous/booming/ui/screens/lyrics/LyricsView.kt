package com.mardous.booming.ui.screens.lyrics

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.util.packInts
import androidx.compose.ui.util.unpackInt1
import androidx.compose.ui.util.unpackInt2
import com.mardous.booming.lyrics.Lyrics
import com.mardous.booming.ui.components.decoration.FadingEdges
import com.mardous.booming.ui.components.decoration.fadingEdges
import kotlinx.coroutines.*
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@JvmInline
@Immutable
private value class ItemInfo(val packedValue: Long) {
    val offsetY: Int get() = unpackInt1(packedValue)

    val height: Int get() = unpackInt2(packedValue)
}

private fun ItemInfo(offsetY: Int, height: Int): ItemInfo {
    return ItemInfo(packInts(offsetY, height))
}

@OptIn(FlowPreview::class)
@Composable
fun LyricsView(
    state: LyricsViewState,
    onLineClick: (Lyrics.Line) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    fadingEdges: FadingEdges = FadingEdges.None,
    fontSize: TextUnit = 32.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    lineHeight: TextUnit = 1.2.em,
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var lyricsHeight by remember { mutableIntStateOf(0) }
    val itemsInfo = remember { mutableMapOf<Int, ItemInfo>() }
    var initialItemsOffsetY by remember { mutableIntStateOf(0) }
    var currItemsOffsetY by remember { mutableIntStateOf(0) }
    var animationItemsRange by remember { mutableStateOf(-1..-1) }
    var itemsAnimationJob by remember { mutableStateOf<Job?>(null) }

    fun updateItemInfo(index: Int, itemCoordinates: LayoutCoordinates) {
        itemsInfo[index] = ItemInfo(
            offsetY = itemCoordinates.positionInParent().y.toInt(),
            height = itemCoordinates.size.height,
        )
    }

    fun getAnimationItemsRange(currentIndex: Int): IntRange {
        val lines = state.lyrics?.lines ?: return -1..-1
        val currItemInfo = itemsInfo[currentIndex] ?: return -1..-1
        val scrollY = scrollState.value
        var start = -1
        var end = -1
        for (i in lines.indices) {
            val itemInfo = itemsInfo[i] ?: continue
            val itemTop = itemInfo.offsetY
            val itemBottom = itemTop + itemInfo.height
            if (itemBottom < scrollY) continue
            if (start == -1) start = i
            if (itemTop > currItemInfo.offsetY + lyricsHeight) break else end = i
        }
        return start..end
    }

    fun getItemOffsetY(index: Int): Int {
        return if (index in animationItemsRange) {
            val value = currItemsOffsetY
            if (index > state.currentLineIndex) {
                val factor = (1f + (index - state.currentLineIndex) * 0.08f)
                val progress = currItemsOffsetY.toFloat() / initialItemsOffsetY
                val finalProgress = (progress * factor).coerceAtMost(1f)
                (initialItemsOffsetY * finalProgress).toInt()
            } else value
        } else 0
    }

    fun startItemsAnimation(targetItemIndex: Int) {
        val targetItemTop = itemsInfo[targetItemIndex]?.offsetY ?: return
        itemsAnimationJob?.cancel()
        itemsAnimationJob = scope.launch {
            val targetScrollY = targetItemTop.coerceAtMost(scrollState.maxValue)
            val diff = targetScrollY - scrollState.value
            animationItemsRange = getAnimationItemsRange(targetItemIndex)
            scrollState.scrollTo(targetScrollY)
            Snapshot.withoutReadObservation { initialItemsOffsetY = diff }
            currItemsOffsetY = diff
            animate(
                initialValue = diff.toFloat(),
                targetValue = 0f,
                animationSpec = tween(durationMillis = 1000)
            ) { value, _ -> currItemsOffsetY = value.toInt() }
        }
    }

    LaunchedEffect(scrollState, state) {
        snapshotFlow { state.currentLineIndex }
            .filter { it >= 0 }
            .distinctUntilChanged()
            .debounce(100)
            .collect(::startItemsAnimation)
    }

    Box(
        modifier = modifier
            .fadingEdges(edges = fadingEdges)
            .onSizeChanged { lyricsHeight = it.height }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(state = scrollState)
                .padding(contentPadding)
        ) {
            val lines = state.lyrics?.lines ?: emptyList()
            for ((index, line) in lines.withIndex()) {
                val isActive = index == state.currentLineIndex
                val contentColor = if (isActive) {
                    if (line.isOppositeTurn) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
                if (line.isWordByWord) {
                    val activeWordIndex = if (isActive) state.currentWordIndex else -1
                    LyricsLineWordByWord(
                        words = line.words,
                        isActive = isActive,
                        activeWordIndex = activeWordIndex,
                        fontSize = fontSize,
                        fontWeight = fontWeight,
                        lineHeight = lineHeight,
                        contentColor = contentColor,
                        onClick = { onLineClick(line) },
                        offsetYProvider = { getItemOffsetY(index) },
                        modifier = Modifier.onGloballyPositioned { updateItemInfo(index, it) }
                    )
                } else {
                    LyricsViewLine(
                        isActive = isActive,
                        content = line.content,
                        contentColor = contentColor,
                        fontSize = fontSize,
                        fontWeight = fontWeight,
                        lineHeight = lineHeight,
                        onClick = { onLineClick(line) },
                        offsetYProvider = { getItemOffsetY(index) },
                        modifier = Modifier.onGloballyPositioned { updateItemInfo(index, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricsViewLine(
    isActive: Boolean,
    content: String,
    contentColor: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    lineHeight: TextUnit,
    onClick: () -> Unit,
    offsetYProvider: () -> Int,
    modifier: Modifier = Modifier,
    activeScale: Float = 1.1f,
    inactiveScale: Float = 1f,
    activeAlpha: Float = 1f,
    inactiveAlpha: Float = 0.35f,
) {
    var scale by remember { mutableFloatStateOf(if (isActive) activeScale else inactiveScale) }
    var alpha by remember { mutableFloatStateOf(if (isActive) activeAlpha else inactiveAlpha) }

    val interactionSource = remember { MutableInteractionSource() }
    val indication = ripple(color = contentColor)

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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(0, offsetYProvider()) }
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
            .padding(
                start = 16.dp,
                top = 8.dp,
                end = 32.dp,
                bottom = 16.dp,
            ),
    ) {
        Text(
            text = content,
            modifier = Modifier
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 1f)
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                },
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
    activeWordIndex: Int,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    lineHeight: TextUnit,
    contentColor: Color,
    onClick: () -> Unit,
    offsetYProvider: () -> Int,
    modifier: Modifier = Modifier,
    activeAlpha: Float = 1f,
    inactiveAlpha: Float = 0.35f,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(0, offsetYProvider()) }
            .clip(MaterialTheme.shapes.medium)
            .indication(interactionSource, ripple(color = contentColor))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        FlowRow(modifier = Modifier.wrapContentHeight()) {
            for ((i, word) in words.withIndex()) {
                val isHighlighted = isActive && i <= activeWordIndex
                val animatedAlpha by animateFloatAsState(
                    targetValue = if (isHighlighted) activeAlpha else inactiveAlpha,
                    animationSpec = tween(200)
                )
                Text(
                    text = word.content,
                    color = contentColor.copy(alpha = animatedAlpha),
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    lineHeight = lineHeight
                )
            }
        }
    }
}