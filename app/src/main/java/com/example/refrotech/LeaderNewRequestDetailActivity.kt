// LeaderNewRequestDetailActivity.kt
package com.example.refrotech

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView     // ADDED
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class LeaderNewRequestDetailActivity : AppCompatActivity() {

    private lateinit var tvName: TextView
    private lateinit var tvAddress: TextView
    private lateinit var tvDateTime: TextView
    private lateinit var btnApprove: TextView
    private lateinit var btnReject: TextView

    // MISSING VIEW — NOW ADDED
    private lateinit var rvDetailUnits: RecyclerView

    private var requestId: String = ""
    private var requestData: RequestData? = null

    private val allTechNames = mutableListOf<String>()
    private val allTechIds = mutableListOf<String>()
    private val allTechDocs = mutableListOf<Map<String, Any>>()

    private val selectedTechNames = mutableListOf<String>()
    private val selectedTechIds = mutableListOf<String>()

    private val db = FirebaseFirestore.getInstance()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_leader_new_request_detail)

        tvName = findViewById(R.id.tvDetailName)
        tvAddress = findViewById(R.id.tvDetailAddress)
        tvDateTime = findViewById(R.id.tvDetailDateTime)
        btnApprove = findViewById(R.id.btnDetailApprove)
        btnReject = findViewById(R.id.btnDetailReject)

        // BIND UNITS RECYCLER
        rvDetailUnits = findViewById(R.id.rvDetailUnits)
        rvDetailUnits.layoutManager = LinearLayoutManager(this)

        requestId = intent.getStringExtra("requestId") ?: ""
        if (requestId.isBlank()) {
            Toast.makeText(this, "Request ID missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadAllTechnicians()
        loadRequest()

        btnApprove.setOnClickListener {
            val req = requestData
            if (req == null) {
                Toast.makeText(this, "Request not loaded yet", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showTechnicianSelectionForRequest()
        }

        btnReject.setOnClickListener {
            showRejectDialog()
        }
    }

    private fun loadRequest() {
        db.collection(FirestoreFields.REQUESTS).document(requestId)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Toast.makeText(this, "Request not found", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }

                val req = RequestData.fromFirestore(doc)
                requestData = req

                tvName.text = req.name
                tvAddress.text = req.address
                tvDateTime.text = "${req.date} • ${req.time}"

                selectedTechNames.clear()
                selectedTechIds.clear()

                // ***** ADD THIS: DISPLAY UNITS *****
                val acUnits = req.units.map { m ->
                    ACUnit(
                        brand = m["brand"]?.toString() ?: "",
                        pk = m["pk"]?.toString() ?: "",
                        workType = m["workType"]?.toString() ?: ""
                    )
                }

                rvDetailUnits.adapter = SimpleUnitsAdapter(acUnits)
                // **************************************
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load request: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadAllTechnicians(callback: (() -> Unit)? = null) {
        db.collection(FirestoreFields.USERS)
            .whereEqualTo("role", "technician")
            .get()
            .addOnSuccessListener { snap ->
                allTechNames.clear()
                allTechIds.clear()
                allTechDocs.clear()

                for (d in snap.documents) {
                    allTechNames.add(d.getString("name") ?: "Tanpa Nama")
                    allTechIds.add(d.id)
                    allTechDocs.add(d.data ?: emptyMap())
                }

                callback?.invoke()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal memuat teknisi", Toast.LENGTH_SHORT).show()
                callback?.invoke()
            }
    }

    private fun showTechnicianSelectionForRequest() {
        val req = requestData ?: return
        val requestDateStr = req.date ?: ""

        val namesArray = allTechNames.toTypedArray()
        val idsArray = allTechIds.toTypedArray()
        val checked = BooleanArray(namesArray.size) { index ->
            selectedTechIds.contains(idsArray[index])
        }
        val availability = BooleanArray(namesArray.size) { index ->
            val docFields = allTechDocs.getOrNull(index) ?: emptyMap<String, Any>()
            technicianIsUnavailableForDate(docFields, requestDateStr)
        }

        AlertDialog.Builder(this)
            .setTitle("Pilih Teknisi untuk tanggal $requestDateStr")
            .setMultiChoiceItems(namesArray, checked) { dialogInterface, which, isChecked ->
                if (availability[which]) {
                    (dialogInterface as? AlertDialog)?.listView?.setItemChecked(which, false)
                    checked[which] = false
                    Toast.makeText(this, "${namesArray[which]} is unavailable on $requestDateStr", Toast.LENGTH_SHORT).show()
                } else {
                    checked[which] = isChecked
                }
            }
            .setPositiveButton("OK") { d, _ ->
                val selectedIds = mutableListOf<String>()
                val selectedNames = mutableListOf<String>()
                for (i in checked.indices) {
                    if (checked[i]) {
                        selectedIds.add(idsArray[i])
                        selectedNames.add(namesArray[i])
                    }
                }

                if (selectedIds.isEmpty()) {
                    Toast.makeText(this, "Pilih teknisi dahulu", Toast.LENGTH_SHORT).show()
                } else {
                    selectedTechIds.clear()
                    selectedTechNames.clear()
                    selectedTechIds.addAll(selectedIds)
                    selectedTechNames.addAll(selectedNames)
                    confirmApprove()
                }
                d.dismiss()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun confirmApprove() {
        val updates = hashMapOf<String, Any>(
            "status" to "confirmed",
            "technician" to selectedTechNames,
            "assignedTechnicianIds" to selectedTechIds,
            "technicianIds" to selectedTechIds,
            "technicians" to selectedTechNames,
            "approvedAt" to Timestamp.now()
        )

        db.collection(FirestoreFields.REQUESTS).document(requestId)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Request approved.", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, LeaderDashboard::class.java))
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal menyimpan: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showRejectDialog() {
        AlertDialog.Builder(this)
            .setTitle("Reject Request")
            .setMessage("Yakin ingin menolak permintaan ini?")
            .setPositiveButton("Reject") { d, _ ->
                db.collection(FirestoreFields.REQUESTS).document(requestId)
                    .update("status", "rejected")
                    .addOnSuccessListener {
                        Toast.makeText(this, "Request rejected", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Gagal menolak: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                d.dismiss()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun technicianIsUnavailableForDate(docFields: Map<String, Any>, targetDateStr: String): Boolean {
        val rawFrom = docFields["unavailableFrom"]?.toString() ?: return false
        val rawTo = docFields["unavailableTo"]?.toString()

        if (rawFrom.isBlank() || rawFrom == "null") return false

        val df = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val target = try { df.parse(targetDateStr) } catch (e: Exception) { return false }
        val start = try { df.parse(rawFrom) } catch (e: Exception) { return false }

        val end = if (rawTo != null && rawTo != "null" && rawTo.isNotBlank()) {
            try { df.parse(rawTo) } catch (e: Exception) { null }
        } else null

        if (end == null) {
            return !target.before(start)
        }
        return !target.before(start) && !target.after(end)
    }
}
