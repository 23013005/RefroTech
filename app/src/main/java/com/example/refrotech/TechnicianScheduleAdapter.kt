package com.example.refrotech

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TechnicianScheduleAdapter(
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<TechnicianScheduleAdapter.VH>() {

    private var items: List<RequestData> = emptyList()

    fun submitList(list: List<RequestData>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_technician_job, parent, false)
        return VH(v, parent.context)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    inner class VH(itemView: View, private val ctx: Context) : RecyclerView.ViewHolder(itemView) {
        private val tvJobCustomer: TextView = itemView.findViewById(R.id.tvJobCustomer)
        private val tvJobAddress: TextView = itemView.findViewById(R.id.tvJobAddress)
        private val tvJobTime: TextView = itemView.findViewById(R.id.tvJobTime)

        fun bind(r: RequestData) {
            tvJobCustomer.text = r.name
            tvJobAddress.text = r.address
            tvJobTime.text = "${r.date} • ${r.time}"

            itemView.setOnClickListener { onClick(r.id) }

            // Optionally change appearance for completed jobs
            // Use jobStatus if present, otherwise fallback to status (older documents)
            val jobStatus = r.jobStatus ?: r.status ?: "scheduled"
            if (jobStatus.equals("completed", ignoreCase = true)) {
                itemView.alpha = 0.7f
            } else {
                itemView.alpha = 1f
            }
        }
    }
}
