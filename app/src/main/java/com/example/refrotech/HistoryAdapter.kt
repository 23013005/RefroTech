package com.example.refrotech

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class HistoryAdapter(
    private val context: Context,
    private val requests: MutableList<RequestData>
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    private val db = FirebaseFirestore.getInstance()

    inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val root = itemView.findViewById<View>(R.id.itemHistoryRoot)
        val tvName = itemView.findViewById<android.widget.TextView>(R.id.tvHistName)
        val tvStatus = itemView.findViewById<android.widget.TextView>(R.id.tvHistStatus)
        val tvAddress = itemView.findViewById<android.widget.TextView>(R.id.tvHistAddress)
        val tvDate = itemView.findViewById<android.widget.TextView>(R.id.tvHistDate)
        val tvTime = itemView.findViewById<android.widget.TextView>(R.id.tvHistTime)
        val tvUnits = itemView.findViewById<android.widget.TextView>(R.id.tvHistUnits)

        val btnEdit = itemView.findViewById<FrameLayout>(R.id.btnEditRequest)
        val btnChange = itemView.findViewById<FrameLayout>(R.id.btnChangeSchedule)
        val btnCancel = itemView.findViewById<FrameLayout>(R.id.btnCancelRequest)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_history_request, parent, false)
        return HistoryViewHolder(v)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val req = requests[position]

        holder.tvName.text = req.name.ifEmpty { "Unknown" }
        holder.tvStatus.text = req.status.ifEmpty { "pending" }
        holder.tvAddress.text = req.address
        holder.tvDate.text = req.date
        holder.tvTime.text = req.time
        holder.tvUnits.text = "${req.units.size} unit(s)"

        // Apply status style (you can adapt drawable names)
        when (req.status.lowercase(Locale.getDefault())) {
            "pending" -> holder.tvStatus.setBackgroundResource(R.drawable.button_light_green)
            "confirmed" -> holder.tvStatus.setBackgroundResource(R.drawable.calender_background)
            "rejected" -> holder.tvStatus.setBackgroundResource(R.drawable.delete_button)
            "reschedule-pending" -> holder.tvStatus.setBackgroundResource(R.drawable.calender_background)
            else -> holder.tvStatus.setBackgroundResource(R.drawable.dialog_background)
        }

        // Disable Edit/Change if appointment < 24 hours away
        val canEdit = TimeUtils.canEditAppointment(req.date, req.time)
        holder.btnEdit.isEnabled = canEdit
        holder.btnChange.isEnabled = canEdit
        holder.btnEdit.alpha = if (canEdit) 1f else 0.4f
        holder.btnChange.alpha = if (canEdit) 1f else 0.4f

        // Edit -> open EditCustomerRequest
        holder.btnEdit.setOnClickListener {
            if (!canEdit) {
                Toast.makeText(context, "Cannot edit within 24 hours", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val statusLower = req.status.lowercase(Locale.getDefault())
            if (statusLower == "confirmed") {
                Toast.makeText(context, "Request already confirmed. Use reschedule if needed.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(context, EditCustomerRequest::class.java)
            intent.putExtra("requestId", req.id)
            context.startActivity(intent)
        }

        // Change schedule / request reschedule
        holder.btnChange.setOnClickListener {
            if (!canEdit) {
                Toast.makeText(context, "Cannot reschedule within 24 hours", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Mark as reschedule-pending and keep oldDate/oldTime
            val updates = mapOf(
                "status" to "reschedule-pending",
                "oldDate" to req.date,
                "oldTime" to req.time
            )
            db.collection("requests").document(req.id)
                .update(updates)
                .addOnSuccessListener {
                    Toast.makeText(context, "Reschedule requested", Toast.LENGTH_SHORT).show()
                    // update local model & UI
                    requests[position] = requests[position].copy(status = "reschedule-pending")
                    notifyItemChanged(position)
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "Failed to request reschedule: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        // Cancel request
        holder.btnCancel.setOnClickListener {
            AlertDialog.Builder(context)
                .setTitle("Cancel Request")
                .setMessage("Are you sure you want to cancel this request?")
                .setPositiveButton("Yes") { _, _ ->
                    db.collection("requests").document(req.id)
                        .update(mapOf("status" to "rejected"))
                        .addOnSuccessListener {
                            Toast.makeText(context, "Request cancelled", Toast.LENGTH_SHORT).show()
                            requests.removeAt(position)
                            notifyItemRemoved(position)
                            notifyItemRangeChanged(position, requests.size)
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(context, "Failed to cancel: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
                .setNegativeButton("No", null)
                .show()
        }
    }

    override fun getItemCount(): Int = requests.size
}
