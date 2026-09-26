package it.ge360.vintedscanner.domain

import it.ge360.vintedscanner.model.FeedbackReason
import it.ge360.vintedscanner.model.Listing
import it.ge360.vintedscanner.model.PreferenceProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DealRankerTest {
    private val now = 2_000_000_000_000L

    @Test
    fun strongFreshDealRanksAheadOfWeakOldDeal() {
        val strong = Listing(
            id = "strong",
            searchId = 0,
            title = "Nike Air Max 95",
            price = 40.0,
            marketMedian = 100.0,
            marketSampleCount = 8,
            marketConfidence = 88,
            marketSimilarity = 86,
            firstSeenAt = now - 20 * 60 * 1000L,
            score = 90,
            estimatedMargin = 55.0,
            url = "https://example.invalid/strong"
        )
        val weak = Listing(
            id = "weak",
            searchId = 0,
            title = "Nike Air Max 95",
            price = 80.0,
            marketMedian = 100.0,
            marketSampleCount = 2,
            marketConfidence = 42,
            marketSimilarity = 48,
            firstSeenAt = now - 9 * 24 * 60 * 60 * 1000L,
            score = 62,
            estimatedMargin = 15.0,
            url = "https://example.invalid/weak"
        )

        val ranked = DealRanker.rank(listOf(weak, strong), now)

        assertEquals("strong", ranked.first().listing.id)
        assertTrue(ranked.first().dealIndex > ranked.last().dealIndex)
    }

    @Test
    fun negativeFeedbackIsExcludedFromDealCenter() {
        val listing = Listing(
            id = "ignored",
            searchId = 0,
            title = "Adidas Samba",
            price = 30.0,
            marketMedian = 80.0,
            marketConfidence = 90,
            marketSimilarity = 90,
            score = 92,
            estimatedMargin = 50.0,
            feedback = -1,
            url = "https://example.invalid/ignored"
        )

        assertTrue(DealRanker.rank(listOf(listing), now).isEmpty())
    }

    @Test
    fun purchasedItemsAreExcludedFromActiveDealCenter() {
        val purchased = Listing(
            id = "purchased",
            searchId = 0,
            title = "Nike Air Max 95",
            price = 40.0,
            marketMedian = 100.0,
            marketConfidence = 90,
            marketSimilarity = 90,
            score = 95,
            estimatedMargin = 55.0,
            feedback = 1,
            feedbackReason = FeedbackReason.PURCHASED,
            url = "https://example.invalid/purchased"
        )

        assertTrue(DealRanker.rank(listOf(purchased), now).isEmpty())
    }

    @Test
    fun riskFlagsReduceDealIndex() {
        val base = Listing(
            id = "base",
            searchId = 0,
            title = "Nike Air Max 95",
            price = 50.0,
            marketMedian = 100.0,
            marketSampleCount = 6,
            marketConfidence = 80,
            marketSimilarity = 80,
            firstSeenAt = now,
            score = 82,
            estimatedMargin = 45.0,
            url = "https://example.invalid/base"
        )

        val clean = DealRanker.evaluate(base, now)
        val risky = DealRanker.evaluate(
            base.copy(id = "risky", riskFlags = listOf("macchia", "strappo")),
            now
        )

        assertTrue(clean.dealIndex > risky.dealIndex)
    }

    @Test
    fun learnedTooExpensiveThresholdPenalizesSimilarPriceRatio() {
        val listing = Listing(
            id = "price-sensitive",
            searchId = 0,
            title = "Nike Air Max 95",
            price = 75.0,
            marketMedian = 100.0,
            marketSampleCount = 8,
            marketConfidence = 85,
            marketSimilarity = 85,
            firstSeenAt = now,
            score = 85,
            estimatedMargin = 20.0,
            url = "https://example.invalid/price-sensitive"
        )

        val neutral = DealRanker.evaluate(listing, now)
        val learned = DealRanker.evaluate(
            listing,
            now,
            PreferenceProfile(
                tooExpensiveCount = 3,
                averageTooExpensiveRatio = 0.70
            )
        )

        assertTrue(learned.dealIndex < neutral.dealIndex)
        assertTrue(learned.reasons.any { it.contains("soglia") })
    }

    @Test
    fun badConditionLearningMakesRiskPenaltyStronger() {
        val listing = Listing(
            id = "condition-sensitive",
            searchId = 0,
            title = "Nike Air Max 95",
            price = 45.0,
            marketMedian = 100.0,
            marketSampleCount = 8,
            marketConfidence = 85,
            marketSimilarity = 85,
            firstSeenAt = now,
            score = 85,
            estimatedMargin = 50.0,
            riskFlags = listOf("macchia"),
            url = "https://example.invalid/condition-sensitive"
        )

        val neutral = DealRanker.evaluate(listing, now)
        val learned = DealRanker.evaluate(
            listing,
            now,
            PreferenceProfile(badConditionCount = 3)
        )

        assertTrue(learned.dealIndex < neutral.dealIndex)
        assertTrue(learned.reasons.any { it.contains("preferenze") })
    }

    @Test
    fun reasonListExplainsFreshHighMarginDeal() {
        val candidate = DealRanker.evaluate(
            Listing(
                id = "explain",
                searchId = 0,
                title = "Nike Air Max 95",
                price = 40.0,
                marketMedian = 100.0,
                marketSampleCount = 10,
                marketConfidence = 90,
                marketSimilarity = 88,
                firstSeenAt = now - 10 * 60 * 1000L,
                score = 92,
                estimatedMargin = 55.0,
                url = "https://example.invalid/explain"
            ),
            now
        )

        assertTrue(candidate.reasons.any { it.contains("Margine") })
        assertTrue(candidate.reasons.any { it.contains("appena") })
    }
}
