package com.example.refrotech

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore

class LeaderConfirmationActivity : AppCompatActivity() {

    private lateinit var tvPageTitle: TextView
    private lateinit var tabNewRequests: TextView
    private lateinit var tabRescheduleRequests: TextView
    private lateinit var recyclerLeaderRequests: RecyclerView

    private lateinit var adapter: LeaderRequestAdapter
    private val requestList = mutableListOf<RequestData>()

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_leader_confirmation)

        // ============= INIT VIEWS =============
        tvPageTitle = findViewById(R.id.tvPageTitle)
        tabNewRequests = findViewById(R.id.tabNewRequests)
        tabRescheduleRequests = findViewById(R.id.tabRescheduleRequests)
        recyclerLeaderRequests = findViewById(R.id.recyclerLeaderRequests)

        adapter = LeaderRequestAdapter(this, requestList)
        recyclerLeaderRequests.layoutManager = LinearLayoutManager(this)
        recyclerLeaderRequests.adapter = adapter

        // Default: Load NEW requests
        setActiveTab(true)
        loadRequests("pending")

        // ============= TAB CLICK HANDLERS =============
        tabNewRequests.setOnClickListener {
            setActiveTab(true)
            tvPageTitle.text = "Konfirmasi Permintaan Baru"
            loadRequests("pending")
        }

        tabRescheduleRequests.setOnClickListener {
            setActiveTab(false)
            tvPageTitle.text = "Konfirmasi Permintaan Penjadwalan Ulang"
            loadRequests("reschedule-pending")
        }

        // ============= BOTTOM NAV =============
        findViewById<LinearLayout>(R.id.navDashboard).setOnClickListener {
            startActivity(Intent(this, leader_dashboard::class.java))
            finish()
        }

        findViewById<LinearLayout>(R.id.navTechnician).setOnClickListener {
            startActivity(Intent(this, TechnicianManagement::class.java))
        }

        findViewById<LinearLayout>(R.id.navRequests).setOnClickListener {
            // Already here
        }
    }

    // ============= TAB HIGHLIGHTING =============
    private fun setActiveTab(isNew: Boolean) {
        if (isNew) {
            tabNewRequests.setBackgroundResource(R.drawable.tab_selected)
            tabRescheduleRequests.setBackgroundResource(R.drawable.tab_unselected)
        } else {
            tabNewRequests.setBackgroundResource(R.drawable.tab_unselected)
            tabRescheduleRequests.setBackgroundResource(R.drawable.tab_selected)
        }
    }

    // ============= LOAD FIRESTORE REQUESTS =============
    private fun loadRequests(status: String) {
        db.collection("requests")
            .whereEqualTo("status", status)
            .get()
            .addOnSuccessListener { snap ->
                requestList.clear()
                for (doc in snap.documents) {
                    requestList.add(RequestData.fromFirestore(doc))
                }
                adapter.notifyDataSetChanged()

                if (requestList.isEmpty()) {
                    Toast.makeText(this, "Tidak ada permintaan", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
