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
import com.prolificinteractive.materialcalendarview.MaterialCalendarView
import com.prolificinteractive.materialcalendarview.OnDateSelectedListener

class leader_dashboard : AppCompatActivity() {

    private lateinit var calendarView: MaterialCalendarView
    private lateinit var tvSelectedDate: TextView
    private lateinit var recyclerSchedules: RecyclerView
    private lateinit var fabAddSchedule: FloatingActionButton

    private lateinit var scheduleAdapter: ScheduleAdapter
    private val allSchedules = mutableMapOf<String, MutableList<Schedule>>() // date -> schedules
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_leader_dashboard)

        // Initialize views
        calendarView = findViewById(R.id.calendarView)
        tvSelectedDate = findViewById(R.id.tvSelectedDate)
        recyclerSchedules = findViewById(R.id.recyclerSchedules)
        fabAddSchedule = findViewById(R.id.fabAddSchedule)

        recyclerSchedules.layoutManager = LinearLayoutManager(this)
        scheduleAdapter = ScheduleAdapter()
        recyclerSchedules.adapter = scheduleAdapter

        db = FirebaseFirestore.getInstance()

        // Temporary dummy data (for testing)
        seedExampleSchedules()

        // Calendar interaction
        calendarView.setOnDateChangedListener(OnDateSelectedListener { _, date, _ ->
            val selectedDate = "${date.day}-${date.month}-${date.year}"
            tvSelectedDate.text = "Jadwal untuk $selectedDate"

            val schedules = allSchedules[selectedDate] ?: emptyList()
            scheduleAdapter.updateData(schedules)

            if (schedules.isEmpty()) {
                Toast.makeText(this, "Tidak ada jadwal untuk tanggal ini.", Toast.LENGTH_SHORT).show()
            }

            // Firestore check to toggle Add/Edit button
            val formattedDate = String.format("%04d-%02d-%02d", date.year, date.month + 1, date.day)
            checkScheduleForDate(formattedDate)
        })

        // Default check for today's date
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        checkScheduleForDate(today)
    }

    private fun seedExampleSchedules() {
        allSchedules["25-10-2025"] = mutableListOf(
            Schedule("Bradley Ganteng", "13:00", "Agus, Span"),
            Schedule("Regen Ganteng", "16:00", "Agus, Span")
        )
        allSchedules["26-10-2025"] = mutableListOf(
            Schedule("Hadi Pratama", "10:00", "Span, Budi")
        )
    }

    // Firestore checker for schedule Add/Edit
    private fun checkScheduleForDate(dateStr: String) {
        db.collection("schedules")
            .whereEqualTo("date", dateStr)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    // No schedule yet → Add mode
                    fabAddSchedule.setImageResource(R.drawable.ic_add)
                    fabAddSchedule.setOnClickListener {
                        val intent = Intent(this, AddSchedulePage::class.java)
                        intent.putExtra("date", dateStr)
                        startActivity(intent)
                    }
                } else {
                    // Schedule exists → Edit mode (for now uses the first found)
                    val doc = documents.documents[0]
                    val scheduleId = doc.id
                    fabAddSchedule.setImageResource(R.drawable.ic_edit)
                    fabAddSchedule.setOnClickListener {
                        val intent = Intent(this, EditSchedulePage::class.java)
                        intent.putExtra("scheduleId", scheduleId)
                        startActivity(intent)
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal memeriksa jadwal: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
