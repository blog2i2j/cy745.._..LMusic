package com.lalilu.lmusic.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lalilu.lmedia.entity.LSong
import com.lalilu.lmusic.service.LMusicBrowser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor() : ViewModel() {

    fun playSongWithPlaylist(items: List<LSong>, index: Int) = viewModelScope.launch {
        LMusicBrowser.setSongs(items, items.getOrNull(index))
        LMusicBrowser.reloadAndPlay()
    }

    fun playSongWithPlaylist(items: List<LSong>, item: LSong) = viewModelScope.launch {
        LMusicBrowser.setSongs(items, item)
        LMusicBrowser.reloadAndPlay()
    }
}