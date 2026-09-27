package it.ge360.vintedscanner.sources

import it.ge360.vintedscanner.domain.ListingIdentity
import it.ge360.vintedscanner.model.Listing
import it.ge360.vintedscanner.model.SavedSearch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class VintedCatalogSource(
    private val baseUrl: String = SourceCatalog.VINTED_ITALY_BASE_URL
) : ListingSource {
    override val descriptor = SourceCatalog.vintedCatalog
    override val minimumScanIntervalMs: Long = 5L * 60L * 1000L

    override fun isConfigured(): Boolean = true

    override suspend fun scan(search: SavedSearch): List<Listing> {
        val endpoint = buildSearchUrl(search)
        val connection = URI(endpoint).toURL().openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 12_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
            )
            connection.setRequestProperty("Accept-Language", "it-IT,it;q=0.9,en;q=0.7")
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36"
            )

            val code = connection.responseCode
            if (code !in 200..299) {
                throw CatalogSourceException(
                    when (code) {
                        403 -> "Catalogo web Vinted HTTP 403: accesso rifiutato. Nessun bypass viene tentato."
                        429 -> "Catalogo web Vinted HTTP 429: limite richieste raggiunto."
                        else -> "Catalogo web Vinted HTTP $code"
                    }
                )
            }

            val body = BufferedReader(InputStreamReader(connection.inputStream)).use {
                it.readText()
            }
            val listings = parseCatalogHtml(body, search.id)
            if (listings.isEmpty()) {
                throw CatalogSourceException(
                    "Catalogo web raggiunto ma nessuna card articolo riconosciuta nell'HTML."
                )
            }
            return listings
        } finally {
            connection.disconnect()
        }
    }

    internal fun buildSearchUrl(search: SavedSearch): String {
        val queryText = search.query.trim()
        val brandText = search.brand?.trim()?.takeIf(String::isNotBlank)
        val query = if (
            brandText != null &&
            normalizedDistance(brandText, queryText) <= 1
        ) {
            queryText
        } else {
            listOfNotNull(brandText, queryText.takeIf(String::isNotBlank))
                .distinct()
                .joinToString(" ")
        }

        val params = mutableListOf(
            "search_text" to query,
            "order" to "newest_first"
        )
        search.maxPrice?.let {
            params += "price_to" to formatNumber(it)
        }

        val encoded = params.joinToString("&") { (key, value) ->
            encode(key) + "=" + encode(value)
        }
        return baseUrl.trimEnd('/') + "/catalog?" + encoded
    }

    internal fun parseCatalogHtml(
        body: String,
        searchId: Long
    ): List<Listing> {
        val document = Jsoup.parse(body, baseUrl)
        val now = System.currentTimeMillis()
        val seen = linkedSetOf<String>()
        val out = mutableListOf<Listing>()

        for (link in document.select("a[href*=/items/]")) {
            if (out.size >= 24) break

            val url = link.absUrl("href").ifBlank {
                normalizeItemUrl(link.attr("href"))
            }
            if (url.isBlank()) continue

            val id = ListingIdentity.canonicalId(url)
            if (!seen.add(id)) continue

            val image = findNearbyImage(link)
            val alt = image?.attr("alt")?.trim().orEmpty()
            val contextText = findCardText(link)
            val sourceText = listOf(alt, contextText)
                .filter(String::isNotBlank)
                .joinToString(" · ")

            val price = extractFirstPrice(sourceText) ?: continue
            val title = extractTitle(alt, contextText)
                ?: "Annuncio Vinted"

            val brand = extractNamedField(alt, "brand")
            val size = extractNamedField(alt, "taglia")
            val status = extractNamedField(alt, "condizioni")
            val condition = listOfNotNull(brand, size, status)
                .distinct()
                .joinToString(" · ")
                .takeIf(String::isNotBlank)

            out += Listing(
                id = id,
                searchId = searchId,
                title = title,
                price = price,
                shipping = 0.0,
                url = url,
                imageUrl = extractImageUrl(image),
                condition = condition,
                publishedAt = null,
                firstSeenAt = now,
                lastSeenAt = now
            )
        }
        return out
    }

    private fun findNearbyImage(link: Element): Element? {
        link.selectFirst("img[alt]")?.let { return it }

        var current: Element? = link
        repeat(5) {
            current = current?.parent()
            val image = current?.selectFirst("img[alt]")
            if (image != null && image.attr("alt").contains("€")) {
                return image
            }
        }
        return null
    }

    private fun findCardText(link: Element): String {
        var current: Element? = link
        repeat(5) {
            val text = current?.text()?.trim().orEmpty()
            if (text.contains("€") && text.length <= 600) {
                return text
            }
            current = current?.parent()
        }
        return link.text().trim()
    }

    private fun extractTitle(alt: String, context: String): String? {
        if (alt.isNotBlank()) {
            val markers = listOf(", brand:", ", condizioni:", ", taglia:")
            val cut = markers
                .map { marker -> alt.indexOf(marker, ignoreCase = true) }
                .filter { it > 0 }
                .minOrNull()
            val title = if (cut != null) alt.substring(0, cut) else alt.substringBefore("€")
            title.trim(' ', ',', '·').takeIf(String::isNotBlank)?.let { return it }
        }

        return context
            .substringBefore("€")
            .trim(' ', ',', '·')
            .takeIf { it.length in 2..180 }
    }

    private fun extractNamedField(text: String, field: String): String? {
        if (text.isBlank()) return null
        val regex = Regex(
            """(?:^|,\s*)${Regex.escape(field)}:\s*([^,]+)""",
            RegexOption.IGNORE_CASE
        )
        return regex.find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf(String::isNotBlank)
    }

    private fun extractFirstPrice(text: String): Double? {
        val match = Regex("""(\d{1,6}(?:[.,]\d{1,2})?)\s*€""")
            .find(text)
            ?: return null
        val raw = match.groupValues[1]
        return if (raw.contains(",")) {
            raw.replace(".", "").replace(",", ".").toDoubleOrNull()
        } else {
            raw.toDoubleOrNull()
        }
    }

    private fun extractImageUrl(image: Element?): String? {
        if (image == null) return null
        return image.absUrl("src").takeIf(String::isNotBlank)
            ?: image.absUrl("data-src").takeIf(String::isNotBlank)
            ?: image.attr("srcset")
                .substringBefore(' ')
                .trim()
                .takeIf(String::isNotBlank)
    }

    private fun normalizeItemUrl(href: String): String {
        if (href.isBlank()) return ""
        return when {
            href.startsWith("http://") || href.startsWith("https://") -> href
            href.startsWith("/") -> baseUrl.trimEnd('/') + href
            else -> baseUrl.trimEnd('/') + "/" + href
        }
    }

    private fun normalizedDistance(a: String, b: String): Int {
        val left = a.lowercase().filter(Char::isLetterOrDigit)
        val right = b.lowercase().filter(Char::isLetterOrDigit)
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length

        val previous = IntArray(right.length + 1) { it }
        val current = IntArray(right.length + 1)
        for (i in left.indices) {
            current[0] = i + 1
            for (j in right.indices) {
                val cost = if (left[i] == right[j]) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + cost
                )
            }
            for (j in previous.indices) previous[j] = current[j]
        }
        return previous[right.length]
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun formatNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
}

class CatalogSourceException(message: String) : Exception(message)
