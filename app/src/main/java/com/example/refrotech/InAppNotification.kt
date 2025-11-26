package com.example.refrotech

import android.app.Activity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

object InAppNotification {

    private const val ANIM_DURATION = 300L
    private const val AUTO_HIDE_DELAY = 3500L

    /**
     * Show the top banner inside the provided Activity.
     * The layout must have:
     *  - LinearLayout with id inAppNotifContainer
     *  - TextView with id notifTitle
     *  - TextView with id notifMessage
     *
     * This uses translation animation and auto-hides after a delay.
     */
    fun show(activity: Activity, title: String, message: String) {
        val container = activity.findViewById<LinearLayout>(R.id.inAppNotifContainer) ?: return
        val titleView = activity.findViewById<TextView>(R.id.notifTitle)
        val msgView = activity.findViewById<TextView>(R.id.notifMessage)

        titleView?.text = title
        msgView?.text = message

        // Ensure layout measured so height is available for animation
        container.measure(
            View.MeasureSpec.makeMeasureSpec(activity.resources.displayMetrics.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.UNSPECIFIED
        )

        // Prepare starting state
        container.visibility = View.VISIBLE
        container.alpha = 0f
        container.translationY = -container.measuredHeight.toFloat()

        // Animate in
        container.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(ANIM_DURATION)
            .withEndAction {
                // Schedule auto-hide
                container.removeCallbacks(null) // remove old callbacks safely
                container.postDelayed(hideRunnable(container), AUTO_HIDE_DELAY)

            }.start()
    }

    private fun hideRunnable(container: View): Runnable {
        return Runnable {
            container.animate()
                .alpha(0f)
                .translationY(-container.height.toFloat())
                .setDuration(ANIM_DURATION)
                .withEndAction { container.visibility = View.GONE }
                .start()
        }
    }

    /** Force hide immediately (cancel any pending hide and hide now) */
    fun hideNow(activity: Activity) {
        val container = activity.findViewById<LinearLayout>(R.id.inAppNotifContainer) ?: return
        container.removeCallbacks(hideRunnable(container))
        container.visibility = View.GONE
    }
}
