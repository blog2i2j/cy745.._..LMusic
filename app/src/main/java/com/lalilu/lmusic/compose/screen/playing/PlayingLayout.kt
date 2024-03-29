package com.lalilu.lmusic.compose.screen.playing

import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.dirror.lyricviewx.LyricUtil
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.lalilu.component.extension.hideControl
import com.lalilu.component.extension.singleViewModel
import com.lalilu.lmusic.compose.component.playing.LyricViewToolbar
import com.lalilu.lmusic.compose.component.playing.PlayingToolbar
import com.lalilu.lmusic.datastore.SettingsSp
import com.lalilu.lmusic.viewmodel.PlayingViewModel
import com.lalilu.lplayer.LPlayer
import com.lalilu.lplayer.extensions.PlayerAction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.mapLatest
import org.koin.compose.koinInject
import kotlin.math.pow

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun PlayingLayout(
    playingVM: PlayingViewModel = singleViewModel(),
    settingsSp: SettingsSp = koinInject()
) {
    val haptic = LocalHapticFeedback.current
    val systemUiController = rememberSystemUiController()
    val lyricLayoutLazyListState = rememberLazyListState()

    val isLyricScrollEnable = remember { mutableStateOf(false) }
    val recyclerViewScrollState = remember { mutableStateOf(false) }
    val backgroundColor = remember { mutableStateOf(Color.DarkGray) }
    val animateColor = animateColorAsState(targetValue = backgroundColor.value, label = "")
    val scrollToTopEvent = remember { mutableStateOf(0L) }
    val seekbarTime = remember { mutableLongStateOf(0L) }

    val draggable = rememberCustomAnchoredDraggableState { oldState, newState ->
        if (newState == DragAnchor.MiddleXMax && oldState != DragAnchor.MiddleXMax) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        if (newState != DragAnchor.Max) {
            isLyricScrollEnable.value = false
        }
    }

    val hideComponent = remember {
        derivedStateOf {
            settingsSp.autoHideSeekbar.value && draggable.state.value == DragAnchor.Max
        }
    }

    LaunchedEffect(hideComponent.value) {
        systemUiController.isStatusBarVisible = !hideComponent.value
    }

    NestedScrollBaseLayout(
        draggable = draggable,
        isLyricScrollEnable = isLyricScrollEnable,
        scrollingItemType = {
            when {
                lyricLayoutLazyListState.isScrollInProgress -> ScrollingItemType.LyricView
                recyclerViewScrollState.value -> ScrollingItemType.RecyclerView
                else -> null
            }
        },
        toolbarContent = {
            Column(
                modifier = Modifier
                    .hideControl(
                        enable = { hideComponent.value },
                        intercept = { true }
                    )
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(bottom = 10.dp)
            ) {
                PlayingToolbar(
                    isItemPlaying = { mediaId -> playingVM.isItemPlaying { it.mediaId == mediaId } },
                    isUserTouchEnable = { draggable.state.value == DragAnchor.Min || draggable.state.value == DragAnchor.Max },
                    isExtraVisible = { draggable.state.value == DragAnchor.Max },
                    onClick = { scrollToTopEvent.value = System.currentTimeMillis() },
                    extraContent = { LyricViewToolbar() }
                )
            }
        },
        dynamicHeaderContent = { constraints ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .background(color = animateColor.value)
            ) {
                val adInterpolator = remember { AccelerateDecelerateInterpolator() }
                val dInterpolator = remember { DecelerateInterpolator() }
                val transition: (Float) -> Float = remember {
                    { x -> -2f * (x - 0.5f).pow(2) + 0.5f }
                }

                val flow = remember {
                    playingVM.lyricRepository.currentLyric
                        .mapLatest {
                            LyricUtil
                                .parseLrc(arrayOf(it?.first, it?.second))
                                ?.mapIndexed { index, lyricEntry ->
                                    LyricEntry(
                                        index = index,
                                        time = lyricEntry.time,
                                        text = lyricEntry.text,
                                        translate = lyricEntry.secondText
                                    )
                                }
                                ?: emptyList()
                        }
                }
                val lyricEntry = flow.collectAsState(initial = emptyList())
                val minToMiddleProgress = remember {
                    derivedStateOf {
                        draggable.progressBetween(
                            from = DragAnchor.Min,
                            to = DragAnchor.Middle,
                            offset = draggable.position.floatValue
                        )
                    }
                }
                val middleToMaxProgress = remember {
                    derivedStateOf {
                        draggable.progressBetween(
                            from = DragAnchor.Middle,
                            to = DragAnchor.Max,
                            offset = draggable.position.floatValue
                        )
                    }
                }

                BlurBackground(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .graphicsLayer {
                            val maxHeight = constraints.maxHeight
                            val maxWidth = constraints.maxWidth

                            // min至middle阶段中的位移
                            val minToMiddleInterpolated =
                                dInterpolator.getInterpolation(minToMiddleProgress.value)
                            val minToMiddleOffset =
                                lerp(-size.width / 2f, 0f, minToMiddleInterpolated)

                            // middle至max阶段中的位移
                            val middleToMaxInterpolated =
                                dInterpolator.getInterpolation(middleToMaxProgress.value)
                            val middleToMaxOffset =
                                lerp(0f, (maxHeight - maxWidth) / 2f, middleToMaxInterpolated)

                            // 用于补偿修正因layout时根据draggable的值进行布局的位移
                            val fixOffset = maxHeight - draggable.position.floatValue

                            // 添加凸显滑动时的动画的位移
                            val progressTransited = transition(middleToMaxProgress.value)
                            val additionalOffset = progressTransited * 200f

                            // 计算父级容器的长宽比，计算需要覆盖父级容器的的缩放比例的值scale
                            val aspectRatio = maxHeight.toFloat() / maxWidth.toFloat()
                            val scale = lerp(1f, aspectRatio, middleToMaxProgress.value)

                            translationY =
                                minToMiddleOffset + middleToMaxOffset + fixOffset + additionalOffset
                            alpha = minToMiddleProgress.value
                            scaleY = scale
                            scaleX = scale
                        },
                    blurProgress = { middleToMaxProgress.value },
                    onBackgroundColorFetched = { backgroundColor.value = it },
                    imageData = {
                        playingVM.playing.value
                            ?: com.lalilu.component.R.drawable.ic_music_2_line_100dp
                    }
                )

                LyricLayout(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val interpolation =
                                adInterpolator.getInterpolation(middleToMaxProgress.value)
                            val progressIncrease = (2 * interpolation - 1F).coerceAtLeast(0F)

                            val fixOffset = size.height - draggable.position.floatValue

                            val progressTransited = transition(middleToMaxProgress.value)
                            val additionalOffset = progressTransited * 200f * 3f

                            translationY = additionalOffset + fixOffset
                            alpha = progressIncrease
                        },
                    lyricEntry = lyricEntry,
                    listState = lyricLayoutLazyListState,
                    currentTime = { seekbarTime.longValue },
                    maxWidth = { constraints.maxWidth },
                    textSize = rememberTextSizeFromInt { settingsSp.lyricTextSize.value },
                    textAlign = rememberTextAlignFromGravity { settingsSp.lyricGravity.value },
                    fontFamily = rememberFontFamilyFromPath { settingsSp.lyricTypefacePath.value },
                    isBlurredEnable = { !isLyricScrollEnable.value && settingsSp.isEnableBlurEffect.value },
                    isTranslationShow = { settingsSp.isDrawTranslation.value },
                    isUserClickEnable = { draggable.state.value == DragAnchor.Max },
                    isUserScrollEnable = { isLyricScrollEnable.value },
                    onPositionReset = {
                        if (isLyricScrollEnable.value) {
                            isLyricScrollEnable.value = false
                        }
                    },
                    onItemClick = {
                        if (isLyricScrollEnable.value) {
                            isLyricScrollEnable.value = false
                        }
                        LPlayer.controller.doAction(PlayerAction.SeekTo(it.time))
                    },
                    onItemLongClick = {
                        if (draggable.state.value == DragAnchor.Max) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isLyricScrollEnable.value = !isLyricScrollEnable.value
                        }
                    },
                )
            }
        },
        recyclerViewContent = {
            CustomRecyclerView(
                modifier = Modifier.clipToBounds(),
                scrollToTopEvent = { scrollToTopEvent.value },
                onScrollStart = { recyclerViewScrollState.value = true },
                onScrollTouchUp = { },
                onScrollIdle = {
                    recyclerViewScrollState.value = false
                    draggable.fling(0f)
                }
            )
        },
        overlayContent = {
            val animateProgress = animateFloatAsState(
                targetValue = if (!isLyricScrollEnable.value) 100f else 0f,
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = ""
            )

            SeekbarLayout(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        alpha = animateProgress.value / 100f
                        translationY = (1f - animateProgress.value / 100f) * 500f
                    },
                seekBarModifier = Modifier.hideControl(enable = { hideComponent.value }),
                onValueChange = { seekbarTime.longValue = it },
                animateColor = animateColor
            )
        }
    )
}