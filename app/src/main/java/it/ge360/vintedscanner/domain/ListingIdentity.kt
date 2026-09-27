package it.ge360.vintedscanner.domain

import java.net.URI
import java.security.MessageDigest

object ListingIdentity {
    private val webItemRegex = Regex("""/items/(\d+)""", RegexOption.IGNORE_CASE)
    private val deepLinkRegex = Regex("""^vinted://item/(\d+)""", RegexOption.IGNORE_CASE)

    fun canonicalId(url: String): String {
        val normalized = normalizeUrl(url)
        val numericId = webItemRegex.find(normalized)?.groupValues?.getOrNull(1)
            ?: deepLinkRegex.find(normalized)?.groupValues?.getOrNull(1)

        return numericId?.let { "vinted-item:$it" } ?: "url:" + sha256(normalized)
    }

    fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.startsWith("vinted://", ignoreCase = true)) {
            return trimmed.substringBefore('#').trimEnd('/')
        }

        return runCatching {
            val uri = URI(trimmed)
            URI(
                uri.scheme?.lowercase(),
                uri.authority?.lowercase(),
                uri.path?.trimEnd('/'),
                uri.query,
                null
            ).toString()
        }.getOrDefault(trimmed.substringBefore('#').trimEnd('/'))
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
