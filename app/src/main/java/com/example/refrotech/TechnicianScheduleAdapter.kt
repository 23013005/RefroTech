package com.example.refrotech

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TechnicianScheduleAdapter(
    private val ctx: Context,
    private var items: List<Schedule>
) : RecyclerView.Adapter<TechnicianScheduleAdapter.VH>() {

    var onItemClick: ((Schedule) -> Unit)? = null

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvCustomer: TextView = itemView.findViewById(R.id.tvScheduleCustomer)
        val tvTime: TextView = itemView.findViewById(R.id.tvScheduleTime)
        val tvTechs: TextView = itemView.findViewById(R.id.tvScheduleTechnicians)
        val tvAddress: TextView = itemView.findViewById(R.id.tvScheduleAddress)
        val tvStatus: TextView = itemView.findViewById(R.id.tvScheduleStatus)
        val btnEdit: ImageView? = itemView.findViewById(R.id.btnEditSchedule)

        init {
            // Technician should NEVER see or use edit button
            btnEdit?.visibility = View.GONE
            btnEdit?.isEnabled = false
            btnEdit?.isClickable = false

            itemView.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onItemClick?.invoke(items[pos])
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_schedule, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = items[position]
        holder.tvCustomer.text = s.customerName.ifBlank { "No name" }
        holder.tvTime.text = "${s.date} • ${s.time}"
        holder.tvAddress.text = s.address
        holder.tvTechs.text = s.technicians.joinToString(", ")

        // Consistent status formatting
        val statusText = when (s.normalizedStatus.lowercase()) {
            "completed" -> "Completed"
            "on-progress" -> "On-Progress"
            "confirmed" -> "Confirmed"
            else -> s.normalizedStatus.replaceFirstChar { it.uppercase() }
        }
        holder.tvStatus.text = "Status: $statusText"
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<Schedule>) {
        items = newItems
        notifyDataSetChanged()
    }
}
