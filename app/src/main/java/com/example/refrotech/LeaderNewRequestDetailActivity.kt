package com.example.refrotech

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore

class LeaderNewRequestDetailActivity : AppCompatActivity() {

    private lateinit var btnApprove: TextView
    private lateinit var btnReject: TextView

    private lateinit var tvDetailName: TextView
    private lateinit var tvDetailPhone: TextView
    private lateinit var tvDetailAddress: TextView
    private lateinit var tvDetailDateTime: TextView
    private lateinit var btnDetailMap: ImageView
    private lateinit var rvDetailUnits: androidx.recyclerview.widget.RecyclerView

    private val db = FirebaseFirestore.getInstance()
    private var requestId: String = ""

    // tech lists for selection
    private val techNames = mutableListOf<String>()
    private val techIds = mutableListOf<String>()
    private val selectedIndices = mutableSetOf<Int>()

    private var leaderId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_leader_new_request_detail)

        btnApprove = findViewById(R.id.btnDetailApprove)
        btnReject = findViewById(R.id.btnDetailReject)

        tvDetailName = findViewById(R.id.tvDetailName)
        tvDetailPhone = findViewById(R.id.tvDetailPhone)
        tvDetailAddress = findViewById(R.id.tvDetailAddress)
        tvDetailDateTime = findViewById(R.id.tvDetailDateTime)
        btnDetailMap = findViewById(R.id.btnDetailMap)
        rvDetailUnits = findViewById(R.id.rvDetailUnits)
        rvDetailUnits.layoutManager = LinearLayoutManager(this)

        requestId = intent.getStringExtra("requestId") ?: ""

        leaderId = getSharedPreferences("user", MODE_PRIVATE)
            .getString("uid", "") ?: ""

        if (requestId.isNotBlank()) {
            loadRequestDetails(requestId)
        } else {
            Toast.makeText(this, "Request ID missing", Toast.LENGTH_SHORT).show()
            finish()
        }

        btnApprove.setOnClickListener {
            // Show technician selection dialog, then create schedule and update request
            showTechnicianSelectionDialogAndApprove()
        }

        btnReject.setOnClickListener {
            showRejectReasonDialog()
        }
    }

    private fun loadRequestDetails(id: String) {
        db.collection("requests").document(id).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Toast.makeText(this, "Permintaan Tidak Ditemukan", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }

                val req = RequestData.fromFirestore(doc)
                tvDetailName.text = req.name
                tvDetailPhone.text = req.phone
                tvDetailAddress.text = req.address
                tvDetailDateTime.text = "${req.date} • ${req.time}"

                btnDetailMap.setOnClickListener {
                    val link = req.mapLink
                    if (!link.isNullOrBlank()) {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                        startActivity(intent)
                    } else {
                        Toast.makeText(this, "Link Maps tidak diberikan", Toast.LENGTH_SHORT).show()
                    }
                }

                val acUnits = req.units.map { m ->
                    ACUnit(
                        brand = m["brand"]?.toString() ?: "-",
                        pk = m["pk"]?.toString() ?: "-",
                        workType = m["workType"]?.toString() ?: "-"
                    )
                }
                rvDetailUnits.adapter = SimpleUnitsAdapter(acUnits)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load request: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showTechnicianSelectionDialogAndApprove() {
        // load technicians if not loaded
        db.collection("users")
            .whereEqualTo("role", "technician")
            .get()
            .addOnSuccessListener { res ->
                techNames.clear(); techIds.clear(); selectedIndices.clear()
                for (d in res) {
                    techNames.add(d.getString("name") ?: d.getString("username") ?: "Tanpa Nama")
                    techIds.add(d.id)
                }

                if (techNames.isEmpty()) {
                    Toast.makeText(this, "Tidak ada teknisi tersedia", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val checked = BooleanArray(techNames.size)
                val namesArray = techNames.toTypedArray()
                val builder = AlertDialog.Builder(this)
                    .setTitle("Pilih teknisi untuk jadwal ini")
                    .setMultiChoiceItems(namesArray, checked) { _, which, isChecked ->
                        if (isChecked) selectedIndices.add(which) else selectedIndices.remove(which)
                    }
                    .setPositiveButton("Buat Jadwal") { dialog, _ ->
                        if (selectedIndices.isEmpty()) {
                            Toast.makeText(this, "Pilih minimal 1 teknisi", Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }
                        createScheduleFromRequestAndConfirm()
                        dialog.dismiss()
                    }
                    .setNegativeButton("Batal", null)
                builder.show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal memuat teknisi: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun createScheduleFromRequestAndConfirm() {
        // first fetch the request doc fields
        db.collection("requests").document(requestId).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Toast.makeText(this, "Request not found", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val date = doc.getString("date") ?: ""
                val time = doc.getString("time") ?: ""
                val customerName = doc.getString("name") ?: ""
                val address = doc.getString("address") ?: ""

                val selectedNames = selectedIndices.map { techNames[it] }
                val selectedIds = selectedIndices.map { techIds[it] }

                val data = hashMapOf<String, Any>(
                    "requestId" to requestId,
                    "origin" to "request",
                    "date" to date,
                    "time" to time,
                    "customerName" to customerName,
                    "address" to address,
                    "technicians" to selectedNames.joinToString(", "),
                    "technicianIds" to selectedIds,
                    "status" to "assigned",
                    "jobStatus" to "assigned",
                    "leaderId" to leaderId,
                    "createdAt" to com.google.firebase.Timestamp.now()
                )

                // create schedule and update request
                db.collection("schedules")
                    .add(data)
                    .addOnSuccessListener { scheduleRef ->
                        db.collection("requests").document(requestId)
                            .update(mapOf("status" to "confirmed", "scheduleId" to scheduleRef.id))
                            .addOnSuccessListener {
                                Toast.makeText(this, "Request confirmed and schedule created", Toast.LENGTH_SHORT).show()
                                finish()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Failed to update request: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to create schedule: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error reading request: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showRejectReasonDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_reject_reason, null, false)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.radioRejectReasons)
        val edtOther = dialogView.findViewById<EditText>(R.id.edtOtherReason)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Pilih Alasan Penolakan")
            .setView(dialogView)
            .setPositiveButton("Submit") { _, _ ->
                val selectedId = radioGroup.checkedRadioButtonId
                val finalReason = if (selectedId != -1 && selectedId != R.id.rbOther) {
                    dialogView.findViewById<RadioButton>(selectedId).text.toString()
                } else {
                    edtOther.text.toString().ifBlank { "No reason provided" }
                }
                rejectRequest(finalReason)
            }
            .setNegativeButton("Batal", null)
            .create()
        dialog.show()
    }

    private fun rejectRequest(reason: String) {
        db.collection("requests")
            .document(requestId)
            .update(mapOf("status" to "rejected", "rejectReason" to reason, "previousStatus" to "pending"))
            .addOnSuccessListener {
                Toast.makeText(this, "Request rejected: $reason", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
