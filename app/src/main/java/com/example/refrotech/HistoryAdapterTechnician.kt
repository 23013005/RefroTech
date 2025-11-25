package com.example.refrotech

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class HistoryAdapterTechnician(
    private var items: MutableList<Schedule>
) : RecyclerView.Adapter<HistoryAdapterTechnician.VH>() {

    var onItemClick: ((Schedule) -> Unit)? = null

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvCustomer: TextView = itemView.findViewById(R.id.tvJobCustomer)
        val tvAddress: TextView = itemView.findViewById(R.id.tvJobAddress)
        val tvTime: TextView = itemView.findViewById(R.id.tvJobTime)

        init {
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
            .inflate(R.layout.item_technician_job, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val it = items[position]

        holder.tvCustomer.text = it.customerName
        holder.tvAddress.text = it.address
        holder.tvTime.text = "${it.date} • ${it.time}"
    }

    fun updateData(newList: List<Schedule>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }
}
