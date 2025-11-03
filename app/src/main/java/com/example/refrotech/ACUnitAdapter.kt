package com.example.refrotech

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

class ACUnitAdapter(private val acUnits: MutableList<ACUnit>) :
    RecyclerView.Adapter<ACUnitAdapter.ACUnitViewHolder>() {

    class ACUnitViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvMerk: TextView = itemView.findViewById(R.id.tvMerkAC)
        val tvPK: TextView = itemView.findViewById(R.id.tvJumlahPK)
        val tvWork: TextView = itemView.findViewById(R.id.tvJenisPekerjaan)
        val btnDelete: ImageView = itemView.findViewById(R.id.btnDeleteUnit)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ACUnitViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ac_unit, parent, false)
        return ACUnitViewHolder(view)
    }

    override fun onBindViewHolder(holder: ACUnitViewHolder, position: Int) {
        val item = acUnits[position]
        holder.tvMerk.text = "Merk AC: ${item.brand}"
        holder.tvPK.text = "Jumlah PK: ${item.pk}"
        holder.tvWork.text = "Jenis Pekerjaan: ${item.workType}"

        holder.btnDelete.setOnClickListener {
            val removed = acUnits.removeAt(holder.adapterPosition)
            notifyItemRemoved(holder.adapterPosition)
            Toast.makeText(
                holder.itemView.context,
                "Unit ${removed.brand} dihapus",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun getItemCount(): Int = acUnits.size

    fun add(item: ACUnit) {
        acUnits.add(item)
        notifyItemInserted(acUnits.size - 1)
    }

    fun clearAll() {
        acUnits.clear()
        notifyDataSetChanged()
    }
}
