package xyz.x3ofiz4.exvia.domain.model.theme


enum class ThemePreset(val id: String, val displayName: String) {
    DEFAULT("default", "Default theme"),
    AYU("ayu", "Ayu"),
    AYU_LIGHT("ayu_light", "Ayu-Light"),
    DEFAULT_LIGHT("default_light", "Default theme-light");

    companion object {
        fun fromId(id: String?): ThemePreset = entries.firstOrNull { it.id == id } ?: DEFAULT
        fun fromDisplayName(name: String?): ThemePreset = entries.firstOrNull { it.displayName == name } ?: DEFAULT
    }
}

data class ThemePalette(
    val primary: String,
    val secondary: String,
    val tertiary: String,
    val quaternary: String,
    val quinary: String,
    val senary: String,
) {
    companion object {
        fun preset(preset: ThemePreset): ThemePalette = when (preset) {
            ThemePreset.DEFAULT -> ThemePalette(
                primary = "#F72323",
                secondary = "#CC0000",
                tertiary = "#000000",
                quaternary = "#1F1F1F",
                quinary = "#7D7D7D",
                senary = "#EDEDED",
            )
            ThemePreset.AYU -> ThemePalette(
                primary = "#FFB454",
                secondary = "#E6B450",
                tertiary = "#0B0E14",
                quaternary = "#131721",
                quinary = "#6C7380",
                senary = "#BFBDB6",
            )
            ThemePreset.AYU_LIGHT -> ThemePalette(
                primary = "#F29718",
                secondary = "#D47A00",
                tertiary = "#FAFAFA",
                quaternary = "#ECEFF1",
                quinary = "#828C99",
                senary = "#5C6166",
            )
            ThemePreset.DEFAULT_LIGHT -> ThemePalette(
                primary = "#F72323",
                secondary = "#CC0000",
                tertiary = "#FFFFFF",
                quaternary = "#EDEDED",
                quinary = "#7D7D7D",
                senary = "#1F1F1F",
            )
        }

        fun isValidHex(value: String): Boolean = value.trim().matches(Regex("#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?"))

    }
}
