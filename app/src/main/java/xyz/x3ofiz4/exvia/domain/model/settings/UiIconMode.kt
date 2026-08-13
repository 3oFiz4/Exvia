package xyz.x3ofiz4.exvia.domain.model.settings

enum class UiIconMode(val id: String, val displayName: String) {
    ICON_ONLY("icon_only", "Icon only"),
    TEXT_ONLY("text_only", "Text only"),
    ICON_AND_TEXT("icon_and_text", "Icon and text");

    companion object {
        fun fromId(value: String?): UiIconMode = entries.firstOrNull { it.id == value } ?: ICON_AND_TEXT
    }
}
