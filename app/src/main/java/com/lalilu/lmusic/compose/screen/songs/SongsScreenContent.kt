package com.lalilu.lmusic.compose.screen.songs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gigamole.composefadingedges.FadingEdgesGravity
import com.gigamole.composefadingedges.content.FadingEdgesContentType
import com.gigamole.composefadingedges.content.scrollconfig.FadingEdgesScrollConfig
import com.gigamole.composefadingedges.fill.FadingEdgesFillType
import com.gigamole.composefadingedges.verticalFadingEdges
import com.lalilu.common.base.Playable
import com.lalilu.component.base.smartBarPadding
import com.lalilu.component.base.songs.SongsSM
import com.lalilu.component.base.songs.SongsScreenEvent
import com.lalilu.component.base.songs.SongsScreenScrollBar
import com.lalilu.component.base.songs.SongsScreenStickyHeader
import com.lalilu.component.card.SongCard
import com.lalilu.component.extension.rememberLazyListAnimateScroller
import com.lalilu.component.extension.startRecord
import com.lalilu.component.navigation.AppRouter
import com.lalilu.lmedia.extension.GroupIdentity
import com.lalilu.lplayer.extensions.PlayerAction
import kotlinx.coroutines.flow.collectLatest

@Composable
internal fun SongsScreenContent(
    songsSM: SongsSM,
    isSelecting: () -> Boolean = { false },
    isSelected: (Playable) -> Boolean = { false },
    onSelect: (Playable) -> Unit = {},
    onClickGroup: (GroupIdentity) -> Unit = {}
) {
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    val listState: LazyListState = rememberLazyListState()
    val statusBar = WindowInsets.statusBars
    val songs by songsSM.songs
    val scroller = rememberLazyListAnimateScroller(
        listState = listState,
        keysKeeper = { songsSM.recorder.list().filterNotNull() }
    )

    LaunchedEffect(Unit) {
        songsSM.event().collectLatest { event ->
            when (event) {
                is SongsScreenEvent.ScrollToItem -> {
                    scroller.animateTo(event.key)
                }

                else -> {}
            }
        }
    }

    SongsScreenScrollBar(
        modifier = Modifier.fillMaxSize(),
        listState = listState
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .verticalFadingEdges(
                    length = statusBar
                        .asPaddingValues()
                        .calculateTopPadding(),
                    contentType = FadingEdgesContentType.Dynamic.Lazy.List(
                        scrollConfig = FadingEdgesScrollConfig.Dynamic(),
                        state = listState
                    ),
                    gravity = FadingEdgesGravity.Start,
                    fillType = remember {
                        FadingEdgesFillType.FadeClip(
                            fillStops = Triple(0f, 0.7f, 1f)
                        )
                    }
                ),
            state = listState,
        ) {
            startRecord(songsSM.recorder) {
                itemWithRecord(key = "全部歌曲") {
                    val count = remember(songs) { songs.values.flatten().size }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .statusBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "全部歌曲",
                            fontSize = 20.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colors.onBackground
                        )
                        Text(
                            text = "共 $count 首歌曲",
                            color = MaterialTheme.colors.onBackground.copy(0.6f),
                            fontSize = 12.sp,
                            lineHeight = 12.sp,
                        )
                    }
                }

                songs.forEach { (group, list) ->
                    if (group !is GroupIdentity.None) {
                        stickyHeaderWithRecord(
                            key = group,
                            contentType = "group"
                        ) {
                            SongsScreenStickyHeader(
                                listState = listState,
                                group = group,
                                minOffset = { statusBar.getTop(density) },
                                onClickGroup = onClickGroup
                            )
                        }
                    }

                    itemsWithRecord(
                        items = list,
                        key = { it.mediaId },
                        contentType = { it::class.java }
                    ) {
                        SongCard(
                            song = { it },
                            isSelected = { isSelected(it) },
                            onClick = {
                                if (isSelecting()) {
                                    onSelect(it)
                                } else {
                                    PlayerAction.PlayById(it.mediaId).action()
                                }
                            },
                            onLongClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)

                                if (isSelecting()) {
                                    onSelect(it)
                                } else {
                                    AppRouter.route("/pages/songs/detail")
                                        .with("mediaId", it.mediaId)
                                        .jump()
                                }
                            },
                            onEnterSelect = { onSelect(it) }
                        )
                    }
                }
            }

            smartBarPadding()
        }
    }
}