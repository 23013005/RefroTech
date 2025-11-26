package com.example.refrotech

import android.app.Activity
import android.util.Log
import android.view.View
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.lang.ref.WeakReference

/**
 * InAppNotificationManager:
 * - Listens for notifications targeted to the currently-signed-in user
 * - Shows them using InAppNotification.show(activity, title, message)
 * - Marks notifications.read = true so they are not shown again
 *
 * Usage:
 * - Call startListening(activity) in onCreate() of dashboard
 * - Call stopListening() in onStop()/onDestroy()
 */
object InAppNotificationManager {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var listener: ListenerRegistration? = null
    private const val TAG = "InAppNotifManager"

    // FIXED: use WeakReference to avoid memory leak
    private var containerRef: WeakReference<View>? = null

    // Register container safely
    fun registerContainer(container: View) {
        containerRef = WeakReference(container)
    }

    // Called by LogoutHelper
    fun clear(activity: Activity) {
        try {
            val container = containerRef?.get()
            container?.animate()?.cancel()
            container?.visibility = View.GONE
        } catch (_: Exception) {}
    }

    fun startListening(activity: Activity) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Log.d(TAG, "No signed-in user — cannot start notification listener")
            return
        }

        if (listener != null) return

        listener = db.collection("notifications")
            .whereEqualTo("userId", uid)
            .whereEqualTo("read", false)
            .orderBy("createdAt")
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    Log.w(TAG, "notification listener error: ${e.message}")
                    return@addSnapshotListener
                }
                if (snap == null || snap.isEmpty) return@addSnapshotListener

                val doc = snap.documents.firstOrNull() ?: return@addSnapshotListener
                val title = doc.getString("title") ?: "Notifikasi"
                val message = doc.getString("message") ?: ""

                try {
                    InAppNotification.show(activity, title, message)
                } catch (ex: Exception) {
                    Log.w(TAG, "Failed to show notification UI: ${ex.message}")
                }

                // mark read
                try {
                    doc.reference.update(
                        "read", true,
                        "readAt", Timestamp.now()
                    )
                } catch (ex: Exception) {
                    Log.w(TAG, "Failed to mark notification read: ${ex.message}")
                }
            }
    }

    fun stopListening() {
        try {
            listener?.remove()
            listener = null
        } catch (_: Exception) {}
    }
}
