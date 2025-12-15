package com.example.refrotech

// Embedded unit model used in requests & schedules
data class ACUnit(
    val brand: String = "",
    val pk: String = "",
    val workType: String = "",
    val description: String = ""   // ✅ NEW
)
