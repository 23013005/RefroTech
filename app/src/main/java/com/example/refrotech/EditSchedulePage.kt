package com.example.refrotech

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

/**
 * EditSchedulePage — parity with AddSchedulePage.
 * Loads and saves units, supports add/edit/delete inside dialog.
 *
 * Also loads documentation (read-only) for both schedule documents and confirmed requests shown as schedules.
 * Leaders can update status from this page (same transitions available to technicians).
 */
class EditSchedulePage : AppCompatActivity() {

    private lateinit var etTime: EditText
    private lateinit var etTechnician: EditText
    private lateinit var etCustomer: EditText
    private lateinit var etAddress: EditText
    // Rating display
    private lateinit var ratingContainer: LinearLayout
    private lateinit var ratingBarLeader: RatingBar
    private lateinit var tvRatingComment: TextView
    private lateinit var tvRatingDate: TextView


    private lateinit var recyclerUnits: RecyclerView
    private lateinit var btnAddUnit: FrameLayout
    private lateinit var btnSave: FrameLayout
    private lateinit var btnDelete: FrameLayout

    // NEW: documentation viewer (read-only)
    private lateinit var recyclerDocs: androidx.recyclerview.widget.RecyclerView
    private lateinit var docsAdapter: DocumentationPreviewAdapter

    // NEW: status spinner for leader to change work status
    private lateinit var spinnerStatus: Spinner

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    private val allTechnicianNames = mutableListOf<String>()
    private val allTechnicianIds = mutableListOf<String>()
    private val allTechnicianDocs = mutableListOf<Map<String, Any>>()

    private val selectedTechNames = mutableListOf<String>()
    private val selectedTechIds = mutableListOf<String>()

    private val units = mutableListOf<ACUnit>()
    private lateinit var unitsAdapter: ACUnitAdapter

    private var scheduleId: String = ""
    private var scheduleDate: String = ""
    private var origin: String = "schedule" // "schedule" or "request"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_schedule_page)

        etTime = findViewById(R.id.etTime)
        etTechnician = findViewById(R.id.etTechnician)
        etCustomer = findViewById(R.id.etCustomer)
        etAddress = findViewById(R.id.etAddress)
        recyclerUnits = findViewById(R.id.recyclerUnits)
        btnAddUnit = findViewById(R.id.btnAddUnit)
        btnSave = findViewById(R.id.btnSave)
        btnDelete = findViewById(R.id.btnDelete)
        ratingContainer = findViewById(R.id.ratingContainer)
        ratingBarLeader = findViewById(R.id.ratingBarLeader)
        tvRatingComment = findViewById(R.id.tvRatingComment)
        tvRatingDate = findViewById(R.id.tvRatingDate)


        // NEW views
        recyclerDocs = findViewById(R.id.recyclerDocs)
        spinnerStatus = findViewById(R.id.spinnerStatus)

        // units setup
        unitsAdapter = ACUnitAdapter(units) { index ->
            showAddEditUnitDialog(index)
        }
        recyclerUnits.layoutManager = LinearLayoutManager(this)
        recyclerUnits.adapter = unitsAdapter

        // docs setup: read-only, so pass empty onDelete and use mutable list internally
        docsAdapter = DocumentationPreviewAdapter(mutableListOf(), onDelete = null)
        recyclerDocs.layoutManager = GridLayoutManager(this, 3)
        recyclerDocs.adapter = docsAdapter

        etTime.setOnClickListener { showTimePicker() }
        etTechnician.setOnClickListener { loadAllTechnicians { showTechnicianDialog() } }

        scheduleId = intent.getStringExtra("scheduleId") ?: ""
        scheduleDate = intent.getStringExtra("date") ?: dateFormat.format(Date())
        origin = intent.getStringExtra("origin") ?: "schedule"

        loadAllTechnicians()
        if (scheduleId.isNotBlank()) loadSchedule()

        btnAddUnit.setOnClickListener { showAddEditUnitDialog(null) }
        btnSave.setOnClickListener { updateSchedule() }
        btnDelete.setOnClickListener { confirmDeleteSchedule() }

