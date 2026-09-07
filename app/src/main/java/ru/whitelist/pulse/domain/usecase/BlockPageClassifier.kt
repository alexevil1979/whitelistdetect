package ru.whitelist.pulse.domain.usecase

object BlockPageClassifier {

    private val markers = listOf(
        "доступ ограничен",
        "доступ к информационному ресурсу ограничен",
        "ресурс заблокирован",
        "blocked by your network",
        "captiveportal",
        "hotspot-detect",
        "wifi login required",
        "network authentication required",
    )

    fun isBlockPage(body: String?, httpCode: Int?): Boolean {
        if (body.isNullOrBlank()) return false
        if (httpCode != null && httpCode !in 200..399) return false
        val lower = body.lowercase()
        return markers.any { it in lower }
    }
}
