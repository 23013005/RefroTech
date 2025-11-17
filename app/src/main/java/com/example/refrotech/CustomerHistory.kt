package com.example.refrotech

import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class CustomerHistory : AppCompatActivity() {

    private lateinit var recyclerHistory: RecyclerView
    private lateinit var navHome: LinearLayout
    private lateinit var navHistory: LinearLayout

    private lateinit var adapter: HistoryAdapter
    private val requestList = mutableListOf<RequestData>()

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer_history)

        recyclerHistory = findViewById(R.id.recyclerHistory)
        navHome = findViewById(R.id.navHome)
        navHistory = findViewById(R.id.navHistory)

        recyclerHistory.layoutManager = LinearLayoutManager(this)

        // >>> FIXED HERE <<<
        adapter = HistoryAdapter(
            context = this,           // The Activity (valid Context)
            requests = requestList    // The list of RequestData
        )
        recyclerHistory.adapter = adapter

        // Load data
        loadRequests()

        navHome.setOnClickListener { finish() }
        navHistory.setOnClickListener { } // currently on history
    }

    private fun loadRequests() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("requests")
            .whereEqualTo("customerId", userId)
            .get()
            .addOnSuccessListener { result ->
                requestList.clear()
                for (doc in result) {
                    val req = RequestData.fromFirestore(doc)
                    requestList.add(req)
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener {
                // handle error
            }
    }
}
