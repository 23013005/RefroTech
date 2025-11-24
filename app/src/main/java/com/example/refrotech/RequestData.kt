package com.example.refrotech

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

data class RequestData(
    val id: String = "",
    val name: String = "",
    val customerName: String = "",
    val address: String = "",
    val date: String = "",
    val time: String = "",
    val mapLink: String = "",
    val phone: String = "",
    val status: String = "",
    val jobStatus: String? = "",
    val units: List<Map<String, Any>> = emptyList(),

    // NEW FIELD REQUIRED BY CustomerHistory
    val createdAtMillis: Long? = null,

    // optional for reschedule
    val newDate: String? = null,
    val newTime: String? = null,
    val oldDate: String? = null,
    val oldTime: String? = null,

    val unitsCount: Int = 0
) {
    companion object {
        fun fromFirestore(doc: DocumentSnapshot): RequestData {
            val data = doc.data ?: emptyMap<String, Any>()

            val createdAt = data["createdAt"] as? Timestamp
            val createdAtMillis = createdAt?.toDate()?.time

            val unitsList = data["units"] as? List<Map<String, Any>> ?: emptyList()

            return RequestData(
                id = doc.id,
                name = data["name"]?.toString() ?: "",
                customerName = data["customerName"]?.toString() ?: "",
                address = data["address"]?.toString() ?: "",
                date = data["date"]?.toString() ?: "",
                time = data["time"]?.toString() ?: "",
                mapLink = data["mapLink"]?.toString() ?: "",
                phone = data["phone"]?.toString() ?: "",
                status = data["status"]?.toString() ?: "",
                jobStatus = data["jobStatus"]?.toString(),
                units = unitsList,

                createdAtMillis = createdAtMillis,

                newDate = data["newDate"]?.toString(),
                newTime = data["newTime"]?.toString(),
                oldDate = data["oldDate"]?.toString(),
                oldTime = data["oldTime"]?.toString(),

                unitsCount = unitsList.size
            )
        }
    }
}
