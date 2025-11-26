package com.example.refrotech

import android.app.Activity
import android.content.Intent
import android.util.Log
import com.google.firebase.auth.FirebaseAuth

object LogoutHelper {

    fun logout(activity: Activity) {

        // 1) Firebase Sign-out
        try {
            FirebaseAuth.getInstance().signOut()
        } catch (e: Exception) {
            Log.e("LogoutHelper", "Firebase signout failed: ${e.message}")
        }

        // 2) Clear in-app notifications safely
        try {
            InAppNotificationManager.clear(activity)
            InAppNotification.hideNow(activity)
        } catch (e: Exception) {
            Log.e("LogoutHelper", "Failed clearing notifications: ${e.message}")
        }

        // 3) Stop notification listeners
        try {
            InAppNotificationManager.stopListening()
        } catch (_: Exception) {}

        // 4) Redirect to login page (MainActivity)
        val intent = Intent(activity, MainActivity::class.java)
        intent.flags =
            Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK

        activity.startActivity(intent)
        activity.finish()
    }
}
