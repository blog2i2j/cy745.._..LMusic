package com.lalilu.lmusic

import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.imagepipeline.core.ImagePipelineConfig
import com.hjq.permissions.XXPermissions
import com.lalilu.lmusic.base.BaseApplication
import com.lalilu.lmusic.database.LMusicDataBase
import com.lalilu.lmusic.service.LMusicPlayerModule
import com.lalilu.lmusic.utils.scanner.LMusicScanner
import com.tencent.mmkv.MMKV

class LMusicApp : BaseApplication() {
    lateinit var playlistMMKV: LMusicPlaylistMMKV
    lateinit var lmusicScanner: LMusicScanner

    override fun onCreate() {
        super.onCreate()
        MMKV.initialize(this)
        XXPermissions.setScopedStorage(true)
        LMusicPlayerModule.getInstance(this)
        LMusicDataBase.getInstance(this)

        playlistMMKV = LMusicPlaylistMMKV.getInstance()
        lmusicScanner = LMusicScanner(this)

        val config = ImagePipelineConfig.newBuilder(this)
            .setDownsampleEnabled(true)
            .build()
        Fresco.initialize(this, config)
    }
}