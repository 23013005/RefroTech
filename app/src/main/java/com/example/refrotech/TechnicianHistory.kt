package com.example.refrotech

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class TechnicianHistory : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var adapter: TechnicianScheduleAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_technician_history)

        val recycler = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerHistory)
        recycler.layoutManager = LinearLayoutManager(this)

        adapter = TechnicianScheduleAdapter { requestId ->
            val i = Intent(this, TechnicianJobDetail::class.java)
            i.putExtra("requestId", requestId)
            startActivity(i)
        }
        recycler.adapter = adapter

        loadHistory()

        // bottom nav
        findViewById<android.view.View>(R.id.navHome).setOnClickListener {
            startActivity(Intent(this, TechnicianDashboard::class.java))
            finish()
        }

        findViewById<android.view.View>(R.id.navHistory).setOnClickListener {
            Toast.makeText(this, "Sudah di halaman History", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadHistory() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Teknisi belum login", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("requests")
            .whereEqualTo("technicianId", uid)
            .whereEqualTo("status", "completed")
            .get()
            .addOnSuccessListener { snap ->
                val list = snap.documents.map { RequestData.fromFirestore(it) }
                adapter.submitList(list)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal memuat history: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