        setupStatusSpinner()
    }

    private fun setupStatusSpinner() {
        // IMPORTANT: remove "pending" as an option for the leader.
        // We add an explicit placeholder at index 0 which means "no change".
        // Index 1..N represent valid status transitions the leader can select.
        val statuses = listOf("-- Change status --", "Confirmed", "On-Progress", "Completed")
        val adapterSpinner = ArrayAdapter(this, android.R.layout.simple_spinner_item, statuses)
        adapterSpinner.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerStatus.adapter = adapterSpinner
    }

    private fun showTimePicker() {
        val c = Calendar.getInstance()
        val hour = c.get(Calendar.HOUR_OF_DAY)
        val minute = c.get(Calendar.MINUTE)
        TimePickerDialog(this, { _, h, m ->
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, h)
            cal.set(Calendar.MINUTE, m)
            etTime.setText(timeFormat.format(cal.time))
        }, hour, minute, true).show()
    }

    private fun showAddEditUnitDialog(editIndex: Int?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_unit, null)
        val etBrand = dialogView.findViewById<EditText>(R.id.etBrand)
        val etPK = dialogView.findViewById<EditText>(R.id.etPK)
        val spinner = dialogView.findViewById<Spinner>(R.id.spinnerWorkType)

        val workTypes = listOf("Service", "Installation", "Repairment")
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, workTypes)

        if (editIndex != null && editIndex in units.indices) {
            val u = units[editIndex]
            etBrand.setText(u.brand)
            etPK.setText(u.pk)
            spinner.setSelection(workTypes.indexOf(u.workType).takeIf { it >= 0 } ?: 0)
        } else {
            spinner.setSelection(0)
        }

        val alert = AlertDialog.Builder(this)
            .setTitle(if (editIndex == null) "Tambah Unit" else "Edit Unit")
            .setView(dialogView)
            .setPositiveButton("Simpan", null)
            .setNegativeButton("Batal", null)
            .setNeutralButton(if (editIndex != null) "Hapus" else "", null)
            .create()

        alert.setOnShowListener {
            val btnSave = alert.getButton(AlertDialog.BUTTON_POSITIVE)
            btnSave.setOnClickListener {
                val brand = etBrand.text?.toString()?.trim() ?: ""
                val pk = etPK.text?.toString()?.trim() ?: ""
                val workType = spinner.selectedItem?.toString() ?: ""

                if (pk.isEmpty()) {
                    etPK.error = "Jumlah PK wajib diisi"
                    etPK.requestFocus()
                    return@setOnClickListener
                }

                val newUnit = ACUnit(brand = brand, pk = pk, workType = workType)
                if (editIndex == null) {
                    units.add(newUnit)
                    unitsAdapter.notifyItemInserted(units.size - 1)
                } else {
                    units[editIndex] = newUnit
                    unitsAdapter.notifyItemChanged(editIndex)
                }
                alert.dismiss()
            }

            val btnDelete = alert.getButton(AlertDialog.BUTTON_NEUTRAL)
            if (editIndex == null) btnDelete.visibility = View.GONE
            btnDelete.setOnClickListener {
                if (editIndex != null && editIndex in units.indices) {
                    units.removeAt(editIndex)
                    unitsAdapter.notifyItemRemoved(editIndex)
                    unitsAdapter.notifyItemRangeChanged(editIndex, units.size - editIndex)
                }
                alert.dismiss()
            }
        }

        alert.show()
    }

    private fun loadAllTechnicians(callback: (() -> Unit)? = null) {
        db.collection(FirestoreFields.USERS)
            .whereEqualTo("role", "technician")
            .get()
            .addOnSuccessListener { snap ->
                allTechnicianNames.clear()
                allTechnicianIds.clear()
                allTechnicianDocs.clear()
                for (d in snap.documents) {
                    allTechnicianNames.add(d.getString("name") ?: "Tanpa Nama")
                    allTechnicianIds.add(d.id)
                    allTechnicianDocs.add(d.data ?: mapOf())
                }
                callback?.invoke()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal memuat daftar teknisi.", Toast.LENGTH_SHORT).show()
                callback?.invoke()
            }
    }

    private fun showTechnicianDialog() {
        val scheduleDateStr = scheduleDate.ifBlank { dateFormat.format(Date()) }

        val items = mutableListOf<TechnicianMultiSelectAdapter.TechItem>()
        for (i in allTechnicianNames.indices) {
            val docFields = if (i < allTechnicianDocs.size) allTechnicianDocs[i] else emptyMap<String, Any>()
            val techId = allTechnicianIds.getOrNull(i) ?: continue
            val techName = allTechnicianNames[i]
            val isUnavailable = technicianIsUnavailableForDate(docFields, scheduleDateStr)
            val statusText = if (isUnavailable) "Unavailable" else "Available"
            val isChecked = selectedTechIds.contains(techId)

            items.add(
                TechnicianMultiSelectAdapter.TechItem(
                    id = techId,
                    name = techName,
                    status = statusText,
                    disabled = isUnavailable,
                    checked = isChecked
                )
            )
        }

        val recycler = RecyclerView(this)
        recycler.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        recycler.layoutManager = LinearLayoutManager(this)
        val adapter = TechnicianMultiSelectAdapter(items) { /* no-op */ }
        recycler.adapter = adapter

        AlertDialog.Builder(this)
            .setTitle("Pilih Teknisi untuk tanggal $scheduleDateStr")
            .setView(recycler)
            .setPositiveButton("OK") { d, _ ->
                selectedTechIds.clear()
                selectedTechNames.clear()
                selectedTechIds.addAll(adapter.getSelectedIds())
                selectedTechNames.addAll(adapter.getSelectedNames())
                etTechnician.setText(selectedTechNames.joinToString(", "))
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

    private fun loadSchedule() {
        if (scheduleId.isBlank()) return

        // If origin == "request" then the document is in requests collection; otherwise in schedules.
        val col = if (origin == "request") FirestoreFields.REQUESTS else FirestoreFields.SCHEDULES

        db.collection(col).document(scheduleId)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) return@addOnSuccessListener

                etCustomer.setText(doc.getString("customerName") ?: doc.getString("name") ?: "")
                etAddress.setText(doc.getString("address") ?: "")
                etTime.setText(doc.getString("time") ?: "")
                scheduleDate = doc.getString("date") ?: scheduleDate
                // ==== LOAD RATING ====
                val ratingValue = doc.getLong("rating")
                val ratingComment = doc.getString("ratingComment")
                val ratingTimestamp = doc.getTimestamp("ratedAt")?.toDate()

                if (ratingValue != null) {
                    ratingContainer.visibility = View.VISIBLE
                    ratingBarLeader.rating = ratingValue.toFloat()

                    tvRatingComment.text =
                        if (!ratingComment.isNullOrBlank()) "Comment: $ratingComment"
                        else "Comment: (none)"

                    if (ratingTimestamp != null) {
                        val sdf = java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale.getDefault())
                        tvRatingDate.text = "Rated on: ${sdf.format(ratingTimestamp)}"
                    } else {
                        tvRatingDate.text = ""
                    }

                } else {
                    ratingContainer.visibility = View.GONE
                }
                // status: find normalized status and set spinner position
                val computedStatus = if (origin == "schedule") {
                    JobNormalizer.scheduleDocToSchedule(doc).normalizedStatus
                } else {
                    JobNormalizer.requestDocToSchedule(doc).normalizedStatus
                }

                // spinner entries: index 0 -> placeholder (no change)
                // 1 -> confirmed, 2 -> on-progress, 3 -> completed
                val spinnerIndex = when (computedStatus) {
                    "confirmed" -> 1
                    "on-progress" -> 2
                    "completed" -> 3
                    else -> 0 // pending or unknown -> placeholder "no change"
                }
                spinnerStatus.setSelection(spinnerIndex, false)

                // technicians
                selectedTechNames.clear()
                selectedTechIds.clear()
                val techs = doc.get(FirestoreFields.FIELD_TECHNICIANS) as? List<*>
                val techIds = doc.get(FirestoreFields.FIELD_TECHNICIAN_IDS) as? List<*>
                techs?.forEach { selectedTechNames.add(it.toString()) }
                techIds?.forEach { selectedTechIds.add(it.toString()) }
                etTechnician.setText(selectedTechNames.joinToString(", "))

                // units
                units.clear()
                val unitsField = doc.get("units")
                if (unitsField is List<*>) {
                    for (u in unitsField) {
                        val m = u as? Map<*, *> ?: continue
                        units.add(ACUnit(
                            brand = m["brand"]?.toString() ?: "",
                            pk = m["pk"]?.toString() ?: "",
                            workType = m["workType"]?.toString() ?: ""
                        ))
                    }
                }
                unitsAdapter.notifyDataSetChanged()

                // documentation: docs live underneath the same parent (requests/<id>/documentation or schedules/<id>/documentation)
                loadDocumentationForDocument(col, scheduleId)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal memuat jadwal: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadDocumentationForDocument(parentCollection: String, docId: String) {
        db.collection(parentCollection).document(docId).collection("documentation")
            .get()
            .addOnSuccessListener { snap ->
                val docs = snap.documents.map { d ->
                    // the stored fields could be base64 or filename; we read what exists
                    DocItem(
                        id = d.id,
                        base64 = d.getString("base64"),
                        fileName = d.getString("fileName"),
                        localUri = null
                    )
                }
                // Pass docs to adapter (read-only)
                docsAdapter.updateItems(docs)
            }
            .addOnFailureListener { e ->
                // swallow failure but log
                Toast.makeText(this, "Failed to load documentation: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateSchedule() {
        if (scheduleId.isBlank()) return

        val customerName = etCustomer.text.toString().trim()
        val address = etAddress.text.toString().trim()
        val time = etTime.text.toString().trim()

        if (customerName.isEmpty()) {
            Toast.makeText(this, "Nama pelanggan diperlukan.", Toast.LENGTH_SHORT).show()
            return
        }
        if (time.isEmpty()) {
            Toast.makeText(this, "Waktu diperlukan.", Toast.LENGTH_SHORT).show()
            return
        }

        // prepare units list
        val unitsList = units.map { u -> mapOf("brand" to u.brand, "pk" to u.pk, "workType" to u.workType) }

        // Evaluate chosen status from spinner
        // NOTE: we removed "pending" option. index 0 = placeholder = "no change"
        // index 1 -> confirmed, 2 -> on-progress, 3 -> completed
        val chosenStatus: String? = when (spinnerStatus.selectedItemPosition) {
            1 -> "confirmed"
            2 -> "on-progress"
            3 -> "completed"
            else -> null // placeholder chosen -> DO NOT CHANGE status
        }

        // Build updates for common fields
        val updates = hashMapOf<String, Any?>(
            "customerName" to customerName,
            "address" to address,
            "time" to time,
            FirestoreFields.FIELD_TECHNICIANS to selectedTechNames,
            FirestoreFields.FIELD_TECHNICIAN_IDS to selectedTechIds,
            FirestoreFields.FIELD_ASSIGNED_TECHNICIAN_IDS to selectedTechIds,
            "units" to unitsList,
            "updatedAt" to Timestamp.now()
        )

        // Add status update to the appropriate field depending on the origin
        if (chosenStatus != null) {
            if (origin == "schedule") {
                updates["workStatus"] = chosenStatus
            } else {
                // origin == "request":
                // We want leader to be able to transition jobStatus (technician progress) as well as leave 'status' (approval).
                // To avoid accidentally demoting leader approval, we update jobStatus for technician progression.
                updates["jobStatus"] = chosenStatus
            }
        } // else: chosenStatus == null -> leave existing status untouched

        val collectionName = if (origin == "schedule") FirestoreFields.SCHEDULES else FirestoreFields.REQUESTS

        db.collection(collectionName).document(scheduleId)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Jadwal diperbarui", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal mengupdate: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun confirmDeleteSchedule() {
        AlertDialog.Builder(this)
            .setTitle("Hapus Jadwal")
            .setMessage("Apakah anda yakin ingin menghapus jadwal ini?")
            .setPositiveButton("Hapus") { _, _ ->
                if (scheduleId.isBlank()) return@setPositiveButton
                val collectionName = if (origin == "schedule") FirestoreFields.SCHEDULES else FirestoreFields.REQUESTS
                db.collection(collectionName).document(scheduleId)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Jadwal dihapus", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Gagal menghapus: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
