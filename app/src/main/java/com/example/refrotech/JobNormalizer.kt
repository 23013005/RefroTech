package com.example.refrotech

import com.google.firebase.firestore.DocumentSnapshot

/**
 * Normalize Firestore document snapshots into Schedule objects for unified display.
 */
object JobNormalizer {

    fun scheduleDocToSchedule(doc: DocumentSnapshot): Schedule {
        val scheduleId = doc.id
        val customerName = doc.getString("customerName") ?: ""
        val date = doc.getString("date") ?: ""
        val time = doc.getString("time") ?: ""
        val technicians = doc.get("technicians") as? List<String> ?: emptyList()
        val technicianIds = doc.get("technicianIds") as? List<String> ?: emptyList()
        val assignedTechnicianIds = doc.get("assignedTechnicianIds") as? List<String> ?: technicianIds
        val address = doc.getString("address") ?: ""
        val origin = "schedule"
        val requestId = doc.getString("requestId") ?: ""
        val workStatus = doc.getString("workStatus") ?: "pending"
        val documentation = doc.get("documentation") as? List<String> ?: emptyList()

        return Schedule(
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
            workStatus = workStatus,
            documentation = documentation
        )
    }

    fun requestDocToSchedule(doc: DocumentSnapshot): Schedule {
        val requestId = doc.id
        val customerName = doc.getString("customerName") ?: doc.getString("name") ?: ""
        val date = doc.getString("date") ?: ""
        val time = doc.getString("time") ?: ""
        val technicians = doc.get("technician") as? List<String> ?: emptyList()
        val technicianIds = doc.get("technicianIds") as? List<String> ?: emptyList()
        val assignedTechnicianIds = doc.get("assignedTechnicianIds") as? List<String> ?: technicianIds
        val address = doc.getString("address") ?: ""
        val origin = "request"
        val workStatus = doc.getString("jobStatus") ?: doc.getString("status") ?: "pending"
        val documentation = doc.get("documentation") as? List<String> ?: emptyList()

        return Schedule(
            scheduleId = requestId, // we keep id here so adapter can send it; origin indicates it's a request
            customerName = customerName,
            date = date,
            time = time,
            technicians = technicians,
            technicianIds = technicianIds,
            assignedTechnicianIds = assignedTechnicianIds,
            address = address,
            origin = origin,
            requestId = requestId,
            workStatus = workStatus,
            documentation = documentation
        )
    }
}
