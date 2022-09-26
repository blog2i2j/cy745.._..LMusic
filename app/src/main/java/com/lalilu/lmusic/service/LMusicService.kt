package com.lalilu.lmusic.service

import android.app.PendingIntent
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import coil.imageLoader
import coil.request.ImageRequest
import com.lalilu.lmedia.entity.LSong
import com.lalilu.lmusic.Config
import com.lalilu.lmusic.Config.MEDIA_DEFAULT_ACTION
import com.lalilu.lmusic.Config.MEDIA_STOPPED_STATE
import com.lalilu.lmusic.datasource.MDataBase
import com.lalilu.lmusic.repository.HistoryDataStore
import com.lalilu.lmusic.repository.SettingsDataStore
import com.lalilu.lmusic.utils.CoroutineSynchronizer
import com.lalilu.lmusic.utils.PlayMode
import com.lalilu.lmusic.utils.extension.getNextOf
import com.lalilu.lmusic.utils.extension.getPreviousOf
import com.lalilu.lmusic.utils.extension.toBitmap
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

@AndroidEntryPoint
class LMusicService : MediaBrowserServiceCompat(), CoroutineScope {
    override val coroutineContext: CoroutineContext = Dispatchers.Default + SupervisorJob()

    @Inject
    lateinit var historyDataStore: HistoryDataStore

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    @Inject
    lateinit var database: MDataBase

    lateinit var mediaSession: MediaSessionCompat

    private val mNotificationManager: LMusicNotificationImpl by lazy {
        LMusicNotificationImpl(this, database, playBack)
    }
    private val playBack: LMusicPlayBack<LSong> = object : LMusicPlayBack<LSong>(this) {
        private val noisyReceiver = LMusicNoisyReceiver(this::onPause)
        private val audioFocusHelper = LMusicAudioFocusHelper(this@LMusicService) {
            when (it) {
                AudioManager.AUDIOFOCUS_LOSS,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    settingsDataStore.apply {
                        if (ignoreAudioFocus.get() != true) {
                            onPause()
                        }
                    }
                }
            }
        }

        /**
         * 请求获取音频焦点，若用户设置了忽略音频焦点，则直接返回true
         */
        override fun requestAudioFocus(): Boolean {
            settingsDataStore.apply {
                if (ignoreAudioFocus.get() == true) return true
            }
            return audioFocusHelper.requestAudioFocus() == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }

        override fun getUriFromItem(item: LSong): Uri = item.uri
        override fun getCurrent(): LSong? = LMusicRuntime.currentPlaying
        override fun getMetaDataFromItem(item: LSong?): MediaMetadataCompat? = item?.metadataCompat
        override fun getById(id: String): LSong? =
            LMusicRuntime.currentPlaylist.find { it.id == id }

        override fun getNext(random: Boolean): LSong? {
            settingsDataStore.apply {
                val current = getCurrent()
                return when (repeatMode.get()) {
                    PlaybackStateCompat.REPEAT_MODE_INVALID,
                    PlaybackStateCompat.REPEAT_MODE_NONE ->
                        LMusicRuntime.currentPlaylist.getNextOf(current, false)
                    PlaybackStateCompat.REPEAT_MODE_ONE -> current
                    else -> LMusicRuntime.currentPlaylist.getNextOf(current, true)
                }
            }
        }

        override fun getPrevious(): LSong? {
            settingsDataStore.apply {
                val current = getCurrent()
                return when (repeatMode.get()) {
                    PlaybackStateCompat.REPEAT_MODE_INVALID,
                    PlaybackStateCompat.REPEAT_MODE_NONE ->
                        LMusicRuntime.currentPlaylist.getPreviousOf(current, false)
                    PlaybackStateCompat.REPEAT_MODE_ONE -> current
                    else -> LMusicRuntime.currentPlaylist.getPreviousOf(current, true)
                }
            }
        }

        override fun getMaxVolume(): Int = settingsDataStore.run {
            volumeControl.get() ?: Config.DEFAULT_SETTINGS_VOLUME_CONTROL
        }

        override fun onPlayingItemUpdate(item: LSong?) {
            LMusicRuntime.currentPlaying = item
        }

