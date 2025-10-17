package com.example.refrotech

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ScheduleAdapter(
    private val context: Context,
    private var scheduleList: List<Schedule>
) : RecyclerView.Adapter<ScheduleAdapter.ScheduleViewHolder>() {

    class ScheduleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvCustomerName: TextView = itemView.findViewById(R.id.tvCustomerName)
        val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        val tvTechnician: TextView = itemView.findViewById(R.id.tvTechnician)
        val btnEdit: ImageView = itemView.findViewById(R.id.btnEditSchedule)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScheduleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_schedule, parent, false)
        return ScheduleViewHolder(view)
    }

    override fun onBindViewHolder(holder: ScheduleViewHolder, position: Int) {
        val schedule = scheduleList[position]
        holder.tvCustomerName.text = schedule.customerName
        holder.tvTime.text = "Waktu: ${schedule.time}"
        holder.tvTechnician.text = "Teknisi: ${schedule.technician}"

        holder.btnEdit.setOnClickListener {
            val intent = Intent(context, EditSchedulePage::class.java)
            intent.putExtra("scheduleId", schedule.scheduleId)
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = scheduleList.size

    fun updateData(newList: List<Schedule>) {
        scheduleList = newList
        notifyDataSetChanged()
    }
}
