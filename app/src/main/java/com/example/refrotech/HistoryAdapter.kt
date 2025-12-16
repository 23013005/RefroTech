package com.example.refrotech

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
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
    private val displayFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    // =========================
    // SLOT MODEL (SAME AS OTHERS)
    // =========================
    private data class TimeSlot(val label: String, val startTime: String)

    private val timeSlots = listOf(
        TimeSlot("08:00 - 09:00", "08:00"),
        TimeSlot("09:00 - 10:00", "09:00"),
        TimeSlot("10:00 - 11:00", "10:00"),
        TimeSlot("12:00 - 13:00", "12:00"),
        TimeSlot("13:00 - 14:00", "13:00"),
        TimeSlot("14:00 - 15:00", "14:00"),
        TimeSlot("15:00 - 16:00", "15:00"),
        TimeSlot("16:00 - 17:00", "16:00")
    )

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
        val btnRate: FrameLayout = itemView.findViewById(R.id.btnRateUs)
        val btnWorkReport: FrameLayout = itemView.findViewById(R.id.btnWorkReport)
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

        holder.tvStatus.text = it.normalizedStatus.replaceFirstChar { c -> c.uppercase() }

        holder.btnChange.isEnabled = it.normalizedStatus == "confirmed"
        holder.btnChange.alpha = if (holder.btnChange.isEnabled) 1f else 0.4f

        holder.btnChange.setOnClickListener { v ->
            showRestrictedDatePicker(
                v.context,
                it.id,
                it.date,
                it.time
            )
        }
    }

    // ======================================================
    // DATE PICKER — 7 DAYS, NO SUNDAY
    // ======================================================
    private fun showRestrictedDatePicker(
        ctx: Context,
        requestId: String,
        oldDate: String?,
        oldTime: String?
    ) {
        val allowedDates = buildAllowedDates()
        val first = allowedDates.first()
        val last = allowedDates.last()

        val dialog = DatePickerDialog(
            ctx,
            { _, y, m, d ->
                val selected = Calendar.getInstance().apply {
                    set(y, m, d, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                if (!allowedDates.any { sameDay(it, selected) }) {
                    Toast.makeText(ctx, "Tanggal tidak valid.", Toast.LENGTH_SHORT).show()
                    return@DatePickerDialog
                }

                val isoDate = isoFormat.format(selected.time)
                fetchBlockedTimesForReschedule(
                    isoDate,
                    requestId,
                    oldDate,
                    oldTime
                ) { blocked ->
                    showTimeSlotDialog(
                        ctx,
                        requestId,
                        isoDate,
                        oldDate,
                        oldTime,
                        blocked
                    )
                }
            },
            first.get(Calendar.YEAR),
            first.get(Calendar.MONTH),
            first.get(Calendar.DAY_OF_MONTH)
        )

        dialog.datePicker.minDate = first.timeInMillis
        dialog.datePicker.maxDate = last.timeInMillis
        dialog.show()
    }

    private fun buildAllowedDates(): List<Calendar> {
        val result = mutableListOf<Calendar>()
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, 1)

        while (result.size < 7) {
            if (cal.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
                result.add(cal.clone() as Calendar)
            }
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return result
    }

    private fun sameDay(a: Calendar, b: Calendar): Boolean {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                a.get(Calendar.MONTH) == b.get(Calendar.MONTH) &&
                a.get(Calendar.DAY_OF_MONTH) == b.get(Calendar.DAY_OF_MONTH)
    }

    // ======================================================
    // TIME SLOT PICKER WITH BLOCKING
    // ======================================================
    private fun showTimeSlotDialog(
        ctx: Context,
        requestId: String,
        newDate: String,
        oldDate: String?,
        oldTime: String?,
        blockedTimes: Set<String>
    ) {
        val labels = timeSlots.map { it.label }

        val adapter = object :
            ArrayAdapter<String>(ctx, android.R.layout.simple_list_item_1, labels) {

            override fun isEnabled(position: Int): Boolean {
                return !blockedTimes.contains(timeSlots[position].startTime)
            }

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                val blocked = blockedTimes.contains(timeSlots[position].startTime)
                view.isEnabled = !blocked
                view.setTextColor(
                    if (blocked) ctx.resources.getColor(android.R.color.darker_gray)
                    else ctx.resources.getColor(android.R.color.black)
                )
                return view
            }
        }

        AlertDialog.Builder(ctx)
            .setTitle("Pilih Waktu")
            .setAdapter(adapter) { d, which ->
                val slot = timeSlots[which]
                submitReschedule(
                    requestId,
                    oldDate,
                    oldTime,
                    newDate,
                    slot.startTime,
                    ctx
                )
                d.dismiss()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ======================================================
    // GLOBAL SLOT BLOCKING
    // ======================================================
    private fun fetchBlockedTimesForReschedule(
        date: String,
        requestId: String,
        oldDate: String?,
        oldTime: String?,
        onResult: (Set<String>) -> Unit
    ) {
        val blocked = mutableSetOf<String>()
        var remaining = 3
        fun done() { if (--remaining == 0) onResult(blocked) }

        db.collection(FirestoreFields.SCHEDULES)
            .whereEqualTo("date", date)
            .get()
            .addOnSuccessListener {
                it.documents.forEach { d ->
                    d.getString("time")?.let(blocked::add)
                }
                done()
            }.addOnFailureListener { done() }

        db.collection(FirestoreFields.REQUESTS)
            .whereEqualTo("date", date)
            .get()
            .addOnSuccessListener {
                it.documents.forEach { d ->
                    if (d.id == requestId &&
                        date == oldDate &&
                        d.getString("time") == oldTime
                    ) return@forEach

                    val status = d.getString("status") ?: ""
                    if (status in listOf("confirmed", "assigned")) {
                        d.getString("time")?.let(blocked::add)
                    }
                }
                done()
            }.addOnFailureListener { done() }

        db.collection(FirestoreFields.REQUESTS)
            .whereEqualTo("newDate", date)
            .get()
            .addOnSuccessListener {
                it.documents.forEach { d ->
                    if (d.id == requestId) return@forEach
                    if (d.getString("rescheduleStatus") == "accepted") {
                        d.getString("newTime")?.let(blocked::add)
                    }
                }
                done()
            }.addOnFailureListener { done() }
    }

    private fun submitReschedule(
        requestId: String,
        oldDate: String?,
        oldTime: String?,
        newDate: String,
        newTime: String,
        ctx: Context
    ) {
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
                Toast.makeText(
                    ctx,
                    "Permintaan Penjadwalan Ulang Dikirim!. Menunggu Konfirmasi Pemilik.",
                    Toast.LENGTH_LONG
                ).show()

                // Force visual confirmation by reloading CustomerHistory
                val intent = Intent(ctx, CustomerHistory::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(intent)
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    ctx,
                    "Failed to request reschedule: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }


    fun updateData(newItems: List<HistoryItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
