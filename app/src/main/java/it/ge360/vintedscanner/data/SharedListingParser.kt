package it.ge360.vintedscanner.data

import it.ge360.vintedscanner.model.SharedListingDraft
import java.security.MessageDigest

object SharedListingParser {
    private val urlRegex = Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE)
    private val priceRegexes = listOf(
        Regex("""(?:€|EUR)\s*([0-9]+(?:[.,][0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""([0-9]+(?:[.,][0-9]{1,2})?)\s*(?:€|EUR)""", RegexOption.IGNORE_CASE)
    )

    fun parse(text: String): SharedListingDraft {
        val clean = text.trim()
        val url = urlRegex.find(clean)?.value?.trimEnd('.', ',', ';', ')', ']', '}')
        val price = priceRegexes.firstNotNullOfOrNull { regex ->
            regex.find(clean)?.groupValues?.getOrNull(1)?.replace(",", ".")?.toDoubleOrNull()
        }
        val title = clean.lineSequence()
            .map(String::trim)
            .firstOrNull { line ->
                line.isNotBlank() &&
                    !line.startsWith("http", ignoreCase = true) &&
                    !line.equals("vinted", ignoreCase = true)
            }
            ?.take(120)
            ?: "Annuncio condiviso"

        return SharedListingDraft(
            rawText = clean,
            url = url,
            titleGuess = title,
            priceGuess = price
        )
    }

    fun stableId(url: String): String {
        val normalized = url.trim().substringBefore('#').trimEnd('/')
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
