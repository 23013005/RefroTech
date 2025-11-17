package com.example.refrotech

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

data class RequestData(
    val id: String = "",
    val customerId: String = "",
    val name: String = "",
    val address: String = "",
    val date: String = "",      // ISO yyyy-MM-dd
    val time: String = "",      // HH:mm
    val mapLink: String = "",
    val phone: String = "",
    val status: String = "",    // pending | confirmed | assigned | in-progress | completed | rejected | reschedule-pending
    val previousStatus: String? = null,
    val units: List<Map<String, Any>> = emptyList(),
    // reschedule fields (optional)
    val oldDate: String? = null,
    val oldTime: String? = null,
    val newDate: String? = null,
    val newTime: String? = null,
    // NEW SCHEDULING / DOCUMENTATION FIELDS
    val technicianId: String? = null,          // uid of assigned technician (or name if you prefer)
    val leaderId: String? = null,              // uid of leader who assigned
    val documentation: List<String> = emptyList(), // list of download URLs
    val jobStatus: String? = null,             // assigned | in-progress | completed
    val timestamp: Timestamp? = null
) {
    companion object {
        fun fromFirestore(doc: DocumentSnapshot): RequestData {
            val data = doc.data ?: emptyMap<String, Any>()

            val units = (data["units"] as? List<Map<String, Any>>) ?: emptyList()
            val docs = (data["documentation"] as? List<String>) ?: emptyList()

            return RequestData(
                id = doc.id,
                customerId = data["customerId"]?.toString() ?: "",
                name = data["name"]?.toString() ?: "",
                address = data["address"]?.toString() ?: "",
                date = data["date"]?.toString() ?: "",
                time = data["time"]?.toString() ?: "",
                mapLink = data["mapLink"]?.toString() ?: "",
                phone = data["phone"]?.toString() ?: "",
                status = data["status"]?.toString()?.lowercase() ?: "",
                previousStatus = data["previousStatus"]?.toString(),
                units = units,
                oldDate = data["oldDate"]?.toString(),
                oldTime = data["oldTime"]?.toString(),
                newDate = data["newDate"]?.toString(),
                newTime = data["newTime"]?.toString(),
                technicianId = data["technicianId"]?.toString(),
                leaderId = data["leaderId"]?.toString(),
                documentation = docs,
                jobStatus = data["jobStatus"]?.toString(),
                timestamp = data["timestamp"] as? Timestamp
            )
        }
    }
}
