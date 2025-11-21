package com.example.refrotech

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore

class LeaderConfirmationActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var recyclerRequests: androidx.recyclerview.widget.RecyclerView
    private lateinit var adapter: LeaderRequestAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_leader_confirmation)

        recyclerRequests = findViewById(R.id.recyclerLeaderRequests)
        recyclerRequests.layoutManager = LinearLayoutManager(this)

        adapter = LeaderRequestAdapter(this, mutableListOf())
        recyclerRequests.adapter = adapter

        loadPendingRequests()
        setupBottomNav()
    }

    private fun loadPendingRequests() {
        db.collection(FirestoreFields.REQUESTS)
            .whereEqualTo("status", "pending")
            .get()
            .addOnSuccessListener { snap ->
                val list = snap.documents.map { doc ->
                    RequestData.fromFirestore(doc)
                }
                adapter.updateData(list)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal memuat permintaan: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupBottomNav() {
        findViewById<LinearLayout>(R.id.navDashboard).setOnClickListener {
            startActivity(Intent(this, LeaderDashboard::class.java))
        }

        findViewById<LinearLayout>(R.id.navTechnician).setOnClickListener {
            startActivity(Intent(this, TechnicianManagement::class.java))
        }

        findViewById<LinearLayout>(R.id.navRequests).setOnClickListener {
            // already here
        }
    }
}
