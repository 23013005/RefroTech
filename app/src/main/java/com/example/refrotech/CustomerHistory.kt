package com.example.refrotech

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class CustomerHistory : AppCompatActivity() {

    private lateinit var recyclerHistory: RecyclerView
    private lateinit var navHome: LinearLayout
    private lateinit var navHistory: LinearLayout

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var adapter: HistoryAdapter
    private val requestList = mutableListOf<RequestData>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer_history)

        recyclerHistory = findViewById(R.id.recyclerHistory)
        navHome = findViewById(R.id.navHome)
        navHistory = findViewById(R.id.navHistory)

        recyclerHistory.layoutManager = LinearLayoutManager(this)
        adapter = HistoryAdapter(requestList, this)
        recyclerHistory.adapter = adapter

        // ===== Navigation =====
        navHome.setOnClickListener {
            finish() // Return to dashboard
        }

        navHistory.setOnClickListener {
            Toast.makeText(this, "Sudah di halaman Riwayat", Toast.LENGTH_SHORT).show()
        }

        loadRequests()
    }

    override fun onResume() {
        super.onResume()
        loadRequests() // Refresh list when page returns
    }

    private fun loadRequests() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "User belum login.", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("requests")
            .whereEqualTo("customerId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->
                requestList.clear()
                for (doc in result) {
                    val request = RequestData(
                        id = doc.id,
                        name = doc.getString("name") ?: "",
                        address = doc.getString("address") ?: "",
                        date = doc.getString("date") ?: "",
                        time = doc.getString("time") ?: "",
                        status = doc.getString("status") ?: "Unknown"
                    )
                    requestList.add(request)
                }

                adapter.notifyDataSetChanged()

                if (requestList.isEmpty()) {
                    Toast.makeText(this, "Belum ada permintaan yang dikirim.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal memuat permintaan: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
