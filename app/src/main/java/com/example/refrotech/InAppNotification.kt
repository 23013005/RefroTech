package com.example.refrotech

import android.app.Activity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

object InAppNotification {

    private const val ANIM_DURATION = 300L
    private const val AUTO_HIDE_DELAY = 3500L

    // We keep a reference to the current hide runnable so it can be removed reliably.
    private var currentHideRunnable: Runnable? = null

    /**
     * Show the top banner inside the provided Activity.
     * The layout must have:
     *  - LinearLayout with id inAppNotifContainer
     *  - TextView with id notifTitle
     *  - TextView with id notifMessage
     *
     * onClick: optional lambda invoked when user taps banner
     */
    fun show(activity: Activity, title: String, message: String, onClick: (() -> Unit)? = null) {
        val container = activity.findViewById<LinearLayout>(R.id.inAppNotifContainer) ?: return
        val titleView = activity.findViewById<TextView>(R.id.notifTitle)
        val msgView = activity.findViewById<TextView>(R.id.notifMessage)

        titleView?.text = title
        msgView?.text = message

        // Cancel any existing hide runnable and animations
        currentHideRunnable?.let { container.removeCallbacks(it) }
        container.animate().cancel()

        // Ensure click wiring
        container.setOnClickListener {
            try {
                onClick?.invoke()
            } catch (_: Exception) { }
        }

        // Ensure layout is measured then animate (post guarantees measured dimensions)
        container.post {
            // Prepare starting state
            container.visibility = View.VISIBLE
            container.alpha = 0f
            container.translationY = -container.height.toFloat() // measured height

            // Animate in
            container.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(ANIM_DURATION)
                .withEndAction {
                    // Schedule auto-hide
                    currentHideRunnable = hideRunnable(container)
                    container.postDelayed(currentHideRunnable!!, AUTO_HIDE_DELAY)
                }
                .start()
        }
    }

    private fun hideRunnable(container: View): Runnable {
        return Runnable {
            try {
                container.animate()
                    .alpha(0f)
                    .translationY(-container.height.toFloat())
                    .setDuration(ANIM_DURATION)
                    .withEndAction {
                        try { container.visibility = View.GONE } catch (_: Exception) {}
                    }
                    .start()
            } catch (_: Exception) {}
        }
    }

    /** Force hide immediately (cancel any pending hide and hide now) */
    fun hideNow(activity: Activity) {
        val container = activity.findViewById<LinearLayout>(R.id.inAppNotifContainer) ?: return
        currentHideRunnable?.let { container.removeCallbacks(it) }
        currentHideRunnable = null
        try {
            container.animate().cancel()
            container.visibility = View.GONE
        } catch (_: Exception) {}
    }
}
