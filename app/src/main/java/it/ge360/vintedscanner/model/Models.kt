package it.ge360.vintedscanner.model

data class SavedSearch(
    val id: Long = 0,
    val query: String,
    val maxPrice: Double?,
    val size: String?,
    val brand: String? = null,
    val condition: String? = null,
    val minMargin: Double = 20.0,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

data class Listing(
    val id: String,
    val searchId: Long,
    val title: String,
    val price: Double,
    val shipping: Double = 0.0,
    val marketMedian: Double? = null,
    val marketSampleCount: Int = 0,
    val marketConfidence: Int = 0,
    val marketSimilarity: Int = 0,
    val marketOutliersRemoved: Int = 0,
    val comparableLabel: String? = null,
    val url: String,
    val imageUrl: String? = null,
    val condition: String? = null,
    val sellerRating: Double? = null,
    val publishedAt: Long? = null,
    val firstSeenAt: Long = System.currentTimeMillis(),
    val lastSeenAt: Long = System.currentTimeMillis(),
    val score: Int = 0,
    val estimatedMargin: Double? = null,
    val preferenceBoost: Int = 0,
    val riskFlags: List<String> = emptyList(),
    val favorite: Boolean = false,
    val feedback: Int = 0,
    val feedbackReason: FeedbackReason = FeedbackReason.NONE
)

data class OpportunityScore(
    val score: Int,
    val estimatedMargin: Double?,
    val preferenceBoost: Int,
    val riskFlags: List<String>
)

data class PricePoint(
    val listingId: String,
    val price: Double,
    val seenAt: Long
)

data class SharedListingDraft(
    val rawText: String,
    val url: String?,
    val titleGuess: String,
    val priceGuess: Double?
)

data class MarketEstimate(
    val median: Double?,
    val sampleCount: Int,
    val confidence: Int,
    val similarity: Int = 0,
    val outliersRemoved: Int = 0,
    val comparableLabel: String? = null
)

enum class FeedbackReason {
    NONE,
    FAVORITE,
    PURCHASED,
    DISCARDED,
    TOO_EXPENSIVE,
    BAD_CONDITION,
    WRONG_MODEL;

    val polarity: Int
        get() = when (this) {
            NONE -> 0
            FAVORITE, PURCHASED -> 1
            DISCARDED, TOO_EXPENSIVE, BAD_CONDITION, WRONG_MODEL -> -1
        }
}

data class PreferenceProfile(
    val tokenWeights: Map<String, Int> = emptyMap(),
    val purchasedCount: Int = 0,
    val discardedCount: Int = 0,
    val tooExpensiveCount: Int = 0,
    val badConditionCount: Int = 0,
    val wrongModelCount: Int = 0,
    val averageTooExpensiveRatio: Double? = null
) {
    fun weightFor(tokens: Collection<String>): Int =
        tokens.sumOf { tokenWeights[it] ?: 0 }
}

data class FeedbackEvent(
    val id: Long,
    val listingId: String,
    val reason: FeedbackReason,
    val createdAt: Long,
    val title: String,
    val price: Double,
    val marketMedian: Double?
)

enum class EstimateVerdict {
    GOOD,
    TOO_HIGH,
    TOO_LOW,
    NO_DATA
}

enum class RankingVerdict {
    GOOD,
    TOO_HIGH,
    TOO_LOW
}

data class CalibrationSession(
    val id: Long,
    val startedAt: Long,
    val completedAt: Long? = null,
    val targetCount: Int = 25,
    val reviewedCount: Int = 0
) {
    val active: Boolean get() = completedAt == null
}

data class CalibrationReview(
    val id: Long,
    val sessionId: Long,
    val listingId: String,
    val title: String,
    val createdAt: Long,
    val signatureCorrect: Boolean,
    val estimateVerdict: EstimateVerdict,
    val rankingVerdict: RankingVerdict,
    val expectedValue: Double?,
    val observedMedian: Double?,
    val marketConfidence: Int,
    val marketSimilarity: Int,
    val dealIndex: Int,
    val score: Int,
    val estimatedMargin: Double?,
    val notes: String?
)

data class CalibrationSummary(
    val sessionId: Long?,
    val reviewedCount: Int = 0,
    val targetCount: Int = 25,
    val signatureAccuracy: Int? = null,
    val priceGoodRate: Int? = null,
    val rankingGoodRate: Int? = null,
    val meanAbsolutePriceErrorPct: Int? = null,
    val recommendations: List<String> = emptyList()
)

enum class SourceKind {
    MANUAL_SHARE,
    VINTED_NOTIFICATIONS,
    AUTHORIZED_REMOTE
}

enum class SourceStatus {
    READY,
    IDLE,
    SCANNING,
    NOT_CONFIGURED,
    ERROR,
    DISABLED
}

data class SourceDescriptor(
    val id: String,
    val name: String,
    val kind: SourceKind,
    val supportsAutomaticScan: Boolean,
    val requiresConfiguration: Boolean
)

data class SourceDiagnostic(
    val descriptor: SourceDescriptor,
    val enabled: Boolean,
    val status: SourceStatus,
    val lastEventAt: Long? = null,
    val lastScanAt: Long? = null,
    val lastSuccessAt: Long? = null,
    val lastReceivedCount: Int = 0,
    val totalReceived: Long = 0,
    val lastDetail: String? = null,
    val lastError: String? = null
)
