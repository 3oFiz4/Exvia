package com.example.exp_tracker

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

object AppFonts {
    private var cached: Typeface? = null

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

    fun apply(textView: TextView, bold: Boolean = false) {
        val base = jetBrains(textView.context)
        textView.typeface = Typeface.create(base, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    fun applyToTree(view: View) {
        if (view is TextView) apply(view, view.typeface?.isBold == true)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) applyToTree(view.getChildAt(index))
        }
    }
}
