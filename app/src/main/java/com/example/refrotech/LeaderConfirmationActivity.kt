package com.example.refrotech

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore

class LeaderConfirmationActivity : AppCompatActivity() {

    private lateinit var tabNew: TextView
    private lateinit var tabReschedule: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: LeaderRequestAdapter

    private val db = FirebaseFirestore.getInstance()
    private val requestList = mutableListOf<RequestData>()

    private var currentTab = "NEW"   // NEW or RESCHEDULE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_leader_confirmation)

        tabNew = findViewById(R.id.tabNewRequests)
        tabReschedule = findViewById(R.id.tabRescheduleRequests)
        recycler = findViewById(R.id.recyclerLeaderRequests)

        adapter = LeaderRequestAdapter(this, requestList)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        loadNewRequests()

        tabNew.setOnClickListener {
            currentTab = "NEW"
            loadNewRequests()
        }

        tabReschedule.setOnClickListener {
            currentTab = "RESCHEDULE"
            loadRescheduleRequests()
        }
    }

    private fun loadNewRequests() {
        db.collection("requests")
            .whereEqualTo("status", "pending") // normalized to lowercase
            .get()
            .addOnSuccessListener { result ->
                requestList.clear()
                for (doc in result) {
                    val req = RequestData.fromFirestore(doc)
                    requestList.add(req)
                }
                adapter.notifyDataSetChanged()
            }
    }

    private fun loadRescheduleRequests() {
        db.collection("requests")
            .whereEqualTo("status", "reschedule-pending")
            .get()
            .addOnSuccessListener { result ->
                requestList.clear()
                for (doc in result) {
                    val req = RequestData.fromFirestore(doc)
                    requestList.add(req)
                }
                adapter.notifyDataSetChanged()
            }
    }
}
