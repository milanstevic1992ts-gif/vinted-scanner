package it.ge360.vintedscanner.domain

import it.ge360.vintedscanner.model.FeedbackReason
import it.ge360.vintedscanner.model.PreferenceProfile
import kotlin.math.roundToInt

object PreferenceEngine {
    fun boost(title: String, profile: PreferenceProfile): Int {
        val tokens = TextSignals.tokens(title)
        if (tokens.isEmpty()) return 0

        val raw = profile.weightFor(tokens)
        val normalized = (raw / tokens.size.toDouble()).roundToInt()
        return normalized.coerceIn(-12, 12)
    }

    fun applyReason(
        existing: Map<String, Int>,
        title: String,
        reason: FeedbackReason,
        direction: Int = 1
    ): Map<String, Int> {
        val delta = deltaFor(reason) * direction
        if (delta == 0) return existing

        return existing.toMutableMap().apply {
            TextSignals.tokens(title).forEach { token ->
                this[token] = ((this[token] ?: 0) + delta).coerceIn(-24, 24)
            }
        }
    }

    fun updatedWeights(
        existing: Map<String, Int>,
        title: String,
        feedback: Int
    ): Map<String, Int> {
        if (feedback == 0) return existing
        val reason = if (feedback > 0) FeedbackReason.FAVORITE else FeedbackReason.DISCARDED
        return applyReason(existing, title, reason)
    }

    fun deltaFor(reason: FeedbackReason): Int =
        when (reason) {
            FeedbackReason.NONE -> 0
            FeedbackReason.FAVORITE -> 2
            FeedbackReason.PURCHASED -> 5
            FeedbackReason.DISCARDED -> -2
            FeedbackReason.WRONG_MODEL -> -4
            FeedbackReason.TOO_EXPENSIVE,
            FeedbackReason.BAD_CONDITION -> 0
        }
}
