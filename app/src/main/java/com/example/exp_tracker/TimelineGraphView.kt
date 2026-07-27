package com.example.exp_tracker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

class TimelineGraphView(context: Context) : View(context) {
    data class Point(val x: Long, val y: Double)

    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1f; style = Paint.Style.STROKE }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1f; style = Paint.Style.STROKE }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2f; style = Paint.Style.STROKE }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f * resources.displayMetrics.scaledDensity }

    private var backgroundColorValue = Color.BLACK
    private var axisColorValue = Color.rgb(125, 125, 125)
    private var lineColorValue = Color.rgb(247, 35, 35)
    private var textColorValue = Color.rgb(237, 237, 237)
    private var gridColorValue = Color.rgb(31, 31, 31)

    private var points: List<Point> = emptyList()
    private var yLabels: Pair<String, String>? = null

    private var zoomX = 1f
    private var zoomY = 1f
    private var panX = 0f
    private var panY = 0f

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoomX = (zoomX * detector.scaleFactor).coerceIn(1f, 30f)
            zoomY = (zoomY * detector.scaleFactor).coerceIn(1f, 30f)
            clampPan()
            invalidate()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onDoubleTap(e: MotionEvent): Boolean {
            resetViewport()
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            val plotWidth = (width - 60f).coerceAtLeast(1f)
            val plotHeight = (height - 46f).coerceAtLeast(1f)
            panX += distanceX / plotWidth / zoomX
            panY -= distanceY / plotHeight / zoomY
            clampPan()
            invalidate()
            return true
        }
    })

    init {
        textPaint.typeface = AppFonts.jetRoboto(context)
        isClickable = true
        isFocusable = true
    }

    fun setPalette(background: Int, axis: Int, line: Int, text: Int, grid: Int) {
        backgroundColorValue = background
        axisColorValue = axis
        lineColorValue = line
        textColorValue = text
        gridColorValue = grid
        invalidate()
    }

    fun setSeries(points: List<Point>, minLabel: String? = null, maxLabel: String? = null) {
        this.points = points.sortedBy { it.x }
        yLabels = if (minLabel != null && maxLabel != null) minLabel to maxLabel else null
        resetViewport()
    }

    fun resetViewport() {
        zoomX = 1f
        zoomY = 1f
        panX = 0f
        panY = 0f
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE || event.pointerCount > 1) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        val scaled = scaleDetector.onTouchEvent(event)
        val gestured = gestureDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return scaled || gestured || super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(backgroundColorValue)
        axisPaint.color = axisColorValue
        gridPaint.color = gridColorValue
        linePaint.color = lineColorValue
        dotPaint.color = lineColorValue
        textPaint.color = textColorValue

        if (points.isEmpty()) {
            canvas.drawText("No dated values to graph", 12f, height / 2f, textPaint)
            return
        }

        val left = 52f
        val right = max(left + 1f, width - 14f)
        val top = 18f
        val bottom = max(top + 1f, height - 34f)
        val plotWidth = right - left
        val plotHeight = bottom - top

        for (i in 1..3) {
            val gx = left + plotWidth * i / 4f
            val gy = top + plotHeight * i / 4f
            canvas.drawLine(gx, top, gx, bottom, gridPaint)
            canvas.drawLine(left, gy, right, gy, gridPaint)
        }
        canvas.drawLine(left, top, left, bottom, axisPaint)
        canvas.drawLine(left, bottom, right, bottom, axisPaint)

        val rawMinX = points.minOf { it.x }.toDouble()
        val rawMaxX = points.maxOf { it.x }.toDouble()
        val rawMinY = points.minOf { it.y }
        val rawMaxY = points.maxOf { it.y }
        val fullXSpan = (rawMaxX - rawMinX).takeIf { it != 0.0 } ?: 1.0
        val fullYSpan = (rawMaxY - rawMinY).takeIf { it != 0.0 } ?: 1.0

        val visibleXSpan = fullXSpan / zoomX
        val visibleYSpan = fullYSpan / zoomY
        val maxPanX = (1f - 1f / zoomX) / 2f
        val maxPanY = (1f - 1f / zoomY) / 2f
        val centerX = rawMinX + fullXSpan * (0.5 + panX.coerceIn(-maxPanX, maxPanX))
        val centerY = rawMinY + fullYSpan * (0.5 + panY.coerceIn(-maxPanY, maxPanY))
        val minX = centerX - visibleXSpan / 2.0
        val maxX = centerX + visibleXSpan / 2.0
        val minY = centerY - visibleYSpan / 2.0
        val maxY = centerY + visibleYSpan / 2.0

        fun sx(x: Long) = left + (((x.toDouble() - minX) / (maxX - minX)) * plotWidth).toFloat()
        fun sy(y: Double) = bottom - (((y - minY) / (maxY - minY)) * plotHeight).toFloat()

        canvas.save()
        canvas.clipRect(left, top, right, bottom)
        val path = Path()
        var started = false
        points.forEach { point ->
            val x = sx(point.x)
            val y = sy(point.y)
            if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
            if (x in left..right && y in top..bottom) canvas.drawCircle(x, y, 4f, dotPaint)
        }
        if (points.size > 1) canvas.drawPath(path, linePaint)
        canvas.restore()

        val formatter = SimpleDateFormat("d/M/yy", Locale.getDefault())
        val startText = formatter.format(Date(minX.toLong()))
        val endText = formatter.format(Date(maxX.toLong()))
        canvas.drawText(startText, left, height - 10f, textPaint)
        canvas.drawText(endText, right - textPaint.measureText(endText), height - 10f, textPaint)

        val low = if (zoomY == 1f) yLabels?.first ?: compact(minY) else compact(minY)
        val high = if (zoomY == 1f) yLabels?.second ?: compact(maxY) else compact(maxY)
        canvas.drawText(high, 4f, top + textPaint.textSize, textPaint)
        canvas.drawText(low, 4f, bottom, textPaint)
        canvas.drawText("Pinch to zoom · drag to pan · double-tap to reset", left, top + textPaint.textSize, textPaint)
    }

    private fun clampPan() {
        val maxX = (1f - 1f / zoomX) / 2f
        val maxY = (1f - 1f / zoomY) / 2f
        panX = panX.coerceIn(-maxX, maxX)
        panY = panY.coerceIn(-maxY, maxY)
    }

    private fun compact(value: Double): String = when {
        value == value.toLong().toDouble() -> value.toLong().toString()
        else -> String.format(Locale.US, "%.2f", value)
    }
}
