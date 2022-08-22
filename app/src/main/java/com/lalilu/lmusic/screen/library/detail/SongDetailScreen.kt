package com.lalilu.lmusic.screen.library.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.FixedScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.blankj.utilcode.util.SizeUtils
import com.lalilu.R
import com.lalilu.lmedia.entity.LSong
import com.lalilu.lmusic.screen.MainScreenData
import com.lalilu.lmusic.screen.component.NavigatorHeader
import com.lalilu.lmusic.screen.component.SmartBar
import com.lalilu.lmusic.screen.component.button.TextWithIconButton
import com.lalilu.lmusic.screen.component.card.NetworkDataCard
import com.lalilu.lmusic.utils.extension.LocalNavigatorHost
import com.lalilu.lmusic.utils.extension.LocalWindowSize
import com.lalilu.lmusic.viewmodel.MainViewModel

@Composable
fun SongDetailScreen(
    song: LSong,
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val windowSize = LocalWindowSize.current
    val navController = LocalNavigatorHost.current
    val mediaBrowser = mainViewModel.mediaBrowser
    val contentPaddingForFooter by SmartBar.contentPaddingForSmartBarDp
    val title = song.name
    val subTitle = "${song._artist}\n\n${song._albumTitle}"

    LaunchedEffect(Unit) {
        SmartBar.addBarItem {
            SongDetailActionsBar(
                onAddSongToPlaylist = {
                    navController.navigate("${MainScreenData.SongsAddToPlaylist.name}/${song.id}")
                },
                onSetSongToNext = {
                    mediaBrowser.addToNext(song.id)
                },
                onPlaySong = {
                    mediaBrowser.playById(mediaId = song.id, playWhenReady = true)
                }
            )
        }
    }

    SongDetailScreen(
        title = title,
        subTitle = subTitle,
        mediaId = song.id,
        imageRequest = ImageRequest.Builder(LocalContext.current)
            .data(song)
            .size(SizeUtils.dp2px(128f))
            .crossfade(true)
            .build(),
        windowSize = windowSize,
        onMatchNetworkData = {
            navController.navigate("${MainScreenData.SongsMatchNetworkData.name}/${song.id}")
        }
    )
}

@Composable
fun SongDetailScreen(
    title: String,
    subTitle: String,
    mediaId: String,
    windowSize: WindowSizeClass,
    imageRequest: ImageRequest,
    onMatchNetworkData: () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        NavigatorHeader(title = title, subTitle = subTitle) {
            Surface(
                elevation = 4.dp,
                shape = RoundedCornerShape(2.dp)
            ) {
                SubcomposeAsyncImage(model = imageRequest, contentDescription = "") {
                    val state = painter.state
                    if (state is AsyncImagePainter.State.Loading || state is AsyncImagePainter.State.Error) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_music_line),
                            contentDescription = "",
                            contentScale = FixedScale(2.5f),
                            colorFilter = ColorFilter.tint(color = Color.LightGray),
                            modifier = Modifier
                                .size(128.dp)
                                .fillMaxWidth()
                                .aspectRatio(1f)
                        )
                    } else {
                        SubcomposeAsyncImageContent(
                            modifier = Modifier
                                .sizeIn(
                                    minHeight = 64.dp,
                                    maxHeight = 128.dp,
                                    minWidth = 64.dp,
                                    maxWidth = 144.dp
                                )
                        )
                    }
                }
            }
        }

        LazyVerticalGrid(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            columns = GridCells.Fixed(
                if (windowSize.widthSizeClass != WindowWidthSizeClass.Compact) 2 else 1
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                NetworkDataCard(
                    onClick = onMatchNetworkData,
                    mediaId = mediaId
                )
            }
        }
    }
}

@Composable
fun SongDetailActionsBar(
    onPlaySong: () -> Unit = {},
    onSetSongToNext: () -> Unit = {},
    onAddSongToPlaylist: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        TextWithIconButton(
            textRes = R.string.text_button_play,
            color = Color(0xFF006E7C),
            onClick = onPlaySong
        )
        TextWithIconButton(
            textRes = R.string.button_set_song_to_next,
            color = Color(0xFF006E7C),
            onClick = onSetSongToNext
        )
        TextWithIconButton(
            textRes = R.string.button_add_song_to_playlist,
            iconRes = R.drawable.ic_play_list_add_line,
            color = Color(0xFF006E7C),
            onClick = onAddSongToPlaylist
        )
    }
}

@Composable
fun EmptySongDetailScreen() {
    Text(text = "无法获取该歌曲信息", modifier = Modifier.padding(20.dp))
}