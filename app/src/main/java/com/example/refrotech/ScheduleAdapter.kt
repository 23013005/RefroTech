package com.example.refrotech

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(ctx).inflate(R.layout.item_schedule, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val it = items[position]
        holder.tvCustomer.text = it.customerName
        holder.tvTime.text = "${it.date} • ${it.time}"
        holder.tvAddress.text = it.address
        holder.tvTechs.text = it.technicians
        // You can add click listeners here to open details etc.
    }

    fun updateData(newItems: List<Schedule>) {
        items = newItems
        notifyDataSetChanged()
    }
}
