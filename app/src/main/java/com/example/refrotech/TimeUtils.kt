package com.example.refrotech

import java.text.SimpleDateFormat
import java.util.*

object TimeUtils {
    // Returns true IF appointment is more than 24 hours away
    fun canEditAppointment(date: String, time: String): Boolean {
        return try {
            // Try ISO first: yyyy-MM-dd HH:mm
            val iso = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val alt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

            val appointment = try {
                iso.parse("$date $time")
            } catch (e: Exception) {
                try {
                    alt.parse("$date $time")
                } catch (e2: Exception) {
                    null
                }
            } ?: return true

            val now = System.currentTimeMillis()
            val diffHours = (appointment.time - now) / (1000 * 60 * 60)
            diffHours >= 24
        } catch (e: Exception) {
            true
        }
    }

    // Convert dd/MM/yyyy to yyyy-MM-dd (if parsing fails, returns original string)
    fun toIsoDate(dateStr: String): String {
        return try {
            val alt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val iso = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val d = alt.parse(dateStr)
            if (d != null) iso.format(d) else dateStr
        } catch (e: Exception) {
            dateStr
        }
    }
}
