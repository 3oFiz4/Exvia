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
import kotlin.math.abs
import kotlin.math.max

/** Compact interactive line plot used for cumulative PRICE and fitted normal density. */
class InteractiveLinePlotView(context: Context) : View(context) {
    enum class AxisKind { TIME, NUMBER }

    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rugPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f * resources.displayMetrics.scaledDensity }

    private var background = Color.BLACK
    private var axis = Color.GRAY
    private var text = Color.WHITE
    private var grid = Color.DKGRAY
    private var line = Color.rgb(61, 139, 255)
    private var points: List<Pair<Double, Double>> = emptyList()
    private var rugSamples: List<Double> = emptyList()
    private var axisKind = AxisKind.NUMBER
    private var zoomX = 1f
    private var zoomY = 1f
    private var panX = 0f
    private var panY = 0f

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoomX = (zoomX * detector.scaleFactor).coerceIn(1f, 40f)
            zoomY = (zoomY * detector.scaleFactor).coerceIn(1f, 40f)
            clampPan()
            invalidate()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onDoubleTap(e: MotionEvent): Boolean { resetViewport(); return true }
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            val w = (width - 72f).coerceAtLeast(1f)
            val h = (height - 62f).coerceAtLeast(1f)
            panX += distanceX / w / zoomX
            panY -= distanceY / h / zoomY
            clampPan()
            invalidate()
            return true
        }
    })

    init {
        textPaint.typeface = AppFonts.jetBrains(context)
        isClickable = true
        isFocusable = true
    }

    fun setPalette(background: Int, axis: Int, text: Int, grid: Int, line: Int = Color.rgb(61, 139, 255)) {
        this.background = background
        this.axis = axis
        this.text = text
        this.grid = grid
        this.line = line
        invalidate()
    }

    fun setSeries(values: List<Pair<Double, Double>>, axisKind: AxisKind, rugSamples: List<Double> = emptyList()) {
        points = values.sortedBy { it.first }
        this.axisKind = axisKind
        this.rugSamples = rugSamples
        resetViewport()
    }

    fun resetViewport() {
        zoomX = 1f; zoomY = 1f; panX = 0f; panY = 0f; invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE || event.pointerCount > 1) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        val a = scaleDetector.onTouchEvent(event)
        val b = gestureDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return a || b || super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(background)
        axisPaint.color = axis
        gridPaint.color = grid
        linePaint.color = line
        pointPaint.color = line
        rugPaint.color = withAlpha(line, 145)
        textPaint.color = text

        if (points.isEmpty()) {
            canvas.drawText("No values", 12f, height / 2f, textPaint)
            return
        }

        val left = 58f
        val right = max(left + 1f, width - 18f)
        val top = 18f
        val bottom = max(top + 1f, height - 48f)
        val plotW = right - left
        val plotH = bottom - top

        for (i in 1..3) {
            val gx = left + plotW * i / 4f
            val gy = top + plotH * i / 4f
            canvas.drawLine(gx, top, gx, bottom, gridPaint)
            canvas.drawLine(left, gy, right, gy, gridPaint)
        }
        canvas.drawLine(left, top, left, bottom, axisPaint)
        canvas.drawLine(left, bottom, right, bottom, axisPaint)

        val x0 = points.minOf { it.first }
        val x1 = points.maxOf { it.first }
        val y0 = points.minOf { it.second }
        val y1 = points.maxOf { it.second }
        val rawXSpan = (x1 - x0).takeIf { it > 0.0 } ?: max(abs(x0) * .1, 1.0)
        val rawYSpan = (y1 - y0).takeIf { it > 0.0 } ?: max(abs(y0) * .1, 1.0)
        val xPad = rawXSpan * .06
        val yPad = rawYSpan * .10
        val rawMinX = x0 - xPad
        val rawMaxX = x1 + xPad
        val rawMinY = y0 - yPad
        val rawMaxY = y1 + yPad
        val fullX = rawMaxX - rawMinX
        val fullY = rawMaxY - rawMinY
        val visX = fullX / zoomX
        val visY = fullY / zoomY
        val maxPanX = (1f - 1f / zoomX) / 2f
        val maxPanY = (1f - 1f / zoomY) / 2f
        val cx = rawMinX + fullX * (0.5 + panX.coerceIn(-maxPanX, maxPanX))
        val cy = rawMinY + fullY * (0.5 + panY.coerceIn(-maxPanY, maxPanY))
        val minX = cx - visX / 2
        val maxX = cx + visX / 2
        val minY = cy - visY / 2
        val maxY = cy + visY / 2

        fun sx(x: Double) = left + (((x - minX) / (maxX - minX)) * plotW).toFloat()
        fun sy(y: Double) = bottom - (((y - minY) / (maxY - minY)) * plotH).toFloat()

        if (minY < 0 && maxY > 0) canvas.drawLine(left, sy(0.0), right, sy(0.0), gridPaint)

        canvas.save()
        canvas.clipRect(left, top, right, bottom)
        val path = Path()
        var started = false
        points.forEach { (x, y) ->
            val px = sx(x); val py = sy(y)
            if (!started) { path.moveTo(px, py); started = true } else path.lineTo(px, py)
        }
        canvas.drawPath(path, linePaint)
        val radius = (2.2f * resources.displayMetrics.density).coerceAtMost(5f)
        points.forEach { (x, y) -> canvas.drawCircle(sx(x), sy(y), radius, pointPaint) }

        if (rugSamples.isNotEmpty()) {
            val rugTop = bottom - (8f * resources.displayMetrics.density)
            rugSamples.forEach { value ->
                val x = sx(value)
                canvas.drawLine(x, bottom, x, rugTop, rugPaint)
            }
        }
        canvas.restore()

        canvas.drawText(compact(maxY), 4f, top + textPaint.textSize, textPaint)
        canvas.drawText(compact(minY), 4f, bottom, textPaint)
        drawXLabels(canvas, left, right, bottom, minX, maxX)
    }

    private fun drawXLabels(canvas: Canvas, left: Float, right: Float, bottom: Float, minX: Double, maxX: Double) {
        val marks = listOf(0.0, .5, 1.0)
        val date = SimpleDateFormat("d/M/yy", Locale.getDefault())
        val time = SimpleDateFormat("HH:mm", Locale.getDefault())
        for (f in marks) {
            val xValue = minX + (maxX - minX) * f
            val px = left + (right - left) * f.toFloat()
            if (axisKind == AxisKind.TIME) {
                val d = Date(xValue.toLong())
                val a = date.format(d)
                val b = time.format(d)
                canvas.drawText(a, (px - textPaint.measureText(a) / 2).coerceIn(left, right - textPaint.measureText(a)), bottom + textPaint.textSize + 3f, textPaint)
                canvas.drawText(b, (px - textPaint.measureText(b) / 2).coerceIn(left, right - textPaint.measureText(b)), bottom + textPaint.textSize * 2 + 4f, textPaint)
            } else {
                val label = compact(xValue)
                canvas.drawText(label, (px - textPaint.measureText(label) / 2).coerceIn(left, right - textPaint.measureText(label)), bottom + textPaint.textSize + 4f, textPaint)
            }
        }
    }

    private fun clampPan() {
        val x = (1f - 1f / zoomX) / 2f
        val y = (1f - 1f / zoomY) / 2f
        panX = panX.coerceIn(-x, x)
        panY = panY.coerceIn(-y, y)
    }

    private fun compact(value: Double): String = when {
        !value.isFinite() -> "N/A"
        value == value.toLong().toDouble() -> value.toLong().toString()
        abs(value) >= 1_000_000 -> String.format(Locale.US, "%.2e", value)
        else -> String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
    }

    private fun withAlpha(color: Int, alpha: Int) = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
}
