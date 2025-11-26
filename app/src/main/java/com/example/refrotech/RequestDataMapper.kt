package com.example.refrotech

import com.google.firebase.firestore.DocumentSnapshot

object RequestDataMapper {

    fun fromSchedule(doc: DocumentSnapshot): RequestData {

        val date = doc.getString("date") ?: ""
        val time = doc.getString("time") ?: ""

        val technicianNames = doc.get("technicians") as? List<String> ?: emptyList()
        val address = doc.getString("address") ?: ""
        val customerName = doc.getString("customerName") ?: ""

        val status = doc.getString("workStatus")
            ?: doc.getString("status")
            ?: "assigned"

        return RequestData(
            id = doc.id,
            name = customerName,
            customerName = customerName,
            address = address,
            date = date,
            time = time,
            mapLink = "",
            phone = "",
            status = status,
            jobStatus = status,
            units = emptyList(),
            createdAtMillis = null,
            newDate = null,
            newTime = null,
            oldDate = null,
            oldTime = null,
            // schedule-origin documents do not have reschedule metadata; default false/null
            rescheduleRequested = false,
            rescheduleStatus = null,
            unitsCount = 0
        )
    }
}
