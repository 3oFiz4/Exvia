package xyz.x3ofiz4.exvia.domain.model.table

data class DynamicRow(
    val values: LinkedHashMap<String, String>,
    val originalIndex: Int,
    val originalJson: String,
)

data class TableData(
    val keys: List<String>,
    val rows: List<DynamicRow>,
    val dateKey: String?,
    val moneyKey: String?,
    val tickerKey: String?,
    val tagsKey: String?,
)
