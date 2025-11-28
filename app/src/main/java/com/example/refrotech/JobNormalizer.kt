package com.example.refrotech

import com.google.firebase.firestore.DocumentSnapshot
import java.util.*

/**
 * JobNormalizer converts Firestore request/schedule docs into Schedule model instances,
 * providing robust fallbacks for varied field names and missing values.
 *
 * Important compatibility decisions:
 * - Accept "technicians" or "technician" (both array fields)
 * - Accept "technicianIds" or "assignedTechnicianIds" where appropriate
 * - Accept "date" or "newDate" and "time" or "newTime" for display
 * - Prefer jobStatus (technician progress) over status (leader approval) when present
 */
object JobNormalizer {

    private fun normalizeStatusRaw(raw: String?): String {
        if (raw == null) return "pending"
        val r = raw.lowercase(Locale.getDefault()).trim()
        return when {
            r.contains("complete") || r == "completed" -> "completed"
            r.contains("on") && r.contains("progress") -> "on-progress"
            r == "in_progress" || r == "in-progress" -> "on-progress"
            r == "confirmed" -> "confirmed"
            r == "accepted" -> "confirmed"
            r == "rejected" -> "rejected"
            r == "pending" || r.isBlank() -> "pending"
            else -> r
        }
    }

    private fun readStringSafe(doc: DocumentSnapshot, vararg keys: String): String {
        for (k in keys) {
            val v = doc.getString(k)
            if (!v.isNullOrBlank()) return v
        }
        return ""
    }

    private fun readStringListSafe(doc: DocumentSnapshot, vararg keys: String): List<String> {
        for (k in keys) {
            val raw = doc.get(k)
            if (raw is List<*>) {
                try {
                    return raw.map { it?.toString() ?: "" }.filter { it.isNotBlank() }
                } catch (_: Exception) { /* continue to next key */ }
            }
        }
        return emptyList()
    }

    private fun canonicalFromRequest(doc: DocumentSnapshot): String {
        // Prefer jobStatus (technician progress) if it exists; otherwise status.
        val jobStatus = doc.getString(FirestoreFields.FIELD_JOB_STATUS)?.lowercase()
        val status = doc.getString(FirestoreFields.FIELD_STATUS)?.lowercase()
        return when {
            !jobStatus.isNullOrBlank() -> jobStatus
            !status.isNullOrBlank() -> status
            else -> "pending"
        }
    }

    private fun canonicalFromSchedule(doc: DocumentSnapshot): String {
        val work = doc.getString("workStatus")?.lowercase()
        val jobStatus = doc.getString(FirestoreFields.FIELD_JOB_STATUS)?.lowercase()
        val status = doc.getString(FirestoreFields.FIELD_STATUS)?.lowercase()
        return when {
            !work.isNullOrBlank() -> work
            !jobStatus.isNullOrBlank() -> jobStatus
            !status.isNullOrBlank() -> status
            else -> "pending"
        }
    }

    fun requestDocToSchedule(doc: DocumentSnapshot): Schedule {
        val requestId = doc.id

        // customer name - try multiple fields (customerName or name)
        val customerName = readStringSafe(doc, "customerName", "name")

        // date/time: prefer date/time, but fall back to newDate/newTime
        val date = readStringSafe(doc, "date", "newDate")
        val time = readStringSafe(doc, "time", "newTime")

        // technicians: accept both "technicians" and "technician"
        val technicians = readStringListSafe(doc, "technicians", "technician")
        val technicianIds = readStringListSafe(doc, "technicianIds", "assignedTechnicianIds", "techniciansIds")
        val assignedTechnicianIds = readStringListSafe(doc, "assignedTechnicianIds", "technicianIds")

        val address = readStringSafe(doc, "address")
        val origin = "request"

        val workStatusRaw = canonicalFromRequest(doc)

        // documentation: sometimes structured as subcollection; when stored inline as list, accept it
        val documentation = (doc.get(FirestoreFields.FIELD_DOCUMENTATION) as? List<*>)?.map { it.toString() } ?: emptyList()

        val unitsList = doc.get("units") as? List<Map<String, Any>> ?: emptyList()

        val schedule = Schedule(
            scheduleId = requestId,
            customerName = customerName,
            date = date,
            time = time,
            technicians = technicians,
            technicianIds = technicianIds,
            assignedTechnicianIds = assignedTechnicianIds,
            address = address,
            origin = origin,
            requestId = requestId,
            workStatus = workStatusRaw,
            documentation = documentation,
            units = unitsList
        )

        schedule.normalizedStatus = normalizeStatusRaw(workStatusRaw)
        return schedule
    }

    fun scheduleDocToSchedule(doc: DocumentSnapshot): Schedule {
        val scheduleId = doc.id
        val customerName = readStringSafe(doc, "customerName", "name")
        val date = readStringSafe(doc, "date", "newDate")
        val time = readStringSafe(doc, "time", "newTime")
        val technicians = readStringListSafe(doc, "technicians", "technician")
        val technicianIds = readStringListSafe(doc, "technicianIds", "assignedTechnicianIds")
        val assignedTechnicianIds = readStringListSafe(doc, "assignedTechnicianIds", "technicianIds")
        val address = readStringSafe(doc, "address")
        val origin = readStringSafe(doc, "origin").ifBlank { "schedule" }
        val requestId = readStringSafe(doc, "requestId")
        val workStatusRaw = canonicalFromSchedule(doc)
        val documentation = (doc.get(FirestoreFields.FIELD_DOCUMENTATION) as? List<*>)?.map { it.toString() } ?: emptyList()

        val schedule = Schedule(
            scheduleId = scheduleId,
            customerName = customerName,
            date = date,
            time = time,
            technicians = technicians,
            technicianIds = technicianIds,
            assignedTechnicianIds = assignedTechnicianIds,
            address = address,
            origin = origin,
            requestId = requestId,
            workStatus = workStatusRaw,
            documentation = documentation
        )

        schedule.normalizedStatus = normalizeStatusRaw(workStatusRaw)
        return schedule
    }
}
