package com.example.exp_tracker

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.view.Gravity
import android.widget.EditText

/**
 * Lightweight JavaScript editor with debounced Ayu-style syntax highlighting.
 * It intentionally avoids a second WebView/code-editor framework to keep typing responsive.
 */
class JavaScriptCodeEditor @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : EditText(context, attrs) {
    private val handler = Handler(Looper.getMainLooper())
    private var applying = false
    private val highlightTask = Runnable { highlightNow() }

    private val keyword = Color.rgb(255, 180, 84)       // Ayu orange
    private val string = Color.rgb(170, 217, 76)        // Ayu green
    private val number = Color.rgb(210, 166, 255)       // Ayu purple
    private val comment = Color.rgb(98, 108, 124)       // Ayu muted
    private val global = Color.rgb(89, 194, 255)        // Ayu blue
    private val normal = Color.rgb(191, 189, 182)

    init {
        setTextColor(normal)
        setHintTextColor(Color.rgb(92, 97, 102))
        setBackgroundColor(Color.rgb(11, 14, 20))
        gravity = Gravity.TOP or Gravity.START
        isHorizontalScrollBarEnabled = true
        isVerticalScrollBarEnabled = true
        setHorizontallyScrolling(true)
        minLines = 12
        maxLines = 24
        setPadding(18, 14, 18, 14)
        typeface = AppFonts.jetBrains(context)
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (applying) return
                handler.removeCallbacks(highlightTask)
                handler.postDelayed(highlightTask, 110L)
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(highlightTask)
        super.onDetachedFromWindow()
    }

    private fun highlightNow() {
        val editable = text ?: return
        val raw = editable.toString()
        applying = true
        try {
            editable.getSpans(0, editable.length, ForegroundColorSpan::class.java)
                .forEach(editable::removeSpan)
            apply(raw, Regex("//[^\\n]*|/\\*[\\s\\S]*?\\*/"), comment, editable)
            apply(raw, Regex("'(?:\\\\.|[^'\\\\])*'|\"(?:\\\\.|[^\"\\\\])*\"|`(?:\\\\.|[^`\\\\])*`"), string, editable)
            apply(raw, Regex("\\b(?:const|let|var|function|return|if|else|for|while|do|switch|case|break|continue|try|catch|finally|throw|new|class|extends|async|await|yield|typeof|instanceof|in|of|delete|void|this|true|false|null|undefined)\\b"), keyword, editable)
            apply(raw, Regex("\\b(?:d3|Plot|aq|jsonFile|context|theme|helpers|Object|Array|Math|Date|JSON|Number|String|Map|Set|Promise)\\b"), global, editable)
            apply(raw, Regex("(?<![A-Za-z0-9_])[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?"), number, editable)
        } finally {
            applying = false
        }
    }

    private fun apply(raw: String, regex: Regex, color: Int, editable: Editable) {
        regex.findAll(raw).forEach { match ->
            editable.setSpan(
                ForegroundColorSpan(color),
                match.range.first,
                match.range.last + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }
}
