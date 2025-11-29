package com.example.refrotech

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore

object NotificationUtils {

    fun createNotification(userId: String, title: String, message: String) {
        val db = FirebaseFirestore.getInstance()

        val notif = hashMapOf(
            "userId" to userId,
            "title" to title,
            "message" to message,
            "createdAt" to Timestamp.now(),
            "read" to false
        )

        db.collection("notifications").add(notif)
    }
}
