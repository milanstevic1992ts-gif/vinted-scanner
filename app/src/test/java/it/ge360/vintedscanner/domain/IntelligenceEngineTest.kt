package it.ge360.vintedscanner.domain

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
    fun preferenceEngineLearnsPositiveAndNegativeSignals() {
        var weights = emptyMap<String, Int>()
        weights = PreferenceEngine.updatedWeights(weights, "Nike Air Max 95", 1)
        weights = PreferenceEngine.updatedWeights(weights, "Adidas Samba", -1)

        val profile = PreferenceProfile(weights)

        assertTrue(PreferenceEngine.boost("Nike Air Max 95", profile) > 0)
        assertTrue(PreferenceEngine.boost("Adidas Samba", profile) < 0)
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
