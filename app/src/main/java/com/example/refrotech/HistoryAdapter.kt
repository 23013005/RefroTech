package com.example.refrotech

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

class HistoryAdapter(private var items: MutableList<RequestData>) :
    RecyclerView.Adapter<HistoryAdapter.VH>() {

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvHistName)
        val tvStatus: TextView = itemView.findViewById(R.id.tvHistStatus)
        val tvAddress: TextView = itemView.findViewById(R.id.tvHistAddress)
        val tvDate: TextView = itemView.findViewById(R.id.tvHistDate)
        val tvTime: TextView = itemView.findViewById(R.id.tvHistTime)
        val btnEdit: Button? = itemView.findViewById(R.id.btnEditRequest)
        val btnChange: Button? = itemView.findViewById(R.id.btnChangeSchedule)
        val btnCancel: Button? = itemView.findViewById(R.id.btnCancelRequest)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_history_request, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val it = items[position]
        holder.tvName.text = it.name
        holder.tvStatus.text = it.status
        holder.tvAddress.text = it.address
        holder.tvDate.text = it.date
        holder.tvTime.text = it.time

        holder.btnEdit?.setOnClickListener {
            val ctx = holder.itemView.context
            val i = Intent(ctx, EditCustomerRequest::class.java)
            i.putExtra("requestId", it.id)
            ctx.startActivity(i)
        }

        holder.btnChange?.setOnClickListener {
            val ctx = holder.itemView.context
            val i = Intent(ctx, EditSchedulePage::class.java)
            i.putExtra("requestId", it.id)
            ctx.startActivity(i)
        }

        holder.btnCancel?.setOnClickListener {
            Toast.makeText(holder.itemView.context, "Cancel belum diimplementasi", Toast.LENGTH_SHORT).show()
        }
    }

    fun updateData(newItems: List<RequestData>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
