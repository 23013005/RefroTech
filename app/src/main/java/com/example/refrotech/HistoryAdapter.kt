package com.example.refrotech

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class HistoryAdapter(
    private var items: MutableList<HistoryItem>
) : RecyclerView.Adapter<HistoryAdapter.VH>() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvHistName)
        val tvStatus: TextView = itemView.findViewById(R.id.tvHistStatus)
        val tvAddress: TextView = itemView.findViewById(R.id.tvHistAddress)
        val tvDate: TextView = itemView.findViewById(R.id.tvHistDate)
        val tvTime: TextView = itemView.findViewById(R.id.tvHistTime)
        val tvUnits: TextView = itemView.findViewById(R.id.tvHistUnits)

        val btnEdit: FrameLayout = itemView.findViewById(R.id.btnEditRequest)
        val btnChange: FrameLayout = itemView.findViewById(R.id.btnChangeSchedule)
        val btnCancel: FrameLayout = itemView.findViewById(R.id.btnCancelRequest)

        // NEW
        val btnRate: FrameLayout = itemView.findViewById(R.id.btnRateUs)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history_request, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val it = items[position]

        holder.tvName.text = it.customerName
        holder.tvAddress.text = it.address
        holder.tvDate.text = it.date
        holder.tvTime.text = it.time
        holder.tvUnits.text = "${it.unitsCount} unit(s)"

        holder.tvStatus.text = when (it.normalizedStatus) {
            "confirmed" -> "Accepted"
            "on-progress" -> "On Progress"
            "completed" -> "Completed"
            "rejected" -> "Rejected"
            "pending" -> "Pending"
            else -> it.normalizedStatus.replaceFirstChar { c -> c.uppercase() }
        }

        // NEW — rating button control
        if (it.normalizedStatus == "completed" && it.rating == null) {
            holder.btnRate.visibility = View.VISIBLE
        } else {
            holder.btnRate.visibility = View.GONE
        }

        holder.btnRate.setOnClickListener { v ->
            val ctx = v.context
            val intent = Intent(ctx, RateWorkActivity::class.java)
            intent.putExtra("requestId", it.id)
            intent.putExtra("requestDate", it.date)
            intent.putExtra("requestTime", it.time)
            intent.putExtra("requestTitle", it.customerName)
            ctx.startActivity(intent)
        }

        // === EDIT ===
        holder.btnEdit.setOnClickListener { v ->
            val ctx = v.context
            if (it.normalizedStatus != "pending") {
                Toast.makeText(ctx, "Request cannot be edited anymore.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val i = Intent(ctx, EditCustomerRequest::class.java)
            i.putExtra("requestId", it.id)
            ctx.startActivity(i)
        }

        // === RESCHEDULE ===
        val allowChange = it.normalizedStatus == "confirmed"
        if (!allowChange) {
            holder.btnChange.isEnabled = false
            holder.btnChange.alpha = 0.4f
        } else {
            holder.btnChange.isEnabled = true
            holder.btnChange.alpha = 1f
            holder.btnChange.setOnClickListener { v ->
                val ctx = v.context
                showDatePickerForReschedule(ctx, it.id, it.date, it.time)
            }
        }

        // === CANCEL ===
        holder.btnCancel.setOnClickListener { v ->
            val ctx = v.context
            val status = it.normalizedStatus

            if (status == "on-progress" || status == "completed") {
                Toast.makeText(ctx, "This job is already in progress/completed. Cannot cancel.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (status != "pending" && status != "confirmed") {
                Toast.makeText(ctx, "This request cannot be cancelled at this stage.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val scheduled = parseDateTimeSafe(it.date, it.time)
            if (scheduled == null) {
                Toast.makeText(ctx, "Invalid schedule date/time.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val now = Date()
            val diffMillis = scheduled.time - now.time

            if (diffMillis < 24L * 60L * 60L * 1000L) {
                Toast.makeText(ctx, "Cancellation allowed only at least 24 hours before schedule.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            AlertDialog.Builder(ctx)
                .setTitle("Cancel Request")
                .setMessage("Are you sure you want to cancel this request?")
                .setPositiveButton("Yes") { _, _ ->
                    db.collection(FirestoreFields.REQUESTS).document(it.id)
                        .update(
                            mapOf(
                                "status" to "cancelled",
                                "updatedAt" to Timestamp.now()
                            )
                        )
                        .addOnSuccessListener {
                            Toast.makeText(ctx, "Request cancelled", Toast.LENGTH_SHORT).show()
                        }
                }
                .setNegativeButton("No", null)
                .show()
        }
    }

    private fun showDatePickerForReschedule(ctx: Context, requestId: String, oldDate: String?, oldTime: String?) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            ctx,
            { _, year, month, dayOfMonth ->
                val newDate = "%04d-%02d-%02d".format(year, month + 1, dayOfMonth)
                showTimePickerForReschedule(ctx, requestId, oldDate, oldTime, newDate)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.minDate = System.currentTimeMillis()
            show()
        }
    }

    private fun showTimePickerForReschedule(ctx: Context, requestId: String, oldDate: String?, oldTime: String?, newDate: String) {
        val now = Calendar.getInstance()
        TimePickerDialog(
            ctx,
            { _, hour, minute ->
                val newTime = "%02d:%02d".format(hour, minute)
                submitReschedule(requestId, oldDate, oldTime, newDate, newTime, ctx)
            },
            now.get(Calendar.HOUR_OF_DAY),
            now.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun submitReschedule(requestId: String, oldDate: String?, oldTime: String?, newDate: String, newTime: String, ctx: Context) {
        val uid = auth.currentUser?.uid ?: ""

        db.collection(FirestoreFields.REQUESTS).document(requestId)
            .update(
                mapOf(
                    "rescheduleRequested" to true,
                    "rescheduleStatus" to "pending",
                    "newDate" to newDate,
                    "newTime" to newTime,
                    "oldDate" to (oldDate ?: ""),
                    "oldTime" to (oldTime ?: ""),
                    "rescheduleRequestedBy" to uid,
                    "updatedAt" to Timestamp.now()
                )
            )
            .addOnSuccessListener {
                Toast.makeText(ctx, "Reschedule requested", Toast.LENGTH_SHORT).show()
            }
    }

    private fun parseDateTimeSafe(dateStr: String?, timeStr: String?): Date? {
        if (dateStr.isNullOrBlank()) return null

        val formatIso = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val formatDisp = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val cal = Calendar.getInstance()

        val d = try {
            when {
                dateStr.contains("/") -> formatDisp.parse(dateStr)
                else -> formatIso.parse(dateStr)
            }
        } catch (e: Exception) {
            null
        } ?: return null

        cal.time = d
        if (!timeStr.isNullOrBlank()) {
            try {
                val t = timeFmt.parse(timeStr)
                val tc = Calendar.getInstance()
                tc.time = t
                cal.set(Calendar.HOUR_OF_DAY, tc.get(Calendar.HOUR_OF_DAY))
                cal.set(Calendar.MINUTE, tc.get(Calendar.MINUTE))
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
            } catch (_: Exception) { }
        }

        return cal.time
    }

    fun updateData(newItems: List<HistoryItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
