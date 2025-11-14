package com.example.refrotech

import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.prolificinteractive.materialcalendarview.MaterialCalendarView
import com.prolificinteractive.materialcalendarview.OnDateSelectedListener

class leader_dashboard : AppCompatActivity() {

    private lateinit var calendarView: MaterialCalendarView
    private lateinit var tvSelectedDate: TextView
    private lateinit var recyclerSchedules: com.example.refrotech.ExpandedRecyclerView
    private lateinit var btnAddSchedule: FrameLayout

    private lateinit var navDashboard: LinearLayout
    private lateinit var navTechnician: LinearLayout
    private lateinit var navRequests: LinearLayout

    private lateinit var scheduleAdapter: ScheduleAdapter
    private lateinit var db: FirebaseFirestore
    private var selectedDateStr: String? = null
    private var scheduleListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_leader_dashboard)

        // === Initialize Views ===
        calendarView = findViewById(R.id.calendarView)
        tvSelectedDate = findViewById(R.id.tvSelectedDate)
        recyclerSchedules = findViewById(R.id.recyclerSchedules)
        btnAddSchedule = findViewById(R.id.btnAddSchedule)

        navDashboard = findViewById(R.id.navDashboard)
        navTechnician = findViewById(R.id.navTechnician)
        navRequests = findViewById(R.id.navRequests)

        recyclerSchedules.layoutManager = LinearLayoutManager(this)
        scheduleAdapter = ScheduleAdapter(this, emptyList())
        recyclerSchedules.adapter = scheduleAdapter

        db = FirebaseFirestore.getInstance()

        // === Add Schedule Button ===
        btnAddSchedule.setOnClickListener {
            if (selectedDateStr.isNullOrBlank()) {
                Toast.makeText(this, "Silakan pilih tanggal terlebih dahulu.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = Intent(this, AddSchedulePage::class.java)
            intent.putExtra("date", selectedDateStr)
            startActivity(intent)
        }

        // === Calendar Date Selection ===
        calendarView.setOnDateChangedListener(OnDateSelectedListener { _, date, _ ->
            val selectedDateDisplay = "${date.day}-${date.month}-${date.year}"
            tvSelectedDate.text = "Jadwal untuk $selectedDateDisplay"

            // MaterialCalendarView.month is 1..12 (not zero-based). Use month directly.
            // Build ISO date yyyy-MM-dd
            selectedDateStr = String.format("%04d-%02d-%02d", date.year, date.month, date.day)
            listenToSchedulesForDate(selectedDateStr!!)
        })

        // === Navigation Bar ===
        navDashboard.setOnClickListener {
            Toast.makeText(this, "Sudah di halaman Dashboard", Toast.LENGTH_SHORT).show()
        }

        navTechnician.setOnClickListener {
            val intent = Intent(this, TechnicianManagement::class.java)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        // === NAVIGATE TO LEADER CONFIRMATION PAGE ===
        navRequests.setOnClickListener {
            val intent = Intent(this, LeaderConfirmationActivity::class.java)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    // ===== Real-time Firestore Listener =====
    private fun listenToSchedulesForDate(dateStr: String) {
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
                        customerName = doc.getString("customerName") ?: (doc.getString("name") ?: "Tanpa Nama"),
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
        scheduleListener?.remove()
        scheduleListener = null
    }

    override fun onResume() {
        super.onResume()
        selectedDateStr?.let { listenToSchedulesForDate(it) }
    }
}
