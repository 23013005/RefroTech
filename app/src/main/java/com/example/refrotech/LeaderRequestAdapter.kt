package com.example.refrotech

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class LeaderRequestAdapter(
    private val context: Context,
    private var requests: MutableList<RequestData>
) : RecyclerView.Adapter<LeaderRequestAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvCustomerName: TextView = itemView.findViewById(R.id.tvCustomerName)
        val tvDateTime: TextView = itemView.findViewById(R.id.tvDateTime)
        val tvStatusBadge: TextView = itemView.findViewById(R.id.tvStatusBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.item_leader_request, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = requests.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val req = requests[position]

        holder.tvCustomerName.text = req.name
        holder.tvDateTime.text = "${req.date} • ${req.time}"
        holder.tvStatusBadge.text = req.status

        when (req.status.lowercase()) {
            "pending" -> holder.tvStatusBadge.setBackgroundResource(R.drawable.button_light_green)
            "confirmed" -> holder.tvStatusBadge.setBackgroundResource(R.drawable.button_light_green)
            "rejected" -> holder.tvStatusBadge.setBackgroundResource(R.drawable.delete_button)
            "reschedule-pending" -> holder.tvStatusBadge.setBackgroundResource(R.drawable.calender_background)
            "reschedule" -> holder.tvStatusBadge.setBackgroundResource(R.drawable.calender_background)
            else -> holder.tvStatusBadge.setBackgroundResource(R.drawable.dialog_background)
        }

        holder.itemView.setOnClickListener {
            val intent = if (req.status.lowercase().contains("reschedule")) {
                Intent(context, LeaderRescheduleDetailActivity::class.java)
            } else {
                Intent(context, LeaderNewRequestDetailActivity::class.java)
            }

            intent.putExtra("requestId", req.id)
            context.startActivity(intent)
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
