package com.example.refrotech

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class LeaderRescheduleDetailActivity : AppCompatActivity() {

    private lateinit var btnApprove: TextView
    private lateinit var btnReject: TextView

    private lateinit var tvDetailName: TextView
    private lateinit var tvDetailPhone: TextView
    private lateinit var tvDetailAddress: TextView
    private lateinit var tvOldDate: TextView
    private lateinit var tvOldTime: TextView
    private lateinit var tvNewDate: TextView
    private lateinit var tvNewTime: TextView
    private lateinit var rvDetailUnits: androidx.recyclerview.widget.RecyclerView
    private lateinit var btnDetailMap: ImageView

    private val db = FirebaseFirestore.getInstance()
    private var requestId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_leader_reschedule_detail)

        btnApprove = findViewById(R.id.btnDetailApprove)
        btnReject = findViewById(R.id.btnDetailReject)

        tvDetailName = findViewById(R.id.tvDetailName)
        tvDetailPhone = findViewById(R.id.tvDetailPhone)
        tvDetailAddress = findViewById(R.id.tvDetailAddress)

        tvOldDate = findViewById(R.id.tvOldDate)
        tvOldTime = findViewById(R.id.tvOldTime)
        tvNewDate = findViewById(R.id.tvNewDate)
        tvNewTime = findViewById(R.id.tvNewTime)

        rvDetailUnits = findViewById(R.id.rvDetailUnits)
        rvDetailUnits.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)

        btnDetailMap = findViewById(R.id.btnDetailMap)

        requestId = intent.getStringExtra("requestId") ?: ""

        if (requestId.isNotBlank()) {
            loadRequestDetails(requestId)
        } else {
            Toast.makeText(this, "Request ID missing", Toast.LENGTH_SHORT).show()
            finish()
        }

        btnApprove.setOnClickListener {
            if (requestId.isBlank()) return@setOnClickListener
            db.collection("requests")
                .document(requestId)
                .update(
                    mapOf(
                        "status" to "confirmed",
                        "previousStatus" to "confirmed"
                    )
                )
                .addOnSuccessListener {
                    Toast.makeText(this, "Reschedule approved", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        btnReject.setOnClickListener {
            if (requestId.isBlank()) return@setOnClickListener
            showRejectDialog()
        }
    }

    private fun loadRequestDetails(id: String) {
        db.collection("requests").document(id).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Toast.makeText(this, "Request not found", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }

                val req = RequestData.fromFirestore(doc)
                tvDetailName.text = req.name
                tvDetailPhone.text = req.phone
                tvDetailAddress.text = req.address

                tvOldDate.text = req.oldDate ?: req.date
                tvOldTime.text = req.oldTime ?: req.time
                tvNewDate.text = req.newDate ?: req.date
                tvNewTime.text = req.newTime ?: req.time

                btnDetailMap.setOnClickListener {
                    val link = req.mapLink
                    if (!link.isNullOrBlank()) {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(link))
                        startActivity(intent)
                    } else {
                        Toast.makeText(this, "No map link provided", Toast.LENGTH_SHORT).show()
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

    private fun showRejectDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_reject_reason, null, false)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.radioRejectReasons)
        val edtOther = dialogView.findViewById<EditText>(R.id.edtOtherReason)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Select Reject Reason")
            .setView(dialogView)
            .setPositiveButton("Confirm") { _, _ ->
                val selectedId = radioGroup.checkedRadioButtonId
                val finalReason = if (selectedId != -1) {
                    dialogView.findViewById<RadioButton>(selectedId).text.toString()
                } else {
                    edtOther.text.toString().ifBlank { "No reason provided" }
                }
                rejectRequest(finalReason)
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
    }

    private fun rejectRequest(reason: String) {
        db.collection("requests")
            .document(requestId)
            .update(
                mapOf(
                    "status" to "rejected",
                    "rejectReason" to reason,
                    "previousStatus" to "confirmed"
                )
            )
            .addOnSuccessListener {
                Toast.makeText(this, "Rejected: $reason", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
