package com.example.refrotech

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
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
 *
 * Capacity rules (global per slot, not per-technician):
 *  - A slot (date + time) is considered TAKEN if:
 *      • there is ANY schedule on that date/time (except workStatus == "cancelled")
 *      • OR a request whose *active* slot is that date/time:
 *          - If rescheduleStatus == "accepted"  -> active slot = newDate/newTime
 *          - Else (pending / none / rejected)   -> active slot = date/time
 *        and the job is active:
 *          - status in {"confirmed", "assigned"}
 *          - OR jobStatus in {"confirmed", "assigned", "on-progress", "completed"}
 *
 *  - Pending reschedules DO NOT block newTime/newDate, only their original time/date.
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
    private lateinit var btnWorkReport: FrameLayout

    private lateinit var recyclerUnits: RecyclerView
    private lateinit var btnAddUnit: FrameLayout
    private lateinit var btnSave: FrameLayout
    private lateinit var btnDelete: FrameLayout

    // documentation viewer (read-only)
    private lateinit var recyclerDocs: RecyclerView
    private lateinit var docsAdapter: DocumentationPreviewAdapter

    // status spinner for leader to change work status
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

    // Fixed 1-hour time slots (start times) used everywhere
    private val slotStartTimes = listOf(
        "08:00",
        "09:00",
        "10:00",
        // 11:00–12:00 is skipped for lunch
        "12:00",
        "13:00",
        "14:00",
        "15:00",
        "16:00"
    )

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
        btnWorkReport = findViewById(R.id.btnWorkReport)

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

        // Work Report button hidden by default; loadSchedule will show when appropriate
        btnWorkReport.visibility = View.GONE

        btnWorkReport.setOnClickListener {
            if (scheduleId.isBlank()) return@setOnClickListener
            val intent = Intent(this, WorkReportActivity::class.java)
            intent.putExtra("origin", origin)
            intent.putExtra("id", scheduleId)
            startActivity(intent)
        }
    }

    private fun setupStatusSpinner() {
        // 0 -> no change; 1 -> confirmed; 2 -> on-progress; 3 -> completed
        val statuses = listOf("-- Change status --", "Confirmed", "On-Progress", "Completed")
        val adapterSpinner = ArrayAdapter(this, android.R.layout.simple_spinner_item, statuses)
        adapterSpinner.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerStatus.adapter = adapterSpinner
    }

    /**
     * Time picker for both leader-made and customer-origin jobs:
     * - For leader-made schedules (origin == "schedule"): leader can change date (any non-Sunday),
     *   then choose a 1-hour slot.
     * - For customer-origin jobs (origin == "request"): date is fixed; only time slot can change.
     * - Capacity is global per slot, not per technician.
     */
    private fun showTimePicker() {
        if (selectedTechIds.isEmpty()) {
            Toast.makeText(this, "Pilih teknisi terlebih dahulu.", Toast.LENGTH_SHORT).show()
            return
        }

        if (origin == "schedule") {
            // Leader-made schedule: allow date change (no Sundays)
            showDatePickerForLeaderSchedule()
        } else {
            // Customer-origin job: date fixed, only time slot can change
            loadConflictsAndShowTimeSlots(scheduleDate)
        }
    }

    /**
     * Date picker only for leader-made schedules.
     * - Allows any date (past or future) except Sunday.
     * - When a valid date is chosen, updates scheduleDate and then opens slot dialog.
     */
    private fun showDatePickerForLeaderSchedule() {
        val c = Calendar.getInstance()

        // Initialize picker with current scheduleDate if parsable, else today
        try {
            val parts = scheduleDate.split("-")
            if (parts.size == 3) {
                val year = parts[0].toInt()
                val month = parts[1].toInt() - 1
                val day = parts[2].toInt()
                c.set(year, month, day)
            }
        } catch (_: Exception) {
            // fall back to today
        }

        val dialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selected = Calendar.getInstance().apply { set(year, month, dayOfMonth) }
                if (selected.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                    Toast.makeText(this, "Tidak dapat memilih hari Minggu.", Toast.LENGTH_SHORT).show()
                    return@DatePickerDialog
                }

                // Store back in ISO yyyy-MM-dd format
                scheduleDate = "%04d-%02d-%02d".format(year, month + 1, dayOfMonth)
                loadConflictsAndShowTimeSlots(scheduleDate)
            },
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH),
            c.get(Calendar.DAY_OF_MONTH)
        )

        dialog.show()
    }

    /**
     * GLOBAL capacity logic (Option 1 for reschedule):
     *
     * A slot is blocked if, on targetDate:
     *  - there is ANY schedule on that date/time (except workStatus == "cancelled")
     *  - OR a request whose ACTIVE slot is that date/time, where:
     *      • If rescheduleStatus == "accepted"  -> active slot = newDate/newTime
     *      • Else                               -> active slot = date/time
     *
     * Only active jobs block:
     *  status in {"confirmed", "assigned"} OR
     *  jobStatus in {"confirmed", "assigned", "on-progress", "completed"}.
     *
     * Pending reschedules: only original date/time blocks (NOT newDate/newTime).
     */
    private fun loadConflictsAndShowTimeSlots(targetDate: String) {
        val blockedStartTimes = mutableSetOf<String>()
        var remaining = 3

        fun doneOne() {
            remaining--
            if (remaining <= 0) {
                showTimeSlotDialog(targetDate, blockedStartTimes)
            }
        }

        // 1) SCHEDULES: any schedule on that date (except cancelled) blocks its "time"
        db.collection(FirestoreFields.SCHEDULES)
            .whereEqualTo("date", targetDate)
            .get()
            .addOnSuccessListener { snap ->
                for (doc in snap.documents) {
                    // avoid blocking itself when editing a schedule
                    if (origin == "schedule" && doc.id == scheduleId) continue

                    val workStatus = (doc.getString("workStatus") ?: "").lowercase(Locale.getDefault())
                    if (workStatus == "cancelled") continue

                    val t = doc.getString("time")
                    if (!t.isNullOrBlank()) {
                        blockedStartTimes.add(t)
                    }
                }
                doneOne()
            }
            .addOnFailureListener {
                doneOne()
            }

        // 2) REQUESTS by original DATE
        db.collection(FirestoreFields.REQUESTS)
            .whereEqualTo("date", targetDate)
            .get()
            .addOnSuccessListener { snap ->
                for (doc in snap.documents) {
                    // avoid blocking itself when origin == request
                    if (origin == "request" && doc.id == scheduleId) continue

                    val status = doc.getString("status")?.lowercase(Locale.getDefault())
                    val jobStatus = doc.getString("jobStatus")?.lowercase(Locale.getDefault())
                    val rescheduleStatus =
                        doc.getString("rescheduleStatus")?.lowercase(Locale.getDefault())

                    val time = doc.getString("time")

                    // If reschedule already ACCEPTED and moved somewhere (newDate/newTime),
                    // original slot should NOT block anymore.
                    val hasAcceptedReschedule =
                        rescheduleStatus == "accepted" &&
                                !doc.getString("newDate").isNullOrBlank() &&
                                !doc.getString("newTime").isNullOrBlank()

                    if (hasAcceptedReschedule) {
                        // Active slot is newDate/newTime; handled in query 3 below.
                        continue
                    }

                    // Otherwise, active slot is original date/time.
                    val blocking =
                        status in listOf("confirmed", "assigned") ||
                                jobStatus in listOf("confirmed", "assigned", "on-progress", "completed")

                    if (blocking && !time.isNullOrBlank()) {
                        blockedStartTimes.add(time)
                    }
                }
                doneOne()
            }
            .addOnFailureListener {
                doneOne()
            }

        // 3) REQUESTS by newDate (only rescheduleStatus == "accepted" blocks newTime)
        db.collection(FirestoreFields.REQUESTS)
            .whereEqualTo("newDate", targetDate)
            .get()
            .addOnSuccessListener { snap ->
                for (doc in snap.documents) {
                    if (origin == "request" && doc.id == scheduleId) continue

                    val rescheduleStatus =
                        doc.getString("rescheduleStatus")?.lowercase(Locale.getDefault())
                    if (rescheduleStatus != "accepted") continue

                    val newTime = doc.getString("newTime")
                    if (!newTime.isNullOrBlank()) {
                        blockedStartTimes.add(newTime)
                    }
                }
                doneOne()
            }
            .addOnFailureListener {
                doneOne()
            }
    }

    /**
     * Show list of 1-hour slots, marking blocked ones as "Penuh".
     * When user taps a blocked slot, we show a Toast and do nothing.
     */
    private fun showTimeSlotDialog(dateStr: String, blockedStartTimes: Set<String>) {
        val labels = slotStartTimes.map { start ->
            val endHour = try {
                start.substring(0, 2).toInt() + 1
            } catch (_: Exception) {
                null
            }
            val endLabel = if (endHour != null) "%02d:00".format(endHour) else ""
            val baseLabel = if (endLabel.isNotEmpty()) "$start to $endLabel" else start

            if (blockedStartTimes.contains(start)) {
                "$baseLabel (Penuh)"
            } else {
                baseLabel
            }
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Pilih jam untuk $dateStr")
            .setItems(labels) { dialog, which ->
                val chosenStart = slotStartTimes[which]
                if (blockedStartTimes.contains(chosenStart)) {
                    Toast.makeText(
                        this,
                        "Slot sudah penuh.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setItems
                }

                etTime.setText(chosenStart)
                dialog.dismiss()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showAddEditUnitDialog(editIndex: Int?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_unit, null)
        val etBrand = dialogView.findViewById<EditText>(R.id.etBrand)
        val etPK = dialogView.findViewById<EditText>(R.id.etPK)
        val spinner = dialogView.findViewById<Spinner>(R.id.spinnerWorkType)
        val etDescription = dialogView.findViewById<EditText>(R.id.etDescription)


        val workTypes = listOf("Service", "Installation", "Repairment")
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, workTypes)

        if (editIndex != null && editIndex in units.indices) {
            val u = units[editIndex]
            etBrand.setText(u.brand)
            etPK.setText(u.pk)
            spinner.setSelection(workTypes.indexOf(u.workType).takeIf { it >= 0 } ?: 0)
            etDescription.setText(u.description)

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

                val newUnit = ACUnit(
                    brand = brand,
                    pk = pk,
                    workType = workType,
                    description = etDescription.text.toString().trim()
                )

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
        recycler.layoutParams =
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
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
                        val sdf = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
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

                val spinnerIndex = when (computedStatus) {
                    "confirmed" -> 1
                    "on-progress" -> 2
                    "completed" -> 3
                    else -> 0
                }
                spinnerStatus.setSelection(spinnerIndex, false)

                // Show Work Report button only when status is completed
                btnWorkReport.visibility = if (computedStatus == "completed") View.VISIBLE else View.GONE

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
                        units.add(
                            ACUnit(
                                brand = m["brand"]?.toString() ?: "",
                                pk = m["pk"]?.toString() ?: "",
                                workType = m["workType"]?.toString() ?: "",
                                description = m["description"]?.toString() ?: ""
                            )
                        )
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
                    DocItem(
                        id = d.id,
                        base64 = d.getString("base64"),
                        fileName = d.getString("fileName"),
                        localUri = null,
                        originCollection = parentCollection,
                        parentId = docId
                    )
                }
                docsAdapter.updateItems(docs)
            }
            .addOnFailureListener { e ->
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

        val unitsList = units.map { u ->
            mapOf(
                "brand" to u.brand,
                "pk" to u.pk,
                "workType" to u.workType,
                "description" to u.description
            )
        }

        val chosenStatus: String? = when (spinnerStatus.selectedItemPosition) {
            1 -> "confirmed"
            2 -> "on-progress"
            3 -> "completed"
            else -> null
        }

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

        // For leader-made schedules, allow date to be updated.
        if (origin == "schedule" && scheduleDate.isNotBlank()) {
            updates["date"] = scheduleDate
        }

        if (chosenStatus != null) {
            if (origin == "schedule") {
                updates["workStatus"] = chosenStatus
            } else {
                updates["jobStatus"] = chosenStatus
            }
        }

        val collectionName = if (origin == "schedule") FirestoreFields.SCHEDULES else FirestoreFields.REQUESTS

        db.collection(collectionName).document(scheduleId)
            .update(updates)
            .addOnSuccessListener {
                for (techId in selectedTechIds) {
                    NotificationUtils.createNotification(
                        techId,
                        "Tugas Baru",
                        "Anda mendapat penugasan baru dari leader"
                    )
                }

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
                val collectionName =
                    if (origin == "schedule") FirestoreFields.SCHEDULES else FirestoreFields.REQUESTS
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
