package com.lalilu.lmusic.utils

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.palette.graphics.Palette
import com.lalilu.lmusic.ui.PaletteDraweeView
import net.opacapp.multilinecollapsingtoolbar.CollapsingToolbarLayout

@SuppressLint("Recycle")
@RequiresApi(Build.VERSION_CODES.O)
class ColorAnimator constructor(fromColor: Int, toColor: Int) : ValueAnimator(),
    ValueAnimator.AnimatorUpdateListener {
    private var rFrom: Float = 0f
    private var gFrom: Float = 0f
    private var bFrom: Float = 0f
    private var rTo: Float = 0f
    private var gTo: Float = 0f
    private var bTo: Float = 0f

    companion object {
        fun setContentScrimColorFromPaletteDraweeView(
            paletteDraweeView: PaletteDraweeView,
            ctLayout: CollapsingToolbarLayout
        ) {
            paletteDraweeView.palette.observeForever {
                if (it != null) {
                    val oldColor = paletteDraweeView.oldPalette.getAutomaticColor()
                    val plColor = it.getAutomaticColor()

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ColorAnimator(oldColor, plColor).setColorChangedListener { color ->
                            ctLayout.setContentScrimColor(color)
                        }.start(600)
                    } else {
                        ctLayout.setContentScrimColor(plColor)
                    }
                }
            }
        }

        fun getPaletteFromFromPaletteDraweeView(
            paletteDraweeView: PaletteDraweeView,
            callback: (color: Palette) -> Unit
        ) {
            paletteDraweeView.palette.observeForever {
                it?.let { palette -> callback(palette) }
            }
        }

        fun isLightColor(color: Int): Boolean {
            val darkness =
                1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(
                    color
                )) / 255
            return darkness < 0.5
        }
    }

    private lateinit var listen: ((Int) -> Unit)

    fun setColorChangedListener(listen: ((Int) -> Unit)): ColorAnimator {
        this.listen = listen
        return this
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onAnimationUpdate(animation: ValueAnimator) {
        val value = animation.animatedValue as Float
        val rResult = rFrom + (rTo - rFrom) * value
        val gResult = gFrom + (gTo - gFrom) * value
        val bResult = bFrom + (bTo - bFrom) * value

        val result = Color.rgb(rResult, gResult, bResult)
        listen.invoke(result)
    }

    override fun setDuration(duration: Long): ValueAnimator {
        super.setDuration(duration)
        return this@ColorAnimator
    }

    fun start(duration: Number) {
        this.duration = duration.toLong()
        start()
    }

    init {
        this.setFloatValues(0f, 1f)
        this.setColor(fromColor, toColor)
        this.addUpdateListener(this)
    }

    private fun setColor(fromColor: Int, toColor: Int): ColorAnimator {
        rFrom = Color.valueOf(fromColor).red()
        gFrom = Color.valueOf(fromColor).green()
        bFrom = Color.valueOf(fromColor).blue()
        rTo = Color.valueOf(toColor).red()
        gTo = Color.valueOf(toColor).green()
        bTo = Color.valueOf(toColor).blue()
        return this
    }
}

fun Palette?.getAutomaticColor(): Int {
    if (this == null) return Color.DKGRAY
    var oldColor = this.getDarkVibrantColor(Color.LTGRAY)
    if (ColorAnimator.isLightColor(oldColor))
        oldColor = this.getDarkMutedColor(Color.LTGRAY)
    return oldColor
}
