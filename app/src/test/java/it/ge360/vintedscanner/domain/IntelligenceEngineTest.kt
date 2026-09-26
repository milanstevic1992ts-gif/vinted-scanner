package it.ge360.vintedscanner.domain

import it.ge360.vintedscanner.model.FeedbackReason
import it.ge360.vintedscanner.model.Listing
import it.ge360.vintedscanner.model.PreferenceProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntelligenceEngineTest {
    @Test
    fun marketEstimatorUsesComparableMedian() {
        val target = Listing(
            id = "target",
            searchId = 0,
            title = "Nike Air Max 95 uomo",
            price = 50.0,
            url = "https://example.invalid/target"
        )
        val universe = listOf(
            target,
            Listing("a", 0, "Nike Air Max 95 nere", 80.0, url = "https://example.invalid/a"),
            Listing("b", 0, "Nike Air Max 95 bianche", 90.0, url = "https://example.invalid/b"),
            Listing("c", 0, "Nike Air Max 95 taglia 43", 100.0, url = "https://example.invalid/c"),
            Listing("d", 0, "Adidas Samba", 70.0, url = "https://example.invalid/d")
        )

        val estimate = MarketEstimator.estimate(target, universe)

        assertEquals(90.0, estimate.median ?: 0.0, 0.001)
        assertEquals(3, estimate.sampleCount)
        assertTrue(estimate.confidence > 0)
    }

    @Test
    fun structuredSignatureExtractsBrandModelAndSize() {
        val signature = ProductSignatureExtractor.extract(
            title = "Nike Air Max 95 taglia 43",
            condition = "Ottime condizioni"
        )

        assertEquals("Nike", signature.brand)
        assertEquals("43", signature.size)
        assertTrue(signature.modelTokens.contains("95"))
        assertTrue(signature.modelTokens.contains("air"))
        assertEquals("footwear", signature.category)
    }

    @Test
    fun explicitCategoryWinsOverModelPhrase() {
        val signature = ProductSignatureExtractor.extract(
            "Nike Air Max jacket giacca taglia M"
        )

        assertEquals("outerwear", signature.category)
    }

    @Test
    fun comparableEngineRejectsDifferentKnownBrand() {
        val nike = ProductSignatureExtractor.extract("Nike Air Max 95")
        val adidas = ProductSignatureExtractor.extract("Adidas Air Max 95")

        assertEquals(0.0, ProductSignatureExtractor.similarity(nike, adidas), 0.001)
    }

    @Test
    fun comparableEngineRejectsKnownSizeMismatch() {
        val a = ProductSignatureExtractor.extract("Nike Air Max 95 taglia 42")
        val b = ProductSignatureExtractor.extract("Nike Air Max 95 taglia 44")

        assertEquals(0.0, ProductSignatureExtractor.similarity(a, b), 0.001)
    }

    @Test
    fun marketEstimatorRemovesExtremePriceOutlier() {
        val target = Listing(
            id = "target-outlier",
            searchId = 0,
            title = "Nike Air Max 95",
            price = 50.0,
            url = "https://example.invalid/target-outlier"
        )
        val universe = listOf(
            target,
            Listing("o1", 0, "Nike Air Max 95 nere", 80.0, url = "https://example.invalid/o1"),
            Listing("o2", 0, "Nike Air Max 95 bianche", 90.0, url = "https://example.invalid/o2"),
            Listing("o3", 0, "Nike Air Max 95 rosse", 100.0, url = "https://example.invalid/o3"),
            Listing("o4", 0, "Nike Air Max 95 limited", 500.0, url = "https://example.invalid/o4")
        )

        val estimate = MarketEstimator.estimate(target, universe)

        assertEquals(90.0, estimate.median ?: 0.0, 0.001)
        assertEquals(3, estimate.sampleCount)
        assertEquals(1, estimate.outliersRemoved)
        assertTrue(estimate.similarity > 50)
    }

    @Test
    fun preferenceEngineLearnsPositiveAndNegativeSignals() {
        var weights = emptyMap<String, Int>()
        weights = PreferenceEngine.updatedWeights(weights, "Nike Air Max 95", 1)
        weights = PreferenceEngine.updatedWeights(weights, "Adidas Samba", -1)

        val profile = PreferenceProfile(weights)

        assertTrue(PreferenceEngine.boost("Nike Air Max 95", profile) > 0)
        assertTrue(PreferenceEngine.boost("Adidas Samba", profile) < 0)
    }

    @Test
    fun purchasedStrengthensPreferencesMoreThanFavorite() {
        val favoriteWeights = PreferenceEngine.applyReason(
            emptyMap(),
            "Nike Air Max 95",
            FeedbackReason.FAVORITE
        )
        val purchasedWeights = PreferenceEngine.applyReason(
            emptyMap(),
            "Nike Air Max 95",
            FeedbackReason.PURCHASED
        )

        assertTrue((purchasedWeights["nike"] ?: 0) > (favoriteWeights["nike"] ?: 0))
    }

    @Test
    fun tooExpensiveDoesNotPenalizeModelTokens() {
        val weights = PreferenceEngine.applyReason(
            emptyMap(),
            "Nike Air Max 95",
            FeedbackReason.TOO_EXPENSIVE
        )

        assertTrue(weights.isEmpty())
    }

    @Test
    fun wrongModelStronglyPenalizesProductTokens() {
        val weights = PreferenceEngine.applyReason(
            emptyMap(),
            "Adidas Samba",
            FeedbackReason.WRONG_MODEL
        )

        assertTrue((weights["adidas"] ?: 0) <= -4)
        assertTrue((weights["samba"] ?: 0) <= -4)
    }

    @Test
    fun riskSignalsReadConditionTextToo() {
        val listing = Listing(
            id = "risk",
            searchId = 0,
            title = "Nike Air Max 95",
            price = 50.0,
            marketMedian = 90.0,
            marketConfidence = 80,
            url = "https://example.invalid/risk",
            condition = "Piccola macchia e un graffio laterale"
        )

        val result = OpportunityScorer.score(listing)

        assertTrue(result.riskFlags.contains("macchia"))
        assertTrue(result.riskFlags.contains("graffio"))
    }

    @Test
    fun extraCostsReduceNetMargin() {
        val noCosts = OpportunityScorer.score(
            Listing(
                id = "a",
                searchId = 0,
                title = "Nike Air Max 95",
                price = 50.0,
                marketMedian = 100.0,
                marketConfidence = 70,
                url = "https://example.invalid/a"
            )
        )
        val withCosts = OpportunityScorer.score(
            Listing(
                id = "b",
                searchId = 0,
                title = "Nike Air Max 95",
                price = 50.0,
                shipping = 12.0,
                marketMedian = 100.0,
                marketConfidence = 70,
                url = "https://example.invalid/b"
            )
        )

        assertEquals(50.0, noCosts.estimatedMargin ?: 0.0, 0.001)
        assertEquals(38.0, withCosts.estimatedMargin ?: 0.0, 0.001)
        assertTrue(withCosts.score < noCosts.score)
    }

    @Test
    fun directNegativeFeedbackLowersScore() {
        val neutral = OpportunityScorer.score(
            Listing(
                id = "n",
                searchId = 0,
                title = "Nike Air Max 95",
                price = 50.0,
                marketMedian = 100.0,
                marketConfidence = 70,
                url = "https://example.invalid/n"
            )
        )
        val disliked = OpportunityScorer.score(
            Listing(
                id = "d",
                searchId = 0,
                title = "Nike Air Max 95",
                price = 50.0,
                marketMedian = 100.0,
                marketConfidence = 70,
                url = "https://example.invalid/d",
                feedback = -1
            )
        )

        assertTrue(disliked.score < neutral.score)
    }
}
