package com.lalilu.lmusic.screen.library.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.funny.data_saver.core.rememberDataSaverState
import com.lalilu.lmedia.entity.items
import com.lalilu.lmedia.indexer.Indexer
import com.lalilu.lmusic.screen.MainScreenData
import com.lalilu.lmusic.screen.bean.SORT_BY_TIME
import com.lalilu.lmusic.screen.bean.next
import com.lalilu.lmusic.screen.component.NavigatorHeaderWithButtons
import com.lalilu.lmusic.screen.component.button.LazyListSortToggleButton
import com.lalilu.lmusic.screen.component.button.SortToggleButton
import com.lalilu.lmusic.screen.component.card.SongCard
import com.lalilu.lmusic.utils.WindowSize
import com.lalilu.lmusic.viewmodel.MainViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtistDetailScreen(
    artistName: String,
    currentWindowSize: WindowSize,
    navigateTo: (destination: String) -> Unit = {},
    contentPaddingForFooter: Dp = 0.dp,
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val haptic = LocalHapticFeedback.current
    var sortByState by rememberDataSaverState("KEY_SORT_BY_ArtistDetailScreen", SORT_BY_TIME)
    var sortDesc by rememberDataSaverState("KEY_SORT_DESC_ArtistDetailScreen", true)
    val songs = Indexer.library.artists.find { it.name.contains(artistName) }?.songs ?: emptyList()

//    LaunchedEffect(artistName) {
//        songs.clear()
//        songs.addAll(
//            artistViewModel
//                .getSongsByName(artistName)
//                .toMutableStateList()
//        )
//    }

//    val sortedItems = remember(sortByState, sortDesc, songs) {
//        sort(sortByState, sortDesc, songs,
//            getTextField = { it.mediaMetadata.title.toString() },
//            getTimeField = { it.mediaId.toLong() }
//        )
//    }

    val onSongSelected: (Int) -> Unit = remember {
        { index: Int ->
            mainViewModel.playSongWithPlaylist(
                items = songs.items(),
                index = index
            )
        }
    }

    val onSongShowDetail: (String) -> Unit = remember {
        { mediaId ->
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            navigateTo("${MainScreenData.SongsDetail.name}/$mediaId")
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        NavigatorHeaderWithButtons(title = artistName, subTitle = "关联：") {
            LazyListSortToggleButton(sortByState = sortByState) {
                sortByState = next(sortByState)
            }
            SortToggleButton(sortDesc = sortDesc) {
                sortDesc = !sortDesc
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (currentWindowSize == WindowSize.Expanded) 2 else 1),
            contentPadding = PaddingValues(
                bottom = contentPaddingForFooter
            )
        ) {
            itemsIndexed(items = songs) { index, item ->
                SongCard(
                    modifier = Modifier.animateItemPlacement(),
                    index = index,
                    song = item,
                    onSongSelected = onSongSelected,
                    onSongShowDetail = onSongShowDetail
                )
            }
        }
    }
}

@Composable
fun EmptyArtistDetailScreen() {

}