        val synchronizer = CoroutineSynchronizer()
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            launch {
                val count = synchronizer.getCount()
                mediaSession.setMetadata(metadata)

                synchronizer.checkCount(count)
                val bitmap = this@LMusicService.imageLoader.execute(
                    ImageRequest.Builder(this@LMusicService)
                        .allowHardware(false)
                        .data(LMusicRuntime.currentPlaying)
                        .size(400)
                        .build()
                ).drawable?.toBitmap()

                synchronizer.checkCount(count)
                mediaSession.setMetadata(
                    MediaMetadataCompat.Builder(metadata)
                        .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, bitmap)
                        .build()
                )
            }
        }

        override fun onPlaybackStateChanged(@PlaybackStateCompat.State playbackState: Int) {
            when (playbackState) {
                PlaybackStateCompat.STATE_STOPPED -> {
                    LMusicRuntime.updatePosition(0, false)
                    LMusicRuntime.currentIsPlaying = false

                    noisyReceiver.unRegisterFrom(this@LMusicService)
                    audioFocusHelper.abandonAudioFocus()

                    mediaSession.isActive = false
                    mediaSession.setPlaybackState(MEDIA_STOPPED_STATE)

                    stopSelf()
                    mNotificationManager.cancelNotification()
                }
                PlaybackStateCompat.STATE_BUFFERING,
                PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS,
                PlaybackStateCompat.STATE_SKIPPING_TO_NEXT -> {
                    // 更新进度，进度置0
                    mediaSession.setPlaybackState(
                        PlaybackStateCompat.Builder()
                            .setActions(MEDIA_DEFAULT_ACTION)
                            .setState(playbackState, 0, 1f)
                            .build()
                    )
                }
                PlaybackStateCompat.STATE_PAUSED -> {
                    // 更新进度
                    mediaSession.setPlaybackState(
                        PlaybackStateCompat.Builder()
                            .setActions(MEDIA_DEFAULT_ACTION)
                            .setState(playbackState, getPosition(), 1f)
                            .build()
                    )

                    mediaSession.isActive = false
                    mNotificationManager.update()
                    noisyReceiver.unRegisterFrom(this@LMusicService)
                    audioFocusHelper.abandonAudioFocus()
                    this@LMusicService.stopForeground(false)

                    LMusicRuntime.updatePosition(getPosition(), getIsPlaying())
                    LMusicRuntime.currentIsPlaying = false
                }
                PlaybackStateCompat.STATE_PLAYING -> {
                    // 更新进度
                    mediaSession.setPlaybackState(
                        PlaybackStateCompat.Builder()
                            .setActions(MEDIA_DEFAULT_ACTION)
                            .setState(playbackState, getPosition(), 1f)
                            .build()
                    )

                    mediaSession.isActive = true
                    startService(Intent(this@LMusicService, LMusicService::class.java))
                    mNotificationManager.update()

                    noisyReceiver.registerTo(this@LMusicService)

                    LMusicRuntime.updatePosition(getPosition(), getIsPlaying())
                    LMusicRuntime.currentIsPlaying = true
                }
            }
        }

        override fun setRepeatMode(repeatMode: Int) {
            mediaSession.setRepeatMode(repeatMode)
            mNotificationManager.update()
        }

        override fun setShuffleMode(shuffleMode: Int) {
            mediaSession.setShuffleMode(shuffleMode)
            mNotificationManager.update()
        }
    }


    override fun onCreate() {
        super.onCreate()

        val sessionActivityPendingIntent =
            packageManager?.getLaunchIntentForPackage(packageName)?.let { sessionIntent ->
                PendingIntent.getActivity(
                    this, 0, sessionIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }

        mediaSession = MediaSessionCompat(this, "LMusicService")
            .apply {
                setSessionActivity(sessionActivityPendingIntent)
                setCallback(playBack)
                isActive = true
            }

        sessionToken = mediaSession.sessionToken

        settingsDataStore.apply {
            launch {
                volumeControl.flow()
                    .onEach { it?.let(playBack::setMaxVolume) }
                    .launchIn(this)

                enableStatusLyric.flow()
                    .onEach { mNotificationManager.statusLyricEnable = it == true }
                    .launchIn(this)

                repeatMode.flow()
                    .onEach {
                        it ?: return@onEach

                        PlayMode.of(it).apply {
                            playBack.setRepeatMode(repeatMode)
                            playBack.setShuffleMode(shuffleMode)
                        }
                    }.launchIn(this)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        println("onStartCommand: ${intent?.action} ${intent?.extras?.getInt(PlayMode.KEY)}")

        intent?.takeIf { it.action === Config.ACTION_SET_REPEAT_MODE }?.extras?.apply {
            val playMode = getInt(PlayMode.KEY)
                .takeIf { it in 0..2 }
                ?: return@apply
            settingsDataStore.apply { repeatMode.set(playMode) }
        }

        MediaButtonReceiver.handleIntent(mediaSession, intent)
        return START_NOT_STICKY
    }

    /**
     * 鉴权判断是否允许访问媒体库
     */
    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        return BrowserRoot("MAIN", null)
    }

    /**
     * 根据请求返回对应的媒体数据
     */
    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        result.sendResult(mutableListOf())
    }
}