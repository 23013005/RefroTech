package com.example.refrotech

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*


class HistoryAdapter(private val requests: List<RequestData>, private val context: Context) :
    RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    private val db = FirebaseFirestore.getInstance()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history_request, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val req = requests[position]

        holder.tvRequestAddress.text = "Alamat: ${req.address}"
        holder.tvRequestDate.text = "Tanggal: ${req.date}"
        holder.tvRequestTime.text = "Jam: ${req.time}"
        holder.tvRequestStatus.text = req.status
        holder.tvRequestStatus.setBackgroundColor(getStatusColor(req.status))

        // ===== Show buttons depending on status =====
        when (req.status.lowercase(Locale.getDefault())) {
            "pending" -> {
                holder.btnCancel.visibility = View.VISIBLE
                holder.btnChangeSchedule.visibility = View.GONE
            }
            "confirmed" -> {
                holder.btnCancel.visibility = View.VISIBLE
                holder.btnChangeSchedule.visibility = View.VISIBLE
            }
            "in progress", "completed", "canceled" -> {
                holder.btnCancel.visibility = View.GONE
                holder.btnChangeSchedule.visibility = View.GONE
            }
            "cancellation requested" -> {
                holder.btnCancel.visibility = View.GONE
                holder.btnChangeSchedule.visibility = View.GONE
            }
            else -> {
                holder.btnCancel.visibility = View.GONE
                holder.btnChangeSchedule.visibility = View.GONE
            }
        }

        // ===== Cancel Request =====
        holder.btnCancel.setOnClickListener {
            db.collection("requests").document(req.id)
                .update("status", "Cancellation Requested")
                .addOnSuccessListener {
                    Toast.makeText(context, "Permintaan pembatalan dikirim.", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Gagal mengirim pembatalan.", Toast.LENGTH_SHORT).show()
                }
        }

        // ===== Reschedule Request =====
        holder.btnChangeSchedule.setOnClickListener {
            showRescheduleDialog(req.id)
        }
    }

    override fun getItemCount(): Int = requests.size

    private fun showRescheduleDialog(requestId: String) {
        val cal = Calendar.getInstance()
        val datePicker = DatePickerDialog(
            context,
            { _, year, month, day ->
                val selectedDate = Calendar.getInstance()
                selectedDate.set(year, month, day)

                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val formattedDate = sdf.format(selectedDate.time)

                db.collection("requests").document(requestId)
                    .update("date", formattedDate)
                    .addOnSuccessListener {
                        Toast.makeText(context, "Jadwal berhasil diperbarui!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "Gagal memperbarui jadwal.", Toast.LENGTH_SHORT).show()
                    }
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.datePicker.minDate = System.currentTimeMillis() + 86400000 // tomorrow
        datePicker.show()
    }

    private fun getStatusColor(status: String): Int {
        return when (status.lowercase(Locale.getDefault())) {
            "pending" -> Color.parseColor("#FFD54F") // amber
            "confirmed" -> Color.parseColor("#81C784") // green
            "in progress" -> Color.parseColor("#64B5F6") // blue
            "completed" -> Color.parseColor("#AED581") // light green
            "canceled" -> Color.parseColor("#E57373") // red
            "cancellation requested" -> Color.parseColor("#FFB74D") // orange
            else -> Color.parseColor("#BDBDBD") // gray
        }
    }

    class HistoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvRequestAddress: TextView = view.findViewById(R.id.tvRequestAddress)
        val tvRequestDate: TextView = view.findViewById(R.id.tvRequestDate)
        val tvRequestTime: TextView = view.findViewById(R.id.tvRequestTime)
        val tvRequestStatus: TextView = view.findViewById(R.id.tvRequestStatus)
        val btnCancel: Button = view.findViewById(R.id.btnCancel)
        val btnChangeSchedule: Button = view.findViewById(R.id.btnChangeSchedule)
    }
}
