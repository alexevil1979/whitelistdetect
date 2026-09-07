package ru.whitelist.pulse.domain.usecase

object DomainListParser {

    private val hostRegex = Regex(
        pattern = "^(?=.{1,253}$)(?!-)[A-Za-z0-9-]{1,63}(?<!-)(\\.(?!-)[A-Za-z0-9-]{1,63}(?<!-))+$",
    )

    fun parse(raw: String): ParseResult {
        val accepted = linkedSetOf<String>()
        val rejected = mutableListOf<String>()
        raw.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
            val host = normalize(trimmed)
            if (host != null) accepted += host else rejected += trimmed
        }
        return ParseResult(accepted.toList(), rejected)
    }

    fun normalize(input: String): String? {
        var value = input.trim()
        if (value.isEmpty()) return null
        value = value.substringBefore('#').trim()
        value = value.removePrefix("http://").removePrefix("https://")
        value = value.substringBefore('/').substringBefore('?').substringBefore(':')
        value = value.trim().trim('.').lowercase()
        if (value.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$"))) return null
        if (!hostRegex.matches(value)) return null
        if (value.endsWith(".local") || value.endsWith(".localhost")) return null
        return value
    }

    data class ParseResult(
        val hosts: List<String>,
        val rejected: List<String>,
    )
}
