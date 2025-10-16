package com.example.refrotech

import android.os.Bundle
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.prolificinteractive.materialcalendarview.CalendarDay
import com.prolificinteractive.materialcalendarview.MaterialCalendarView
import com.prolificinteractive.materialcalendarview.OnDateSelectedListener

class leader_dashboard : AppCompatActivity() {

    private lateinit var calendarView: MaterialCalendarView
    private lateinit var tvSelectedDate: TextView
    private lateinit var recyclerSchedules: RecyclerView
    private lateinit var addButton: FrameLayout

    private lateinit var scheduleAdapter: ScheduleAdapter
    private val allSchedules = mutableMapOf<String, MutableList<Schedule>>() // date -> schedules

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_leader_dashboard)

        // Initialize views
        calendarView = findViewById(R.id.calendarView)
        tvSelectedDate = findViewById(R.id.tvSelectedDate)
        recyclerSchedules = findViewById(R.id.recyclerSchedules)
        addButton = findViewById(R.id.btnAddSchedule)

        recyclerSchedules.layoutManager = LinearLayoutManager(this)
        scheduleAdapter = ScheduleAdapter()
        recyclerSchedules.adapter = scheduleAdapter

        // Temporary data (for testing)
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
        })

        addButton.setOnClickListener {
            Toast.makeText(this, "Fitur tambah jadwal akan datang!", Toast.LENGTH_SHORT).show()
        }
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
}
