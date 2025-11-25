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

    private var scheduleListener: ListenerRegistration? = null
    private var requestListener: ListenerRegistration? = null
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
            refreshForDate(selectedDate)
        }

        // default = today
        val today = CalendarDay.today()
        selectedDate = formatDate(today)
        tvSelectedDate.text = selectedDate
        refreshForDate(selectedDate)

        // ===================== ADD SCHEDULE BUTTON =====================
        btnAddSchedule.setOnClickListener {
            val intent = Intent(this, AddSchedulePage::class.java)
            intent.putExtra("date", selectedDate)
            startActivity(intent)
        }

        setupBottomNav()
    }

    /**
     * Refresh displayed items for a single date by merging:
     *  - schedules where date == selectedDate
     *  - requests where date == selectedDate and status == "confirmed"
     */
    private fun refreshForDate(date: String) {
        scheduleListener?.remove()
        requestListener?.remove()

        // Listen schedules for this date
        scheduleListener = db.collection(FirestoreFields.SCHEDULES)
            .whereEqualTo("date", date)
            .addSnapshotListener { snaps, e ->
                if (e != null) {
                    Toast.makeText(this, "Gagal memuat jadwal: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                // After receiving schedules change, re-query both to merge (keeps logic simple)
                mergeSchedulesAndConfirmedRequestsForDate(date)
            }

        // Listen confirmed requests for this date
        requestListener = db.collection(FirestoreFields.REQUESTS)
            .whereEqualTo("date", date)
            .whereEqualTo("status", "confirmed")
            .addSnapshotListener { _, _ ->
                // when requests change, re-merge lists for that date
                mergeSchedulesAndConfirmedRequestsForDate(date)
            }

        // initial one-off merge
        mergeSchedulesAndConfirmedRequestsForDate(date)
    }

    private fun mergeSchedulesAndConfirmedRequestsForDate(date: String) {
        val merged = mutableListOf<Schedule>()

        // fetch schedules for date
        db.collection(FirestoreFields.SCHEDULES)
            .whereEqualTo("date", date)
            .get()
            .addOnSuccessListener { schSnap ->
                for (d in schSnap.documents) {
                    val (names, ids) = try {
                        FirestoreNormalizer.normalizeTechnicians(d)
                    } catch (_: Exception) {
                        Pair(emptyList<String>(), emptyList<String>())
                    }

                    merged.add(
                        Schedule(
                            scheduleId = d.id,
                            customerName = d.getString("customerName") ?: "",
                            date = d.getString("date") ?: "",
                            time = d.getString("time") ?: "",
                            technicians = names,
                            technicianIds = ids,
                            assignedTechnicianIds = d.get("assignedTechnicianIds") as? List<String> ?: ids,
                            address = d.getString("address") ?: "",
                            origin = d.getString("origin") ?: "schedule",
                            requestId = d.getString("requestId") ?: ""
                        )
                    )
                }

                // fetch confirmed requests for date
                db.collection(FirestoreFields.REQUESTS)
                    .whereEqualTo("date", date)
                    .whereEqualTo("status", "confirmed")
                    .get()
                    .addOnSuccessListener { reqSnap ->
                        for (r in reqSnap.documents) {
                            // name may be stored in "customerName" or "name"
                            val custName = r.getString("customerName") ?: r.getString("name") ?: ""
                            val (names, ids) = try {
                                FirestoreNormalizer.normalizeTechnicians(r)
                            } catch (_: Exception) {
                                Pair(emptyList<String>(), emptyList<String>())
                            }

                            // convert request -> Schedule-like item for display
                            merged.add(
                                Schedule(
                                    scheduleId = r.id,
                                    customerName = custName,
                                    date = r.getString("date") ?: "",
                                    time = r.getString("time") ?: "",
                                    technicians = names,
                                    technicianIds = ids,
                                    assignedTechnicianIds = r.get("assignedTechnicianIds") as? List<String> ?: (r.get("technicianIds") as? List<String> ?: ids),
                                    address = r.getString("address") ?: "",
                                    origin = "request",
                                    requestId = r.id
                                )
                            )
                        }

                        // sort lexicographically by yyyy-MM-dd and HH:mm
                        merged.sortWith(compareBy({ it.date }, { it.time }))

                        adapter.updateData(merged)
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed loading requests: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed loading schedules: ${e.message}", Toast.LENGTH_SHORT).show()
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
        findViewById<LinearLayout>(R.id.navDashboard).setOnClickListener {
            // stay
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
        scheduleListener?.remove()
        requestListener?.remove()
    }
}
