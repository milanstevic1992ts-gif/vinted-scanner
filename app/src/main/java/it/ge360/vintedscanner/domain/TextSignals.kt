package it.ge360.vintedscanner.domain

object TextSignals {
    private val stopWords = setOf(
        "con", "per", "del", "della", "delle", "dei", "da", "di", "in", "un", "una",
        "il", "lo", "la", "le", "gli", "the", "and", "with", "taglia", "size", "uomo",
        "donna", "nuovo", "nuova", "nuovi", "nuove", "ottimo", "ottima", "vinted"
    )

    private val riskyPhrases = linkedMapOf(
        "macchia" to "macchia",
        "macchie" to "macchie",
        "strappo" to "strappo",
        "strappato" to "strappato",
        "rotto" to "rotto",
        "rotta" to "rotta",
        "difetto" to "difetto",
        "difetti" to "difetti",
        "consumato" to "consumato",
        "consumata" to "consumata",
        "da riparare" to "da riparare",
        "da sistemare" to "da sistemare",
        "replica" to "replica",
        "non originale" to "non originale",
        "fake" to "fake",
        "scolorito" to "scolorito",
        "scolorita" to "scolorita",
        "graffio" to "graffio",
        "graffi" to "graffi",
        "buco" to "buco",
        "buchi" to "buchi"
    )

    fun tokens(text: String): Set<String> =
        text.lowercase()
            .replace(Regex("[^a-z0-9à-ÿ]+"), " ")
            .split(' ')
            .asSequence()
            .map(String::trim)
            .filter { it.length >= 2 }
            .filterNot { it in stopWords }
            .filterNot { it.all(Char::isDigit) }
            .toSet()

    fun riskFlags(vararg texts: String?): List<String> {
        val haystack = texts.filterNotNull().joinToString(" ").lowercase()
        return riskyPhrases.entries
            .filter { (needle, _) -> haystack.contains(needle) }
            .map { it.value }
            .distinct()
    }

    fun similarity(a: String, b: String): Double {
        val aa = tokens(a)
        val bb = tokens(b)
        if (aa.isEmpty() || bb.isEmpty()) return 0.0
        val intersection = aa.intersect(bb).size.toDouble()
        val union = aa.union(bb).size.toDouble()
        return if (union == 0.0) 0.0 else intersection / union
    }
}
