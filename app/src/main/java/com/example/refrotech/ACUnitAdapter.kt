package com.example.refrotech

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ACUnitAdapter(
    private val items: MutableList<ACUnit>,
    private val onItemClick: ((Int) -> Unit)? = null
) : RecyclerView.Adapter<ACUnitAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMerk: TextView = view.findViewById(R.id.tvMerkAC)
        val tvPK: TextView = view.findViewById(R.id.tvJumlahPK)
        val tvWork: TextView = view.findViewById(R.id.tvJenisPekerjaan)
        val tvDescription: TextView = view.findViewById(R.id.tvDescription)
        val tvNumber: TextView = view.findViewById(R.id.tvUnitNumber)

        init {
            view.setOnClickListener {
                onItemClick?.invoke(adapterPosition)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ac_unit, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.tvMerk.text = "Merk AC: ${item.brand}"
        holder.tvPK.text = "Jumlah PK: ${item.pk}"
        holder.tvWork.text = "Jenis Pekerjaan: ${item.workType}"

        // ✅ DESCRIPTION HANDLING (SAFE)
        if (item.description.isNotBlank()) {
            holder.tvDescription.visibility = View.VISIBLE
            holder.tvDescription.text = "Deskripsi: ${item.description}"
        } else {
            holder.tvDescription.visibility = View.GONE
        }

        holder.tvNumber.text = "#${position + 1}"
    }

    override fun getItemCount(): Int = items.size
}
