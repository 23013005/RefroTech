package com.example.refrotech

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class TechnicianDashboard : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var recyclerSchedules: ExpandedRecyclerView
    private lateinit var adapter: ScheduleAdapter
    private var scheduleListener: ListenerRegistration? = null
    private var technicianId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_technician_dashboard)

        technicianId = intent.getStringExtra("userId") // must be passed at login
        if (technicianId.isNullOrBlank()) {
            Toast.makeText(this, "Technician ID missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        recyclerSchedules = findViewById(R.id.recyclerSchedules) // ensure layout id matches
        recyclerSchedules.layoutManager = LinearLayoutManager(this)
        adapter = ScheduleAdapter(this, emptyList())
        recyclerSchedules.adapter = adapter

        listenToAssignedSchedules()
    }

    private fun listenToAssignedSchedules() {
        scheduleListener?.remove()
        scheduleListener = db.collection("schedules")
            .whereArrayContains("assignedTechnicianIds", technicianId!!)
            .addSnapshotListener { snaps, e ->
                if (e != null) {
                    Toast.makeText(this, "Gagal memuat jadwal: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snaps == null || snaps.isEmpty) {
                    adapter.updateData(emptyList())
                    return@addSnapshotListener
                }

                val list = snaps.map { doc ->

                    Schedule(
                        scheduleId = doc.id,
                        customerName = doc.getString("customerName") ?: "-",
                        date = doc.getString("date") ?: "",
                        time = doc.getString("time") ?: "",
                        technicians = doc.getString("technicians") ?: "",
                        technicianIds = doc.get("technicianIds") as? List<String> ?: emptyList(),
                        address = doc.getString("address") ?: "-",
                        origin = doc.getString("origin") ?: "",
                        requestId = doc.getString("requestId") ?: ""
                    )
                }


                adapter.updateData(list)
            }
    }

    override fun onStop() {
        super.onStop()
        scheduleListener?.remove()
        scheduleListener = null
    }
}
