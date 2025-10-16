package com.example.refrotech

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ScheduleAdapter : RecyclerView.Adapter<ScheduleAdapter.ViewHolder>() {

    private val scheduleList = mutableListOf<Schedule>()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNamaPemesan: TextView = view.findViewById(R.id.tvNamaPemesan)
        val tvJam: TextView = view.findViewById(R.id.tvJam)
        val tvTeknisi: TextView = view.findViewById(R.id.tvTeknisi)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_schedule, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val schedule = scheduleList[position]
        holder.tvNamaPemesan.text = "Nama Pemesan: ${schedule.namaPemesan}"
        holder.tvJam.text = "Jam: ${schedule.jam}"
        holder.tvTeknisi.text = "Teknisi: ${schedule.teknisi}"
    }

    override fun getItemCount(): Int = scheduleList.size

    fun updateData(newList: List<Schedule>) {
        scheduleList.clear()
        scheduleList.addAll(newList)
        notifyDataSetChanged()
    }
}
