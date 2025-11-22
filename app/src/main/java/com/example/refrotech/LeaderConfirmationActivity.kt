package com.example.refrotech

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore

class LeaderConfirmationActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var recyclerRequests: androidx.recyclerview.widget.RecyclerView
    private lateinit var adapter: LeaderRequestAdapter

    // top tab views
    private lateinit var tabNewRequests: TextView
    private lateinit var tabRescheduleRequests: TextView

    // keep track which tab is active
    private enum class Tab { NEW, RESCHEDULE }
    private var activeTab = Tab.NEW

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_leader_confirmation)

        // recycler
        recyclerRequests = findViewById(R.id.recyclerLeaderRequests)
        recyclerRequests.layoutManager = LinearLayoutManager(this)

        // top tabs
        tabNewRequests = findViewById(R.id.tabNewRequests)
        tabRescheduleRequests = findViewById(R.id.tabRescheduleRequests)

        // adapter: pass click callback so activity handles navigation (safer)
        adapter = LeaderRequestAdapter(mutableListOf()) { req ->
            onRequestClicked(req)
        }
        recyclerRequests.adapter = adapter

        // wire tab clicks
        tabNewRequests.setOnClickListener {
            if (activeTab != Tab.NEW) {
                activeTab = Tab.NEW
                updateTabUI()
                loadPendingRequests()
            }
        }

        tabRescheduleRequests.setOnClickListener {
            if (activeTab != Tab.RESCHEDULE) {
                activeTab = Tab.RESCHEDULE
                updateTabUI()
                loadRescheduleRequests()
            }
        }

        // bottom nav setup (unchanged)
        setupBottomNav()

        // default: show pending new requests
        updateTabUI()
        loadPendingRequests()
    }

    private fun updateTabUI() {
        // Visual feedback for active tab — keep your drawable choices
        if (activeTab == Tab.NEW) {
            tabNewRequests.setBackgroundResource(R.drawable.login_button)
            tabRescheduleRequests.setBackgroundResource(R.drawable.dialog_background)
        } else {
            tabRescheduleRequests.setBackgroundResource(R.drawable.login_button)
            tabNewRequests.setBackgroundResource(R.drawable.dialog_background)
        }
    }

    /**
     * Load requests whose status equals "pending" (new requests).
     */
    private fun loadPendingRequests() {
        db.collection(FirestoreFields.REQUESTS)
            .whereEqualTo("status", "pending")
            .get()
            .addOnSuccessListener { snap ->
                val list = snap.documents.mapNotNull { doc ->
                    try {
                        RequestData.fromFirestore(doc)
                    } catch (ex: Exception) {
                        null
                    }
                }
                adapter.updateData(list)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal memuat permintaan: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Load reschedule requests.
     *
     * Firestore queries for many different status variants are brittle.
     * To be safe we fetch a small superset and filter client-side here.
     */
    private fun loadRescheduleRequests() {
        db.collection(FirestoreFields.REQUESTS)
            .get()
            .addOnSuccessListener { snap ->
                val candidates = snap.documents.mapNotNull { doc ->
                    try {
                        RequestData.fromFirestore(doc)
                    } catch (ex: Exception) {
                        null
                    }
                }

                // any status string containing "resched" or "reschedule" we treat as reschedule-related
                val reschedules = candidates.filter { r ->
                    val s = (r.status ?: "").lowercase()
                    s.contains("resched") || s.contains("reschedule") || s.contains("reschedule-pending")
                }

                adapter.updateData(reschedules)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal memuat permintaan penjadwalan ulang: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun onRequestClicked(req: RequestData) {
        // guard: need an id
        val rid = req.id
        if (rid.isNullOrBlank()) {
            Toast.makeText(this, "Request ID is missing, cannot open details", Toast.LENGTH_SHORT).show()
            return
        }

        // choose destination based on status (reschedule -> reschedule detail, otherwise new request detail)
        val lowercaseStatus = (req.status ?: "").lowercase()
        val intent = if (lowercaseStatus.contains("resched") || lowercaseStatus.contains("reschedule")) {
            Intent(this, LeaderRescheduleDetailActivity::class.java)
        } else {
            Intent(this, LeaderNewRequestDetailActivity::class.java)
        }

        intent.putExtra("requestId", rid)
        startActivity(intent)
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
