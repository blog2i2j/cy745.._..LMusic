package com.lalilu.lmusic.utils.extension

import android.content.ContentUris
import android.content.Context
import android.content.ContextWrapper
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.GradientDrawable.Orientation.*
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dirror.lyricviewx.LyricEntry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.roundToInt

fun Drawable.toBitmap(): Bitmap {
    val w = this.intrinsicWidth
    val h = this.intrinsicHeight

    val config = Bitmap.Config.ARGB_8888
    val bitmap = Bitmap.createBitmap(w, h, config)
    val canvas = Canvas(bitmap)
    this.setBounds(0, 0, w, h)
    this.draw(canvas)
    return bitmap
}

fun Bitmap.addShadow(
    fromColor: Int = Color.argb(55, 0, 0, 0),
    toColor: Int = Color.TRANSPARENT,
    percent: Float = 0.25f,
    orientation: List<GradientDrawable.Orientation> = listOf(TOP_BOTTOM, BOTTOM_TOP)
): Bitmap {
    orientation.forEach {
        val mBackShadowColors = intArrayOf(fromColor, toColor)
        val mBackShadowDrawableLR = GradientDrawable(it, mBackShadowColors)
        val bound = Rect(0, 0, width, height)
        val percentHeight = (height * percent).roundToInt()
        val percentWidth = (width * percent).roundToInt()

        when (it) {
            TOP_BOTTOM -> bound.set(0, 0, width, percentHeight)
            RIGHT_LEFT -> bound.set(0, 0, percentWidth, height)
            BOTTOM_TOP -> bound.set(0, height - percentHeight, width, height)
            LEFT_RIGHT -> bound.set(width - percentWidth, 0, width, height)
            else -> {}
        }
        mBackShadowDrawableLR.bounds = bound
        mBackShadowDrawableLR.gradientType = GradientDrawable.LINEAR_GRADIENT
        mBackShadowDrawableLR.draw(Canvas(this))
    }
    return this
}

fun <T, K> List<T>.moveHeadToTailWithSearch(id: K, checkIsSame: (T, K) -> Boolean): MutableList<T> {
    val size = this.indexOfFirst { checkIsSame(it, id) }
    if (size <= 0) return this.toMutableList()
    return this.moveHeadToTail(size)
}

fun <T> List<T>.moveHeadToTail(size: Int): MutableList<T> {
    val temp = this.take(size).toMutableList()
    temp.addAll(0, this.drop(size))
    return temp
}

fun Cursor.getSongId(): Long {
    val index = this.getColumnIndex(MediaStore.Audio.Media._ID)
    return if (index < 0) return 0 else this.getLong(index)
}

fun Cursor.getSongTitle(): String {
    val index = this.getColumnIndex(MediaStore.Audio.Media.TITLE)
    return if (index < 0) "" else this.getString(index)
}

fun Cursor.getAlbumId(): Long {
    val index = this.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
    return if (index < 0) return 0 else this.getLong(index)
}

fun Cursor.getAlbumTitle(): String {
    val index = this.getColumnIndex(MediaStore.Audio.Media.ALBUM)
    return if (index < 0) "" else this.getString(index)
}

fun Cursor.getArtistId(): Long {
    val index = this.getColumnIndex(MediaStore.Audio.Media.ARTIST_ID)
    return if (index < 0) 0 else this.getLong(index)
}

fun Cursor.getArtist(): String {
    val index = this.getColumnIndex(MediaStore.Audio.Media.ARTIST)
    return if (index < 0) "" else this.getString(index)
}

fun Cursor.getArtists(): List<String> {
    return this.getArtist().split("/")
}

fun Cursor.getSongSize(): Long {
    val index = this.getColumnIndex(MediaStore.Audio.Media.SIZE)
    return if (index < 0) return 0 else this.getLong(index)
}

fun Cursor.getSongData(): String {
    val index = this.getColumnIndex(MediaStore.Audio.Media.DATA)
    return if (index < 0) "" else this.getString(index)
}

fun Cursor.getSongDuration(): Long {
    val index = this.getColumnIndex(MediaStore.Audio.Media.DURATION)
    return if (index < 0) return 0 else this.getLong(index)
}

fun Cursor.getSongMimeType(): String {
    val index = this.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
    return if (index < 0) "" else this.getString(index)
}

@RequiresApi(Build.VERSION_CODES.R)
fun Cursor.getSongGenre(): String? {
    val index = this.getColumnIndex(MediaStore.Audio.AudioColumns.GENRE)
    return if (index == -1) null else this.getString(index)
}

fun Cursor.getAlbumArt(): Uri {
    return ContentUris.withAppendedId(
        Uri.parse("content://media/external/audio/albumart/"),
        getAlbumId()
    )
}

fun Cursor.getMediaUri(): Uri {
    return Uri.withAppendedPath(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        getSongId().toString()
    )
}

fun Context.getActivity(): AppCompatActivity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is AppCompatActivity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}

fun <T> List<T>.getNextOf(item: T, cycle: Boolean = false): T? {
    val nextIndex = indexOf(item) + 1
    return getOrNull(if (cycle) nextIndex % size else nextIndex)
}


fun <T> List<T>.getPreviousOf(item: T, cycle: Boolean = false): T? {
    var previousIndex = indexOf(item) - 1
    if (previousIndex < 0 && cycle) {
        previousIndex = size - 1
    }
    return getOrNull(previousIndex)
}

fun <T> List<T>.move(from: Int, to: Int): List<T> {
    val targetIndex = if (from < to) to else to + 1
    return this.toMutableList().apply {
        val temp = removeAt(from)
        add(targetIndex, temp)
    }
}

/**
 * 根据当前时间使用二分查找，查找最接近的歌词
 */
fun findShowLine(list: List<LyricEntry>?, time: Long): Int {
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

fun <T> List<T>.average(numToCalc: (T) -> Number): Float {
    return this.fold(0f) { acc, t ->
        acc + numToCalc(t).toFloat()
    } / this.size
}

fun calculateExtraLayoutSpace(context: Context, size: Int): LinearLayoutManager {
    return object : LinearLayoutManager(context) {
        override fun calculateExtraLayoutSpace(
            state: RecyclerView.State,
            extraLayoutSpace: IntArray
        ) {
            extraLayoutSpace[0] = size
            extraLayoutSpace[1] = size
        }
    }
}

/**
 * 简易的防抖实现
 */
@OptIn(FlowPreview::class)
fun CoroutineScope.debounce(delay: Long, callback: suspend () -> Unit): () -> Unit {
    val countFlow = MutableStateFlow(System.currentTimeMillis())

    countFlow.debounce(delay)
        .onEach { callback() }
        .launchIn(this)

    return { launch { countFlow.emit(System.currentTimeMillis()) } }
}

/**
 * 简易的节流实现
 */
fun CoroutineScope.throttle(delay: Long, callback: suspend () -> Unit): () -> Unit {
    var lastCount = System.currentTimeMillis()
    val countFlow = MutableStateFlow(System.currentTimeMillis())

    countFlow.onEach {
        if (it - delay >= lastCount) {
            callback()
            lastCount = it
        }
    }.launchIn(this)

    return { launch { countFlow.emit(System.currentTimeMillis()) } }
}

fun ViewDataBinding.launch(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
): Job? = lifecycleOwner?.lifecycleScope?.launch(context, start, block)

fun MediaSessionCompat.getMediaId(): String? {
    return controller?.metadata?.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)
}