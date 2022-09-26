package com.lalilu.lmusic.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.os.Build
import androidx.annotation.RequiresApi
import com.lalilu.lmedia.entity.LSong
import com.lalilu.lmusic.datasource.MDataBase
import com.lalilu.lmusic.utils.CoroutineSynchronizer
import com.lalilu.lmusic.utils.StatusBarLyricExt
import com.lalilu.lmusic.utils.extension.findShowLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

/**
 *
 */
@Singleton
class LMusicNotificationImpl constructor(
    private val mContext: LMusicService,
    private val database: MDataBase,
    private val playBack: LMusicPlayBack<LSong>
) : LMusicNotification(mContext), CoroutineScope {
    override val coroutineContext: CoroutineContext = Dispatchers.Default
    private val synchronizer = CoroutineSynchronizer()

    var statusLyricEnable = true
        set(value) {
            field = value
            if (value) startLyricPushCycle() else cancelLyricNotification()
        }

    /**
     *  API 26 以上需要注册Channel，否则不显示通知。
     */
    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
        notificationManager.cancelAll()
        LMusicLyricManager.currentLyricEntry.launchIn(this)
    }

    override fun fillData(data: Any?): Any? {
        data ?: return null
        if (data !is LSong) return null
        return database.networkDataDao().getById(data.id)
            ?.requireCoverUri()
            ?: data
    }

    override fun getImageData(): Any? = playBack.getCurrent()
    override fun getIsPlaying(): Boolean = playBack.getIsPlaying()
    override fun getIsStop(): Boolean = playBack.getIsStopped()
    override fun getPosition(): Long = playBack.getPosition()
    override fun getService(): Service = mContext

    /**
     * 更新Notification
     *
     */
    fun update() = launch(Dispatchers.IO) {
        val count = synchronizer.getCount()

        synchronizer.checkCount(count)
        val builder = buildNotification(mContext.mediaSession, PLAYER_CHANNEL_ID)
            ?.loadCoverAndPalette()
            ?: return@launch
        builder.build().apply {
            synchronizer.checkCount(count)
            flags = flags.or(FLAG_ALWAYS_SHOW_TICKER)
            pushPlayingNotification(this)
        }

        synchronizer.checkCount(count)
        cancelLyricNotification()
        if (getIsPlaying()) {
            startLyricPushCycle()
        }
    }

    /**
     * 启动推送状态栏歌词的循环
     */
    private fun startLyricPushCycle() = launch {
        val count = synchronizer.getCount()
        var lastLyricIndex = 0

        repeat(Int.MAX_VALUE) {
            if (!getIsPlaying() || !statusLyricEnable) return@launch
            synchronizer.checkCount(count)
            delay(200)

            if (!getIsPlaying()) return@launch
            val lyricList = LMusicLyricManager.currentLyricEntry.get() ?: return@launch
            val time = getPosition().takeIf { it >= 0L } ?: return@launch
            val index = findShowLine(lyricList, time + 500)

            if (lastLyricIndex != index) {
                val lyricEntry = lyricList.getOrNull(index)
                val nowLyric = lyricEntry?.text
                    ?.takeIf { it.isNotEmpty() }
                    ?: lyricEntry?.secondText

                StatusBarLyricExt.send(nowLyric)
                buildLyricNotification(mContext.mediaSession, PLAYER_CHANNEL_ID)
                    .setTicker(nowLyric)
                    .build()
                    .apply {
                        flags = flags.or(FLAG_ONLY_UPDATE_TICKER)
                        flags = flags.or(FLAG_ALWAYS_SHOW_TICKER)
                        pushPlayingNotification(this)
                    }
                lastLyricIndex = index
            }
        }
    }

    /**
     * 取消状态栏歌词，不影响媒体控制器
     */
    private fun cancelLyricNotification() = launch {
        StatusBarLyricExt.stop()

        val builder = buildNotification(
            mediaSession = mContext.mediaSession, PLAYER_CHANNEL_ID
        )?.loadCoverAndPalette()
            ?: return@launch

        builder.setTicker(null)
            .build()
            .apply {
                flags = flags.or(FLAG_ONLY_UPDATE_TICKER)
                flags = flags.or(FLAG_ALWAYS_SHOW_TICKER)
                pushPlayingNotification(this)
            }
    }

    fun cancelNotification() {
        mContext.stopForeground(true)
        notificationManager.cancelAll()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun createNotificationChannel() {
        val channel = NotificationChannel(
            PLAYER_CHANNEL_ID,
            PLAYER_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        )
        channel.description = "【LMusic通知频道】：$PLAYER_CHANNEL_NAME"
        channel.importance = NotificationManager.IMPORTANCE_LOW
        channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        channel.setShowBadge(false)
        channel.enableLights(false)
        channel.enableVibration(false)
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        const val NOTIFICATION_PLAYER_ID = 7
        const val NOTIFICATION_LOGGER_ID = 8

        private const val PLAYER_CHANNEL_NAME = "LMusic Player"
        private const val LOGGER_CHANNEL_NAME = "LMusic Logger"

        const val PLAYER_CHANNEL_ID = PLAYER_CHANNEL_NAME + "_ID"
        const val LOGGER_CHANNEL_ID = PLAYER_CHANNEL_NAME + "_ID"

        const val FLAG_ALWAYS_SHOW_TICKER = 0x1000000
        const val FLAG_ONLY_UPDATE_TICKER = 0x2000000
    }
}