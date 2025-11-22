package com.example.refrotech

import android.app.AlertDialog
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
import kotlin.math.abs

class HistoryAdapter(
    private var items: MutableList<RequestData>
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
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_history_request, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val it = items[position]

        holder.tvName.text = it.name
        // show "Accepted" when internal status is "confirmed"
        holder.tvStatus.text = when (it.status.lowercase(Locale.getDefault())) {
            "confirmed" -> "Accepted"
            else -> it.status.replaceFirstChar { c -> c.uppercase(Locale.getDefault()) }
        }

        holder.tvAddress.text = it.address
        holder.tvDate.text = it.date
        holder.tvTime.text = it.time

        // show only unit count as requested
        holder.tvUnits.text = "${it.unitsCount} unit(s)"

        // EDIT: allow editing only when the request is still editable (pending / waiting_approval / not_reviewed)
        holder.btnEdit.setOnClickListener { v ->
            val ctx = v.context
            // Only allow editing when status is editable (mirror logic from EditCustomerRequest)
            val editableStatuses = setOf("pending", "waiting_approval", "not_reviewed", "notreviewed", "draft")
            if (!editableStatuses.contains(it.status.lowercase(Locale.getDefault()))) {
                Toast.makeText(ctx, "Request cannot be edited at this stage.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val i = Intent(ctx, EditCustomerRequest::class.java)
            i.putExtra("requestId", it.id)
            ctx.startActivity(i)
        }

        // CHANGE / RESCHEDULE
        holder.btnChange.setOnClickListener { v ->
            val ctx = v.context

            // Must be confirmed (internal) to request reschedule
            if (it.status.lowercase(Locale.getDefault()) != "confirmed") {
                Toast.makeText(ctx, "Reschedule is allowed only for accepted requests.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Parse scheduled datetime and enforce 24-hour rule
            val scheduledDate = parseDateTimeSafe(it.date, it.time)
            if (scheduledDate == null) {
                Toast.makeText(ctx, "Unable to parse schedule date/time.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val now = Date()
            val diffMillis = scheduledDate.time - now.time

            val hoursDiff = diffMillis / (1000 * 60 * 60)
            if (diffMillis <= 0) {
                Toast.makeText(ctx, "Cannot reschedule past or current jobs.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (diffMillis < 24 * 60 * 60 * 1000L) {
                Toast.makeText(ctx, "Reschedule requests must be made at least 24 hours before scheduled time.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Confirm user action (no new date required from customer)
            AlertDialog.Builder(ctx)
                .setTitle("Request Reschedule")
                .setMessage("Submit reschedule request to leader? Leader will contact you to set a new date/time.")
                .setPositiveButton("Yes") { _, _ ->
                    submitRescheduleRequest(it.id, ctx)
                }
                .setNegativeButton("No", null)
                .show()
        }

        // CANCEL request -> mark cancelled (listener may delete)
        holder.btnCancel.setOnClickListener { v ->
            val ctx = v.context
            AlertDialog.Builder(ctx)
                .setTitle("Cancel Request")
                .setMessage("Are you sure you want to cancel this request?")
                .setPositiveButton("Yes") { _, _ ->
                    db.collection(FirestoreFields.REQUESTS).document(it.id)
                        .update(mapOf("status" to "cancelled", "updatedAt" to Timestamp.now()))
                        .addOnSuccessListener {
                            Toast.makeText(ctx, "Request cancellation requested", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(ctx, "Failed to cancel: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
                .setNegativeButton("No", null)
                .show()
        }
    }

    private fun submitRescheduleRequest(requestId: String, ctx: Context) {
        val currentUser = auth.currentUser
        val uid = currentUser?.uid ?: ""

        val updates = mapOf(
            "rescheduleRequested" to true,
            "rescheduleRequestedAt" to Timestamp.now(),
            "rescheduleRequestedBy" to uid,
            "rescheduleStatus" to "pending",
            "updatedAt" to Timestamp.now()
        )

        db.collection(FirestoreFields.REQUESTS).document(requestId)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(ctx, "Reschedule requested. Leader will review it.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(ctx, "Failed to request reschedule: ${e.message}", Toast.LENGTH_SHORT).show()
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
        // merge time if present
        if (!timeStr.isNullOrBlank()) {
            try {
                val t = timeFmt.parse(timeStr)
                val tc = Calendar.getInstance()
                tc.time = t
                cal.set(Calendar.HOUR_OF_DAY, tc.get(Calendar.HOUR_OF_DAY))
                cal.set(Calendar.MINUTE, tc.get(Calendar.MINUTE))
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
            } catch (_: Exception) {
                // ignore and leave date-only (midnight)
            }
        }
        return cal.time
    }

    fun updateData(newItems: List<RequestData>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
