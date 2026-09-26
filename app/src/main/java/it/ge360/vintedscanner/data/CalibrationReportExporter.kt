package it.ge360.vintedscanner.data

import it.ge360.vintedscanner.model.CalibrationReview
import it.ge360.vintedscanner.model.CalibrationSession
import it.ge360.vintedscanner.model.CalibrationSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object CalibrationReportExporter {
    fun toJson(
        session: CalibrationSession,
        summary: CalibrationSummary,
        reviews: List<CalibrationReview>,
        generatedAt: Long = System.currentTimeMillis()
    ): String = buildString {
        append("{\n")
        append("  \"format\": \"vinted-scanner-calibration\",\n")
        append("  \"version\": 1,\n")
        append("  \"generatedAt\": \"").append(isoDate(generatedAt)).append("\",\n")
        append("  \"session\": {")
        append("\"id\":").append(session.id).append(',')
        append("\"startedAt\":").append(session.startedAt).append(',')
        append("\"completedAt\":").append(longOrNull(session.completedAt)).append(',')
        append("\"targetCount\":").append(session.targetCount).append(',')
        append("\"reviewedCount\":").append(summary.reviewedCount)
        append("},\n")

        append("  \"summary\": {")
        append("\"signatureAccuracy\":").append(intOrNull(summary.signatureAccuracy)).append(',')
        append("\"priceGoodRate\":").append(intOrNull(summary.priceGoodRate)).append(',')
        append("\"rankingGoodRate\":").append(intOrNull(summary.rankingGoodRate)).append(',')
        append("\"meanAbsolutePriceErrorPct\":").append(intOrNull(summary.meanAbsolutePriceErrorPct)).append(',')
        append("\"recommendations\":[")
        summary.recommendations.forEachIndexed { index, item ->
            append(json(item))
            if (index != summary.recommendations.lastIndex) append(',')
        }
        append("]},\n")

        append("  \"reviews\": [\n")
        reviews.forEachIndexed { index, review ->
            append("    {")
            append("\"id\":").append(review.id).append(',')
            append("\"listingId\":").append(json(review.listingId)).append(',')
            append("\"title\":").append(json(review.title)).append(',')
            append("\"createdAt\":").append(review.createdAt).append(',')
            append("\"signatureCorrect\":").append(review.signatureCorrect).append(',')
            append("\"estimateVerdict\":").append(json(review.estimateVerdict.name)).append(',')
            append("\"rankingVerdict\":").append(json(review.rankingVerdict.name)).append(',')
            append("\"expectedValue\":").append(numberOrNull(review.expectedValue)).append(',')
            append("\"observedMedian\":").append(numberOrNull(review.observedMedian)).append(',')
            append("\"marketConfidence\":").append(review.marketConfidence).append(',')
            append("\"marketSimilarity\":").append(review.marketSimilarity).append(',')
            append("\"dealIndex\":").append(review.dealIndex).append(',')
            append("\"score\":").append(review.score).append(',')
            append("\"estimatedMargin\":").append(numberOrNull(review.estimatedMargin)).append(',')
            append("\"notes\":").append(jsonOrNull(review.notes))
            append("}")
            if (index != reviews.lastIndex) append(',')
            append('\n')
        }
        append("  ]\n")
        append("}\n")
    }

    private fun numberOrNull(value: Double?): String = value?.toString() ?: "null"
    private fun longOrNull(value: Long?): String = value?.toString() ?: "null"
    private fun intOrNull(value: Int?): String = value?.toString() ?: "null"
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
