package it.ge360.vintedscanner.domain

data class ProductSignature(
    val brand: String?,
    val modelTokens: Set<String>,
    val size: String?,
    val category: String?,
    val conditionRank: Int?,
    val label: String
)

object ProductSignatureExtractor {
    private val brands = linkedMapOf(
        "new balance" to "New Balance",
        "the north face" to "The North Face",
        "stone island" to "Stone Island",
        "dr martens" to "Dr. Martens",
        "louis vuitton" to "Louis Vuitton",
        "ralph lauren" to "Ralph Lauren",
        "nike" to "Nike",
        "adidas" to "Adidas",
        "jordan" to "Jordan",
        "gucci" to "Gucci",
        "prada" to "Prada",
        "moncler" to "Moncler",
        "supreme" to "Supreme",
        "lacoste" to "Lacoste",
        "levis" to "Levi's",
        "levi" to "Levi's",
        "diesel" to "Diesel",
        "carhartt" to "Carhartt",
        "asics" to "Asics",
        "salomon" to "Salomon",
        "converse" to "Converse",
        "vans" to "Vans",
        "puma" to "Puma",
        "reebok" to "Reebok",
        "timberland" to "Timberland",
        "burberry" to "Burberry",
        "dior" to "Dior",
        "chanel" to "Chanel",
        "hermes" to "Hermès",
        "armani" to "Armani"
    )

    private val genericTokens = setOf(
        "uomo", "donna", "unisex", "scarpe", "scarpa", "sneakers", "sneaker",
        "maglia", "maglietta", "tshirt", "shirt", "felpa", "hoodie", "giacca",
        "giubbotto", "pantaloni", "jeans", "borsa", "bag", "cappotto", "vestito",
        "nuovo", "nuova", "ottimo", "ottima", "buono", "buona", "condizioni",
        "taglia", "size", "tg", "originale", "original", "vendo", "pari"
    )

    private val categoryKeywords = linkedMapOf(
        "footwear" to setOf(
            "scarpe", "scarpa", "sneaker", "sneakers", "trainer", "trainers",
            "stivali", "stivale", "sandali", "sandalo", "airmax", "air", "dunk",
            "samba", "gazelle", "jordan", "yeezy"
        ),
        "outerwear" to setOf(
            "giacca", "giubbotto", "cappotto", "parka", "piumino", "jacket", "coat"
        ),
        "tops" to setOf(
            "maglia", "maglietta", "tshirt", "shirt", "felpa", "hoodie", "polo",
            "camicia", "sweater", "maglione"
        ),
        "bottoms" to setOf(
            "pantaloni", "pantalone", "jeans", "shorts", "bermuda", "gonna"
        ),
        "bags" to setOf(
            "borsa", "bag", "zaino", "backpack", "pochette", "tracolla"
        ),
        "accessories" to setOf(
            "cintura", "belt", "cappello", "hat", "berretto", "sciarpa", "wallet",
            "portafoglio", "occhiali", "sunglasses"
        )
    )

    private val explicitSizeRegex = Regex(
        """(?:taglia|size|tg)s*[:-]?s*([0-9]{1,2}(?:[.,]5)?|[2-5]XL|XL|XXL|XXXL|XS|XXS|S|M|L)""",
        RegexOption.IGNORE_CASE
    )

    fun extract(title: String, condition: String? = null): ProductSignature {
        val normalized = normalize(title)
        val fullText = normalize(listOfNotNull(title, condition).joinToString(" "))

        val brandEntry = brands.entries
            .sortedByDescending { it.key.length }
            .firstOrNull { normalized.contains(it.key) }

        val size = explicitSizeRegex.find(fullText)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace(",", ".")
            ?.uppercase()

        val rawTokens = normalized
            .split(' ')
            .filter { it.length >= 2 }

        val brandTokens = brandEntry?.key?.split(' ')?.toSet().orEmpty()
        val modelTokens = rawTokens
            .filterNot { it in genericTokens }
            .filterNot { it in brandTokens }
            .filterNot { token -> size != null && token.equals(size, ignoreCase = true) }
            .filterNot { it == "taglia" || it == "size" || it == "tg" }
            .toSet()

        val category = categoryKeywords.entries.firstOrNull { (_, keywords) ->
            rawTokens.any { token -> token in keywords }
        }?.key

        val conditionRank = conditionRank(fullText)

        val labelParts = buildList {
            brandEntry?.value?.let(::add)
            modelTokens.take(3).joinToString(" ").takeIf(String::isNotBlank)?.let(::add)
            size?.let { add("tg $it") }
        }

        return ProductSignature(
            brand = brandEntry?.value,
            modelTokens = modelTokens,
            size = size,
            category = category,
            conditionRank = conditionRank,
            label = labelParts.joinToString(" · ").ifBlank { title.take(60) }
        )
    }

    fun similarity(a: ProductSignature, b: ProductSignature): Double {
        if (a.brand != null && b.brand != null && !a.brand.equals(b.brand, ignoreCase = true)) {
            return 0.0
        }
        if (a.category != null && b.category != null && a.category != b.category) {
            return 0.0
        }
        if (a.size != null && b.size != null && a.size != b.size) {
            return 0.0
        }

        val modelSimilarity = jaccard(a.modelTokens, b.modelTokens)
        val brandScore = when {
            a.brand != null && b.brand != null -> 1.0
            a.brand == null && b.brand == null -> 0.45
            else -> 0.25
        }
        val categoryScore = when {
            a.category != null && b.category != null -> 1.0
            a.category == null && b.category == null -> 0.5
            else -> 0.35
        }
        val sizeScore = when {
            a.size != null && b.size != null -> 1.0
            a.size == null && b.size == null -> 0.55
            else -> 0.45
        }
        val conditionScore = conditionSimilarity(a.conditionRank, b.conditionRank)

        return (
            modelSimilarity * 0.55 +
                brandScore * 0.20 +
                categoryScore * 0.10 +
                sizeScore * 0.10 +
                conditionScore * 0.05
            ).coerceIn(0.0, 1.0)
    }

    private fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val intersection = a.intersect(b).size.toDouble()
        val union = a.union(b).size.toDouble()
        return if (union == 0.0) 0.0 else intersection / union
    }

    private fun conditionSimilarity(a: Int?, b: Int?): Double {
        if (a == null || b == null) return 0.5
        val distance = kotlin.math.abs(a - b)
        return when (distance) {
            0 -> 1.0
            1 -> 0.7
            2 -> 0.4
            else -> 0.1
        }
    }

    private fun conditionRank(text: String): Int? =
        when {
            listOf("nuovo con cartellino", "nuova con cartellino", "new with tags", "mai usato").any(text::contains) -> 5
            listOf("nuovo senza cartellino", "nuova senza cartellino", "new without tags").any(text::contains) -> 4
            listOf("ottime condizioni", "ottimo stato", "pari al nuovo", "excellent").any(text::contains) -> 4
            listOf("buone condizioni", "buono stato", "good condition").any(text::contains) -> 3
            listOf("discrete condizioni", "segni di usura", "fair condition").any(text::contains) -> 2
            listOf("da riparare", "molto usurato", "poor condition").any(text::contains) -> 1
            else -> null
        }

    private fun normalize(value: String): String =
        value.lowercase()
            .replace("levi's", "levis")
            .replace("dr. martens", "dr martens")
            .replace(Regex("[^a-z0-9à-ÿ]+"), " ")
            .replace(Regex("\s+"), " ")
            .trim()
}
