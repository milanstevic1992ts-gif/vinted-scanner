package it.ge360.vintedscanner.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import it.ge360.vintedscanner.domain.PreferenceEngine
import it.ge360.vintedscanner.model.Listing
import it.ge360.vintedscanner.model.PreferenceProfile
import it.ge360.vintedscanner.model.PricePoint
import it.ge360.vintedscanner.model.SavedSearch

class AppDatabase(context: Context) : SQLiteOpenHelper(context, "vinted_scanner.db", null, 3) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE searches (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                query TEXT NOT NULL,
                max_price REAL,
                size TEXT,
                brand TEXT,
                condition_text TEXT,
                min_margin REAL NOT NULL DEFAULT 20,
                active INTEGER NOT NULL DEFAULT 1,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE listings (
                id TEXT PRIMARY KEY,
                search_id INTEGER NOT NULL,
                title TEXT NOT NULL,
                price REAL NOT NULL,
                shipping REAL NOT NULL DEFAULT 0,
                market_median REAL,
                market_sample_count INTEGER NOT NULL DEFAULT 0,
                market_confidence INTEGER NOT NULL DEFAULT 0,
                url TEXT NOT NULL,
                image_url TEXT,
                condition_text TEXT,
                seller_rating REAL,
                published_at INTEGER,
                first_seen_at INTEGER NOT NULL,
                last_seen_at INTEGER NOT NULL,
                score INTEGER NOT NULL DEFAULT 0,
                estimated_margin REAL,
                preference_boost INTEGER NOT NULL DEFAULT 0,
                risk_flags TEXT NOT NULL DEFAULT '',
                favorite INTEGER NOT NULL DEFAULT 0,
                feedback INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE price_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                listing_id TEXT NOT NULL,
                price REAL NOT NULL,
                seen_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE preference_weights (
                token TEXT PRIMARY KEY,
                weight INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_listings_search_id ON listings(search_id)")
        db.execSQL("CREATE INDEX idx_listings_score ON listings(score DESC)")
        db.execSQL("CREATE INDEX idx_price_history_listing ON price_history(listing_id, seen_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE searches ADD COLUMN brand TEXT")
            db.execSQL("ALTER TABLE searches ADD COLUMN condition_text TEXT")
            db.execSQL("ALTER TABLE listings ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE listings ADD COLUMN last_seen_at INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE listings SET last_seen_at = first_seen_at WHERE last_seen_at = 0")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS price_history (id INTEGER PRIMARY KEY AUTOINCREMENT, listing_id TEXT NOT NULL, price REAL NOT NULL, seen_at INTEGER NOT NULL)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_price_history_listing ON price_history(listing_id, seen_at DESC)")
            db.execSQL(
                "INSERT INTO price_history(listing_id, price, seen_at) SELECT id, price, first_seen_at FROM listings"
            )
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE listings ADD COLUMN market_sample_count INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE listings ADD COLUMN market_confidence INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE listings ADD COLUMN preference_boost INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE listings ADD COLUMN feedback INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS preference_weights (token TEXT PRIMARY KEY, weight INTEGER NOT NULL, updated_at INTEGER NOT NULL)"
            )
        }
    }

    fun saveSearch(search: SavedSearch): Long {
        val values = ContentValues().apply {
            put("query", search.query)
            search.maxPrice?.let { put("max_price", it) } ?: putNull("max_price")
            search.size?.let { put("size", it) } ?: putNull("size")
            search.brand?.let { put("brand", it) } ?: putNull("brand")
            search.condition?.let { put("condition_text", it) } ?: putNull("condition_text")
            put("min_margin", search.minMargin)
            put("active", if (search.active) 1 else 0)
            put("created_at", search.createdAt)
        }
        return if (search.id == 0L) {
            writableDatabase.insertOrThrow("searches", null, values)
        } else {
            writableDatabase.update("searches", values, "id = ?", arrayOf(search.id.toString()))
            search.id
        }
    }

    fun deleteSearch(id: Long) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete(
                "price_history",
                "listing_id IN (SELECT id FROM listings WHERE search_id = ?)",
                arrayOf(id.toString())
            )
            writableDatabase.delete("listings", "search_id = ?", arrayOf(id.toString()))
            writableDatabase.delete("searches", "id = ?", arrayOf(id.toString()))
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun setSearchActive(id: Long, active: Boolean) {
        val values = ContentValues().apply { put("active", if (active) 1 else 0) }
        writableDatabase.update("searches", values, "id = ?", arrayOf(id.toString()))
    }

    fun getSearches(): List<SavedSearch> {
        val out = mutableListOf<SavedSearch>()
        readableDatabase.rawQuery("SELECT * FROM searches ORDER BY created_at DESC", null).use { c ->
            while (c.moveToNext()) {
                out += SavedSearch(
                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                    query = c.getString(c.getColumnIndexOrThrow("query")),
                    maxPrice = c.doubleOrNull("max_price"),
                    size = c.stringOrNull("size"),
                    brand = c.stringOrNull("brand"),
                    condition = c.stringOrNull("condition_text"),
                    minMargin = c.getDouble(c.getColumnIndexOrThrow("min_margin")),
                    active = c.getInt(c.getColumnIndexOrThrow("active")) == 1,
                    createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"))
                )
            }
        }
        return out
    }

    fun upsertListing(listing: Listing): Boolean {
        val existing = readableDatabase.rawQuery(
            "SELECT price, first_seen_at, favorite, feedback FROM listings WHERE id = ?",
            arrayOf(listing.id)
        ).use { c ->
            if (c.moveToFirst()) {
                ExistingListing(
                    price = c.getDouble(0),
                    firstSeenAt = c.getLong(1),
                    favorite = c.getInt(2) == 1,
                    feedback = c.getInt(3)
                )
            } else null
        }

        val isNew = existing == null
        val previousPrice = existing?.price
        val firstSeenAt = existing?.firstSeenAt ?: listing.firstSeenAt
        val favorite = existing?.favorite ?: listing.favorite
        val feedback = existing?.feedback ?: listing.feedback
        val now = maxOf(listing.lastSeenAt, System.currentTimeMillis())

        val values = ContentValues().apply {
            put("id", listing.id)
            put("search_id", listing.searchId)
            put("title", listing.title)
            put("price", listing.price)
            put("shipping", listing.shipping)
            listing.marketMedian?.let { put("market_median", it) } ?: putNull("market_median")
            put("market_sample_count", listing.marketSampleCount)
            put("market_confidence", listing.marketConfidence)
            put("url", listing.url)
            listing.imageUrl?.let { put("image_url", it) } ?: putNull("image_url")
            listing.condition?.let { put("condition_text", it) } ?: putNull("condition_text")
            listing.sellerRating?.let { put("seller_rating", it) } ?: putNull("seller_rating")
            listing.publishedAt?.let { put("published_at", it) } ?: putNull("published_at")
            put("first_seen_at", firstSeenAt)
            put("last_seen_at", now)
            put("score", listing.score)
            listing.estimatedMargin?.let { put("estimated_margin", it) } ?: putNull("estimated_margin")
            put("preference_boost", listing.preferenceBoost)
            put("risk_flags", listing.riskFlags.joinToString("|"))
            put("favorite", if (favorite) 1 else 0)
            put("feedback", feedback)
        }

        writableDatabase.beginTransaction()
        try {
            writableDatabase.insertWithOnConflict(
                "listings",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
            )
            if (isNew || previousPrice != listing.price) {
                val history = ContentValues().apply {
                    put("listing_id", listing.id)
                    put("price", listing.price)
                    put("seen_at", now)
                }
                writableDatabase.insertOrThrow("price_history", null, history)
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
        return isNew
    }

    fun updateIntelligence(listing: Listing) {
        val values = ContentValues().apply {
            listing.marketMedian?.let { put("market_median", it) } ?: putNull("market_median")
            put("market_sample_count", listing.marketSampleCount)
            put("market_confidence", listing.marketConfidence)
            put("score", listing.score)
            listing.estimatedMargin?.let { put("estimated_margin", it) } ?: putNull("estimated_margin")
            put("preference_boost", listing.preferenceBoost)
            put("risk_flags", listing.riskFlags.joinToString("|"))
        }
        writableDatabase.update("listings", values, "id = ?", arrayOf(listing.id))
    }

    fun getListing(listingId: String): Listing? =
        readableDatabase.rawQuery(
            "SELECT * FROM listings WHERE id = ?",
            arrayOf(listingId)
        ).use { c ->
            if (c.moveToFirst()) c.toListing() else null
        }

    fun setFavorite(listingId: String, favorite: Boolean) {
        setListingFeedback(listingId, if (favorite) 1 else 0)
        val values = ContentValues().apply { put("favorite", if (favorite) 1 else 0) }
        writableDatabase.update("listings", values, "id = ?", arrayOf(listingId))
    }

    fun setNotInterested(listingId: String) {
        setListingFeedback(listingId, -1)
        val values = ContentValues().apply { put("favorite", 0) }
        writableDatabase.update("listings", values, "id = ?", arrayOf(listingId))
    }

    fun getPreferenceProfile(): PreferenceProfile {
        val weights = linkedMapOf<String, Int>()
        readableDatabase.rawQuery(
            "SELECT token, weight FROM preference_weights WHERE weight != 0",
            null
        ).use { c ->
            while (c.moveToNext()) {
                weights[c.getString(0)] = c.getInt(1)
            }
        }
        return PreferenceProfile(weights)
    }

    private fun setListingFeedback(listingId: String, newFeedback: Int) {
        val current = readableDatabase.rawQuery(
            "SELECT title, feedback FROM listings WHERE id = ?",
            arrayOf(listingId)
        ).use { c ->
            if (c.moveToFirst()) c.getString(0) to c.getInt(1) else null
        } ?: return

        val (title, oldFeedback) = current
        if (oldFeedback == newFeedback) return

        var weights = getPreferenceProfile().tokenWeights

        if (oldFeedback != 0) {
            weights = PreferenceEngine.updatedWeights(weights, title, -oldFeedback)
        }
        if (newFeedback != 0) {
            weights = PreferenceEngine.updatedWeights(weights, title, newFeedback)
        }

        writableDatabase.beginTransaction()
        try {
            val now = System.currentTimeMillis()
            weights.forEach { (token, weight) ->
                if (weight == 0) {
                    writableDatabase.delete("preference_weights", "token = ?", arrayOf(token))
                } else {
                    val values = ContentValues().apply {
                        put("token", token)
                        put("weight", weight)
                        put("updated_at", now)
                    }
                    writableDatabase.insertWithOnConflict(
                        "preference_weights",
                        null,
                        values,
                        SQLiteDatabase.CONFLICT_REPLACE
                    )
                }
            }

            val feedbackValues = ContentValues().apply { put("feedback", newFeedback.coerceIn(-1, 1)) }
            writableDatabase.update("listings", feedbackValues, "id = ?", arrayOf(listingId))
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun getTopListings(limit: Int = 100, favoritesOnly: Boolean = false): List<Listing> {
        val where = if (favoritesOnly) "WHERE favorite = 1" else ""
        val out = mutableListOf<Listing>()
        readableDatabase.rawQuery(
            "SELECT * FROM listings $where ORDER BY score DESC, first_seen_at DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out += c.toListing()
            }
        }
        return out
    }

    fun getArchiveListings(limit: Int = 1000): List<Listing> {
        val out = mutableListOf<Listing>()
        readableDatabase.rawQuery(
            "SELECT * FROM listings ORDER BY last_seen_at DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out += c.toListing()
            }
        }
        return out
    }

    fun getAllListings(limit: Int = 500): List<Listing> {
        val out = mutableListOf<Listing>()
        readableDatabase.rawQuery(
            "SELECT * FROM listings ORDER BY last_seen_at DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out += c.toListing()
            }
        }
        return out
    }

    fun getPriceHistory(listingId: String): List<PricePoint> {
        val out = mutableListOf<PricePoint>()
        readableDatabase.rawQuery(
            "SELECT price, seen_at FROM price_history WHERE listing_id = ? ORDER BY seen_at DESC",
            arrayOf(listingId)
        ).use { c ->
            while (c.moveToNext()) {
                out += PricePoint(
                    listingId = listingId,
                    price = c.getDouble(0),
                    seenAt = c.getLong(1)
                )
            }
        }
        return out
    }

    private fun Cursor.toListing(): Listing =
        Listing(
            id = getString(getColumnIndexOrThrow("id")),
            searchId = getLong(getColumnIndexOrThrow("search_id")),
            title = getString(getColumnIndexOrThrow("title")),
            price = getDouble(getColumnIndexOrThrow("price")),
            shipping = getDouble(getColumnIndexOrThrow("shipping")),
            marketMedian = doubleOrNull("market_median"),
            marketSampleCount = getInt(getColumnIndexOrThrow("market_sample_count")),
            marketConfidence = getInt(getColumnIndexOrThrow("market_confidence")),
            url = getString(getColumnIndexOrThrow("url")),
            imageUrl = stringOrNull("image_url"),
            condition = stringOrNull("condition_text"),
            sellerRating = doubleOrNull("seller_rating"),
            publishedAt = longOrNull("published_at"),
            firstSeenAt = getLong(getColumnIndexOrThrow("first_seen_at")),
            lastSeenAt = getLong(getColumnIndexOrThrow("last_seen_at")),
            score = getInt(getColumnIndexOrThrow("score")),
            estimatedMargin = doubleOrNull("estimated_margin"),
            preferenceBoost = getInt(getColumnIndexOrThrow("preference_boost")),
            riskFlags = getString(getColumnIndexOrThrow("risk_flags"))
                .split("|")
                .filter(String::isNotBlank),
            favorite = getInt(getColumnIndexOrThrow("favorite")) == 1,
            feedback = getInt(getColumnIndexOrThrow("feedback"))
        )

    private fun Cursor.doubleOrNull(name: String): Double? {
        val i = getColumnIndexOrThrow(name)
        return if (isNull(i)) null else getDouble(i)
    }

    private fun Cursor.longOrNull(name: String): Long? {
        val i = getColumnIndexOrThrow(name)
        return if (isNull(i)) null else getLong(i)
    }

    private fun Cursor.stringOrNull(name: String): String? {
        val i = getColumnIndexOrThrow(name)
        return if (isNull(i)) null else getString(i)
    }

    private data class ExistingListing(
        val price: Double,
        val firstSeenAt: Long,
        val favorite: Boolean,
        val feedback: Int
    )
}
