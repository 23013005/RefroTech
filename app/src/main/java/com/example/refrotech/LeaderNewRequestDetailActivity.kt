package com.example.refrotech

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
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

    private var requestId: String = ""
    private var requestData: RequestData? = null   // FIXED: nullable to avoid crash

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
            }
    }

    private fun showTechnicianSelectionForRequest() {
        val req = requestData ?: return
        val requestDateStr = req.date ?: ""

        val visibleNames = mutableListOf<String>()
        val visibleIds = mutableListOf<String>()

        for (i in allTechNames.indices) {
            val techId = allTechIds[i]
            val techName = allTechNames[i]
            val docFields = allTechDocs[i]
            val isUnavailable = technicianIsUnavailableForDate(docFields, requestDateStr)

            if (!isUnavailable) {
                visibleNames.add(techName)
                visibleIds.add(techId)
            }
        }

        if (visibleNames.isEmpty()) {
            Toast.makeText(this, "Tidak ada teknisi tersedia pada $requestDateStr", Toast.LENGTH_SHORT).show()
            return
        }

        val selected = BooleanArray(visibleNames.size) { selectedTechNames.contains(visibleNames[it]) }

        AlertDialog.Builder(this)
            .setTitle("Pilih Teknisi")
            .setMultiChoiceItems(visibleNames.toTypedArray(), selected) { _, which, checked ->
                val name = visibleNames[which]
                val id = visibleIds[which]

                if (checked) {
                    if (!selectedTechNames.contains(name)) {
                        selectedTechNames.add(name)
                        selectedTechIds.add(id)
                    }
                } else {
                    selectedTechNames.remove(name)
                    selectedTechIds.remove(id)
                }
            }
            .setPositiveButton("OK") { d, _ ->
                if (selectedTechIds.isEmpty()) {
                    Toast.makeText(this, "Pilih teknisi dahulu", Toast.LENGTH_SHORT).show()
                } else {
                    confirmApprove()
                }
                d.dismiss()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun confirmApprove() {
        val updates = mapOf(
            "status" to "confirmed",                       // FIXED
            "technician" to selectedTechNames,
            "assignedTechnicianIds" to selectedTechIds,
            "technicianIds" to selectedTechIds,
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
        val from = docFields["unavailableFrom"]?.toString() ?: return false
        val to = docFields["unavailableTo"]?.toString()

        val target = try { dateFormat.parse(targetDateStr) } catch (e: Exception) { return false }
        val start = try { dateFormat.parse(from) } catch (e: Exception) { return false }

        if (to.isNullOrBlank()) return !target.before(start)

        val end = try { dateFormat.parse(to) } catch (e: Exception) { return false }
        return !target.before(start) && !target.after(end)
    }
}
