package xyz.x3ofiz4.exvia.presentation.common

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.PorterDuff
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import xyz.x3ofiz4.exvia.domain.model.settings.UiIconMode

/**
 * Asset-backed UI icons. All icons live under assets/icons/ and are tinted with the active Primary color.
 * Missing, empty, or undecodable files deliberately resolve to null so UI construction can never fail.
 */
object UiIconSupport {
    fun assetFor(label: String): String {
        val clean = label
            .replace(Regex("\\$\\{[^}]+}"), "")
            .replace("→", " ")
            .replace("·", " ")
            .replace("›", " ")
            .replace("+", " ")
            .replace("−", " ")
            .replace("×", "Delete")
            .trim()

        val lower = clean.lowercase()
        if (lower.startsWith("revert repository")) return "Revert.png"
        if (lower.startsWith("filter ") || lower == "filtering") return "Filtering.png"
        if (lower.startsWith("flag ") || lower == "flagging") return "Flagging.png"
        if (lower == "enabled" || lower == "disabled") return "Toggle.png"
        if (lower.startsWith("page ")) return "Page.png"
        if (lower.startsWith("git")) return "Git.png"
        if (lower.startsWith("re-sync") || lower.startsWith("resync")) return "Resync.png"

        val base = clean.split(Regex("[^A-Za-z0-9]+"))
            .filter { it.isNotBlank() }
            .joinToString("") { token -> token.replaceFirstChar { it.uppercase() } }
            .ifBlank { "Action" }
        return "$base.png"
    }

    fun load(context: Context, assetName: String, tint: Int, sizePx: Int): Drawable? = runCatching {
        context.assets.open("icons/$assetName").use { input ->
            val bitmap = BitmapFactory.decodeStream(input) ?: return null
            BitmapDrawable(context.resources, bitmap).apply {
                setBounds(0, 0, sizePx, sizePx)
                setColorFilter(tint, PorterDuff.Mode.SRC_IN)
            }
        }
    }.getOrNull()

    fun apply(
        view: TextView,
        label: String,
        mode: UiIconMode,
        primary: Int,
        iconSizePx: Int,
        drawablePaddingPx: Int,
        assetName: String = assetFor(label),
        endAssetName: String? = null,
    ) {
        view.contentDescription = label
        val start = if (mode == UiIconMode.TEXT_ONLY) null else load(view.context, assetName, primary, iconSizePx)
        val end = if (mode == UiIconMode.TEXT_ONLY || endAssetName == null) null else load(view.context, endAssetName, primary, iconSizePx)
        view.text = if (mode == UiIconMode.ICON_ONLY) "" else label
        view.compoundDrawablePadding = drawablePaddingPx
        view.setCompoundDrawables(start, null, end, null)
    }
}

/** Adapter used by configuration dropdowns. The collapsed value respects icon mode;
 * the expanded menu always keeps text so individual options remain identifiable. */
class IconSpinnerAdapter(
    context: Context,
    private val items: List<String>,
    private val iconAsset: String,
    private val mode: UiIconMode,
    private val primary: Int,
    private val textColor: Int,
    private val backgroundColor: Int,
    private val iconSizePx: Int,
    private val paddingPx: Int,
) : ArrayAdapter<String>(context, android.R.layout.simple_spinner_dropdown_item, items) {
    override fun getCount(): Int = items.size
    override fun getItem(position: Int): String = items[position]

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
        buildTextView(items[position], collapsed = true)

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
        buildTextView(items[position], collapsed = false)

    private fun buildTextView(label: String, collapsed: Boolean): TextView = TextView(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
        setTextColor(textColor)
        setBackgroundColor(backgroundColor)
        minHeight = paddingPx * 5
        AppFonts.apply(this)
        val effectiveMode = if (!collapsed && mode == UiIconMode.ICON_ONLY) UiIconMode.ICON_AND_TEXT else mode
        UiIconSupport.apply(
            view = this,
            label = label,
            mode = effectiveMode,
            primary = primary,
            iconSizePx = iconSizePx,
            drawablePaddingPx = paddingPx,
            assetName = iconAsset,
        )
    }
}
