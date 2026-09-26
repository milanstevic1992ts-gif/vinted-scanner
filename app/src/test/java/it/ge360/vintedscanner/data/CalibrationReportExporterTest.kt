package it.ge360.vintedscanner.data

import it.ge360.vintedscanner.model.CalibrationReview
import it.ge360.vintedscanner.model.CalibrationSession
import it.ge360.vintedscanner.model.CalibrationSummary
import it.ge360.vintedscanner.model.EstimateVerdict
import it.ge360.vintedscanner.model.RankingVerdict
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationReportExporterTest {
    @Test
    fun exportsSnapshotsAndRecommendations() {
        val session = CalibrationSession(
            id = 7,
            startedAt = 1000,
            completedAt = null,
            targetCount = 25,
            reviewedCount = 1
        )
        val review = CalibrationReview(
            id = 1,
            sessionId = 7,
            listingId = "abc",
            title = "Nike Air Max 95",
            createdAt = 2000,
            signatureCorrect = true,
            estimateVerdict = EstimateVerdict.GOOD,
            rankingVerdict = RankingVerdict.GOOD,
            expectedValue = 90.0,
            observedMedian = 92.0,
            marketConfidence = 82,
            marketSimilarity = 88,
            dealIndex = 84,
            score = 86,
            estimatedMargin = 35.0,
            notes = "test reale"
        )
        val summary = CalibrationSummary(
            sessionId = 7,
            reviewedCount = 1,
            targetCount = 25,
            signatureAccuracy = 100,
            priceGoodRate = 100,
            rankingGoodRate = 100,
            meanAbsolutePriceErrorPct = 2,
            recommendations = listOf("Raccogli altri annunci")
        )

        val json = CalibrationReportExporter.toJson(
            session = session,
            summary = summary,
            reviews = listOf(review),
            generatedAt = 0
        )

        assertTrue(json.contains("\"format\": \"vinted-scanner-calibration\""))
        assertTrue(json.contains("\"listingId\":\"abc\""))
        assertTrue(json.contains("\"dealIndex\":84"))
        assertTrue(json.contains("Raccogli altri annunci"))
        assertTrue(json.contains("test reale"))
    }
}
