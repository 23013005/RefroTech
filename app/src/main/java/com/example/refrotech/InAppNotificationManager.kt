package com.example.refrotech

import android.app.Activity
import android.util.Log
import android.view.View
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.lang.ref.WeakReference

object InAppNotificationManager {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var listener: ListenerRegistration? = null
    private const val TAG = "InAppNotifManager"

    // weak ref to avoid leaks
    private var containerRef: WeakReference<View>? = null

    fun registerContainer(container: View) {
        containerRef = WeakReference(container)
    }

    fun startListening(activity: Activity) {
        val uid = auth.currentUser?.uid ?: return
        if (listener != null) return

        listener = db.collection("notifications")
            .whereEqualTo("userId", uid)
            .whereEqualTo("read", false)
            .orderBy("createdAt")
            .addSnapshotListener { snap, e ->
                if (e != null || snap == null || snap.isEmpty) return@addSnapshotListener

                val doc = snap.documents.first()
                val title = doc.getString("title") ?: "Notifikasi"
                val msg = doc.getString("message") ?: ""

                try {
                    InAppNotification.show(activity, title, msg)
                } catch (_: Exception) {}

                doc.reference.update(
                    mapOf(
                        "read" to true,
                        "readAt" to Timestamp.now()
                    )
                )
            }
    }

    fun stopListening() {
        listener?.remove()
        listener = null
    }

    fun clear(activity: Activity) {
        try {
            containerRef?.get()?.visibility = View.GONE
        } catch (_: Exception) {}
    }
}
