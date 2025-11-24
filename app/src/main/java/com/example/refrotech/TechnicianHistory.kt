package com.example.refrotech

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore

class TechnicianHistory : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var recyclerHistory: androidx.recyclerview.widget.RecyclerView
    private lateinit var adapter: HistoryAdapter
    private var technicianId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_technician_history)

        technicianId = intent.getStringExtra("userId") ?: ""

        recyclerHistory = findViewById(R.id.recyclerHistory)
        recyclerHistory.layoutManager = LinearLayoutManager(this)

        adapter = HistoryAdapter(mutableListOf())
        recyclerHistory.adapter = adapter

        loadHistory()
    }

    private fun loadHistory() {
        db.collection(FirestoreFields.REQUESTS)
            .whereArrayContains(FirestoreFields.FIELD_TECHNICIAN_IDS, technicianId)
            .whereEqualTo(FirestoreFields.FIELD_JOB_STATUS, "completed")
            .get()
            .addOnSuccessListener { snap ->
                val list = snap.documents.map { RequestData.fromFirestore(it) }
                adapter.updateData(list)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal memuat riwayat: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
