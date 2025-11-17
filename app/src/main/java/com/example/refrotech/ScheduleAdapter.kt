package com.example.refrotech

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore

class ScheduleAdapter(
    private val context: Context,
    private var scheduleList: List<Schedule>
) : RecyclerView.Adapter<ScheduleAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvCustomerName: TextView = itemView.findViewById(R.id.tvCustomerName)
        val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        val tvTechnician: TextView = itemView.findViewById(R.id.tvTechnician)

        // EDIT BUTTON from XML (ImageView)
        val btnEdit: ImageView = itemView.findViewById(R.id.btnEditSchedule)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.item_schedule, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = scheduleList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val schedule = scheduleList[position]

        holder.tvCustomerName.text = schedule.customerName
        holder.tvTime.text = schedule.time
        holder.tvTechnician.text = schedule.technician

        // === EDIT BUTTON ===
        holder.btnEdit.setOnClickListener {
            val intent = Intent(context, EditSchedulePage::class.java)
            intent.putExtra("scheduleId", schedule.scheduleId)
            context.startActivity(intent)
        }

        // There is NO delete button in your XML.
        // So NO delete functionality here.
    }

    // Used by Leader Dashboard to refresh list
    fun updateData(newList: List<Schedule>) {
        scheduleList = newList
        notifyDataSetChanged()
    }
}
