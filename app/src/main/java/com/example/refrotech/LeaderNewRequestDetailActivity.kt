package com.example.refrotech

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

/**
 * When leader approves a request, they can assign multiple technicians.
 * Technicians listed as unavailable for request date will be excluded from selection.
 */
class LeaderNewRequestDetailActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private lateinit var tvName: TextView
    private lateinit var tvAddress: TextView
    private lateinit var tvDateTime: TextView
    private lateinit var btnApprove: FrameLayout
    private lateinit var btnReject: FrameLayout

    private var requestId: String = ""
    private lateinit var requestData: RequestData

    private val allTechNames = mutableListOf<String>()
    private val allTechIds = mutableListOf<String>()
    private val allTechDocs = mutableListOf<Map<String, Any>>()

    private val selectedTechNames = mutableListOf<String>()
    private val selectedTechIds = mutableListOf<String>()

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

        // load all technicians for selection; when showing selection, filter by request date
        loadAllTechnicians()
        loadRequest()

        btnApprove.setOnClickListener {
            if (selectedTechIds.isEmpty()) {
                Toast.makeText(this, "Pilih teknisi terlebih dahulu.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            confirmApprove()
        }

        btnReject.setOnClickListener {
            showRejectDialog()
        }
    }

    private fun loadAllTechnicians() {
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
                    allTechDocs.add(d.data ?: mapOf<String, Any>())
                }
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
                requestData = RequestData.fromFirestore(doc)

                tvName.text = requestData.name
                tvAddress.text = requestData.address
                tvDateTime.text = "${requestData.date} • ${requestData.time}"

                // No pre-assigned technicians on requests: leader chooses manually
                selectedTechNames.clear()
                selectedTechIds.clear()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load request: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Show a technician selection dialog filtered by the request date.
     */
    private fun showTechnicianSelectionForRequest() {
        val requestDateStr = requestData.date

        val visibleNames = mutableListOf<String>()
        val visibleIds = mutableListOf<String>()

        for (i in allTechNames.indices) {
            val docFields = if (i < allTechDocs.size) allTechDocs[i] else emptyMap<String, Any>()
            val techId = allTechIds.getOrNull(i) ?: continue
            val techName = allTechNames[i]
            val isUnavailable = technicianIsUnavailableForDate(docFields, requestDateStr)
            if (!isUnavailable) {
                visibleNames.add(techName)
                visibleIds.add(techId)
            }
        }

        if (visibleNames.isEmpty()) {
            Toast.makeText(this, "Tidak ada teknisi tersedia untuk tanggal $requestDateStr", Toast.LENGTH_SHORT).show()
            return
        }

        val selected = BooleanArray(visibleNames.size)
        visibleNames.forEachIndexed { idx, name ->
            selected[idx] = selectedTechNames.contains(name)
        }

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
                // show chosen names somewhere, or just keep them for approve
                d.dismiss()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun technicianIsUnavailableForDate(docFields: Map<String, Any>, targetDateStr: String): Boolean {
        val arr = docFields["unavailablePeriods"] as? List<*>
        if (arr.isNullOrEmpty()) return false

        val targetDate = try {
            dateFormat.parse(targetDateStr) ?: return false
        } catch (e: Exception) {
            return false
        }

        @Suppress("UNCHECKED_CAST")
        for (item in arr) {
            val m = item as? Map<*, *> ?: continue
            val s = m["start"]?.toString() ?: continue
            val e = m["end"]?.toString() ?: continue
            val start = try { dateFormat.parse(s) } catch (_: Exception) { null }
            val end = try { dateFormat.parse(e) } catch (_: Exception) { null }
            if (start == null || end == null) continue
            if (!targetDate.before(start) && !targetDate.after(end)) return true
        }
        return false
    }

    private fun confirmApprove() {
        val unitsFromRequest = requestData.units.map { unit ->
            mapOf(
                "brand" to (unit["brand"] ?: ""),
                "pk" to (unit["pk"] ?: ""),
                "workType" to (unit["workType"] ?: "")
            )
        }

        val scheduleDoc = hashMapOf<String, Any>(
            "origin" to "request",
            "customerName" to requestData.name,
            "address" to requestData.address,
            "date" to requestData.date,
            "time" to requestData.time,
            FirestoreFields.FIELD_TECHNICIANS to selectedTechNames,
            FirestoreFields.FIELD_TECHNICIAN_IDS to selectedTechIds,
            FirestoreFields.FIELD_ASSIGNED_TECHNICIAN_IDS to selectedTechIds,
            "units" to unitsFromRequest,
            FirestoreFields.FIELD_JOB_STATUS to "assigned",
            "status" to "assigned",
            "requestId" to requestId,
            FirestoreFields.FIELD_DOCUMENTATION to requestData.documentation.ifEmpty { listOf<String>() },
            "createdAt" to Timestamp.now()
        )

        db.collection(FirestoreFields.SCHEDULES)
            .add(scheduleDoc)
            .addOnSuccessListener { docRef ->
                val updates = hashMapOf<String, Any>(
                    FirestoreFields.FIELD_SCHEDULE_ID to docRef.id,
                    FirestoreFields.FIELD_JOB_STATUS to "assigned",
                    FirestoreFields.FIELD_TECHNICIAN_IDS to selectedTechIds
                )
                db.collection(FirestoreFields.REQUESTS).document(requestId)
                    .update(updates as Map<String, Any>)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Request approved and schedule created.", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, LeaderConfirmationActivity::class.java))
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Schedule created but failed to update request: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to create schedule: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun showRejectDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_reject_reason, null)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Tolak Permintaan")
            .setView(view)
            .setPositiveButton("Kirim") { d, _ ->
                val radioGroup = view.findViewById<RadioGroup>(R.id.radioRejectReasons)
                val selectedId = radioGroup.checkedRadioButtonId
                val reason = when (selectedId) {
                    R.id.rbTechUnavailable -> "Teknisi Tidak Tersedia"
                    R.id.rbCustomerUnreachable -> "Jadwal Penuh"
                    R.id.rbWrongAddress -> "Lokasi Terlalu Jauh"
                    R.id.rbOther -> view.findViewById<EditText>(R.id.edtOtherReason).text.toString().trim()
                    else -> "Ditolak"
                }

                db.collection(FirestoreFields.REQUESTS).document(requestId)
                    .update(mapOf("status" to "rejected", "rejectReason" to reason))
                    .addOnSuccessListener {
                        Toast.makeText(this, "Permintaan ditolak.", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, LeaderConfirmationActivity::class.java))
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Gagal menolak: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                d.dismiss()
            }
            .setNegativeButton("Batal", null)
            .create()

        dialog.show()
    }
}
