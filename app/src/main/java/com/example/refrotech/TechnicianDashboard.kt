package com.example.refrotech

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.prolificinteractive.materialcalendarview.MaterialCalendarView
import java.text.SimpleDateFormat
import java.util.*

class TechnicianDashboard : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private var scheduleListener: ListenerRegistration? = null
    private var requestListener: ListenerRegistration? = null

    private lateinit var recyclerSchedules: RecyclerView
    private lateinit var adapter: TechnicianScheduleAdapter

    private var technicianId: String = ""

    private var allSchedules: MutableList<Schedule> = mutableListOf()

    private lateinit var calendarView: MaterialCalendarView
    private lateinit var tvSelectedDate: TextView
    private lateinit var navHome: LinearLayout
    private lateinit var navHistory: LinearLayout
    private lateinit var navLogout: FrameLayout

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // Dashboard shows only active jobs
    private val dashboardAllowed = setOf("confirmed", "on-progress")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_technician_dashboard)

        technicianId = intent.getStringExtra("userId") ?: ""

        recyclerSchedules = findViewById(R.id.recyclerSchedules)
        recyclerSchedules.layoutManager = LinearLayoutManager(this)
        adapter = TechnicianScheduleAdapter(this, emptyList())
        recyclerSchedules.adapter = adapter

        calendarView = findViewById(R.id.calendarView)
        tvSelectedDate = findViewById(R.id.tvSelectedDate)
        navHome = findViewById(R.id.navHome)
        navHistory = findViewById(R.id.navHistory)
        navLogout = findViewById<FrameLayout>(R.id.navLogout)


        // Navigation
        navHome.setOnClickListener {
            val i = Intent(this, TechnicianDashboard::class.java)
            i.putExtra("userId", technicianId)
            startActivity(i)
            finish()
        }
        navHistory.setOnClickListener {
            val i = Intent(this, TechnicianHistory::class.java)
            i.putExtra("userId", technicianId)
            startActivity(i)
            finish()
        }
        navLogout.setOnClickListener {
            LogoutHelper.logout(this)
        }

        // Calendar date selection
        calendarView.setOnDateChangedListener { _, date, _ ->
            val cal = Calendar.getInstance()
            cal.set(date.year, date.month - 1, date.day)
            val formatted = dateFormat.format(cal.time)
            tvSelectedDate.text = formatted
            filterSchedulesByDate(formatted)
        }

        // Item click → Job Detail (send correct id depending on origin)
        adapter.onItemClick = { schedule ->
            val intent = Intent(this, TechnicianJobDetail::class.java)
            intent.putExtra("userId", technicianId)
            if (schedule.origin == "request") {
                // A: send requestId for request origin
                intent.putExtra("origin", "request")
                intent.putExtra("id", schedule.requestId)
            } else {
                intent.putExtra("origin", "schedule")
                intent.putExtra("id", schedule.scheduleId)
            }
            startActivity(intent)
        }

        if (technicianId.isBlank()) {
            Toast.makeText(this, "Technician ID missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        listenAssignedJobs()

        // START in-app notifications listener for technician dashboard
        InAppNotificationManager.startListening(this)
    }

    // ======================
    // REAL-TIME LISTENERS
    // ======================
    private fun listenAssignedJobs() {
        scheduleListener?.remove()
        requestListener?.remove()

        scheduleListener = db.collection(FirestoreFields.SCHEDULES)
            .whereArrayContains(FirestoreFields.FIELD_ASSIGNED_TECHNICIAN_IDS, technicianId)
            .addSnapshotListener { _, _ -> refreshMergedJobs() }

        // Some request documents may use technicianIds or assignedTechnicianIds — we listen by both where necessary.
        // Use technicianIds as more reliable for request assignment if assignedTechnicianIds not present.
        requestListener = db.collection(FirestoreFields.REQUESTS)
            .whereArrayContains(FirestoreFields.FIELD_TECHNICIAN_IDS, technicianId)
            .addSnapshotListener { _, _ -> refreshMergedJobs() }

        refreshMergedJobs()
    }

    // ======================
    // MERGE REQUESTS + SCHEDULES
    // ======================
    private fun refreshMergedJobs() {
        db.collection(FirestoreFields.SCHEDULES)
            .whereArrayContains(FirestoreFields.FIELD_ASSIGNED_TECHNICIAN_IDS, technicianId)
            .get()
            .addOnSuccessListener { scheduleSnap ->

                db.collection(FirestoreFields.REQUESTS)
                    .whereArrayContains(FirestoreFields.FIELD_TECHNICIAN_IDS, technicianId)
                    .get()
                    .addOnSuccessListener { requestSnap ->

                        val merged = mutableListOf<Schedule>()

                        // --- SCHEDULES ---
                        for (d in scheduleSnap.documents) {
                            val workStatus = (d.getString("workStatus") ?: "pending").lowercase()

                            if (workStatus in dashboardAllowed) {
                                merged.add(JobNormalizer.scheduleDocToSchedule(d))
                            }
                        }

                        // --- REQUESTS ---
                        for (d in requestSnap.documents) {
                            val jobStatus = (d.getString("jobStatus") ?: "pending").lowercase()

                            if (jobStatus in dashboardAllowed) {
                                merged.add(JobNormalizer.requestDocToSchedule(d))
                            }
                        }

                        // Sort by date + time
                        merged.sortWith(compareBy({ it.date }, { it.time }))

                        allSchedules = merged

                        val selected = tvSelectedDate.text.toString()
                        if (selected.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                            filterSchedulesByDate(selected)
                        } else {
                            adapter.updateData(merged)
                        }
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed loading schedules: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun filterSchedulesByDate(yyyyMMdd: String) {
        adapter.updateData(allSchedules.filter { it.date == yyyyMMdd })
    }

    override fun onStop() {
        super.onStop()
        scheduleListener?.remove()
        requestListener?.remove()

        // stop notification listener when leaving dashboard
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
