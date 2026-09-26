package it.ge360.vintedscanner.data

import it.ge360.vintedscanner.model.FeedbackEvent
import it.ge360.vintedscanner.model.Listing
import it.ge360.vintedscanner.model.PreferenceProfile
import it.ge360.vintedscanner.model.SavedSearch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object BackupExporter {
    fun toJson(
        searches: List<SavedSearch>,
        listings: List<Listing>,
        profile: PreferenceProfile,
        feedbackEvents: List<FeedbackEvent> = emptyList(),
        generatedAt: Long = System.currentTimeMillis()
    ): String = buildString {
        append("{\n")
        append("  \"format\": \"vinted-scanner-backup\",\n")
        append("  \"version\": 2,\n")
        append("  \"generatedAt\": \"").append(isoDate(generatedAt)).append("\",\n")

        append("  \"searches\": [\n")
        searches.forEachIndexed { index, item ->
            append("    {")
            append("\"id\":").append(item.id).append(',')
            append("\"query\":").append(json(item.query)).append(',')
            append("\"maxPrice\":").append(numberOrNull(item.maxPrice)).append(',')
            append("\"size\":").append(jsonOrNull(item.size)).append(',')
            append("\"brand\":").append(jsonOrNull(item.brand)).append(',')
            append("\"condition\":").append(jsonOrNull(item.condition)).append(',')
            append("\"minMargin\":").append(item.minMargin).append(',')
            append("\"active\":").append(item.active).append(',')
            append("\"createdAt\":").append(item.createdAt)
            append("}")
            if (index != searches.lastIndex) append(',')
            append('\n')
        }
        append("  ],\n")

        append("  \"listings\": [\n")
        listings.forEachIndexed { index, item ->
            append("    {")
            append("\"id\":").append(json(item.id)).append(',')
            append("\"searchId\":").append(item.searchId).append(',')
            append("\"title\":").append(json(item.title)).append(',')
            append("\"price\":").append(item.price).append(',')
            append("\"extraCosts\":").append(item.shipping).append(',')
            append("\"marketMedian\":").append(numberOrNull(item.marketMedian)).append(',')
            append("\"marketSampleCount\":").append(item.marketSampleCount).append(',')
            append("\"marketConfidence\":").append(item.marketConfidence).append(',')
            append("\"marketSimilarity\":").append(item.marketSimilarity).append(',')
            append("\"marketOutliersRemoved\":").append(item.marketOutliersRemoved).append(',')
            append("\"comparableLabel\":").append(jsonOrNull(item.comparableLabel)).append(',')
            append("\"url\":").append(json(item.url)).append(',')
            append("\"condition\":").append(jsonOrNull(item.condition)).append(',')
            append("\"firstSeenAt\":").append(item.firstSeenAt).append(',')
            append("\"lastSeenAt\":").append(item.lastSeenAt).append(',')
            append("\"score\":").append(item.score).append(',')
            append("\"estimatedMargin\":").append(numberOrNull(item.estimatedMargin)).append(',')
            append("\"preferenceBoost\":").append(item.preferenceBoost).append(',')
            append("\"favorite\":").append(item.favorite).append(',')
            append("\"feedback\":").append(item.feedback).append(',')
            append("\"feedbackReason\":").append(json(item.feedbackReason.name)).append(',')
            append("\"riskFlags\":[")
            item.riskFlags.forEachIndexed { riskIndex, flag ->
                append(json(flag))
                if (riskIndex != item.riskFlags.lastIndex) append(',')
            }
            append("]}")
            if (index != listings.lastIndex) append(',')
            append('\n')
        }
        append("  ],\n")

        append("  \"feedbackEvents\": [\n")
        feedbackEvents.forEachIndexed { index, event ->
            append("    {")
            append("\"id\":").append(event.id).append(',')
            append("\"listingId\":").append(json(event.listingId)).append(',')
            append("\"reason\":").append(json(event.reason.name)).append(',')
            append("\"createdAt\":").append(event.createdAt).append(',')
            append("\"title\":").append(json(event.title)).append(',')
            append("\"price\":").append(event.price).append(',')
            append("\"marketMedian\":").append(numberOrNull(event.marketMedian))
            append("}")
            if (index != feedbackEvents.lastIndex) append(',')
            append('\n')
        }
        append("  ],\n")

        append("  \"preferenceWeights\": {")
        val weights = profile.tokenWeights.entries.sortedBy { it.key }
        weights.forEachIndexed { index, entry ->
            append(json(entry.key)).append(':').append(entry.value)
            if (index != weights.lastIndex) append(',')
        }
        append("},\n")
        append("  \"learningProfile\": {")
        append("\"purchasedCount\":").append(profile.purchasedCount).append(',')
        append("\"discardedCount\":").append(profile.discardedCount).append(',')
        append("\"tooExpensiveCount\":").append(profile.tooExpensiveCount).append(',')
        append("\"badConditionCount\":").append(profile.badConditionCount).append(',')
        append("\"wrongModelCount\":").append(profile.wrongModelCount).append(',')
        append("\"averageTooExpensiveRatio\":").append(numberOrNull(profile.averageTooExpensiveRatio))
        append("}\n")
        append("}\n")
    }

    private fun numberOrNull(value: Double?): String = value?.toString() ?: "null"
    private fun jsonOrNull(value: String?): String = value?.let(::json) ?: "null"

    private fun json(value: String): String =
        "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t") + "\""

    private fun isoDate(timestamp: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(Date(timestamp))
    }
}
