package com.lalilu.component.extension

import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.dynamicanimation.animation.springAnimationOf
import androidx.dynamicanimation.animation.withSpringForceProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.random.Random

class LazyListAnimateScroller internal constructor(
    private val keysKeeper: () -> Collection<Any>,
    private val listState: LazyListState,
    private val currentValue: MutableFloatState,
    private val targetValue: MutableFloatState,
    private val deltaValue: MutableFloatState,
    private val targetRange: MutableState<IntRange>,
    private val sizeMap: SnapshotStateMap<Int, Int>
) {
    private val keyEvent: MutableStateFlow<Any> = MutableStateFlow(Any())
    val animator: SpringAnimation = springAnimationOf(
        getter = { currentValue.floatValue },
        setter = {
            deltaValue.floatValue = it - currentValue.floatValue
            currentValue.floatValue = it
        },
        finalPosition = 0f
    ).withSpringForceProperties {
        dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
        stiffness = SpringForce.STIFFNESS_VERY_LOW
    }.addEndListener { animation, canceled, value, velocity ->
        if (!canceled) {
            targetRange.value = IntRange.EMPTY
        }
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    internal suspend fun startLoop(scope: CoroutineScope) = withContext(scope.coroutineContext) {
        keyEvent.mapLatest { key ->
            // 1. 从当前可见元素直接查找offset （准确值）
            // get the offset directly from the visibleItemsInfo
            val offset = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.key == key }
                ?.offset

            if (offset != null) {
                animator.cancel()
                currentValue.floatValue = 0f
                targetValue.floatValue =
                    offset.toFloat() + Random(System.currentTimeMillis()).nextFloat() * 0.1f
                // 添加随机值，为了确保能触发LaunchedEffect重组
                // add random value to offset, to ensure that LaunchedEffect will be recomposed

                println("[visible target]: ${targetValue.floatValue}")
                return@mapLatest null
            }

            return@mapLatest key
        }.debounce(20L)
            .collectLatest { key ->
                if (key == null) return@collectLatest

                // 2. 使用实时维护的sizeMap查找并计算目标元素的offset （非准确值）
                // Use the real-time maintained sizeMap to find and calculate the offset of the target element
                val index = keysKeeper().indexOfFirst { it == key }
                if (index == -1) return@collectLatest // 元素不存在keys列表中，则不进行滚动

                if (!isActive) return@collectLatest
                val firstVisibleIndex = listState.firstVisibleItemIndex
                val firstVisibleOffset = listState.firstVisibleItemScrollOffset
                targetRange.value = minOf(firstVisibleIndex, index)..maxOf(firstVisibleIndex, index)

                // 计算方向乘数，向下滚动则为正数
                // calculate the direction multiplier, if scrolling down, it's positive
                val forwardMultiple = if (index >= firstVisibleIndex) 1f else -1f

                if (!isActive) return@collectLatest
                // 计算目标距离，若未缓存有相应位置的值，则计算使用平均值
                // calculate the target offset，if these no value cached then use the average value
                val sizeAverage = sizeMap.values.average().toInt()
                val sizeSum = targetRange.value.sumOf { sizeMap.getOrPut(it) { sizeAverage } }
                val spacingSum =
                    (targetRange.value.last - targetRange.value.first) * listState.layoutInfo.mainAxisItemSpacing
                var offsetTemp = (sizeSum + spacingSum) * forwardMultiple

                val firstVisibleHeight = listState.layoutInfo.visibleItemsInfo
                    .getOrNull(firstVisibleIndex)?.size
                    ?: sizeMap[firstVisibleIndex]
                    ?: sizeAverage

                // 针对firstVisibleItem的边界情况修正offset值
                // fix the offset value for the boundary case of the firstVisibleItem
                offsetTemp += if (forwardMultiple > 0) firstVisibleOffset else (firstVisibleOffset - firstVisibleHeight)

                // 使用非准确值进行滚动
                // use the non-accurate value for scrolling
                if (!isActive) return@collectLatest
                animator.cancel()
                currentValue.floatValue = 0f
                targetValue.floatValue =
                    offsetTemp + Random(System.currentTimeMillis()).nextFloat() * 0.1f

                println("[calculate target]: ${targetValue.floatValue} -> range: [${targetRange.value.first} -> ${targetRange.value.last}]")
            }
    }

    fun animateTo(key: Any) {
        keyEvent.tryEmit(key)
    }
}

@Composable
fun rememberLazyListAnimateScroller(
    listState: LazyListState,
    keysKeeper: () -> Collection<Any> = { emptyList() },
): LazyListAnimateScroller {
    val isDragged = listState.interactionSource.collectIsDraggedAsState()
    val currentValue = remember { mutableFloatStateOf(0f) }
    val targetValue = remember { mutableFloatStateOf(0f) }
    val deltaValue = remember { mutableFloatStateOf(0f) }
    val targetRange = remember { mutableStateOf(IntRange(0, 0)) }
    val sizeMap = remember { mutableStateMapOf<Int, Int>() }

    val scroller = remember {
        LazyListAnimateScroller(
            listState = listState,
            currentValue = currentValue,
            targetValue = targetValue,
            deltaValue = deltaValue,
            targetRange = targetRange,
            sizeMap = sizeMap,
            keysKeeper = keysKeeper
        )
    }

    LaunchedEffect(targetValue.floatValue) {
        scroller.animator.animateToFinalPosition(targetValue.floatValue)
    }

    LaunchedEffect(deltaValue.floatValue) {
        if (isDragged.value) return@LaunchedEffect
        listState.scroll { scrollBy(deltaValue.floatValue) }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }
            .distinctUntilChanged()
            .collectLatest { list ->
                list.forEach {
                    // 若当前更新的元素处于动画偏移值计算数据源的目标范围中
                    if (it.index in targetRange.value && scroller.animator.isRunning) {
                        val delta = it.size - (sizeMap[it.index] ?: 0)

                        if (delta != 0) {
                            // 则将变化值直接更新到targetValue，触发动画位移修正
                            println("update targetValue:  [${it.index} -> $delta]")
                            targetValue.floatValue += delta
                        }
                    }
                    sizeMap[it.index] = it.size
                }
            }
    }

    LaunchedEffect(Unit) {
        scroller.startLoop(this)
    }

    return scroller
}