package it.ge360.vintedscanner.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import it.ge360.vintedscanner.model.Listing
import it.ge360.vintedscanner.model.SavedSearch

class AppDatabase(context: Context) : SQLiteOpenHelper(context, "vinted_scanner.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE searches (id INTEGER PRIMARY KEY AUTOINCREMENT, query TEXT NOT NULL, max_price REAL, size TEXT, min_margin REAL NOT NULL DEFAULT 20, active INTEGER NOT NULL DEFAULT 1, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE listings (id TEXT PRIMARY KEY, search_id INTEGER NOT NULL, title TEXT NOT NULL, price REAL NOT NULL, shipping REAL NOT NULL DEFAULT 0, market_median REAL, url TEXT NOT NULL, image_url TEXT, condition_text TEXT, seller_rating REAL, published_at INTEGER, first_seen_at INTEGER NOT NULL, score INTEGER NOT NULL DEFAULT 0, estimated_margin REAL, risk_flags TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE INDEX idx_listings_search_id ON listings(search_id)")
        db.execSQL("CREATE INDEX idx_listings_score ON listings(score DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun insertSearch(search: SavedSearch): Long {
        val values = ContentValues().apply {
            put("query", search.query)
            search.maxPrice?.let { put("max_price", it) } ?: putNull("max_price")
            search.size?.let { put("size", it) } ?: putNull("size")
            put("min_margin", search.minMargin)
            put("active", if (search.active) 1 else 0)
            put("created_at", search.createdAt)
        }
        return writableDatabase.insertOrThrow("searches", null, values)
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
                    minMargin = c.getDouble(c.getColumnIndexOrThrow("min_margin")),
                    active = c.getInt(c.getColumnIndexOrThrow("active")) == 1,
                    createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"))
                )
            }
        }
        return out
    }

    fun upsertListing(listing: Listing) {
        val values = ContentValues().apply {
            put("id", listing.id)
            put("search_id", listing.searchId)
            put("title", listing.title)
            put("price", listing.price)
            put("shipping", listing.shipping)
            listing.marketMedian?.let { put("market_median", it) } ?: putNull("market_median")
            put("url", listing.url)
            listing.imageUrl?.let { put("image_url", it) } ?: putNull("image_url")
            listing.condition?.let { put("condition_text", it) } ?: putNull("condition_text")
            listing.sellerRating?.let { put("seller_rating", it) } ?: putNull("seller_rating")
            listing.publishedAt?.let { put("published_at", it) } ?: putNull("published_at")
            put("first_seen_at", listing.firstSeenAt)
            put("score", listing.score)
            listing.estimatedMargin?.let { put("estimated_margin", it) } ?: putNull("estimated_margin")
            put("risk_flags", listing.riskFlags.joinToString("|"))
        }
        writableDatabase.insertWithOnConflict("listings", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getTopListings(limit: Int = 100): List<Listing> {
        val out = mutableListOf<Listing>()
        readableDatabase.rawQuery(
            "SELECT * FROM listings ORDER BY score DESC, first_seen_at DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out += Listing(
                    id = c.getString(c.getColumnIndexOrThrow("id")),
                    searchId = c.getLong(c.getColumnIndexOrThrow("search_id")),
                    title = c.getString(c.getColumnIndexOrThrow("title")),
                    price = c.getDouble(c.getColumnIndexOrThrow("price")),
                    shipping = c.getDouble(c.getColumnIndexOrThrow("shipping")),
                    marketMedian = c.doubleOrNull("market_median"),
                    url = c.getString(c.getColumnIndexOrThrow("url")),
                    imageUrl = c.stringOrNull("image_url"),
                    condition = c.stringOrNull("condition_text"),
                    sellerRating = c.doubleOrNull("seller_rating"),
                    publishedAt = c.longOrNull("published_at"),
                    firstSeenAt = c.getLong(c.getColumnIndexOrThrow("first_seen_at")),
                    score = c.getInt(c.getColumnIndexOrThrow("score")),
                    estimatedMargin = c.doubleOrNull("estimated_margin"),
                    riskFlags = c.getString(c.getColumnIndexOrThrow("risk_flags")).split("|").filter(String::isNotBlank)
                )
            }
        }
        return out
    }

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
}
