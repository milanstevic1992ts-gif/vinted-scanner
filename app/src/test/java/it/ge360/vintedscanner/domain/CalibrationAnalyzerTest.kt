package it.ge360.vintedscanner.domain

import it.ge360.vintedscanner.model.CalibrationReview
import it.ge360.vintedscanner.model.EstimateVerdict
import it.ge360.vintedscanner.model.RankingVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationAnalyzerTest {
    @Test
    fun summarizesRealWorldReviewQuality() {
        val reviews = (1L..10L).map { id ->
            CalibrationReview(
                id = id,
                sessionId = 1,
                listingId = "item-$id",
                title = "Item $id",
                createdAt = id,
                signatureCorrect = id <= 8,
                estimateVerdict = if (id <= 7) EstimateVerdict.GOOD else EstimateVerdict.TOO_HIGH,
                rankingVerdict = if (id <= 6) RankingVerdict.GOOD else RankingVerdict.TOO_HIGH,
                expectedValue = 100.0,
                observedMedian = if (id <= 7) 105.0 else 135.0,
                marketConfidence = 70,
                marketSimilarity = 70,
                dealIndex = 75,
                score = 80,
                estimatedMargin = 30.0,
                notes = null
            )
        }

        val summary = CalibrationAnalyzer.summarize(
            sessionId = 1,
            reviews = reviews,
            targetCount = 25
        )

        assertEquals(10, summary.reviewedCount)
        assertEquals(80, summary.signatureAccuracy)
        assertEquals(70, summary.priceGoodRate)
        assertEquals(60, summary.rankingGoodRate)
        assertTrue(summary.recommendations.any { it.contains("Centro Affari") })
    }

    @Test
    fun warnsAgainstTuningTinySamples() {
        val review = CalibrationReview(
            id = 1,
            sessionId = 1,
            listingId = "one",
            title = "One",
            createdAt = 1,
            signatureCorrect = false,
            estimateVerdict = EstimateVerdict.TOO_LOW,
            rankingVerdict = RankingVerdict.TOO_LOW,
            expectedValue = null,
            observedMedian = null,
            marketConfidence = 0,
            marketSimilarity = 0,
            dealIndex = 10,
            score = 20,
            estimatedMargin = null,
            notes = null
        )

        val summary = CalibrationAnalyzer.summarize(1, listOf(review), 25)

        assertTrue(summary.recommendations.single().contains("almeno 10"))
    }

    @Test
    fun calculatesMeanAbsolutePriceErrorWhenGroundTruthExists() {
        val reviews = listOf(
            calibrationReview(1, expected = 100.0, observed = 110.0),
            calibrationReview(2, expected = 100.0, observed = 80.0)
        )

        val summary = CalibrationAnalyzer.summarize(1, reviews, 25)

        assertEquals(15, summary.meanAbsolutePriceErrorPct)
    }

    private fun calibrationReview(
        id: Long,
        expected: Double,
        observed: Double
    ) = CalibrationReview(
        id = id,
        sessionId = 1,
        listingId = "item-$id",
        title = "Item $id",
        createdAt = id,
        signatureCorrect = true,
        estimateVerdict = EstimateVerdict.GOOD,
        rankingVerdict = RankingVerdict.GOOD,
        expectedValue = expected,
        observedMedian = observed,
        marketConfidence = 80,
        marketSimilarity = 80,
        dealIndex = 80,
        score = 80,
        estimatedMargin = 20.0,
        notes = null
    )
}
