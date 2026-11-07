package com.perso.hubmode

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/**
 * Conteneur qui fait pivoter son contenu de 0/90/180/270°.
 * Sur un téléphone, Android affiche souvent l'écran de veille en portrait même
 * quand le téléphone est couché : on remet alors l'affichage à l'endroit nous-mêmes.
 * Les appuis (boutons Spotify) sont correctement convertis par Android.
 */
class RotatingLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var angle: Int = 0
        set(value) {
            val norm = ((value % 360) + 360) % 360
            if (field != norm) {
                field = norm
                requestLayout()
                invalidate()
            }
        }

    private val swapped get() = angle == 90 || angle == 270

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        val cw = if (swapped) h else w
        val ch = if (swapped) w else h
        for (i in 0 until childCount) {
            getChildAt(i).measure(
                MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY)
            )
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val w = right - left
        val h = bottom - top
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val cw = child.measuredWidth
            val ch = child.measuredHeight
            val l = (w - cw) / 2
            val t = (h - ch) / 2
            child.layout(l, t, l + cw, t + ch)
            child.pivotX = cw / 2f
            child.pivotY = ch / 2f
            child.rotation = angle.toFloat()
        }
    }
}
