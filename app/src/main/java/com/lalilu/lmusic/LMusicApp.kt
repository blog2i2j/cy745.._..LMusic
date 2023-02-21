package com.lalilu.lmusic

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.lalilu.lmedia.extension.LMediaExt
import com.lalilu.lmedia.extension.LMediaExtFactory
import com.lalilu.lmedia.extension.LMediaLifeCycle
import com.lalilu.lmusic.utils.EQHelper
import com.lalilu.lmusic.utils.StatusBarLyricExt
import com.lalilu.lmusic.utils.coil.CrossfadeTransitionFactory
import com.lalilu.lmusic.utils.coil.fetcher.AlbumCoverFetcher
import com.lalilu.lmusic.utils.coil.fetcher.SongCoverFetcher
import com.lalilu.lmusic.utils.coil.keyer.SongCoverKeyer
import okhttp3.OkHttpClient
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class LMusicApp : Application(), ImageLoaderFactory, LMediaExtFactory {

    private val callFactory: OkHttpClient by inject()

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .callFactory(callFactory)
            .components {
                add(AlbumCoverFetcher.AlbumFactory())
                add(SongCoverFetcher.SongFactory(callFactory))
                add(SongCoverKeyer())
            }
            .transitionFactory(CrossfadeTransitionFactory())
            .build()

    override fun newLMediaExt(): LMediaExt =
        LMediaExt.Builder()
//            .addIndexFilter(DictionaryFilter(lmusicSp = LMusicSp(this)))
            .setLifeCycleListener(object : LMediaLifeCycle.Listener {
                override fun onFinishedIndex() {
                    LMusicFlowBus.libraryUpdate.post(System.currentTimeMillis())
                }
            })
            .build()

    override fun onCreate() {
        super.onCreate()

        StatusBarLyricExt.init(this)
        EQHelper.init(this)

        startKoin {
            androidContext(this@LMusicApp)
            modules(
                AppModule,
                ApiModule,
                ViewModelModule,
                DatabaseModule,
                RuntimeModule,
                PlayerModule
            )
        }
    }
}