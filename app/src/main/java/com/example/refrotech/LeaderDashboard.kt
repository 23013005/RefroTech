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
    private lateinit var navLogout: FrameLayout

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
        navLogout = findViewById< FrameLayout>(R.id.navLogout)

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

        navLogout.setOnClickListener {
            LogoutHelper.logout(this)
        }


        setupBottomNav()
    }

    /**
     * Refresh displayed items for a single date by merging:
     *  - schedules where date == selectedDate
     *  - requests where date == selectedDate and status in ("confirmed","assigned")
     *
     * Important: we call JobNormalizer to get canonical status. We do NOT change route/origin logic.
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

        // Listen confirmed/assigned requests for this date
        // NOTE: use whereIn to listen for more than one status at once
        requestListener = db.collection(FirestoreFields.REQUESTS)
            .whereEqualTo("date", date)
            .whereIn("status", listOf("confirmed", "assigned"))
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
                    // normalize and convert via JobNormalizer
                    try {
                        val sch = JobNormalizer.scheduleDocToSchedule(d)
                        merged.add(sch)
                    } catch (_: Exception) {
                        // ignore problematic doc but continue processing
                    }
                }

                // fetch confirmed/assigned requests for date (leader-confirmed or assigned to techs)
                db.collection(FirestoreFields.REQUESTS)
                    .whereEqualTo("date", date)
                    .whereIn("status", listOf("confirmed", "assigned"))
                    .get()
                    .addOnSuccessListener { reqSnap ->
                        for (r in reqSnap.documents) {
                            try {
                                val reqAsSchedule = JobNormalizer.requestDocToSchedule(r)
                                merged.add(reqAsSchedule)
                            } catch (_: Exception) {
                                // ignore
                            }
                        }

                        // sort lexicographically by yyyy-MM-dd and HH:mm
                        merged.sortWith(compareBy({ it.date }, { it.time }))

                        // update adapter (adapter shows normalizedStatus)
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
        // material-calendarview months are 1-12 (but earlier code used month directly),
        // keep behavior consistent with your previous implementation (month as returned)
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

        // stop notification listener when dashboard is not visible
        InAppNotificationManager.stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        scheduleListener?.remove()
        requestListener?.remove()

        // ensure notification listener stopped
        InAppNotificationManager.stopListening()
    }
}
