package com.example.refrotech

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore

/**
 * LeaderRescheduleDetailActivity : Handles reschedule detail display and technician assignment.
 *
 * Behaviour:
 *  - Shows old schedule & proposed new schedule (if any)
 *  - Approve -> leader assigns technicians and the request's date/time is set to the proposed values
 *  - Reject  -> leader provides reject reason; request is marked "rejected"
 *
 * Important behaviour detail (per user choice "C"):
 *  - Technician availability for reschedule is determined solely by the
 *    technician's unavailableFrom / unavailableTo fields.
 *  - Existing assignments on other requests DO NOT make a technician unavailable.
 *  - The dialog shows ALL technicians. Unavailable ones are shown but cannot be selected.
 *  - If none of the technicians are available for the proposed date, the dialog will
 *    show all technicians but keep them unselectable (matches AddSchedulePage behaviour).
 */
class LeaderRescheduleDetailActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    private lateinit var tvDetailName: android.widget.TextView
    private lateinit var tvDetailPhone: android.widget.TextView
    private lateinit var tvDetailAddress: android.widget.TextView
    private lateinit var tvOldDate: android.widget.TextView
    private lateinit var tvOldTime: android.widget.TextView
    private lateinit var tvNewDate: android.widget.TextView
    private lateinit var tvNewTime: android.widget.TextView
    private lateinit var btnDetailMap: android.widget.ImageView
    private lateinit var rvDetailUnits: RecyclerView
    private lateinit var btnDetailApprove: android.widget.TextView
    private lateinit var btnDetailReject: android.widget.TextView

    private var requestId: String = ""
    private var newDate: String = ""
    private var newTime: String = ""

    // caches for assigning technicians
    private val availableTechNames = mutableListOf<String>()
    private val availableTechIds = mutableListOf<String>()
    private val availableTechDocs = mutableListOf<Map<String, Any>>() // raw doc map

    // assigned technician ids from the request document (if any)
    private val assignedTechnicianIdsFromRequest = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_leader_reschedule_detail)

        // bind views
        tvDetailName = findViewById(R.id.tvDetailName)
        tvDetailPhone = findViewById(R.id.tvDetailPhone)
        tvDetailAddress = findViewById(R.id.tvDetailAddress)
        tvOldDate = findViewById(R.id.tvOldDate)
        tvOldTime = findViewById(R.id.tvOldTime)
        tvNewDate = findViewById(R.id.tvNewDate)
        tvNewTime = findViewById(R.id.tvNewTime)
        btnDetailMap = findViewById(R.id.btnDetailMap)
        rvDetailUnits = findViewById(R.id.rvDetailUnits)
        btnDetailApprove = findViewById(R.id.btnDetailApprove)
        btnDetailReject = findViewById(R.id.btnDetailReject)

        rvDetailUnits.layoutManager = LinearLayoutManager(this)

        requestId = intent.getStringExtra("requestId") ?: ""
        if (requestId.isBlank()) {
            Toast.makeText(this, "Request ID missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadRequestDetails(requestId)

        btnDetailMap.setOnClickListener {
            // Open map link if present
            db.collection(FirestoreFields.REQUESTS).document(requestId).get()
                .addOnSuccessListener { doc ->
                    val link = doc.getString("mapLink") ?: ""
                    if (link.isNotBlank()) {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                    } else {
                        Toast.makeText(this, "No map link provided", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to fetch map link", Toast.LENGTH_SHORT).show()
                }
        }

        btnDetailApprove.setOnClickListener {
            if (newDate.isBlank() || newTime.isBlank()) {
                Toast.makeText(this, "No proposed new schedule to approve", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showAssignTechnicianDialog()
        }

        btnDetailReject.setOnClickListener {
            showRejectDialog()
        }
    }

    /**
     * Load request details from Firestore and populate UI.
     * Also pulls assignedTechnicianIds (if present) so we can pre-check them in the dialog.
     */
    private fun loadRequestDetails(id: String) {
        db.collection(FirestoreFields.REQUESTS).document(id)
            .get()
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

                tvOldDate.text = req.oldDate ?: req.date ?: "-"
                tvOldTime.text = req.oldTime ?: req.time ?: "-"

                tvNewDate.text = req.newDate ?: "-"
                tvNewTime.text = req.newTime ?: "-"

                newDate = req.newDate ?: ""
                newTime = req.newTime ?: ""

                // capture assignedTechnicianIds from the document (fall back to technicianIds or empty)
                assignedTechnicianIdsFromRequest.clear()
                val assignedFromDoc = doc.get("assignedTechnicianIds") as? List<*>
                val techIdsFromDoc = doc.get("technicianIds") as? List<*>
                if (!assignedFromDoc.isNullOrEmpty()) {
                    assignedFromDoc.forEach { v -> if (v is String) assignedTechnicianIdsFromRequest.add(v) }
                } else if (!techIdsFromDoc.isNullOrEmpty()) {
                    techIdsFromDoc.forEach { v -> if (v is String) assignedTechnicianIdsFromRequest.add(v) }
                }

                // convert units to ACUnit and display
                val acUnits = (req.units ?: emptyList<Map<String, Any>>()).map { m ->
                    val brand = (m["brand"] ?: "").toString()
                    val pk = (m["pk"] ?: "").toString()
                    val workType = (m["workType"] ?: "").toString()
                    ACUnit(brand = brand, pk = pk, workType = workType)
                }
                rvDetailUnits.adapter = SimpleUnitsAdapter(acUnits)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load request: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Show an assign-technicians dialog.
     *
     * Implementation replaced the previous MultiChoice dialog with a RecyclerView using TechnicianMultiSelectAdapter.
     * Behaviour:
     * - show ALL technicians
     * - mark unavailable ones as unselectable (keeps visible)
     * - if none available, all remain unselectable (this matches AddSchedulePage behaviour)
     * - pre-check technicians that are already assigned in the request (assignedTechnicianIdsFromRequest)
     */
    private fun showAssignTechnicianDialog() {
        // load technicians from Firestore
        db.collection(FirestoreFields.USERS)
            .whereEqualTo("role", "technician")
            .get()
            .addOnSuccessListener { techSnap ->
                availableTechNames.clear()
                availableTechIds.clear()
                availableTechDocs.clear()

                for (d in techSnap.documents) {
                    val name = d.getString("name") ?: "Unknown"
                    availableTechNames.add(name)
                    availableTechIds.add(d.id)
                    availableTechDocs.add(d.data ?: mapOf<String, Any>())
                }

                if (availableTechNames.isEmpty()) {
                    Toast.makeText(this, "No technicians found", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // Build the list of TechItem for the adapter (matching TechnicianMultiSelectAdapter.TechItem)
                val items = mutableListOf<TechnicianMultiSelectAdapter.TechItem>()
                var anyAvailable = false
                for (i in availableTechNames.indices) {
                    val name = availableTechNames[i]
                    val id = availableTechIds[i]
                    val doc = availableTechDocs.getOrNull(i) ?: emptyMap<String, Any>()
                    val isUnavailable = isTechnicianUnavailableForCandidate(doc, newDate)
                    if (!isUnavailable) anyAvailable = true

                    // Pre-check if this tech was previously assigned on the request
                    val preChecked = assignedTechnicianIdsFromRequest.contains(id)

                    items.add(
                        TechnicianMultiSelectAdapter.TechItem(
                            id = id,
                            name = name,
                            status = if (isUnavailable) "Unavailable" else "Available",
                            disabled = isUnavailable,
                            checked = preChecked
                        )
                    )
                }

                // If none available -> mark all as disabled (this keeps existing checked flags but UI will not allow selection changes)
                if (!anyAvailable) {
                    for (idx in items.indices) {
                        items[idx] = items[idx].copy(disabled = true)
                    }
                }

                // Create RecyclerView for dialog
                val recycler = RecyclerView(this)
                recycler.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                recycler.layoutManager = LinearLayoutManager(this)
                val adapter = TechnicianMultiSelectAdapter(items.toMutableList()) {
                    // no-op selection changed callback; adapter updates item.checked
                }
                recycler.adapter = adapter

                val message = "Proposed: ${if (newDate.isBlank()) "-" else newDate} ${if (newTime.isBlank()) "" else "@ $newTime"}\n\n" +
                        "Unavailable technicians cannot be selected."

                val dialog = AlertDialog.Builder(this)
                    .setTitle("Assign Technicians")
                    .setMessage(message)
                    .setView(recycler)
                    .setPositiveButton("Confirm", null)
                    .setNegativeButton("Cancel", null)
                    .create()

                dialog.setOnShowListener {
                    val btnConfirm = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    btnConfirm.setOnClickListener {
                        // collect selected ids/names, but ensure none are disabled (guard)
                        val selectedIds = adapter.getSelectedIds().toMutableList()
                        val selectedNames = adapter.getSelectedNames().toMutableList()

                        // Double-check disabled -> should not be selected, but guard anyway
                        val itemsList = (0 until items.size).map { i -> items[i] }
                        for (it in itemsList) {
                            if (it.disabled && selectedIds.contains(it.id)) {
                                Toast.makeText(this, "${it.name} is unavailable and cannot be selected.", Toast.LENGTH_SHORT).show()
                                return@setOnClickListener
                            }
                        }

                        if (selectedIds.isEmpty()) {
                            Toast.makeText(this, "Select at least one technician", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }

                        // Persist assignment
                        saveAssignedSchedule(selectedIds, selectedNames)
                        dialog.dismiss()
                    }
                }

                dialog.show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load technicians: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Check technician unavailability for a candidate date string (yyyy-MM-dd expected).
     * Uses same logic you used elsewhere: unavailableFrom/unavailableTo (strings).
     *
     * IMPORTANT: returns FALSE (available) when fields are absent/blank/invalid.
     */
    private fun isTechnicianUnavailableForCandidate(docFields: Map<String, Any>, targetDateStr: String): Boolean {
        if (targetDateStr.isBlank()) return false

        // No fields? → AVAILABLE
        val rawFrom = docFields["unavailableFrom"]?.toString() ?: return false
        val rawTo = docFields["unavailableTo"]?.toString()

        // Values "null", "", or invalid → AVAILABLE
        if (rawFrom.isBlank() || rawFrom == "null") return false

        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())

        val target = try { dateFormat.parse(targetDateStr) } catch (e: Exception) { return false }
        val start = try { dateFormat.parse(rawFrom) } catch (e: Exception) { return false }

        val end = if (rawTo != null && rawTo != "null" && rawTo.isNotBlank()) {
            try { dateFormat.parse(rawTo) } catch (e: Exception) { null }
        } else null

        // Case 1: Indefinite unavailability
        if (end == null) {
            return !target.before(start)
        }

        // Case 2: Range
        return !target.before(start) && !target.after(end)
    }

    /**
     * Persist the assigned technicians into the request document.
     * Sets:
     *  - status -> "assigned"
     *  - jobStatus -> "assigned"
     *  - technician -> comma-separated names
     *  - technicians -> list of names
     *  - assignedTechnicianIds -> list of ids
     *  - technicianIds -> list of ids
     *  - date/time -> newDate/newTime
     */
    private fun saveAssignedSchedule(techIds: List<String>, techNames: List<String>) {
        if (requestId.isBlank()) return

        val updates = mapOf(
            "status" to "assigned",
            "jobStatus" to "assigned",
            "technician" to techNames.joinToString(", "),
            "technicians" to techNames,
            "technicianIds" to techIds,
            "assignedTechnicianIds" to techIds,
            "date" to newDate,
            "time" to newTime,
            "updatedAt" to Timestamp.now()
        )

        db.collection(FirestoreFields.REQUESTS).document(requestId)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Reschedule approved & technicians assigned", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save assignment: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Show reject dialog by inflating your existing dialog_reject_reason.xml and update the doc accordingly.
     */
    private fun showRejectDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_reject_reason, null)
        val radioGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.radioRejectReasons)
        val edtOther = dialogView.findViewById<android.widget.EditText>(R.id.edtOtherReason)
        edtOther.visibility = View.GONE

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            edtOther.visibility = if (checkedId == R.id.rbOther) View.VISIBLE else View.GONE
        }

        AlertDialog.Builder(this)
            .setTitle("Reject Reschedule")
            .setView(dialogView)
            .setPositiveButton("Submit") { _, _ ->
                val selectedId = radioGroup.checkedRadioButtonId
                val reason = if (selectedId == R.id.rbOther) {
                    edtOther.text.toString().trim()
                } else {
                    val rb = dialogView.findViewById<android.widget.RadioButton?>(selectedId)
                    rb?.text?.toString() ?: ""
                }

                if (reason.isBlank()) {
                    Toast.makeText(this, "Reason cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                rejectRequest(reason)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun rejectRequest(reason: String) {
        if (requestId.isBlank()) return
        db.collection(FirestoreFields.REQUESTS).document(requestId)
            .update(
                mapOf(
                    "status" to "rejected",
                    "rejectReason" to reason,
                    "updatedAt" to Timestamp.now()
                )
            )
            .addOnSuccessListener {
                Toast.makeText(this, "Reschedule rejected", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to reject: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
