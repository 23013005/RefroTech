package com.example.refrotech

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore

class LeaderNewRequestDetailActivity : AppCompatActivity() {

    private lateinit var btnApprove: View
    private lateinit var btnReject: View

    private lateinit var tvDetailName: TextView
    private lateinit var tvDetailPhone: TextView
    private lateinit var tvDetailAddress: TextView
    private lateinit var tvDetailDateTime: TextView
    private lateinit var btnDetailMap: ImageView
    private lateinit var rvDetailUnits: RecyclerView

    private val db = FirebaseFirestore.getInstance()
    private var requestId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_leader_request_detail)

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

        if (requestId.isNotBlank()) {
            loadRequestDetails(requestId)
        } else {
            Toast.makeText(this, "Request ID missing", Toast.LENGTH_SHORT).show()
            finish()
        }

        btnApprove.setOnClickListener {
            if (requestId.isBlank()) return@setOnClickListener
            val updates = mapOf(
                "status" to "confirmed",
                "previousStatus" to "pending"
            )
            db.collection("requests")
                .document(requestId)
                .update(updates)
                .addOnSuccessListener {
                    Toast.makeText(this, "Request confirmed", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to confirm: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        btnReject.setOnClickListener {
            if (requestId.isBlank()) return@setOnClickListener
            // reuse dialog_reject_reason, as original code
            val dialogView = layoutInflater.inflate(R.layout.dialog_reject_reason, null)
            val radioGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.radioRejectReasons)
            val etOther = dialogView.findViewById<android.widget.EditText>(R.id.edtOtherReason)

            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Select Reject Reason")
                .setView(dialogView)
                .setPositiveButton("Confirm") { _, _ ->
                    val selectedId = radioGroup.checkedRadioButtonId
                    val selectedText = if (selectedId != -1) {
                        dialogView.findViewById<android.widget.RadioButton>(selectedId).text.toString()
                    } else ""
                    val finalReason = if (selectedId == R.id.rbOther) {
                        etOther.text.toString().trim()
                    } else selectedText

                    if (finalReason.isBlank()) {
                        Toast.makeText(this, "Please select or enter a reason.", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    val updates = mapOf(
                        "status" to "rejected",
                        "rejectReason" to finalReason,
                        "previousStatus" to "pending"
                    )

                    db.collection("requests")
                        .document(requestId)
                        .update(updates)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Request rejected.", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Failed to reject: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
                .setNegativeButton("Cancel", null)
                .show()
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
                tvDetailDateTime.text = "${req.date} • ${req.time}"

                // Map button
                btnDetailMap.setOnClickListener {
                    val link = req.mapLink
                    if (!link.isNullOrBlank()) {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                        startActivity(intent)
                    } else {
                        Toast.makeText(this, "No map link provided", Toast.LENGTH_SHORT).show()
                    }
                }

                // Units list
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
}
