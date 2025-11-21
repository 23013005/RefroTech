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
import com.prolificinteractive.materialcalendarview.CalendarDay
import com.prolificinteractive.materialcalendarview.MaterialCalendarView

class LeaderDashboard : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    private lateinit var calendarView: MaterialCalendarView
    private lateinit var tvSelectedDate: TextView
    private lateinit var recyclerSchedules: ExpandedRecyclerView
    private lateinit var btnAddSchedule: FrameLayout

    private lateinit var adapter: ScheduleAdapter

    private var listener: ListenerRegistration? = null
    private var selectedDate: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_leader_dashboard)

        // ===================== FIND VIEWS FROM XML =====================
        calendarView = findViewById(R.id.calendarView)
        tvSelectedDate = findViewById(R.id.tvSelectedDate)
        recyclerSchedules = findViewById(R.id.recyclerSchedules)
        btnAddSchedule = findViewById(R.id.btnAddSchedule)

        // ===================== SETUP RECYCLER VIEW =====================
        recyclerSchedules.layoutManager = LinearLayoutManager(this)
        adapter = ScheduleAdapter(this, emptyList())
        recyclerSchedules.adapter = adapter

        // ===================== CALENDAR EVENT =====================
        calendarView.setOnDateChangedListener { _, date, _ ->
            selectedDate = formatDate(date)
            tvSelectedDate.text = selectedDate
            loadSchedulesForDate(selectedDate)
        }

        // default = today
        val today = CalendarDay.today()
        selectedDate = formatDate(today)
        tvSelectedDate.text = selectedDate
        loadSchedulesForDate(selectedDate)

        // ===================== ADD SCHEDULE BUTTON =====================
        btnAddSchedule.setOnClickListener {
            val intent = Intent(this, AddSchedulePage::class.java)
            startActivity(intent)
        }

        setupBottomNav()
    }

    // ===================== LOAD SCHEDULES =====================
    private fun loadSchedulesForDate(date: String) {
        listener?.remove()

        listener = db.collection(FirestoreFields.SCHEDULES)
            .whereEqualTo("date", date)
            .addSnapshotListener { snaps, e ->
                if (e != null) {
                    Toast.makeText(this, "Gagal memuat jadwal: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snaps == null || snaps.isEmpty) {
                    adapter.updateData(emptyList())
                    return@addSnapshotListener
                }

                val list = snaps.documents.map { doc ->

                    val (names, ids) = FirestoreNormalizer.normalizeTechnicians(doc)

                    Schedule(
                        scheduleId = doc.id,
                        customerName = doc.getString("customerName") ?: "",
                        date = doc.getString("date") ?: "",
                        time = doc.getString("time") ?: "",
                        technicians = names,
                        technicianIds = ids,
                        assignedTechnicianIds = ids,
                        address = doc.getString("address") ?: "",
                        origin = doc.getString("origin") ?: "",
                        requestId = doc.getString("requestId") ?: ""
                    )
                }

                adapter.updateData(list)
            }
    }

    // ===================== DATE FORMATTER =====================
    private fun formatDate(day: CalendarDay): String {
        val y = day.year
        val m = String.format("%02d", day.month)
        val d = String.format("%02d", day.day)
        return "$y-$m-$d"
    }

    // ===================== BOTTOM NAVIGATION =====================
    private fun setupBottomNav() {
        // The nav items in layout are LinearLayout — use LinearLayout to avoid ClassCastException
        findViewById<LinearLayout>(R.id.navDashboard).setOnClickListener {
            // reload same page (no-op)
        }

        findViewById<LinearLayout>(R.id.navTechnician).setOnClickListener {
            startActivity(Intent(this, TechnicianManagement::class.java))
        }

        findViewById<LinearLayout>(R.id.navRequests).setOnClickListener {
            startActivity(Intent(this, LeaderConfirmationActivity::class.java))
        }
    }

    override fun onStop() {
        super.onStop()
        listener?.remove()
    }
}
