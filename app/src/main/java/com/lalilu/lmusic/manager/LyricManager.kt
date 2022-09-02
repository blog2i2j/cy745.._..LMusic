package com.lalilu.lmusic.manager

import com.dirror.lyricviewx.LyricEntry
import com.dirror.lyricviewx.LyricUtil
import com.lalilu.lmedia.indexer.Library
import com.lalilu.lmusic.service.LMusicRuntime
import com.lalilu.lmusic.utils.sources.LyricSourceFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 专门负责歌词解析处理的全局单例
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class LyricManager @Inject constructor(
    lyricSourceFactory: LyricSourceFactory
) {
    private val currentLyric: Flow<Pair<String, String?>?> =
        LMusicRuntime.currentPlayingFlow.mapLatest { item ->
            val song = item?.let { Library.getSongOrNull(it.id) } ?: return@mapLatest null
            lyricSourceFactory.getLyric(song)
        }

    val currentSentence: Flow<String?> = currentLyric.mapLatest { pair ->
        pair ?: return@mapLatest null
        LyricUtil.parseLrc(arrayOf(pair.first, pair.second))
    }.combine(LMusicRuntime.currentPositionFlow) { list, time ->
        if (list == null || time == 0L) return@combine null

        val index = findShowLine(list, time + 350)
        val lyricEntry = list.getOrNull(index)
        val nowLyric = lyricEntry?.text ?: lyricEntry?.secondText

        return@combine nowLyric to index
    }.distinctUntilChanged()
        .combine(LMusicRuntime.currentIsPlayingFlow) { pair, isPlaying ->
            if (pair == null || !isPlaying) return@combine null
            return@combine pair.first
        }

    private fun findShowLine(list: List<LyricEntry>?, time: Long): Int {
        if (list == null || list.isEmpty()) return 0
        var left = 0
        var right = list.size
        while (left <= right) {
            val middle = (left + right) / 2
            val middleTime = list[middle].time
            if (time < middleTime) {
                right = middle - 1
            } else {
                if (middle + 1 >= list.size || time < list[middle + 1].time) {
                    return middle
                }
                left = middle + 1
            }
        }
        return 0
    }
}