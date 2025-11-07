package com.example.refrotech

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TechnicianManageAdapter(
    private val technicians: MutableList<Technician>,
    private val onEditClick: (Technician) -> Unit,
    private val onDeleteClick: (Technician) -> Unit
) : RecyclerView.Adapter<TechnicianManageAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.tvTechnicianName)
        val usernameText: TextView = view.findViewById(R.id.tvTechnicianUsername)
        val passwordText: TextView = view.findViewById(R.id.tvTechnicianPassword)
        val btnEdit: FrameLayout = view.findViewById(R.id.btnEditTech)
        val btnDelete: FrameLayout = view.findViewById(R.id.btnDeleteTech)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_technician_manage, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val technician = technicians[position]
        holder.nameText.text = technician.name
        holder.usernameText.text = technician.username
        holder.passwordText.text = technician.password // visible password for leader

        holder.btnEdit.setOnClickListener { onEditClick(technician) }
        holder.btnDelete.setOnClickListener { onDeleteClick(technician) }
    }

    override fun getItemCount(): Int = technicians.size
}
