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

        holder.btnEdit.setOnClickListener {
            val intent = Intent(ctx, EditSchedulePage::class.java)
            intent.putExtra("scheduleId", item.scheduleId)
            intent.putExtra("date", item.date)
            ctx.startActivity(intent)
        }

        holder.itemView.setOnClickListener {
            val intent = if (item.origin == "request") {
                Intent(ctx, LeaderNewRequestDetailActivity::class.java).apply {
                    putExtra("requestId", item.requestId)
                }
            } else {
                Intent(ctx, EditSchedulePage::class.java).apply {
                    putExtra("scheduleId", item.scheduleId)
                    putExtra("date", item.date)
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
