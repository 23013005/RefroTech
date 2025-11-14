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
    val status: String = "",    // normalized lowercase
    val previousStatus: String? = null,
    val units: List<Map<String, Any>> = emptyList(),
    // reschedule fields (optional, may be null)
    val oldDate: String? = null, // ISO yyyy-MM-dd
    val oldTime: String? = null,
    val newDate: String? = null, // ISO yyyy-MM-dd
    val newTime: String? = null,
    val timestamp: Timestamp? = null
) {
    companion object {
        fun fromFirestore(doc: DocumentSnapshot): RequestData {
            val data = doc.data ?: emptyMap<String, Any>()

            // Helper to safely cast list of maps
            val units = (data["units"] as? List<Map<String, Any>>)
                ?: emptyList<Map<String, Any>>()

            return RequestData(
                id = doc.id,
                customerId = (data["customerId"]?.toString()) ?: "",
                name = (data["name"]?.toString()) ?: "",
                address = (data["address"]?.toString()) ?: "",
                date = (data["date"]?.toString()) ?: "",
                time = (data["time"]?.toString()) ?: "",
                mapLink = (data["mapLink"]?.toString()) ?: "",
                phone = (data["phone"]?.toString()) ?: "",
                status = (data["status"]?.toString()?.lowercase()) ?: "",
                previousStatus = data["previousStatus"]?.toString(),
                units = units,
                oldDate = data["oldDate"]?.toString(),
                oldTime = data["oldTime"]?.toString(),
                newDate = data["newDate"]?.toString(),
                newTime = data["newTime"]?.toString(),
                timestamp = data["timestamp"] as? Timestamp
            )
        }
    }
}
