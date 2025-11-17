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

        // Add Schedule -> open AddSchedulePage with selected date
        btnAddSchedule.setOnClickListener {
            val date = selectedDateStr
            if (date.isNullOrBlank()) {
                Toast.makeText(this, "Pilih tanggal terlebih dahulu di kalender", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = Intent(this, AddSchedulePage::class.java)
            intent.putExtra("date", date)
            startActivity(intent)
        }

        // Calendar date selection
        calendarView.setOnDateChangedListener(OnDateSelectedListener { _, date, _ ->
            // MaterialCalendarView's month is 1-based in this listener in some versions; preserve same formatting
            val selectedDateDisplay = "${date.day}-${date.month}-${date.year}"
            tvSelectedDate.text = "Jadwal untuk $selectedDateDisplay"
            selectedDateStr = String.format("%04d-%02d-%02d", date.year, date.month, date.day)
            listenToSchedulesForDate(selectedDateStr!!)
        })

        navDashboard.setOnClickListener {
            Toast.makeText(this, "Sudah di halaman Dashboard", Toast.LENGTH_SHORT).show()
        }

        navTechnician.setOnClickListener {
            val intent = Intent(this, TechnicianManagement::class.java)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        navRequests.setOnClickListener {
            val intent = Intent(this, LeaderConfirmationActivity::class.java)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    private fun listenToSchedulesForDate(dateStr: String) {
        scheduleListener?.remove()

        // Query schedules collection for that date (includes manual and request-origin schedules)
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
                        scheduleId = doc.id,
                        customerName = doc.getString("customerName") ?: "-",
                        date = doc.getString("date") ?: "-",
                        time = doc.getString("time") ?: "-",
                        technicians = doc.getString("technicians") ?: "",
                        technicianIds = doc.get("technicianIds") as? List<String> ?: emptyList(),
                        address = doc.getString("address") ?: "-",
                        origin = doc.getString("origin") ?: "manual",
                        requestId = doc.getString("requestId") ?: ""
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
