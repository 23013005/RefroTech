package com.example.refrotech

/**
 * HistoryItem represents a single row shown on the CustomerHistory screen.
 *
 * Kept intentionally full: includes technicians and rating fields so the UI and
 * adapters can render additional info without breaking when those fields exist.
 *
 * Do NOT remove fields or change their names without updating all adapters and mappers.
 */
data class HistoryItem(
    val id: String,
    val customerName: String,
    val address: String,
    val date: String,
    val time: String,
    val unitsCount: Int,
    val technicians: List<String>,    // RESTORED: required by adapters and normalizer
    val normalizedStatus: String,
    val origin: String, // "request" or "schedule"

    // Rating fields (nullable)
    val rating: Long? = null,
    val ratingComment: String? = null,
    val ratedAtMillis: Long? = null,

    // NEW: creation timestamp (for date filtering)
    val createdAtMillis: Long? = null
)
