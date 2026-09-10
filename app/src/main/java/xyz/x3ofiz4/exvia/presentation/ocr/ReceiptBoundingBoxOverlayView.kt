package xyz.x3ofiz4.exvia.presentation.ocr

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import xyz.x3ofiz4.exvia.data.ocr.ReceiptScriptEngine
import xyz.x3ofiz4.exvia.domain.model.custom.OcrBoundingBox
import xyz.x3ofiz4.exvia.domain.model.theme.ThemePalette
import xyz.x3ofiz4.exvia.domain.model.theme.ThemePreset
import xyz.x3ofiz4.exvia.presentation.common.AppFonts
import xyz.x3ofiz4.exvia.presentation.common.primaryColor
import xyz.x3ofiz4.exvia.presentation.common.senaryColor
import xyz.x3ofiz4.exvia.presentation.common.tertiaryColor
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Interactive canvas view allowing visual definition, inspection, and manipulation of
 * OCR bounding boxes directly over a receipt image.
 *
 * Coordinates are normalized to [0.0..1.0] relative to the underlying image bitmap dimensions,
 * making templates robust across different image resolutions and screen display sizes.
 *
 * Unselected boxes feature zero border stroke and a subtle transparent background tint,
 * ensuring underlying text is never obscured when inspecting or viewing the receipt.
 */
class ReceiptBoundingBoxOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var bitmap: Bitmap? = null
        set(value) {
            field = value
            invalidate()
        }

    private val _boxes = mutableListOf<OcrBoundingBox>()
    val boxes: List<OcrBoundingBox> get() = _boxes

    var selectedIndex: Int = -1
        private set

    val selectedBox: OcrBoundingBox?
        get() = _boxes.getOrNull(selectedIndex)

    var onSelectionChangedListener: ((OcrBoundingBox?, Int) -> Unit)? = null
    var onBoxCreatedListener: ((OcrBoundingBox) -> Unit)? = null
    var onBoxesChangedListener: (() -> Unit)? = null

    var palette: ThemePalette = ThemePalette.preset(ThemePreset.DEFAULT)
        set(value) {
            field = value
            updatePaintColors()
            invalidate()
        }

    // Drawing paints
    private val boxFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val selectedStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(0.85f)
    }

    private val selectedFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }

    private val badgeBackgroundUnselectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(150, 0, 0, 0)
    }

    private val badgeBackgroundSelectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(225, 0, 0, 0)
    }

    private val badgeStrokeSelectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(0.8f)
    }

    private val textPaintUnselected = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(8.5f)
        typeface = AppFonts.jetBrains(context)
    }

    private val textPaintSelected = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(9f)
        typeface = AppFonts.jetBrains(context)
    }

    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7D7D7D")
        textSize = dp(13f)
        textAlign = Paint.Align.CENTER
        typeface = AppFonts.jetBrains(context)
    }

    private val imageDestRect = RectF()

    // Touch interaction states
    private enum class TouchMode { NONE, DRAW_NEW, MOVE, RESIZE_TL, RESIZE_TR, RESIZE_BL, RESIZE_BR }
    private var touchMode = TouchMode.NONE
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var currentTouchX = 0f
    private var currentTouchY = 0f
    private var boxBeforeDrag: OcrBoundingBox? = null

    private val handleRadius = dp(4.5f)
    private val touchSlop = dp(20f)

    init {
        updatePaintColors()
    }

    private fun updatePaintColors() {
        val primary = palette.primaryColor()
        val r = Color.red(primary)
        val g = Color.green(primary)
        val b = Color.blue(primary)

        // Unselected boxes: subtle, non-intrusive transparent fill (zero border)
        boxFillPaint.color = Color.argb(28, r, g, b)

        // Selected box: ultra-thin border & slight tint
        selectedStrokePaint.color = primary
        selectedStrokePaint.strokeWidth = dp(0.85f)
        selectedFillPaint.color = Color.argb(50, r, g, b)

        handleStrokePaint.color = primary
        badgeStrokeSelectedPaint.color = primary

        textPaintUnselected.color = palette.senaryColor()
    }

    fun setThemePalette(newPalette: ThemePalette) {
        palette = newPalette
    }

    fun setBoxes(newBoxes: List<OcrBoundingBox>) {
        _boxes.clear()
        _boxes.addAll(newBoxes)
        if (selectedIndex >= _boxes.size) {
            selectedIndex = if (_boxes.isNotEmpty()) 0 else -1
        }
        invalidate()
        onSelectionChangedListener?.invoke(selectedBox, selectedIndex)
        onBoxesChangedListener?.invoke()
    }

    fun selectBox(index: Int) {
        selectedIndex = if (index in _boxes.indices) index else -1
        invalidate()
        onSelectionChangedListener?.invoke(selectedBox, selectedIndex)
    }

    fun clearSelection() {
        selectBox(-1)
    }

    fun updateSelectedBox(
        name: String,
        mapTo: String,
        script: String = selectedBox?.script.orEmpty(),
        regex: String = selectedBox?.regex.orEmpty(),
    ) {
        val idx = selectedIndex
        if (idx in _boxes.indices) {
            val current = _boxes[idx]
            _boxes[idx] = current.copy(name = name, mapTo = mapTo, script = script, regex = regex)
            invalidate()
            onBoxesChangedListener?.invoke()
        }
    }

    fun deleteSelectedBox(): Boolean {
        val idx = selectedIndex
        if (idx in _boxes.indices) {
            _boxes.removeAt(idx)
            selectedIndex = if (_boxes.isNotEmpty()) (idx - 1).coerceAtLeast(0) else -1
            invalidate()
            onSelectionChangedListener?.invoke(selectedBox, selectedIndex)
            onBoxesChangedListener?.invoke()
            return true
        }
        return false
    }

    fun addBox(box: OcrBoundingBox) {
        _boxes.add(box)
        selectedIndex = _boxes.lastIndex
        invalidate()
        onBoxCreatedListener?.invoke(box)
        onSelectionChangedListener?.invoke(box, selectedIndex)
        onBoxesChangedListener?.invoke()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(palette.tertiaryColor())

        val bmp = bitmap
        if (bmp == null || bmp.width <= 0 || bmp.height <= 0) {
            canvas.drawText("No receipt image loaded", width / 2f, height / 2f, placeholderPaint)
            return
        }

        // Fit image inside view bounds maintaining aspect ratio
        val viewW = width.toFloat()
        val viewH = height.toFloat()
        val bmpW = bmp.width.toFloat()
        val bmpH = bmp.height.toFloat()

        val scale = min(viewW / bmpW, viewH / bmpH)
        val scaledW = bmpW * scale
        val scaledH = bmpH * scale
        val imgLeft = (viewW - scaledW) / 2f
        val imgTop = (viewH - scaledH) / 2f

        imageDestRect.set(imgLeft, imgTop, imgLeft + scaledW, imgTop + scaledH)
        canvas.drawBitmap(bmp, null, imageDestRect, null)

        // Draw existing bounding boxes: unselected first, selected last so handles render on top
        _boxes.forEachIndexed { index, box ->
            if (index != selectedIndex) {
                drawBox(canvas, box, isSelected = false)
            }
        }
        if (selectedIndex in _boxes.indices) {
            drawBox(canvas, _boxes[selectedIndex], isSelected = true)
        }

        // Draw active creation drag
        if (touchMode == TouchMode.DRAW_NEW) {
            val left = min(touchDownX, currentTouchX).coerceIn(imageDestRect.left, imageDestRect.right)
            val right = max(touchDownX, currentTouchX).coerceIn(imageDestRect.left, imageDestRect.right)
            val top = min(touchDownY, currentTouchY).coerceIn(imageDestRect.top, imageDestRect.bottom)
            val bottom = max(touchDownY, currentTouchY).coerceIn(imageDestRect.top, imageDestRect.bottom)

            val draftRect = RectF(left, top, right, bottom)
            canvas.drawRect(draftRect, selectedFillPaint)
            canvas.drawRect(draftRect, selectedStrokePaint)
        }
    }

    private fun drawBox(canvas: Canvas, box: OcrBoundingBox, isSelected: Boolean) {
        val rect = toScreenRect(box)

        if (isSelected) {
            // Selected box: subtle fill and ultra-thin border
            canvas.drawRect(rect, selectedFillPaint)
            canvas.drawRect(rect, selectedStrokePaint)
        } else {
            // Unselected box: NO border stroke, only unobtrusive transparent fill
            canvas.drawRect(rect, boxFillPaint)
        }

        // Draw compact label badge
        val labelText = if (box.mapTo.isNotBlank()) "${box.name} [→ ${box.mapTo}]" else box.name
        val textPaint = if (isSelected) textPaintSelected else textPaintUnselected
        val textWidth = textPaint.measureText(labelText)
        val badgePadding = dp(3f)
        val badgeHeight = dp(13f)

        val badgeLeft = rect.left
        val badgeTop = (rect.top - badgeHeight - dp(1.5f)).coerceAtLeast(imageDestRect.top)
        val badgeRight = badgeLeft + textWidth + (badgePadding * 2)
        val badgeBottom = badgeTop + badgeHeight

        val badgeRect = RectF(badgeLeft, badgeTop, badgeRight, badgeBottom)
        if (isSelected) {
            canvas.drawRoundRect(badgeRect, dp(2.5f), dp(2.5f), badgeBackgroundSelectedPaint)
            canvas.drawRoundRect(badgeRect, dp(2.5f), dp(2.5f), badgeStrokeSelectedPaint)
        } else {
            // Unselected: no border, semi-transparent background
            canvas.drawRoundRect(badgeRect, dp(2.5f), dp(2.5f), badgeBackgroundUnselectedPaint)
        }
        canvas.drawText(labelText, badgeLeft + badgePadding, badgeBottom - dp(3f), textPaint)

        // Draw corner resize handles only for selected box
        if (isSelected) {
            drawHandle(canvas, rect.left, rect.top)
            drawHandle(canvas, rect.right, rect.top)
            drawHandle(canvas, rect.left, rect.bottom)
            drawHandle(canvas, rect.right, rect.bottom)
        }
    }

    private fun drawHandle(canvas: Canvas, x: Float, y: Float) {
        canvas.drawCircle(x, y, handleRadius, handlePaint)
        canvas.drawCircle(x, y, handleRadius, handleStrokePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (bitmap == null || imageDestRect.width() <= 0) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x
                touchDownY = event.y
                currentTouchX = event.x
                currentTouchY = event.y

                // 1. Check if touching a resize handle of selected box
                if (selectedIndex in _boxes.indices) {
                    val rect = toScreenRect(_boxes[selectedIndex])
                    when {
                        isNear(event.x, event.y, rect.left, rect.top) -> {
                            touchMode = TouchMode.RESIZE_TL
                            boxBeforeDrag = _boxes[selectedIndex]
                            return true
                        }
                        isNear(event.x, event.y, rect.right, rect.top) -> {
                            touchMode = TouchMode.RESIZE_TR
                            boxBeforeDrag = _boxes[selectedIndex]
                            return true
                        }
                        isNear(event.x, event.y, rect.left, rect.bottom) -> {
                            touchMode = TouchMode.RESIZE_BL
                            boxBeforeDrag = _boxes[selectedIndex]
                            return true
                        }
                        isNear(event.x, event.y, rect.right, rect.bottom) -> {
                            touchMode = TouchMode.RESIZE_BR
                            boxBeforeDrag = _boxes[selectedIndex]
                            return true
                        }
                    }
                }

                // 2. Check if touching inside an existing box (start with selected, then top-to-bottom)
                val hitIndex = findBoxAt(event.x, event.y)
                if (hitIndex != -1) {
                    selectedIndex = hitIndex
                    touchMode = TouchMode.MOVE
                    boxBeforeDrag = _boxes[selectedIndex]
                    invalidate()
                    onSelectionChangedListener?.invoke(selectedBox, selectedIndex)
                    return true
                }

                // 3. Touching outside any box inside the image area: start tentative drag
                if (imageDestRect.contains(event.x, event.y)) {
                    touchMode = TouchMode.DRAW_NEW
                    return true
                }

                touchMode = TouchMode.NONE
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                currentTouchX = event.x
                currentTouchY = event.y

                when (touchMode) {
                    TouchMode.MOVE -> {
                        val base = boxBeforeDrag ?: return true
                        val dxNorm = (event.x - touchDownX) / imageDestRect.width()
                        val dyNorm = (event.y - touchDownY) / imageDestRect.height()

                        val newPosX = (base.posX + dxNorm).coerceIn(0f, 1f - base.width)
                        val newPosY = (base.posY + dyNorm).coerceIn(0f, 1f - base.height)

                        _boxes[selectedIndex] = base.copy(posX = newPosX, posY = newPosY)
                        invalidate()
                    }

                    TouchMode.RESIZE_TL, TouchMode.RESIZE_TR, TouchMode.RESIZE_BL, TouchMode.RESIZE_BR -> {
                        val base = boxBeforeDrag ?: return true
                        val baseRect = toScreenRect(base)

                        var left = baseRect.left
                        var top = baseRect.top
                        var right = baseRect.right
                        var bottom = baseRect.bottom

                        val minSize = dp(16f)

                        when (touchMode) {
                            TouchMode.RESIZE_TL -> {
                                left = min(event.x, right - minSize).coerceIn(imageDestRect.left, imageDestRect.right)
                                top = min(event.y, bottom - minSize).coerceIn(imageDestRect.top, imageDestRect.bottom)
                            }
                            TouchMode.RESIZE_TR -> {
                                right = max(event.x, left + minSize).coerceIn(imageDestRect.left, imageDestRect.right)
                                top = min(event.y, bottom - minSize).coerceIn(imageDestRect.top, imageDestRect.bottom)
                            }
                            TouchMode.RESIZE_BL -> {
                                left = min(event.x, right - minSize).coerceIn(imageDestRect.left, imageDestRect.right)
                                bottom = max(event.y, top + minSize).coerceIn(imageDestRect.top, imageDestRect.bottom)
                            }
                            TouchMode.RESIZE_BR -> {
                                right = max(event.x, left + minSize).coerceIn(imageDestRect.left, imageDestRect.right)
                                bottom = max(event.y, top + minSize).coerceIn(imageDestRect.top, imageDestRect.bottom)
                            }
                            else -> Unit
                        }

                        val updated = toNormalizedBox(
                            name = base.name,
                            mapTo = base.mapTo,
                            screenRect = RectF(left, top, right, bottom),
                            script = base.script,
                            regex = base.regex,
                        )
                        _boxes[selectedIndex] = updated
                        invalidate()
                    }

                    TouchMode.DRAW_NEW -> {
                        invalidate()
                    }

                    TouchMode.NONE -> Unit
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val dragDistX = abs(event.x - touchDownX)
                val dragDistY = abs(event.y - touchDownY)

                if (touchMode == TouchMode.DRAW_NEW) {
                    if (dragDistX < dp(10f) && dragDistY < dp(10f)) {
                        // User tapped empty area without dragging -> Deselect all boxes
                        clearSelection()
                    } else {
                        val left = min(touchDownX, event.x).coerceIn(imageDestRect.left, imageDestRect.right)
                        val right = max(touchDownX, event.x).coerceIn(imageDestRect.left, imageDestRect.right)
                        val top = min(touchDownY, event.y).coerceIn(imageDestRect.top, imageDestRect.bottom)
                        val bottom = max(touchDownY, event.y).coerceIn(imageDestRect.top, imageDestRect.bottom)

                        val drawnRect = RectF(left, top, right, bottom)
                        if (drawnRect.width() >= dp(14f) && drawnRect.height() >= dp(12f)) {
                            val newBox = toNormalizedBox(
                                name = "Field ${_boxes.size + 1}",
                                mapTo = if (_boxes.isEmpty()) "price" else "description",
                                screenRect = drawnRect,
                                script = ReceiptScriptEngine.DEFAULT_RECEIPT_JS_SCRIPT,
                                regex = "",
                            )
                            _boxes.add(newBox)
                            selectedIndex = _boxes.lastIndex
                            onBoxCreatedListener?.invoke(newBox)
                            onSelectionChangedListener?.invoke(newBox, selectedIndex)
                            onBoxesChangedListener?.invoke()
                        }
                    }
                } else if (touchMode == TouchMode.MOVE || touchMode.name.startsWith("RESIZE")) {
                    onBoxesChangedListener?.invoke()
                }

                touchMode = TouchMode.NONE
                boxBeforeDrag = null
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun findBoxAt(x: Float, y: Float): Int {
        if (selectedIndex in _boxes.indices && toScreenRect(_boxes[selectedIndex]).contains(x, y)) {
            return selectedIndex
        }
        for (i in _boxes.indices.reversed()) {
            if (toScreenRect(_boxes[i]).contains(x, y)) {
                return i
            }
        }
        return -1
    }

    private fun isNear(x1: Float, y1: Float, x2: Float, y2: Float): Boolean {
        return abs(x1 - x2) <= touchSlop && abs(y1 - y2) <= touchSlop
    }

    private fun toScreenRect(box: OcrBoundingBox): RectF {
        val left = imageDestRect.left + (box.posX * imageDestRect.width())
        val top = imageDestRect.top + (box.posY * imageDestRect.height())
        val right = left + (box.width * imageDestRect.width())
        val bottom = top + (box.height * imageDestRect.height())
        return RectF(left, top, right, bottom)
    }

    private fun toNormalizedBox(
        name: String,
        mapTo: String,
        screenRect: RectF,
        script: String = "",
        regex: String = "",
    ): OcrBoundingBox {
        val imgW = imageDestRect.width().coerceAtLeast(1f)
        val imgH = imageDestRect.height().coerceAtLeast(1f)

        val posX = ((screenRect.left - imageDestRect.left) / imgW).coerceIn(0f, 1f)
        val posY = ((screenRect.top - imageDestRect.top) / imgH).coerceIn(0f, 1f)
        val width = (screenRect.width() / imgW).coerceIn(0.01f, 1f - posX)
        val height = (screenRect.height() / imgH).coerceIn(0.01f, 1f - posY)

        return OcrBoundingBox(
            name = name,
            posX = posX,
            posY = posY,
            width = width,
            height = height,
            mapTo = mapTo,
            script = script,
            regex = regex,
        )
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
