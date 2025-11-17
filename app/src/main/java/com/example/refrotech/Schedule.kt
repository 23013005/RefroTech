package com.example.refrotech

data class Schedule(
    val scheduleId: String = "",
    val customerName: String = "",
    val date: String = "",
    val time: String = "",
    val technicians: String = "",       // comma-separated names
    val technicianIds: List<String> = emptyList(),
    val address: String = "",
    val origin: String = "manual",     // "manual" or "request"
    val requestId: String = ""
)
