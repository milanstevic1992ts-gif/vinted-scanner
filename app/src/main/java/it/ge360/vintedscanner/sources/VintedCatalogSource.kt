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
import org.json.JSONArray
import org.json.JSONObject

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
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Accept-Language", "it-IT,it;q=0.9,en;q=0.7")
            connection.setRequestProperty(
                "User-Agent",
                "VintedScanner/0.11.0 (Android; read-only catalog experiment)"
            )

            val code = connection.responseCode
            if (code !in 200..299) {
                throw CatalogSourceException(
                    when (code) {
                        403 -> "Catalogo Vinted HTTP 403: accesso rifiutato. Nessun bypass viene tentato."
                        429 -> "Catalogo Vinted HTTP 429: limite richieste raggiunto."
                        else -> "Catalogo Vinted HTTP $code"
                    }
                )
            }

            val body = BufferedReader(InputStreamReader(connection.inputStream)).use {
                it.readText()
            }
            return parseCatalogResponse(body, search.id)
        } finally {
            connection.disconnect()
        }
    }

    internal fun buildSearchUrl(search: SavedSearch): String {
        val query = listOfNotNull(
            search.brand?.trim()?.takeIf(String::isNotBlank),
            search.query.trim().takeIf(String::isNotBlank)
        ).distinct().joinToString(" ")

        val params = mutableListOf(
            "page" to "1",
            "per_page" to "24",
            "order" to "newest_first",
            "currency" to "EUR",
            "search_text" to query
        )
        search.maxPrice?.let {
            params += "price_to" to formatNumber(it)
        }

        val encoded = params.joinToString("&") { (key, value) ->
            encode(key) + "=" + encode(value)
        }
        return baseUrl.trimEnd('/') + "/api/v2/catalog/items?" + encoded
    }

    internal fun parseCatalogResponse(
        body: String,
        searchId: Long
    ): List<Listing> {
        val root = JSONObject(body)
        val items = root.optJSONArray("items") ?: JSONArray()
        val now = System.currentTimeMillis()
        val out = ArrayList<Listing>(items.length())

        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val id = item.opt("id")?.toString()?.takeIf(String::isNotBlank) ?: continue
            val title = item.optString("title").trim().takeIf(String::isNotBlank)
                ?: "Annuncio Vinted $id"
            val price = extractMoney(item.opt("price"))
                ?: extractMoney(item.opt("total_item_price"))
                ?: continue

            val url = item.optString("url")
                .trim()
                .takeIf(String::isNotBlank)
                ?: baseUrl.trimEnd('/') + "/items/" + id

            val brand = item.optString("brand_title").trim().takeIf(String::isNotBlank)
            val size = item.optString("size_title").trim().takeIf(String::isNotBlank)
            val status = extractTitle(item.opt("status"))
            val condition = listOfNotNull(brand, size, status)
                .distinct()
                .joinToString(" · ")
                .takeIf(String::isNotBlank)

            out += Listing(
                id = ListingIdentity.canonicalId(url),
                searchId = searchId,
                title = title,
                price = price,
                shipping = 0.0,
                url = url,
                imageUrl = extractPhotoUrl(item),
                condition = condition,
                publishedAt = extractTimestamp(item),
                firstSeenAt = now,
                lastSeenAt = now
            )
        }
        return out
    }

    private fun extractMoney(value: Any?): Double? =
        when (value) {
            null, JSONObject.NULL -> null
            is Number -> value.toDouble()
            is String -> value.replace(",", ".").toDoubleOrNull()
            is JSONObject -> {
                val candidates = listOf(
                    value.opt("amount"),
                    value.opt("amount_numeric"),
                    value.opt("value")
                )
                candidates.firstNotNullOfOrNull(::extractMoney)
            }
            else -> null
        }

    private fun extractTitle(value: Any?): String? =
        when (value) {
            null, JSONObject.NULL -> null
            is String -> value.trim().takeIf(String::isNotBlank)
            is JSONObject -> value.optString("title").trim().takeIf(String::isNotBlank)
            else -> value.toString().trim().takeIf(String::isNotBlank)
        }

    private fun extractPhotoUrl(item: JSONObject): String? {
        val direct = item.optJSONObject("photo")
            ?.optString("url")
            ?.trim()
            ?.takeIf(String::isNotBlank)
        if (direct != null) return direct

        val photos = item.optJSONArray("photos") ?: return null
        if (photos.length() == 0) return null
        return photos.optJSONObject(0)
            ?.optString("url")
            ?.trim()
            ?.takeIf(String::isNotBlank)
    }

    private fun extractTimestamp(item: JSONObject): Long? {
        val raw = item.opt("created_at_ts")
            ?: item.opt("created_at_timestamp")
            ?: return null
        val value = when (raw) {
            is Number -> raw.toLong()
            is String -> raw.toLongOrNull()
            else -> null
        } ?: return null
        return if (value < 10_000_000_000L) value * 1000L else value
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun formatNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
}

class CatalogSourceException(message: String) : Exception(message)
