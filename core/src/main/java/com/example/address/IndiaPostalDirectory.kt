package com.example.address

/** Bundled PIN/state directory snapshot; no patient address is sent to a lookup service. */
object IndiaPostalDirectory {
    data class Area(val state: String, val district: String)
    enum class Match { INCOMPLETE, INVALID_FORMAT, UNKNOWN_PIN, MISMATCH, MATCH }

    private val lines by lazy {
        requireNotNull(javaClass.getResourceAsStream("/india-postal.tsv")) { "Postal directory is missing" }
            .bufferedReader().use { it.readLines() }
    }
    val states: List<String> by lazy { lines.first().split('\t').drop(1) }
    private val entries: Map<String, List<Area>> by lazy {
        lines.drop(1).mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size == 3) parts[0] to Area(parts[1], parts[2]) else null
        }.groupBy({ it.first }, { it.second }).mapValues { it.value.distinct() }
    }
    fun canonicalState(value: String): String? {
        val normalized = value.trim().replace("&", "and").replace(Regex("\\s+"), " ")
        val alias = when (normalized.lowercase()) {
            "orissa" -> "Odisha"
            "pondicherry" -> "Puducherry"
            "uttaranchal" -> "Uttarakhand"
            "nct of delhi", "new delhi" -> "Delhi"
            else -> normalized
        }
        return states.firstOrNull { it.equals(alias, ignoreCase = true) }
    }
    fun areas(pin: String): List<Area> = entries[pin.trim()].orEmpty()
    fun match(state: String, pin: String): Match {
        if (state.isBlank() || pin.isBlank()) return Match.INCOMPLETE
        if (!pin.matches(Regex("[1-9][0-9]{5}"))) return Match.INVALID_FORMAT
        val canonical = canonicalState(state) ?: return Match.MISMATCH
        val areas = areas(pin)
        if (areas.isEmpty()) return Match.UNKNOWN_PIN
        return if (areas.any { it.state == canonical }) Match.MATCH else Match.MISMATCH
    }
}
