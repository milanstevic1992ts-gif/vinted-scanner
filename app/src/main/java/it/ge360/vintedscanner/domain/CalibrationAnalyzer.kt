package it.ge360.vintedscanner.domain

import it.ge360.vintedscanner.model.CalibrationReview
import it.ge360.vintedscanner.model.CalibrationSummary
import it.ge360.vintedscanner.model.EstimateVerdict
import it.ge360.vintedscanner.model.RankingVerdict
import kotlin.math.abs
import kotlin.math.roundToInt

object CalibrationAnalyzer {
    fun summarize(
        sessionId: Long?,
        reviews: List<CalibrationReview>,
        targetCount: Int = 25
    ): CalibrationSummary {
        if (reviews.isEmpty()) {
            return CalibrationSummary(
                sessionId = sessionId,
                targetCount = targetCount
            )
        }

        val signatureAccuracy = percent(
            reviews.count { it.signatureCorrect },
            reviews.size
        )

        val priceRated = reviews.filter { it.estimateVerdict != EstimateVerdict.NO_DATA }
        val priceGoodRate = priceRated
            .takeIf { it.isNotEmpty() }
            ?.let { percent(it.count { review -> review.estimateVerdict == EstimateVerdict.GOOD }, it.size) }

        val rankingGoodRate = percent(
            reviews.count { it.rankingVerdict == RankingVerdict.GOOD },
            reviews.size
        )

        val priceErrors = reviews.mapNotNull { review ->
            val expected = review.expectedValue
            val observed = review.observedMedian
            if (expected == null || observed == null || expected <= 0.0) {
                null
            } else {
                abs(observed - expected) / expected * 100.0
            }
        }

        val meanPriceError = priceErrors
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?.roundToInt()

        val recommendations = buildList {
            if (reviews.size < 10) {
                add("Raccogli almeno 10 recensioni prima di cambiare le soglie.")
            } else {
                if (signatureAccuracy < 80) {
                    add("Firma prodotto sotto l'80%: rivedere regole marca/modello/taglia.")
                }

                if (priceGoodRate != null && priceGoodRate < 70) {
                    val high = priceRated.count { it.estimateVerdict == EstimateVerdict.TOO_HIGH }
                    val low = priceRated.count { it.estimateVerdict == EstimateVerdict.TOO_LOW }
                    when {
                        high > low -> add("Le stime prezzo risultano spesso alte: rendere più severi i comparabili.")
                        low > high -> add("Le stime prezzo risultano spesso basse: ampliare i comparabili validi.")
                        else -> add("Le stime prezzo sono instabili: verificare comparabili e outlier.")
                    }
                }

                if (rankingGoodRate < 70) {
                    val tooHigh = reviews.count { it.rankingVerdict == RankingVerdict.TOO_HIGH }
                    val tooLow = reviews.count { it.rankingVerdict == RankingVerdict.TOO_LOW }
                    when {
                        tooHigh > tooLow -> add("Il Centro Affari sovrastima troppo spesso: aumentare penalità rischio/dati deboli.")
                        tooLow > tooHigh -> add("Il Centro Affari sottostima opportunità: ridurre penalità o aumentare peso margine.")
                        else -> add("Il ranking è poco coerente: calibrare pesi con il campione reale.")
                    }
                }

                if (meanPriceError != null && meanPriceError > 20) {
                    add("Errore prezzo medio oltre il 20%: non usare ancora la stima come riferimento forte.")
                }

                if (
                    signatureAccuracy >= 85 &&
                    (priceGoodRate == null || priceGoodRate >= 75) &&
                    rankingGoodRate >= 75
                ) {
                    add("Campione coerente: il motore è abbastanza stabile per una calibrazione fine.")
                }
            }
        }

        return CalibrationSummary(
            sessionId = sessionId,
            reviewedCount = reviews.size,
            targetCount = targetCount,
            signatureAccuracy = signatureAccuracy,
            priceGoodRate = priceGoodRate,
            rankingGoodRate = rankingGoodRate,
            meanAbsolutePriceErrorPct = meanPriceError,
            recommendations = recommendations
        )
    }

    private fun percent(good: Int, total: Int): Int =
        if (total <= 0) 0 else ((good * 100.0) / total).roundToInt()
}
