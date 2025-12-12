package com.example.refrotech

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class TechnicianHistory : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    private lateinit var recycler: androidx.recyclerview.widget.RecyclerView
    private lateinit var adapter: HistoryAdapterTechnician

    private lateinit var navHome: LinearLayout
    private lateinit var navHistory: LinearLayout

    // FILTER UI
    private lateinit var spinnerStatusFilter: Spinner
    private lateinit var tvDateFilter: TextView

    private var technicianId: String = ""

    // ALL JOBS (source of truth for filters)
    private val allJobs = mutableListOf<Schedule>()

    // CURRENT FILTER VALUES
    private var filterStatus: String = "all"   // "all", "confirmed", "on-progress", "completed"
    private var filterDate: String? = null     // yyyy-MM-dd or null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_technician_history)

        technicianId = intent.getStringExtra("userId") ?: ""

        recycler = findViewById(R.id.recyclerHistory)
        recycler.layoutManager = LinearLayoutManager(this)

        adapter = HistoryAdapterTechnician(mutableListOf())
        recycler.adapter = adapter

        spinnerStatusFilter = findViewById(R.id.spinnerStatusFilter)
        tvDateFilter = findViewById(R.id.tvDateFilter)

        navHome = findViewById(R.id.navHome)
        navHistory = findViewById(R.id.navHistory)

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

        adapter.onItemClick = { schedule ->
            val i = Intent(this, TechnicianJobDetail::class.java)
            i.putExtra("userId", technicianId)

            if (schedule.origin == "request") {
                i.putExtra("origin", "request")
                i.putExtra("id", schedule.requestId)
            } else {
                i.putExtra("origin", "schedule")
                i.putExtra("id", schedule.scheduleId)
            }
            startActivity(i)
        }

        setupFilters()
        loadHistory()
    }

    private fun setupFilters() {
        val statuses = listOf("All", "Confirmed", "On-Progress", "Completed")
        val adapterSpinner = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            statuses
        )
        adapterSpinner.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerStatusFilter.adapter = adapterSpinner

        spinnerStatusFilter.onItemSelectedListener = object :
            android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>,
                view: android.view.View?,
                pos: Int,
                id: Long
            ) {
                filterStatus = statuses[pos].lowercase(Locale.getDefault())
                applyFilters()
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }

        tvDateFilter.setOnClickListener { pickDate() }
    }

    private fun pickDate() {
        val c = Calendar.getInstance()

        val dp = DatePickerDialog(
            this,
            { _, year, month, day ->
                val m = month + 1
                filterDate = "%04d-%02d-%02d".format(year, m, day)
                tvDateFilter.text = filterDate
                applyFilters()
            },
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH),
            c.get(Calendar.DAY_OF_MONTH)
        )
        dp.show()
    }

    private fun applyFilters() {
        var list = allJobs.toList()

        // STATUS FILTER
        if (filterStatus != "all") {
            list = list.filter { it.normalizedStatus == filterStatus }
        }

        // DATE FILTER
        if (filterDate != null) {
            list = list.filter { it.date == filterDate }
        }

        adapter.updateData(list)
    }

    /**
     * New history logic:
     * - Show ALL jobs (past, present, future) where this technician is/was assigned
     * - Only exclude jobs whose normalizedStatus == "cancelled"
     * - Let filters operate purely on normalizedStatus + date
     */
    private fun loadHistory() {
        val results = mutableListOf<Schedule>()

        // ========== LOAD REQUEST JOBS ==========
        db.collection(FirestoreFields.REQUESTS)
            .whereArrayContains(FirestoreFields.FIELD_TECHNICIAN_IDS, technicianId)
            .get()
            .addOnSuccessListener { snap ->
                for (doc in snap.documents) {
                    val s = JobNormalizer.requestDocToSchedule(doc)

                    // Exclude cancelled only
                    if (s.normalizedStatus == "cancelled") continue

                    results.add(s)
                }

                // ========== LOAD SCHEDULE JOBS ==========
                db.collection(FirestoreFields.SCHEDULES)
                    .whereArrayContains(FirestoreFields.FIELD_ASSIGNED_TECHNICIAN_IDS, technicianId)
                    .get()
                    .addOnSuccessListener { schSnap ->

                        for (doc in schSnap.documents) {
                            val s = JobNormalizer.scheduleDocToSchedule(doc)

                            // Exclude cancelled only
                            if (s.normalizedStatus == "cancelled") continue

                            results.add(s)
                        }

                        // SORT BY DATE + TIME ASC
                        results.sortWith(compareBy({ it.date }, { it.time }))

                        allJobs.clear()
                        allJobs.addAll(results)

                        // Now apply current filters (status + date)
                        applyFilters()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(
                            this,
                            "Failed to load schedules: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load history", Toast.LENGTH_SHORT).show()
            }
    }
}
