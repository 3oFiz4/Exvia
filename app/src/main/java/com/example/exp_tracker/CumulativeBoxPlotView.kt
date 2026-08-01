package com.example.exp_tracker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
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
import kotlin.math.min

/**
 * Interactive cumulative statistical timeline.
 *
 * Each timestamp is a candlestick-like distribution snapshot over timestamp-level
 * totals up to that time. Rows sharing the same datetime have already been summed:
 *  - Q1..Q3: solid red/green box
 *  - median: solid high-contrast horizontal mark
 *  - mean: dotted high-contrast horizontal mark
 *  - mean ± 1 population standard deviation: red/green whisker + caps
 *  - Tukey outliers (outside Q1 - 1.5 IQR .. Q3 + 1.5 IQR): hollow circles
 *  - total numeric value at that timestamp: blue filled rhombus
 *
 * Box direction compares timestamp totals: green when current > previous, otherwise
 * red. The first timestamp is neutral because it has no prior observation.
 *
 * Gestures: pinch = zoom, drag = pan, double tap = reset, tap = inspect.
 */
class CumulativeBoxPlotView(context: Context) : View(context) {
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1f; style = Paint.Style.STROKE }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1f; style = Paint.Style.STROKE }
    private val boxFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1.8f; style = Paint.Style.STROKE }
    private val medianPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2.4f; style = Paint.Style.STROKE }
    private val meanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 2.2f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(5f, 3f), 0f)
    }
    private val stdPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1.8f; style = Paint.Style.STROKE }
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2.5f; style = Paint.Style.STROKE }
    private val trendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1.8f; style = Paint.Style.STROKE }
    private val outlierPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1.8f; style = Paint.Style.STROKE }
    private val actualPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val actualOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1.3f; style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f * resources.displayMetrics.scaledDensity }
    private val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f * resources.displayMetrics.scaledDensity }
    private val detailBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private var backgroundColorValue = Color.BLACK
    private var axisColorValue = Color.rgb(125, 125, 125)
    private var textColorValue = Color.rgb(237, 237, 237)
    private var gridColorValue = Color.rgb(31, 31, 31)

    // Direction and chart semantics. These intentionally stay independent from the
    // configurable six-role theme so red/green/blue retain the same meaning.
    private val negativeColor = Color.rgb(247, 35, 35)
    private val positiveColor = Color.rgb(52, 199, 89)
    private val actualColor = Color.rgb(61, 139, 255)
    private val outlierColor = Color.rgb(247, 35, 35)
    private val positiveContrast = Color.rgb(240, 255, 244)
    private val negativeContrast = Color.rgb(255, 240, 240)

    private var points: List<Statistics.CumulativeBoxPoint> = emptyList()
    private var selectedIndex: Int? = null
    private var timeAxis = true
    private var customXLabels: Map<Long, String> = emptyMap()

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

        override fun onDoubleTap(e: MotionEvent): Boolean {
            resetViewport()
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            selectNearest(e.x, e.y)
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            val plotWidth = (width - 72f).coerceAtLeast(1f)
            val plotHeight = (height - 72f).coerceAtLeast(1f)
            panX += distanceX / plotWidth / zoomX
            panY -= distanceY / plotHeight / zoomY
            clampPan()
            invalidate()
            return true
        }
    })

    init {
        textPaint.typeface = AppFonts.jetBrains(context)
        detailPaint.typeface = AppFonts.jetBrains(context)
        isClickable = true
        isFocusable = true
    }

    fun setPalette(background: Int, axis: Int, text: Int, grid: Int) {
        backgroundColorValue = background
        axisColorValue = axis
        textColorValue = text
        gridColorValue = grid
        invalidate()
    }

    fun setSeries(points: List<Statistics.CumulativeBoxPoint>) {
        this.points = points.sortedBy { it.x }
        selectedIndex = this.points.lastIndex.takeIf { it >= 0 }
        resetViewport()
    }

    fun setXAxis(timeAxis: Boolean, labels: Map<Long, String> = emptyMap()) {
        this.timeAxis = timeAxis
        this.customXLabels = labels
        invalidate()
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
        textPaint.color = textColorValue
        detailPaint.color = textColorValue
        detailBackgroundPaint.color = withAlpha(backgroundColorValue, 232)
        selectedPaint.color = actualColor
        trendPaint.color = withAlpha(actualColor, 102) // 40% alpha
        outlierPaint.color = outlierColor
        actualPaint.color = actualColor
        actualOutlinePaint.color = textColorValue

        if (points.isEmpty()) {
            canvas.drawText("No dated numeric values for cumulative box plot", 12f, height / 2f, textPaint)
            return
        }

        val left = 58f
        val right = max(left + 1f, width - 14f)
        val top = 50f
        val bottom = max(top + 1f, height - 66f)
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

        val dataMinX = points.minOf { it.x }.toDouble()
        val dataMaxX = points.maxOf { it.x }.toDouble()
        val dataXSpan = (dataMaxX - dataMinX).takeIf { it > 0.0 } ?: 1.0
        // Keep the first and last statistical boxes/nodes away from the plot clip edge.
        val xPadding = dataXSpan * 0.055
        val rawMinX = dataMinX - xPadding
        val rawMaxX = dataMaxX + xPadding
        val rawMinY0 = points.minOf { p ->
            min(min(p.q1, p.lowerStd), min(p.sourceValue, p.outliers.minOrNull() ?: p.sourceValue))
        }
        val rawMaxY0 = points.maxOf { p ->
            max(max(p.q3, p.upperStd), max(p.sourceValue, p.outliers.maxOrNull() ?: p.sourceValue))
        }
        val yPadding = ((rawMaxY0 - rawMinY0) * 0.08).takeIf { it > 0.0 } ?: max(abs(rawMaxY0) * 0.08, 1.0)
        val rawMinY = rawMinY0 - yPadding
        val rawMaxY = rawMaxY0 + yPadding
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

        if (minY < 0.0 && maxY > 0.0) {
            val zeroY = sy(0.0)
            val zeroPaint = Paint(gridPaint).apply { color = withAlpha(negativeColor, 150); strokeWidth = 1.2f }
            canvas.drawLine(left, zeroY, right, zeroY, zeroPaint)
            canvas.drawText("0", 4f, zeroY + textPaint.textSize / 2f, textPaint)
        }

        val visible = points.mapIndexedNotNull { index, point ->
            val x = sx(point.x)
            if (x < left - 30f || x > right + 30f) null else index to x
        }
        val estimatedSpacing = if (visible.size > 1) {
            visible.zipWithNext().map { abs(it.second.second - it.first.second) }.filter { it > 0.5f }.minOrNull() ?: 18f
        } else 22f
        val halfBox = (estimatedSpacing * 0.30f).coerceIn(3f, 10f * resources.displayMetrics.density)
        val capHalf = halfBox * 0.72f

        canvas.save()
        canvas.clipRect(left, top, right, bottom)

        // 40%-transparent observation path. It connects timestamp-level totals and
        // intentionally sits behind the statistical boxes so it remains secondary.
        val trendPath = Path()
        var trendStarted = false
        visible.forEach { (index, x) ->
            val y = sy(points[index].sourceValue)
            if (!trendStarted) {
                trendPath.moveTo(x, y)
                trendStarted = true
            } else {
                trendPath.lineTo(x, y)
            }
        }
        if (trendStarted) canvas.drawPath(trendPath, trendPaint)

        visible.forEach { (index, x) ->
            val p = points[index]
            val q1Y = sy(p.q1)
            val q3Y = sy(p.q3)
            val medianY = sy(p.median)
            val meanY = sy(p.mean)
            val lowY = sy(p.lowerStd)
            val highY = sy(p.upperStd)

            val previousValue = points.getOrNull(index - 1)?.sourceValue
            val directionColor = when {
                previousValue == null -> axisColorValue
                p.sourceValue > previousValue -> positiveColor
                else -> negativeColor
            }
            val contrastColor = when {
                previousValue == null -> textColorValue
                p.sourceValue > previousValue -> positiveContrast
                else -> negativeContrast
            }

            boxFillPaint.color = directionColor
            boxPaint.color = directionColor
            stdPaint.color = directionColor
            medianPaint.color = contrastColor
            meanPaint.color = contrastColor

            // Standard-deviation whisker follows the box direction color.
            canvas.drawLine(x, lowY, x, highY, stdPaint)
            canvas.drawLine(x - capHalf, lowY, x + capHalf, lowY, stdPaint)
            canvas.drawLine(x - capHalf, highY, x + capHalf, highY, stdPaint)

            // Q1-Q3 box uses a fully opaque fill: green if the timestamp total rose,
            // red otherwise. The first observation is neutral because no comparison exists.
            val boxTop = min(q1Y, q3Y)
            val boxBottom = max(q1Y, q3Y)
            if (boxBottom - boxTop < 1.5f) {
                canvas.drawLine(x - halfBox, boxTop, x + halfBox, boxTop, boxPaint)
            } else {
                canvas.drawRect(x - halfBox, boxTop, x + halfBox, boxBottom, boxFillPaint)
                canvas.drawRect(x - halfBox, boxTop, x + halfBox, boxBottom, boxPaint)
            }

            // Mean = dotted line; median = solid line. Both use a high-contrast tint
            // derived from the red/green state so they remain visible on an opaque box.
            canvas.drawLine(x - halfBox, meanY, x + halfBox, meanY, meanPaint)
            canvas.drawLine(x - halfBox, medianY, x + halfBox, medianY, medianPaint)

            // Tukey outliers for the cumulative timestamp-total distribution.
            // Actual timestamp total = a compact blue rhombus. It is slightly wider than
            // tall so the marker reads like a diamond/short rectangle rather than a dot.
            val actualY = sy(p.sourceValue)
            val actualHalfW = (halfBox * 0.52f).coerceIn(3.5f, 6.5f * resources.displayMetrics.density)
            val actualHalfH = (actualHalfW * 0.58f).coerceAtLeast(2.4f)
            val diamond = Path().apply {
                moveTo(x - actualHalfW, actualY)
                lineTo(x, actualY - actualHalfH)
                lineTo(x + actualHalfW, actualY)
                lineTo(x, actualY + actualHalfH)
                close()
            }
            canvas.drawPath(diamond, actualPaint)
            canvas.drawPath(diamond, actualOutlinePaint)

            // Tiny red hollow circles denote Tukey outliers. Draw them after the blue
            // observation so an observation that is itself an outlier remains visible.
            val outlierRadius = (1.55f * resources.displayMetrics.density).coerceAtLeast(1.6f)
            p.outliers.forEach { outlier ->
                canvas.drawCircle(x, sy(outlier), outlierRadius, outlierPaint)
            }

            if (selectedIndex == index) {
                val outlierTop = p.outliers.minOfOrNull { sy(it) } ?: actualY
                val outlierBottom = p.outliers.maxOfOrNull { sy(it) } ?: actualY
                val highlightTop = min(min(highY, boxTop), min(actualY, outlierTop))
                val highlightBottom = max(max(lowY, boxBottom), max(actualY, outlierBottom))
                canvas.drawRect(x - halfBox - 3f, highlightTop - 3f, x + halfBox + 3f, highlightBottom + 3f, selectedPaint)
            }
        }
        canvas.restore()

        if (timeAxis) drawWrappedTimeLabels(canvas, visible, left, right, bottom) else drawCustomXLabels(canvas, visible, left, right, bottom)
        canvas.drawText(compact(maxY), 4f, top + textPaint.textSize, textPaint)
        canvas.drawText(compact(minY), 4f, bottom, textPaint)

        drawLegend(canvas, left, right, top)
        selectedIndex?.takeIf { it in points.indices }?.let { drawSelectedDetail(canvas, points[it], left, right, bottom) }
    }

    private fun drawWrappedTimeLabels(
        canvas: Canvas,
        visible: List<Pair<Int, Float>>,
        left: Float,
        right: Float,
        bottom: Float,
    ) {
        if (visible.isEmpty()) return
        val dateFormatter = SimpleDateFormat("d/M/yy", Locale.getDefault())
        val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        val minimumLabelWidth = 68f * resources.displayMetrics.density
        val maxLabels = max(2, ((right - left) / minimumLabelWidth).toInt())
        val step = max(1, kotlin.math.ceil(visible.size.toDouble() / maxLabels).toInt())
        val chosen = visible.filterIndexed { i, _ -> i % step == 0 }.toMutableList()
        if (chosen.lastOrNull()?.first != visible.last().first) chosen += visible.last()

        chosen.distinctBy { it.first }.forEach { (index, x) ->
            val date = Date(points[index].x)
            val topLine = dateFormatter.format(date)
            val bottomLine = timeFormatter.format(date)
            val dateX = (x - textPaint.measureText(topLine) / 2f).coerceIn(left, right - textPaint.measureText(topLine))
            val timeX = (x - textPaint.measureText(bottomLine) / 2f).coerceIn(left, right - textPaint.measureText(bottomLine))
            canvas.drawText(topLine, dateX, bottom + textPaint.textSize + 5f, textPaint)
            canvas.drawText(bottomLine, timeX, bottom + textPaint.textSize * 2f + 8f, textPaint)
        }
    }


    private fun drawCustomXLabels(
        canvas: Canvas,
        visible: List<Pair<Int, Float>>,
        left: Float,
        right: Float,
        bottom: Float,
    ) {
        if (visible.isEmpty()) return
        val minimumLabelWidth = 64f * resources.displayMetrics.density
        val maxLabels = max(2, ((right - left) / minimumLabelWidth).toInt())
        val step = max(1, kotlin.math.ceil(visible.size.toDouble() / maxLabels).toInt())
        val chosen = visible.filterIndexed { i, _ -> i % step == 0 }.toMutableList()
        if (chosen.lastOrNull()?.first != visible.last().first) chosen += visible.last()
        chosen.distinctBy { it.first }.forEach { (index, x) ->
            val raw = customXLabels[points[index].x] ?: points[index].x.toString()
            val label = ellipsize(raw, minimumLabelWidth * 1.4f, textPaint)
            val labelX = (x - textPaint.measureText(label) / 2f).coerceIn(left, right - textPaint.measureText(label))
            canvas.drawText(label, labelX, bottom + textPaint.textSize + 7f, textPaint)
        }
    }

    private fun drawLegend(canvas: Canvas, left: Float, right: Float, top: Float) {
        var x = left
        var y = top - detailPaint.textSize - 7f
        val rowStep = detailPaint.textSize + 4f

        fun label(symbol: String, text: String, color: Int) {
            val symbolWidth = detailPaint.measureText(symbol)
            val textWidth = detailPaint.measureText(text)
            val itemWidth = symbolWidth + 3f + textWidth + 11f
            if (x > left && x + itemWidth > right) {
                x = left
                y += rowStep
            }
            detailPaint.color = color
            canvas.drawText(symbol, x, y, detailPaint)
            x += symbolWidth + 3f
            detailPaint.color = textColorValue
            canvas.drawText(text, x, y, detailPaint)
            x += textWidth + 11f
        }

        label("■", "box: ↑ green / ↓ red", positiveColor)
        label("—", "median", textColorValue)
        label("⋯", "mean", textColorValue)
        label("│", "±1σ follows box", axisColorValue)
        label("○", "outlier", outlierColor)
        label("◆", "datetime total", actualColor)
        label("─", "observation path (40%)", withAlpha(actualColor, 102))
    }

    private fun drawSelectedDetail(canvas: Canvas, p: Statistics.CumulativeBoxPoint, left: Float, right: Float, bottom: Float) {
        val xLabel = if (timeAxis) {
            SimpleDateFormat("d/M/yy @ HH:mm", Locale.getDefault()).format(Date(p.x))
        } else customXLabels[p.x] ?: p.x.toString()
        val outlierStatus = if (p.sourceIsOutlier) "  total=OUTLIER" else ""
        val line = "$xLabel  total=${compact(p.sourceValue)}  rows=${p.sourceCount}  timestamp n=${p.n}  Q1=${compact(p.q1)}  med=${compact(p.median)}  mean=${compact(p.mean)}  Q3=${compact(p.q3)}  σ=${compact(p.stdv)}  outliers=${p.outliers.size}$outlierStatus"
        val maxWidth = right - left
        val clipped = ellipsize(line, maxWidth, detailPaint)
        val textWidth = detailPaint.measureText(clipped)
        val y = bottom + 19f
        canvas.drawRect(left - 3f, y - detailPaint.textSize - 3f, left + textWidth + 5f, y + 4f, detailBackgroundPaint)
        detailPaint.color = textColorValue
        canvas.drawText(clipped, left, y, detailPaint)
    }

    private fun selectNearest(touchX: Float, touchY: Float) {
        if (points.isEmpty()) return
        val left = 58f
        val right = max(left + 1f, width - 14f)
        val top = 50f
        val bottom = max(top + 1f, height - 66f)
        if (touchX !in left..right || touchY !in top..bottom) return

        val dataMinX = points.minOf { it.x }.toDouble()
        val dataMaxX = points.maxOf { it.x }.toDouble()
        val dataSpan = (dataMaxX - dataMinX).takeIf { it > 0.0 } ?: 1.0
        val pad = dataSpan * 0.055
        val rawMinX = dataMinX - pad
        val rawMaxX = dataMaxX + pad
        val fullXSpan = (rawMaxX - rawMinX).takeIf { it != 0.0 } ?: 1.0
        val visibleXSpan = fullXSpan / zoomX
        val maxPanX = (1f - 1f / zoomX) / 2f
        val centerX = rawMinX + fullXSpan * (0.5 + panX.coerceIn(-maxPanX, maxPanX))
        val minX = centerX - visibleXSpan / 2.0
        val maxX = centerX + visibleXSpan / 2.0
        val plotWidth = right - left
        fun sx(x: Long) = left + (((x.toDouble() - minX) / (maxX - minX)) * plotWidth).toFloat()

        selectedIndex = points.indices.minByOrNull { abs(sx(points[it].x) - touchX) }
        invalidate()
    }

    private fun clampPan() {
        val maxX = (1f - 1f / zoomX) / 2f
        val maxY = (1f - 1f / zoomY) / 2f
        panX = panX.coerceIn(-maxX, maxX)
        panY = panY.coerceIn(-maxY, maxY)
    }

    private fun compact(value: Double): String = when {
        value.isNaN() || value.isInfinite() -> "N/A"
        value == value.toLong().toDouble() -> value.toLong().toString()
        abs(value) >= 1_000_000 -> String.format(Locale.US, "%.2e", value)
        else -> String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
    }

    private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(
        alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color),
    )

    private fun ellipsize(text: String, maxWidth: Float, paint: Paint): String {
        if (paint.measureText(text) <= maxWidth) return text
        val suffix = "…"
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end) + suffix) > maxWidth) end--
        return text.substring(0, end) + suffix
    }
}
