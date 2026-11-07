package com.perso.hubmode

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.view.View

/** Petite courbe du cours de la journée, avec la clôture de la veille en pointillés. */
class SparklineView(ctx: Context) : View(ctx) {

    private var points: List<Double> = emptyList()
    private var baseline = Double.NaN
    private var color = Color.WHITE

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ctx.dp(2f)
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ctx.dp(1f)
        color = Color.argb(90, 255, 255, 255)
        pathEffect = DashPathEffect(floatArrayOf(ctx.dp(3f), ctx.dp(3f)), 0f)
    }
    private val path = Path()
    private val area = Path()

    fun set(points: List<Double>, baseline: Double, color: Int) {
        this.points = points
        this.baseline = baseline
        this.color = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.size < 2) return
        val w = width.toFloat()
        val h = height.toFloat()
        val pad = context.dp(3f)

        var lo = points.min()
        var hi = points.max()
        if (!baseline.isNaN()) {
            lo = minOf(lo, baseline)
            hi = maxOf(hi, baseline)
        }
        if (hi - lo < 1e-9) {
            hi += 0.5; lo -= 0.5
        }
        fun y(v: Double) = (h - pad - ((v - lo) / (hi - lo)) * (h - 2 * pad)).toFloat()
        val n = points.size
        fun x(i: Int) = i * w / (n - 1)

        path.reset()
        area.reset()
        path.moveTo(x(0), y(points[0]))
        area.moveTo(x(0), h)
        area.lineTo(x(0), y(points[0]))
        for (i in 1 until n) {
            path.lineTo(x(i), y(points[i]))
            area.lineTo(x(i), y(points[i]))
        }
        area.lineTo(x(n - 1), h)
        area.close()

        fill.shader = LinearGradient(
            0f, 0f, 0f, h,
            Color.argb(80, Color.red(color), Color.green(color), Color.blue(color)),
            Color.argb(0, Color.red(color), Color.green(color), Color.blue(color)),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(area, fill)

        if (!baseline.isNaN()) {
            val by = y(baseline)
            canvas.drawLine(0f, by, w, by, dash)
        }

        line.color = color
        canvas.drawPath(path, line)
    }
}
