package com.example.refrotech

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * LeaderRequestAdapter:
 * - Holds list of RequestData
 * - Exposes onItemClick callback to Activity (Activity decides navigation)
 */
class LeaderRequestAdapter(
    private var requests: MutableList<RequestData>,
    private val onItemClick: (RequestData) -> Unit
) : RecyclerView.Adapter<LeaderRequestAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvCustomerName: TextView = itemView.findViewById(R.id.tvCustomerName)
        val tvDateTime: TextView = itemView.findViewById(R.id.tvDateTime)
        val tvStatusBadge: TextView = itemView.findViewById(R.id.tvStatusBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_leader_request, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = requests.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val req = requests[position]

        holder.tvCustomerName.text = req.name ?: ""
        holder.tvDateTime.text = "${req.date ?: "-"} • ${req.time ?: "-"}"
        holder.tvStatusBadge.text = req.status ?: ""

        when ((req.status ?: "").lowercase()) {
            "pending" -> holder.tvStatusBadge.setBackgroundResource(R.drawable.button_light_green)
            "confirmed", "accepted" -> holder.tvStatusBadge.setBackgroundResource(R.drawable.button_light_green)
            "rejected" -> holder.tvStatusBadge.setBackgroundResource(R.drawable.delete_button)
            "reschedule-pending", "reschedule", "reschedule-request", "reschedule_requested" ->
                holder.tvStatusBadge.setBackgroundResource(R.drawable.calender_background)
            else -> holder.tvStatusBadge.setBackgroundResource(R.drawable.dialog_background)
        }

        holder.itemView.setOnClickListener {
            onItemClick(req)
        }
    }

    /**
     * Replace adapter data (safe) and notify.
     */
    fun updateData(newList: List<RequestData>) {
        requests.clear()
        requests.addAll(newList)
        notifyDataSetChanged()
    }
}
