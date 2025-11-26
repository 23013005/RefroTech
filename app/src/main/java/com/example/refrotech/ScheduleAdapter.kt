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
    private val ctx: Context,
    private var items: List<Schedule>
) : RecyclerView.Adapter<ScheduleAdapter.VH>() {

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvCustomer: TextView = itemView.findViewById(R.id.tvScheduleCustomer)
        val tvTime: TextView = itemView.findViewById(R.id.tvScheduleTime)
        val tvTechs: TextView = itemView.findViewById(R.id.tvScheduleTechnicians)
        val tvAddress: TextView = itemView.findViewById(R.id.tvScheduleAddress)
        val tvStatus: TextView = itemView.findViewById(R.id.tvScheduleStatus)
        val btnEdit: ImageView = itemView.findViewById(R.id.btnEditSchedule)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_schedule, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvCustomer.text = item.customerName.ifBlank { "No name" }
        holder.tvTime.text = "${item.date} • ${item.time}"
        holder.tvAddress.text = item.address
        holder.tvTechs.text = item.technicians.joinToString(", ")

        // Display the normalized status for consistent UI
        val statusText = when (item.normalizedStatus.lowercase()) {
            "completed" -> "Completed"
            "on-progress" -> "On-Progress"
            "confirmed" -> "Confirmed"
            "rejected" -> "Rejected"
            else -> item.normalizedStatus.replaceFirstChar { it.uppercase() }
        }
        holder.tvStatus.text = "Status: $statusText"

        holder.btnEdit.setOnClickListener {
            val intent = Intent(ctx, EditSchedulePage::class.java)
            intent.putExtra("scheduleId", item.scheduleId)
            intent.putExtra("date", item.date)
            // also pass origin so EditSchedulePage can load documentation from requests if needed
            intent.putExtra("origin", item.origin)
            ctx.startActivity(intent)
        }

        holder.itemView.setOnClickListener {
            // if origin == "request" AND the request is still pending -> open LeaderNewRequestDetailActivity
            // otherwise open EditSchedulePage (for confirmed requests and for schedules)
            val isPendingRequest = item.origin == "request" && item.normalizedStatus == "pending"
            val intent = if (isPendingRequest) {
                Intent(ctx, LeaderNewRequestDetailActivity::class.java).apply {
                    putExtra("requestId", item.requestId)
                }
            } else {
                Intent(ctx, EditSchedulePage::class.java).apply {
                    putExtra("scheduleId", item.scheduleId)
                    putExtra("date", item.date)
                    putExtra("origin", item.origin) // important so EditSchedulePage loads docs from right collection
                }
            }
            ctx.startActivity(intent)
        }
    }

    fun updateData(newItems: List<Schedule>) {
        items = newItems
        notifyDataSetChanged()
    }
}
