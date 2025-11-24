package com.example.refrotech

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class TechnicianDashboard : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private var scheduleListener: ListenerRegistration? = null

    private lateinit var recyclerSchedules: ExpandedRecyclerView
    private lateinit var adapter: ScheduleAdapter

    private var technicianId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_technician_dashboard)

        technicianId = intent.getStringExtra("userId") ?: ""

        recyclerSchedules = findViewById(R.id.recyclerSchedules)
        recyclerSchedules.layoutManager = LinearLayoutManager(this)
        adapter = ScheduleAdapter(this, emptyList())
        recyclerSchedules.adapter = adapter

        if (technicianId.isBlank()) {
            Toast.makeText(this, "Technician ID missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        listenAssignedSchedules()
    }

    private fun listenAssignedSchedules() {
        scheduleListener?.remove()
        scheduleListener = db.collection(FirestoreFields.SCHEDULES)
            .whereArrayContains(FirestoreFields.FIELD_ASSIGNED_TECHNICIAN_IDS, technicianId)
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

    override fun onStop() {
        super.onStop()
        scheduleListener?.remove()
        scheduleListener = null
    }
}
