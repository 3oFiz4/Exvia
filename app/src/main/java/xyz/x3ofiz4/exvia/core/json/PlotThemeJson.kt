package xyz.x3ofiz4.exvia.core.json

import org.json.JSONObject
import xyz.x3ofiz4.exvia.domain.model.theme.PlotTheme

/** Serializes a domain plot theme at the application boundary. */
fun PlotTheme.toJson(): JSONObject = JSONObject().apply {
    put("background", background)
    put("surface", surface)
    put("text", text)
    put("muted", muted)
    put("grid", grid)
    put("axis", axis)
    put("positive", positive)
    put("negative", negative)
    put("observation", observation)
    put("outlier", outlier)
    put("center", center)
    put("accent", accent)
    put("selection", selection)
    put("tooltipBackground", tooltipBackground)
    put("tooltipText", tooltipText)
    put("tooltipBorder", tooltipBorder)
}
