package it.ge360.vintedscanner.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import it.ge360.vintedscanner.domain.PreferenceEngine
import it.ge360.vintedscanner.model.CalibrationReview
import it.ge360.vintedscanner.model.CalibrationSession
import it.ge360.vintedscanner.model.EstimateVerdict
import it.ge360.vintedscanner.model.FeedbackEvent
import it.ge360.vintedscanner.model.FeedbackReason
import it.ge360.vintedscanner.model.RankingVerdict
import it.ge360.vintedscanner.model.Listing
import it.ge360.vintedscanner.model.PreferenceProfile
import it.ge360.vintedscanner.model.PricePoint
import it.ge360.vintedscanner.model.SavedSearch
import it.ge360.vintedscanner.model.SourceDescriptor
import it.ge360.vintedscanner.model.SourceDiagnostic
import it.ge360.vintedscanner.model.SourceKind
import it.ge360.vintedscanner.model.SourceStatus

class AppDatabase(context: Context) : SQLiteOpenHelper(context, "vinted_scanner.db", null, 7) {
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
                market_similarity INTEGER NOT NULL DEFAULT 0,
                market_outliers_removed INTEGER NOT NULL DEFAULT 0,
                comparable_label TEXT,
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
                feedback INTEGER NOT NULL DEFAULT 0,
                feedback_reason TEXT NOT NULL DEFAULT 'NONE'
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
        db.execSQL(
            """
            CREATE TABLE feedback_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                listing_id TEXT NOT NULL,
                reason TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                title TEXT NOT NULL,
                price REAL NOT NULL,
                market_median REAL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_feedback_events_listing ON feedback_events(listing_id, created_at DESC)")
        db.execSQL(
            """
            CREATE TABLE calibration_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                started_at INTEGER NOT NULL,
                completed_at INTEGER,
                target_count INTEGER NOT NULL DEFAULT 25
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE calibration_reviews (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id INTEGER NOT NULL,
                listing_id TEXT NOT NULL,
                title TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                signature_correct INTEGER NOT NULL,
                estimate_verdict TEXT NOT NULL,
                ranking_verdict TEXT NOT NULL,
                expected_value REAL,
                observed_median REAL,
                market_confidence INTEGER NOT NULL,
                market_similarity INTEGER NOT NULL,
                deal_index INTEGER NOT NULL,
                score INTEGER NOT NULL,
                estimated_margin REAL,
                notes TEXT,
                UNIQUE(session_id, listing_id)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_calibration_reviews_session ON calibration_reviews(session_id, created_at DESC)")
        db.execSQL(
            """
            CREATE TABLE source_diagnostics (
                source_id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                kind TEXT NOT NULL,
                supports_auto INTEGER NOT NULL DEFAULT 0,
                requires_config INTEGER NOT NULL DEFAULT 0,
                enabled INTEGER NOT NULL DEFAULT 1,
                status TEXT NOT NULL,
                last_event_at INTEGER,
                last_scan_at INTEGER,
                last_success_at INTEGER,
                last_received_count INTEGER NOT NULL DEFAULT 0,
                total_received INTEGER NOT NULL DEFAULT 0,
                last_error TEXT
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
        if (oldVersion < 4) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS source_diagnostics (
                    source_id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    kind TEXT NOT NULL,
                    supports_auto INTEGER NOT NULL DEFAULT 0,
                    requires_config INTEGER NOT NULL DEFAULT 0,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    status TEXT NOT NULL,
                    last_event_at INTEGER,
                    last_scan_at INTEGER,
                    last_success_at INTEGER,
                    last_received_count INTEGER NOT NULL DEFAULT 0,
                    total_received INTEGER NOT NULL DEFAULT 0,
                    last_error TEXT
                )
                """.trimIndent()
            )
        }
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE listings ADD COLUMN market_similarity INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE listings ADD COLUMN market_outliers_removed INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE listings ADD COLUMN comparable_label TEXT")
        }
        if (oldVersion < 6) {
            db.execSQL("ALTER TABLE listings ADD COLUMN feedback_reason TEXT NOT NULL DEFAULT 'NONE'")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS feedback_events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    listing_id TEXT NOT NULL,
                    reason TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    title TEXT NOT NULL,
                    price REAL NOT NULL,
                    market_median REAL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_feedback_events_listing ON feedback_events(listing_id, created_at DESC)"
            )
            db.execSQL(
                "UPDATE listings SET feedback_reason = CASE WHEN feedback > 0 THEN 'FAVORITE' WHEN feedback < 0 THEN 'DISCARDED' ELSE 'NONE' END"
            )
        }
        if (oldVersion < 7) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS calibration_sessions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    started_at INTEGER NOT NULL,
                    completed_at INTEGER,
                    target_count INTEGER NOT NULL DEFAULT 25
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS calibration_reviews (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    session_id INTEGER NOT NULL,
                    listing_id TEXT NOT NULL,
                    title TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    signature_correct INTEGER NOT NULL,
                    estimate_verdict TEXT NOT NULL,
                    ranking_verdict TEXT NOT NULL,
                    expected_value REAL,
                    observed_median REAL,
                    market_confidence INTEGER NOT NULL,
                    market_similarity INTEGER NOT NULL,
                    deal_index INTEGER NOT NULL,
                    score INTEGER NOT NULL,
                    estimated_margin REAL,
                    notes TEXT,
                    UNIQUE(session_id, listing_id)
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_calibration_reviews_session ON calibration_reviews(session_id, created_at DESC)"
            )
        }
    }

    fun startCalibrationSession(targetCount: Int = 25): CalibrationSession {
        val now = System.currentTimeMillis()
        writableDatabase.beginTransaction()
        try {
            val closeValues = ContentValues().apply { put("completed_at", now) }
            writableDatabase.update(
                "calibration_sessions",
                closeValues,
                "completed_at IS NULL",
                null
            )

            val values = ContentValues().apply {
                put("started_at", now)
                putNull("completed_at")
                put("target_count", targetCount.coerceIn(10, 100))
            }
            val id = writableDatabase.insertOrThrow("calibration_sessions", null, values)
            writableDatabase.setTransactionSuccessful()
            return CalibrationSession(
                id = id,
                startedAt = now,
                targetCount = targetCount.coerceIn(10, 100),
                reviewedCount = 0
            )
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun getActiveCalibrationSession(): CalibrationSession? =
        readableDatabase.rawQuery(
            """
            SELECT s.id, s.started_at, s.completed_at, s.target_count,
                   COUNT(r.id) AS reviewed_count
            FROM calibration_sessions s
            LEFT JOIN calibration_reviews r ON r.session_id = s.id
            WHERE s.completed_at IS NULL
            GROUP BY s.id
            ORDER BY s.started_at DESC
            LIMIT 1
            """.trimIndent(),
            null
        ).use { c ->
            if (!c.moveToFirst()) return@use null
            CalibrationSession(
                id = c.getLong(0),
                startedAt = c.getLong(1),
                completedAt = if (c.isNull(2)) null else c.getLong(2),
                targetCount = c.getInt(3),
                reviewedCount = c.getInt(4)
            )
        }

    fun getLatestCalibrationSession(): CalibrationSession? =
        readableDatabase.rawQuery(
            """
            SELECT s.id, s.started_at, s.completed_at, s.target_count,
                   COUNT(r.id) AS reviewed_count
            FROM calibration_sessions s
            LEFT JOIN calibration_reviews r ON r.session_id = s.id
            GROUP BY s.id
            ORDER BY s.started_at DESC
            LIMIT 1
            """.trimIndent(),
            null
        ).use { c ->
            if (!c.moveToFirst()) return@use null
            CalibrationSession(
                id = c.getLong(0),
                startedAt = c.getLong(1),
                completedAt = if (c.isNull(2)) null else c.getLong(2),
                targetCount = c.getInt(3),
                reviewedCount = c.getInt(4)
            )
        }

    fun completeCalibrationSession(sessionId: Long) {
        val values = ContentValues().apply {
            put("completed_at", System.currentTimeMillis())
        }
        writableDatabase.update(
            "calibration_sessions",
            values,
            "id = ? AND completed_at IS NULL",
            arrayOf(sessionId.toString())
        )
    }

    fun saveCalibrationReview(review: CalibrationReview) {
        val values = ContentValues().apply {
            put("session_id", review.sessionId)
            put("listing_id", review.listingId)
            put("title", review.title)
            put("created_at", review.createdAt)
            put("signature_correct", if (review.signatureCorrect) 1 else 0)
            put("estimate_verdict", review.estimateVerdict.name)
            put("ranking_verdict", review.rankingVerdict.name)
            review.expectedValue?.let { put("expected_value", it) } ?: putNull("expected_value")
            review.observedMedian?.let { put("observed_median", it) } ?: putNull("observed_median")
            put("market_confidence", review.marketConfidence)
            put("market_similarity", review.marketSimilarity)
            put("deal_index", review.dealIndex)
            put("score", review.score)
            review.estimatedMargin?.let { put("estimated_margin", it) } ?: putNull("estimated_margin")
            review.notes?.takeIf(String::isNotBlank)?.let { put("notes", it) } ?: putNull("notes")
        }
        writableDatabase.insertWithOnConflict(
            "calibration_reviews",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun getCalibrationReviews(sessionId: Long): List<CalibrationReview> {
        val out = mutableListOf<CalibrationReview>()
        readableDatabase.rawQuery(
            """
            SELECT id, session_id, listing_id, title, created_at, signature_correct,
                   estimate_verdict, ranking_verdict, expected_value, observed_median,
                   market_confidence, market_similarity, deal_index, score,
                   estimated_margin, notes
            FROM calibration_reviews
            WHERE session_id = ?
            ORDER BY created_at DESC
            """.trimIndent(),
            arrayOf(sessionId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out += CalibrationReview(
                    id = c.getLong(0),
                    sessionId = c.getLong(1),
                    listingId = c.getString(2),
                    title = c.getString(3),
                    createdAt = c.getLong(4),
                    signatureCorrect = c.getInt(5) == 1,
                    estimateVerdict = runCatching {
                        EstimateVerdict.valueOf(c.getString(6))
                    }.getOrDefault(EstimateVerdict.NO_DATA),
                    rankingVerdict = runCatching {
                        RankingVerdict.valueOf(c.getString(7))
                    }.getOrDefault(RankingVerdict.GOOD),
                    expectedValue = if (c.isNull(8)) null else c.getDouble(8),
                    observedMedian = if (c.isNull(9)) null else c.getDouble(9),
                    marketConfidence = c.getInt(10),
                    marketSimilarity = c.getInt(11),
                    dealIndex = c.getInt(12),
                    score = c.getInt(13),
                    estimatedMargin = if (c.isNull(14)) null else c.getDouble(14),
                    notes = if (c.isNull(15)) null else c.getString(15)
                )
            }
        }
        return out
    }

    fun ensureSources(descriptors: List<SourceDescriptor>) {
        val db = writableDatabase
        descriptors.forEach { descriptor ->
            val exists = readableDatabase.rawQuery(
                "SELECT 1 FROM source_diagnostics WHERE source_id = ?",
                arrayOf(descriptor.id)
            ).use { it.moveToFirst() }

            if (!exists) {
                val initialStatus = when {
                    descriptor.requiresConfiguration -> SourceStatus.NOT_CONFIGURED
                    else -> SourceStatus.READY
                }
                val values = ContentValues().apply {
                    put("source_id", descriptor.id)
                    put("name", descriptor.name)
                    put("kind", descriptor.kind.name)
                    put("supports_auto", if (descriptor.supportsAutomaticScan) 1 else 0)
                    put("requires_config", if (descriptor.requiresConfiguration) 1 else 0)
                    put("enabled", 1)
                    put("status", initialStatus.name)
                    put("last_received_count", 0)
                    put("total_received", 0)
                }
                db.insertWithOnConflict(
                    "source_diagnostics",
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_IGNORE
                )
            } else {
                val values = ContentValues().apply {
                    put("name", descriptor.name)
                    put("kind", descriptor.kind.name)
                    put("supports_auto", if (descriptor.supportsAutomaticScan) 1 else 0)
                    put("requires_config", if (descriptor.requiresConfiguration) 1 else 0)
                }
                db.update("source_diagnostics", values, "source_id = ?", arrayOf(descriptor.id))
            }
        }
    }

    fun getSourceDiagnostics(): List<SourceDiagnostic> {
        val out = mutableListOf<SourceDiagnostic>()
        readableDatabase.rawQuery(
            "SELECT * FROM source_diagnostics ORDER BY source_id",
            null
        ).use { c ->
            while (c.moveToNext()) {
                val descriptor = SourceDescriptor(
                    id = c.getString(c.getColumnIndexOrThrow("source_id")),
                    name = c.getString(c.getColumnIndexOrThrow("name")),
                    kind = SourceKind.valueOf(c.getString(c.getColumnIndexOrThrow("kind"))),
                    supportsAutomaticScan = c.getInt(c.getColumnIndexOrThrow("supports_auto")) == 1,
                    requiresConfiguration = c.getInt(c.getColumnIndexOrThrow("requires_config")) == 1
                )
                out += SourceDiagnostic(
                    descriptor = descriptor,
                    enabled = c.getInt(c.getColumnIndexOrThrow("enabled")) == 1,
                    status = SourceStatus.valueOf(c.getString(c.getColumnIndexOrThrow("status"))),
                    lastEventAt = c.longOrNull("last_event_at"),
                    lastScanAt = c.longOrNull("last_scan_at"),
                    lastSuccessAt = c.longOrNull("last_success_at"),
                    lastReceivedCount = c.getInt(c.getColumnIndexOrThrow("last_received_count")),
                    totalReceived = c.getLong(c.getColumnIndexOrThrow("total_received")),
                    lastError = c.stringOrNull("last_error")
                )
            }
        }
        return out
    }

    fun setSourceEnabled(sourceId: String, enabled: Boolean) {
        val values = ContentValues().apply {
            put("enabled", if (enabled) 1 else 0)
            put("status", if (enabled) SourceStatus.IDLE.name else SourceStatus.DISABLED.name)
            put("last_event_at", System.currentTimeMillis())
            putNull("last_error")
        }
        writableDatabase.update("source_diagnostics", values, "source_id = ?", arrayOf(sourceId))
    }

    fun markSourceScanning(sourceId: String) {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("status", SourceStatus.SCANNING.name)
            put("last_event_at", now)
            put("last_scan_at", now)
            putNull("last_error")
        }
        writableDatabase.update("source_diagnostics", values, "source_id = ?", arrayOf(sourceId))
    }

    fun markSourceSuccess(sourceId: String, receivedCount: Int) {
        val now = System.currentTimeMillis()
        writableDatabase.beginTransaction()
        try {
            val currentTotal = readableDatabase.rawQuery(
                "SELECT total_received FROM source_diagnostics WHERE source_id = ?",
                arrayOf(sourceId)
            ).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }

            val values = ContentValues().apply {
                put("status", SourceStatus.READY.name)
                put("last_event_at", now)
                put("last_success_at", now)
                put("last_received_count", receivedCount)
                put("total_received", currentTotal + receivedCount)
                putNull("last_error")
            }
            writableDatabase.update(
                "source_diagnostics",
                values,
                "source_id = ?",
                arrayOf(sourceId)
            )
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun markSourceNotConfigured(sourceId: String) {
        val values = ContentValues().apply {
            put("status", SourceStatus.NOT_CONFIGURED.name)
            put("last_event_at", System.currentTimeMillis())
            put("last_received_count", 0)
            putNull("last_error")
        }
        writableDatabase.update("source_diagnostics", values, "source_id = ?", arrayOf(sourceId))
    }

    fun markSourceError(sourceId: String, message: String) {
        val values = ContentValues().apply {
            put("status", SourceStatus.ERROR.name)
            put("last_event_at", System.currentTimeMillis())
            put("last_received_count", 0)
            put("last_error", message.take(500))
        }
        writableDatabase.update("source_diagnostics", values, "source_id = ?", arrayOf(sourceId))
    }

    fun recordManualSourceImport(sourceId: String, count: Int = 1) {
        markSourceSuccess(sourceId, count)
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
            writableDatabase.delete(
                "feedback_events",
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
            "SELECT price, first_seen_at, favorite, feedback, feedback_reason FROM listings WHERE id = ?",
            arrayOf(listing.id)
        ).use { c ->
            if (c.moveToFirst()) {
                ExistingListing(
                    price = c.getDouble(0),
                    firstSeenAt = c.getLong(1),
                    favorite = c.getInt(2) == 1,
                    feedback = c.getInt(3),
                    feedbackReason = runCatching {
                        FeedbackReason.valueOf(c.getString(4))
                    }.getOrDefault(FeedbackReason.NONE)
                )
            } else null
        }

        val isNew = existing == null
        val previousPrice = existing?.price
        val firstSeenAt = existing?.firstSeenAt ?: listing.firstSeenAt
        val favorite = existing?.favorite ?: listing.favorite
        val feedback = existing?.feedback ?: listing.feedback
        val feedbackReason = existing?.feedbackReason ?: listing.feedbackReason
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
            put("market_similarity", listing.marketSimilarity)
            put("market_outliers_removed", listing.marketOutliersRemoved)
            listing.comparableLabel?.let { put("comparable_label", it) } ?: putNull("comparable_label")
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
            put("feedback_reason", feedbackReason.name)
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
            put("market_similarity", listing.marketSimilarity)
            put("market_outliers_removed", listing.marketOutliersRemoved)
            listing.comparableLabel?.let { put("comparable_label", it) } ?: putNull("comparable_label")
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
        val current = getListing(listingId) ?: return
        if (favorite) {
            if (current.feedbackReason == FeedbackReason.PURCHASED) {
                val values = ContentValues().apply { put("favorite", 1) }
                writableDatabase.update("listings", values, "id = ?", arrayOf(listingId))
            } else {
                setFeedbackReason(listingId, FeedbackReason.FAVORITE)
            }
        } else {
            val nextReason = if (current.feedbackReason == FeedbackReason.FAVORITE) {
                FeedbackReason.NONE
            } else {
                current.feedbackReason
            }
            if (nextReason != current.feedbackReason) {
                setFeedbackReason(listingId, nextReason)
            }
            val values = ContentValues().apply { put("favorite", 0) }
            writableDatabase.update("listings", values, "id = ?", arrayOf(listingId))
        }
    }

    fun setNotInterested(listingId: String) {
        setFeedbackReason(listingId, FeedbackReason.DISCARDED)
    }

    fun setFeedbackReason(listingId: String, newReason: FeedbackReason) {
        val current = getListing(listingId) ?: return
        val oldReason = current.feedbackReason
        if (oldReason == newReason) return

        var weights = getPreferenceProfile().tokenWeights
        weights = PreferenceEngine.applyReason(
            existing = weights,
            title = current.title,
            reason = oldReason,
            direction = -1
        )
        weights = PreferenceEngine.applyReason(
            existing = weights,
            title = current.title,
            reason = newReason,
            direction = 1
        )

        writableDatabase.beginTransaction()
        try {
            val now = System.currentTimeMillis()
            persistPreferenceWeights(weights, now)

            val values = ContentValues().apply {
                put("feedback", newReason.polarity)
                put("feedback_reason", newReason.name)
                put(
                    "favorite",
                    if (newReason == FeedbackReason.FAVORITE) 1 else 0
                )
            }
            writableDatabase.update("listings", values, "id = ?", arrayOf(listingId))

            if (newReason != FeedbackReason.NONE) {
                val event = ContentValues().apply {
                    put("listing_id", listingId)
                    put("reason", newReason.name)
                    put("created_at", now)
                    put("title", current.title)
                    put("price", current.price)
                    current.marketMedian?.let { put("market_median", it) } ?: putNull("market_median")
                }
                writableDatabase.insertOrThrow("feedback_events", null, event)
            }

            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
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

        var purchased = 0
        var discarded = 0
        var tooExpensive = 0
        var badCondition = 0
        var wrongModel = 0
        val rejectedRatios = mutableListOf<Double>()

        readableDatabase.rawQuery(
            "SELECT feedback_reason, price, shipping, market_median FROM listings WHERE feedback_reason != 'NONE'",
            null
        ).use { c ->
            while (c.moveToNext()) {
                val reason = runCatching {
                    FeedbackReason.valueOf(c.getString(0))
                }.getOrDefault(FeedbackReason.NONE)
                when (reason) {
                    FeedbackReason.PURCHASED -> purchased++
                    FeedbackReason.DISCARDED -> discarded++
                    FeedbackReason.TOO_EXPENSIVE -> {
                        tooExpensive++
                        if (!c.isNull(3)) {
                            val median = c.getDouble(3)
                            if (median > 0) {
                                rejectedRatios += (c.getDouble(1) + c.getDouble(2)) / median
                            }
                        }
                    }
                    FeedbackReason.BAD_CONDITION -> badCondition++
                    FeedbackReason.WRONG_MODEL -> wrongModel++
                    else -> Unit
                }
            }
        }

        return PreferenceProfile(
            tokenWeights = weights,
            purchasedCount = purchased,
            discardedCount = discarded,
            tooExpensiveCount = tooExpensive,
            badConditionCount = badCondition,
            wrongModelCount = wrongModel,
            averageTooExpensiveRatio = rejectedRatios.takeIf { it.isNotEmpty() }?.average()
        )
    }

    fun getFeedbackEvents(limit: Int = 100): List<FeedbackEvent> {
        val out = mutableListOf<FeedbackEvent>()
        readableDatabase.rawQuery(
            "SELECT id, listing_id, reason, created_at, title, price, market_median FROM feedback_events ORDER BY created_at DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out += FeedbackEvent(
                    id = c.getLong(0),
                    listingId = c.getString(1),
                    reason = runCatching {
                        FeedbackReason.valueOf(c.getString(2))
                    }.getOrDefault(FeedbackReason.NONE),
                    createdAt = c.getLong(3),
                    title = c.getString(4),
                    price = c.getDouble(5),
                    marketMedian = if (c.isNull(6)) null else c.getDouble(6)
                )
            }
        }
        return out
    }

    private fun persistPreferenceWeights(weights: Map<String, Int>, now: Long) {
        writableDatabase.delete("preference_weights", null, null)
        weights.forEach { (token, weight) ->
            if (weight != 0) {
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
            marketSimilarity = getInt(getColumnIndexOrThrow("market_similarity")),
            marketOutliersRemoved = getInt(getColumnIndexOrThrow("market_outliers_removed")),
            comparableLabel = stringOrNull("comparable_label"),
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
            feedback = getInt(getColumnIndexOrThrow("feedback")),
            feedbackReason = runCatching {
                FeedbackReason.valueOf(getString(getColumnIndexOrThrow("feedback_reason")))
            }.getOrDefault(FeedbackReason.NONE)
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
        val feedback: Int,
        val feedbackReason: FeedbackReason
    )
}
