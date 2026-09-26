package it.ge360.vintedscanner.domain

import it.ge360.vintedscanner.model.Listing
import org.junit.Assert.assertTrue
import org.junit.Test

class OpportunityScorerTest {
    @Test
    fun cheapListingScoresHigherThanMedian() {
        val result = OpportunityScorer.score(
            Listing(
                id = "1",
                searchId = 1,
                title = "Nike Air Max 95",
                price = 40.0,
                shipping = 5.0,
                marketMedian = 90.0,
                url = "https://example.invalid"
            )
        )
        assertTrue(result.score > 50)
        assertTrue((result.estimatedMargin ?: 0.0) > 0)
    }

    @Test
    fun riskyWordsReduceScore() {
        val clean = OpportunityScorer.score(
            Listing("1", 1, "Sneakers nuove", 40.0, marketMedian = 80.0, url = "https://example.invalid")
        )
        val risky = OpportunityScorer.score(
            Listing("2", 1, "Sneakers con strappo", 40.0, marketMedian = 80.0, url = "https://example.invalid")
        )
        assertTrue(risky.score < clean.score)
        assertTrue(risky.riskFlags.isNotEmpty())
    }
}
