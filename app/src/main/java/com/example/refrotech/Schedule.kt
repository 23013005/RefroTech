package com.example.refrotech

import android.net.Uri

data class Schedule(
    val scheduleId: String = "",
    val customerName: String = "",
    val date: String = "",
    val time: String = "",
    val technicians: List<String> = emptyList(),
    val technicianIds: List<String> = emptyList(),
    val assignedTechnicianIds: List<String> = emptyList(),
    val address: String = "",
    val origin: String = "manual",     // "schedule" or "request"
    val requestId: String = "",
    val workStatus: String = "pending",     // canonical schedule status field (workStatus/jobStatus/status)
    val documentation: List<String> = emptyList(),
    val units: List<Map<String, Any>> = emptyList()
) {
    // UNIFIED status for filtering / display across schedule/request differences
    var normalizedStatus: String = ""
}
