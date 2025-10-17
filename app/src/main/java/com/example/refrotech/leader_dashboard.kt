package com.example.refrotech

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.prolificinteractive.materialcalendarview.MaterialCalendarView
import com.prolificinteractive.materialcalendarview.OnDateSelectedListener

class leader_dashboard : AppCompatActivity() {

    private lateinit var calendarView: MaterialCalendarView
    private lateinit var tvSelectedDate: TextView
    private lateinit var recyclerSchedules: RecyclerView
    private lateinit var fabAddSchedule: FloatingActionButton

    private lateinit var scheduleAdapter: ScheduleAdapter
    private lateinit var db: FirebaseFirestore

    // Track currently selected date and listener
    private var selectedDateStr: String? = null
    private var scheduleListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_leader_dashboard)

        // Initialize views
        calendarView = findViewById(R.id.calendarView)
        tvSelectedDate = findViewById(R.id.tvSelectedDate)
        recyclerSchedules = findViewById(R.id.recyclerSchedules)
        fabAddSchedule = findViewById(R.id.fabAddSchedule)

        recyclerSchedules.layoutManager = LinearLayoutManager(this)
        scheduleAdapter = ScheduleAdapter(this, emptyList())
        recyclerSchedules.adapter = scheduleAdapter

        db = FirebaseFirestore.getInstance()

        // ===== FAB: Add Schedule =====
        fabAddSchedule.setImageResource(R.drawable.ic_add)
        fabAddSchedule.setOnClickListener {
            if (selectedDateStr.isNullOrBlank()) {
                Toast.makeText(this, "Silakan pilih tanggal terlebih dahulu.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = Intent(this, AddSchedulePage::class.java)
            intent.putExtra("date", selectedDateStr)
            startActivity(intent)
        }

        // ===== Calendar interaction =====
        calendarView.setOnDateChangedListener(OnDateSelectedListener { _, date, _ ->
            val selectedDateDisplay = "${date.day}-${date.month}-${date.year}"
            tvSelectedDate.text = "Jadwal untuk $selectedDateDisplay"

            selectedDateStr = String.format("%04d-%02d-%02d", date.year, date.month + 1, date.day)
            listenToSchedulesForDate(selectedDateStr!!)
        })
    }

    // ===== Real-time Firestore Listener =====
    private fun listenToSchedulesForDate(dateStr: String) {
        // Stop any previous listener
        scheduleListener?.remove()

        scheduleListener = db.collection("schedules")
            .whereEqualTo("date", dateStr)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Toast.makeText(this, "Gagal memuat jadwal: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshots == null || snapshots.isEmpty) {
                    scheduleAdapter.updateData(emptyList())
                    Toast.makeText(this, "Tidak ada jadwal untuk tanggal ini.", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                val schedules = snapshots.map { doc ->
                    Schedule(
                        customerName = doc.getString("customerName") ?: "Tanpa Nama",
                        time = doc.getString("time") ?: "-",
                        technician = doc.getString("technician") ?: "-",
                        scheduleId = doc.id
                    )
                }

                scheduleAdapter.updateData(schedules)
            }
    }

    override fun onStop() {
        super.onStop()
        // Remove listener to prevent memory leaks
        scheduleListener?.remove()
        scheduleListener = null
    }

    override fun onResume() {
        super.onResume()
        // Reconnect listener when returning to dashboard
        selectedDateStr?.let { listenToSchedulesForDate(it) }
    }
}
