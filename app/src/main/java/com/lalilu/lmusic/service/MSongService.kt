package com.lalilu.lmusic.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.lalilu.lmedia.entity.items
import com.lalilu.lmedia.indexer.Library
import com.lalilu.lmusic.Config
import com.lalilu.lmusic.manager.GlobalDataManager
import com.lalilu.lmusic.manager.SpManager
import com.lalilu.lmusic.repository.HistoryDataStore
import com.lalilu.lmusic.utils.RepeatMode
import com.lalilu.lmusic.utils.then
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

@UnstableApi
@AndroidEntryPoint
class MSongService : MediaLibraryService(), CoroutineScope {
    override val coroutineContext: CoroutineContext = Dispatchers.IO
    private lateinit var player: Player
    private lateinit var exoPlayer: ExoPlayer

    private lateinit var mediaLibrarySession: MediaLibrarySession
    private lateinit var mediaController: MediaController

    @Inject
    lateinit var historyDataStore: HistoryDataStore

    @Inject
    lateinit var globalDataManager: GlobalDataManager

    @Inject
    lateinit var notificationProvider: LMusicNotificationProvider

    private val audioAttributes = AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()

    override fun onCreate() {
        super.onCreate()
        exoPlayer = ExoPlayer.Builder(this)
            .setUseLazyPreparation(false)
            .setHandleAudioBecomingNoisy(true)
            .build()
        player = object : ForwardingPlayer(exoPlayer) {
            override fun getMaxSeekToPreviousPosition(): Long = Long.MAX_VALUE
            override fun seekToPrevious() {
                if (this.hasPreviousMediaItem() && this.currentPosition <= maxSeekToPreviousPosition) {
                    seekToPreviousMediaItem()
                    return
                }
                super.seekToPrevious()
            }
        }

        SpManager.listen(Config.KEY_SETTINGS_IGNORE_AUDIO_FOCUS,
            SpManager.SpBoolListener(Config.DEFAULT_SETTINGS_IGNORE_AUDIO_FOCUS) {
                exoPlayer.setAudioAttributes(audioAttributes, !it)
            })

        SpManager.listen(Config.KEY_SETTINGS_REPEAT_MODE,
            SpManager.SpIntListener(Config.DEFAULT_SETTINGS_REPEAT_MODE) {
                RepeatMode.values().getOrNull(it)?.let { repeatMode ->
                    exoPlayer.shuffleModeEnabled = repeatMode.isShuffle
                    exoPlayer.repeatMode = repeatMode.repeatMode
                }
            })

        val pendingIntent: PendingIntent =
            packageManager.getLaunchIntentForPackage(packageName).let { sessionIntent ->
                PendingIntent.getActivity(
                    this, 0, sessionIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }

        mediaLibrarySession =
            MediaLibrarySession.Builder(this, player, CustomMediaLibrarySessionCallback())
                .setSessionActivity(pendingIntent)
                .build()

        val controllerFuture =
            MediaController.Builder(this, mediaLibrarySession.token)
                .buildAsync()

        controllerFuture.addListener({
            mediaController = controllerFuture.get()
            mediaController.addListener(globalDataManager.playerListener)
            mediaController.addListener(LastPlayedListener())
            globalDataManager.player = mediaController
        }, MoreExecutors.directExecutor())

        setMediaNotificationProvider(notificationProvider)
    }

    private inner class LastPlayedListener : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            events.containsAny(
                Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                Player.EVENT_REPEAT_MODE_CHANGED,
                Player.EVENT_MEDIA_ITEM_TRANSITION
            ).then {
                onUpdateNotification(mediaLibrarySession)
            }
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            /**
             * 在播放列表数据发生变更时，将最新的列表内容存入DataStore中，确保重新启动时能拿到相应的数据
             */
            val idList = List(mediaController.mediaItemCount) {
                mediaController.getMediaItemAt(it).mediaId
            }
            launch {
                globalDataManager.currentPlaylist.emit(
                    idList.mapNotNull { Library.getSongOrNull(it)?.item }
                )
            }
            historyDataStore.apply {
                lastPlayedListIdsKey.set(idList)
            }
        }
    }

    private inner class CustomMediaLibrarySessionCallback : MediaLibrarySession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val mediaIds = mediaItems.map { it.mediaId }
            val items = mediaIds.mapNotNull { id -> Library.getSongOrNull(id)?.item }
            return Futures.immediateFuture(items.toMutableList())
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return Futures.immediateFuture(LibraryResult.ofItem(MediaItem.EMPTY, params))
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val item = Library.getSongOrNull(mediaId)?.item
                ?: return Futures.immediateFuture(
                    LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                )
            return Futures.immediateFuture(LibraryResult.ofItem(item, null))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val children = Library.getAlbumOrNull(parentId)?.songs?.items()
                ?: Library.getArtistOrNull(parentId)?.songs?.items()
                ?: return Futures.immediateFuture(
                    LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                )
            return Futures.immediateFuture(LibraryResult.ofItemList(children, params))
        }
    }

    override fun onDestroy() {
        player.release()
        mediaLibrarySession.release()
        super.onDestroy()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession {
        return mediaLibrarySession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        println("onTaskRemoved: 应用被关闭")
        super.onTaskRemoved(rootIntent)
    }
}