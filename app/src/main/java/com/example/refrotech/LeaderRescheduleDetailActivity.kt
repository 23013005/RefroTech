package com.example.refrotech

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore

/**
 * LeaderRescheduleDetailActivity (fixed)
 *
 * - Loads a request
 * - Shows old schedule & proposed new schedule (if any)
 * - Approve -> leader assigns technicians and the request's date/time is set to the proposed values
 * - Reject  -> leader provides reject reason; request is marked "rejected"
 *
 * This file intentionally avoids referencing dialog layout IDs that do not exist in your repo.
 * Instead the Assign-Technician dialog is created with setMultiChoiceItems to avoid missing-XML-id errors.
 */
class LeaderRescheduleDetailActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    private lateinit var tvDetailName: TextView
    private lateinit var tvDetailPhone: TextView
    private lateinit var tvDetailAddress: TextView
    private lateinit var tvOldDate: TextView
    private lateinit var tvOldTime: TextView
    private lateinit var tvNewDate: TextView
    private lateinit var tvNewTime: TextView
    private lateinit var btnDetailMap: android.widget.ImageView
    private lateinit var rvDetailUnits: RecyclerView
    private lateinit var btnDetailApprove: TextView
    private lateinit var btnDetailReject: TextView

    private var requestId: String = ""
    private var newDate: String = ""
    private var newTime: String = ""

    // caches for assigning technicians
    private val availableTechNames = mutableListOf<String>()
    private val availableTechIds = mutableListOf<String>()
    private val availableTechDocs = mutableListOf<Map<String, Any>>() // raw doc map

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
     * Implementation uses AlertDialog.setMultiChoiceItems to avoid relying on a custom XML that may be missing.
     * We fetch technicians, compute simple availability for each on newDate, then present them as selectable items.
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

                // Prepare arrays for multi-choice dialog
                val namesArray = availableTechNames.toTypedArray()
                val checked = BooleanArray(namesArray.size) { false }
                val availability = BooleanArray(namesArray.size) { false } // true = unavailable

                // compute availability for each tech
                for (i in namesArray.indices) {
                    val doc = availableTechDocs.getOrNull(i) ?: emptyMap<String, Any>()
                    availability[i] = isTechnicianUnavailableForCandidate(doc, newDate)
                }

                // Build message with proposed new date/time
                val message = "Proposed: ${if (newDate.isBlank()) "-" else newDate} ${if (newTime.isBlank()) "" else "@ $newTime"}\n\n" +
                        "Unavailable technicians cannot be selected."

                val builder = AlertDialog.Builder(this)
                    .setTitle("Assign Technicians")
                    .setMessage(message)
                    .setMultiChoiceItems(namesArray, checked) { dialogInterface, which, isChecked ->
                        // if tech unavailable and user tries to check -> disallow
                        if (availability[which] && isChecked) {
                            // uncheck it immediately and show toast
                            (dialogInterface as? AlertDialog)?.listView?.setItemChecked(which, false)
                            checked[which] = false
                            Toast.makeText(this, "${namesArray[which]} is unavailable on $newDate", Toast.LENGTH_SHORT).show()
                        } else {
                            checked[which] = isChecked
                        }
                    }
                    .setPositiveButton("Confirm", null)
                    .setNegativeButton("Cancel", null)

                val dialog = builder.create()
                dialog.setOnShowListener {
                    val btnConfirm = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    btnConfirm.setOnClickListener {
                        val selectedIds = mutableListOf<String>()
                        for (i in checked.indices) {
                            if (checked[i]) selectedIds.add(availableTechIds[i])
                        }
                        if (selectedIds.isEmpty()) {
                            Toast.makeText(this, "Select at least one technician", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        saveAssignedSchedule(selectedIds)
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
     */
    private fun isTechnicianUnavailableForCandidate(docFields: Map<String, Any>, targetDateStr: String): Boolean {
        if (targetDateStr.isBlank()) return false
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val from = docFields["unavailableFrom"]?.toString() ?: return false
        val to = docFields["unavailableTo"]?.toString()

        val target = try { dateFormat.parse(targetDateStr) } catch (e: Exception) { return false }
        val start = try { dateFormat.parse(from) } catch (e: Exception) { return false }

        if (to.isNullOrBlank()) {
            // unavailable from 'start' indefinitely
            return !target.before(start)
        }

        val end = try { dateFormat.parse(to) } catch (e: Exception) { return false }
        return !target.before(start) && !target.after(end)
    }

    /**
     * Persist the assigned technicians into the request document.
     * Sets:
     *  - status -> "assigned"
     *  - jobStatus -> "assigned"
     *  - technician -> comma-separated names
     *  - assignedTechnicianIds -> list of ids
     *  - date/time -> newDate/newTime
     */
    private fun saveAssignedSchedule(techIds: List<String>) {
        if (requestId.isBlank()) return

        val names = techIds.mapNotNull { id ->
            val idx = availableTechIds.indexOf(id)
            if (idx >= 0) availableTechNames.getOrNull(idx) else null
        }

        val updates = mapOf(
            "status" to "assigned",
            "jobStatus" to "assigned",
            "technician" to names.joinToString(", "),
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
