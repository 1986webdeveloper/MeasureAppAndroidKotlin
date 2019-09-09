package com.acquaintsoft.measureappandroidkotlin

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point

import android.util.AttributeSet
import android.util.Log
import android.view.View

import java.util.HashMap


class OverlayView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    View(context, attrs, defStyleAttr) {
    internal val paint = Paint()
    internal val pointMap = HashMap<String, Point>()

    init {
        paint.color = Color.RED
        paint.isAntiAlias = true
        paint.isDither = true
    }

    fun setPoint(tag: String, x: Float, y: Float) {
        pointMap[tag] = Point(x.toInt(), y.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        if (pointMap == null) {
            return
        }

        for (key in pointMap.keys) {
            val point = pointMap[key]
            canvas.drawCircle(point!!.x.toFloat(), point.y.toFloat(), 20f, paint)
            Log.d("OverlayView", "drawCircle")
        }

    }

}