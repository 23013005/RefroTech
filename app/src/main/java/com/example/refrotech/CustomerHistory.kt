package com.example.refrotech

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class CustomerHistory : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    private lateinit var recyclerHistory: androidx.recyclerview.widget.RecyclerView
    private lateinit var adapter: HistoryAdapter
    private lateinit var userId: String

    // NAVIGATION
    private lateinit var navHome: LinearLayout
    private lateinit var navHistory: LinearLayout

    private var listener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer_history)

        userId = intent.getStringExtra("userId") ?: ""

        recyclerHistory = findViewById(R.id.recyclerHistory)
        recyclerHistory.layoutManager = LinearLayoutManager(this)
        adapter = HistoryAdapter(mutableListOf())
        recyclerHistory.adapter = adapter

        // FIX: navigation views must be LinearLayout (NOT FrameLayout)
        navHome = findViewById(R.id.navHome)
        navHistory = findViewById(R.id.navHistory)

        setupNavigation()
        startRealtimeListener()
    }

    private fun setupNavigation() {
        navHome.setOnClickListener {
            val intent = Intent(this, DashboardCustomer::class.java)
            intent.putExtra("userId", userId)
            startActivity(intent)
            finish()
        }

        navHistory.setOnClickListener {
            // current page, do nothing or refresh
        }
    }

    private fun startRealtimeListener() {
        listener?.remove()

        listener = db.collection(FirestoreFields.REQUESTS)
            .whereEqualTo("customerId", userId)   // FIXED — this is how requests are saved
            .addSnapshotListener { snaps, e ->
                if (e != null) {
                    Toast.makeText(this, "Failed to load history: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snaps == null) {
                    adapter.updateData(emptyList())
                    return@addSnapshotListener
                }

                // auto-delete cancelled
                for (doc in snaps.documents) {
                    val status = doc.getString("status") ?: ""
                    if (status.equals("cancelled", ignoreCase = true)) {
                        db.collection(FirestoreFields.REQUESTS)
                            .document(doc.id)
                            .delete()
                    }
                }

                val list = snaps.documents.mapNotNull { doc ->
                    try { RequestData.fromFirestore(doc) } catch (_: Exception) { null }
                }.sortedByDescending { it.createdAtMillis ?: 0L }

                adapter.updateData(list)
            }
    }



    override fun onStop() {
        super.onStop()
        listener?.remove()
        listener = null
    }

    override fun onDestroy() {
        super.onDestroy()
        listener?.remove()
        listener = null
    }
}
