package xyz.x3ofiz4.exvia.presentation.common


import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.util.WeakHashMap

object AppFonts {
    private var cached: Typeface? = null
    private val baseTextSizesPx = WeakHashMap<TextView, Float>()

    /** Runtime scale used by dynamically created views after the initial tree pass. */
    @Volatile var defaultTextScale: Double = 1.0

    fun jetBrains(context: Context): Typeface {
        cached?.let { return it }
        val loaded = try {
            Typeface.createFromAsset(context.assets, "fonts/JetBrains.ttf")
        } catch (_: Exception) {
            Typeface.create("sans-serif", Typeface.NORMAL)
        }
        cached = loaded
        return loaded
    }

    fun apply(textView: TextView, bold: Boolean = false, textScale: Double? = null) {
        val base = jetBrains(textView.context)
        textView.typeface = Typeface.create(base, if (bold) Typeface.BOLD else Typeface.NORMAL)
        val effectiveScale = textScale ?: defaultTextScale
        val original = baseTextSizesPx.getOrPut(textView) { textView.textSize }
        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, (original * effectiveScale.toFloat()).coerceAtLeast(1f))
    }

    fun applyToTree(view: View, textScale: Double = 1.0) {
        if (view is TextView) apply(view, view.typeface?.isBold == true, textScale)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) applyToTree(view.getChildAt(index), textScale)
        }
    }
}
