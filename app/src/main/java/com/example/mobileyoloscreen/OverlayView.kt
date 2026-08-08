package com.example.mobileyoloscreen

import android.content.Context
import android.graphics.*
import android.view.View

class OverlayView(context: Context) : View(context) {
    @Volatile var detections: List<Detection> = emptyList()
    @Volatile var fps: Float = 0f
    private val box = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(85,230,193); style = Paint.Style.STROKE; strokeWidth = 4f }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 32f; typeface = Typeface.DEFAULT_BOLD }
    private val bg = Paint().apply { color = Color.argb(190, 10, 15, 18) }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        c.drawRect(12f, 12f, 200f, 58f, bg)
        c.drawText("YOLO %.1f FPS".format(fps), 20f, 46f, text)
        detections.forEach { d ->
            c.drawRect(d.left, d.top, d.right, d.bottom, box)
            val caption = "${d.label} ${(d.score * 100).toInt()}%"
            val width = text.measureText(caption) + 16f
            val y = maxOf(34f, d.top)
            c.drawRect(d.left, y - 34f, d.left + width, y + 4f, bg)
            c.drawText(caption, d.left + 8f, y, text)
        }
    }
}
