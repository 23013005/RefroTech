package com.example.refrotech

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

/**
 * AddSchedulePage with full unit management:
 * - Add/Edit/Delete units via dialog
 * - Units saved as list of maps: { brand, pk, workType }
 * - Technician selection with availability check
 * - Date is passed in Intent (from LeaderDashboard) or defaults to today
 *
 * Time:
 * - Slot-based: 08–09, 09–10, 10–11, (skip 11–12), 12–13, 13–14, 14–15, 15–16, 16–17
 * - Stored as start time "HH:mm" in field "time"
 * - Blocked per date + technician based on SCHEDULES collection
 */
class AddSchedulePage : AppCompatActivity() {

    private lateinit var etTime: EditText
    private lateinit var etTechnician: EditText
    private lateinit var etCustomer: EditText
    private lateinit var etAddress: EditText

    private lateinit var recyclerUnits: RecyclerView
    private lateinit var btnAddUnit: FrameLayout
    private lateinit var btnSave: FrameLayout

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    // technicians cache
    private val allTechnicianNames = mutableListOf<String>()
    private val allTechnicianIds = mutableListOf<String>()
    private val allTechnicianDocs = mutableListOf<Map<String, Any>>()

    private val selectedTechNames = mutableListOf<String>()
    private val selectedTechIds = mutableListOf<String>()

    // Units
    private val units = mutableListOf<ACUnit>()
    private lateinit var unitsAdapter: ACUnitAdapter

    private var scheduleDate: String = ""

    // --- Time slot model (shared logic conceptually with customer side) ---
    private data class TimeSlot(val label: String, val startTime: String)

    private val timeSlots = listOf(
        TimeSlot("08:00 - 09:00", "08:00"),
        TimeSlot("09:00 - 10:00", "09:00"),
        TimeSlot("10:00 - 11:00", "10:00"),
        // 11:00 - 12:00 intentionally skipped (lunch)
        TimeSlot("12:00 - 13:00", "12:00"),
        TimeSlot("13:00 - 14:00", "13:00"),
        TimeSlot("14:00 - 15:00", "14:00"),
        TimeSlot("15:00 - 16:00", "15:00"),
        TimeSlot("16:00 - 17:00", "16:00")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_schedule_page)

        etTime = findViewById(R.id.etTime)
        etTechnician = findViewById(R.id.etTechnician)
        etCustomer = findViewById(R.id.etCustomer)
        etAddress = findViewById(R.id.etAddress)

        recyclerUnits = findViewById(R.id.recyclerUnits)
        btnAddUnit = findViewById(R.id.btnAddUnit)
        btnSave = findViewById(R.id.btnSave)

        // read date from intent (leader dashboard) or default to today (yyyy-MM-dd)
        scheduleDate = intent.getStringExtra("date") ?: dateFormat.format(Date())

        // setup time picker -> now slot-based and technician-aware
        etTime.setOnClickListener { showTimePicker() }

        // setup units adapter
        unitsAdapter = ACUnitAdapter(units) { index ->
            // open edit dialog when unit tapped
            showAddEditUnitDialog(index)
        }
        recyclerUnits.layoutManager = LinearLayoutManager(this)
        recyclerUnits.adapter = unitsAdapter

        // load technicians cache
        loadAllTechnicians()

        etTechnician.setOnClickListener { loadAllTechnicians { showTechnicianDialog() } }

        btnAddUnit.setOnClickListener { showAddEditUnitDialog(null) }
        btnSave.setOnClickListener { saveScheduleAsLeader() }
    }

    /**
     * SLOT-BASED TIME PICKER FOR LEADER
     * - Requires at least one technician selected
     * - Blocks times that already have schedules on this date for any selected technician
     */
    private fun showTimePicker() {
        if (selectedTechIds.isEmpty()) {
            Toast.makeText(
                this,
                "Pilih teknisi terlebih dahulu sebelum memilih waktu.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Fetch blocked times for this date + selected technicians from SCHEDULES collection
        fetchBlockedTimesForTechnicians(scheduleDate, selectedTechIds) { blockedTimes ->
            showTimeSlotDialog(blockedTimes)
        }
    }

    /**
     * Query SCHEDULES collection to find which start times are blocked for this date
     * and *any* of the selected technicians.
     *
     * Blocks when:
     * - schedule.date == scheduleDate
     * - assignedTechnicianIds (or FIELD_ASSIGNED_TECHNICIAN_IDS) contains any selected techId
     */
    private fun fetchBlockedTimesForTechnicians(
        dateIso: String,
        techIds: List<String>,
        onResult: (Set<String>) -> Unit
    ) {
        if (techIds.isEmpty()) {
            onResult(emptySet())
            return
        }

        val blocked = mutableSetOf<String>()
        var remaining = 3

        fun done() {
            remaining--
            if (remaining <= 0) onResult(blocked)
        }

        // 1) SCHEDULES (leader-made)
        db.collection(FirestoreFields.SCHEDULES)
            .whereEqualTo("date", dateIso)
            .whereArrayContainsAny(FirestoreFields.FIELD_ASSIGNED_TECHNICIAN_IDS, techIds)
            .get()
            .addOnSuccessListener { snap ->
                for (doc in snap.documents) {
                    val t = doc.getString("time")
                    if (!t.isNullOrBlank()) blocked.add(t)
                }
                done()
            }
            .addOnFailureListener { done() }

        // 2) REQUESTS normal (confirmed / assigned / on-progress / completed)
        db.collection(FirestoreFields.REQUESTS)
            .whereArrayContainsAny("technicianIds", techIds)
            .get()
            .addOnSuccessListener { snap ->
                for (doc in snap.documents) {
                    val status = doc.getString("status")?.lowercase()
                    val jobStatus = doc.getString("jobStatus")?.lowercase()

                    val isBlocking =
                        status == "confirmed" ||
                                status == "assigned" ||
                                jobStatus == "confirmed" ||
                                jobStatus == "on-progress" ||
                                jobStatus == "completed"

                    if (!isBlocking) continue

                    val d = doc.getString("date")
                    val t = doc.getString("time")

                    if (d == dateIso && !t.isNullOrBlank()) blocked.add(t)
                }
                done()
            }
            .addOnFailureListener { done() }

        // 3) REQUESTS rescheduled (accepted)
        db.collection(FirestoreFields.REQUESTS)
            .whereArrayContainsAny("assignedTechnicianIds", techIds)
            .get()
            .addOnSuccessListener { snap ->
                for (doc in snap.documents) {
                    val res = doc.getString("rescheduleStatus")?.lowercase()
                    if (res != "accepted") continue

                    val d = doc.getString("newDate")
                    val t = doc.getString("newTime")

                    if (d == dateIso && !t.isNullOrBlank()) blocked.add(t)
                }
                done()
            }
            .addOnFailureListener { done() }
    }


    /**
     * Shows a dialog listing the time slots.
     * - Disabled (grey) if startTime is in blockedTimes.
     * - Selecting a slot sets etTime to the start time ("HH:mm").
     */
    private fun showTimeSlotDialog(blockedTimes: Set<String>) {
        val labels = timeSlots.map { it.label }

        val adapter = object :
            ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, labels) {
            override fun isEnabled(position: Int): Boolean {
                val slot = timeSlots[position]
                return !blockedTimes.contains(slot.startTime)
            }

            override fun getView(
                position: Int,
                convertView: View?,
                parent: ViewGroup
            ): View {
                val view = super.getView(position, convertView, parent) as TextView
                val slot = timeSlots[position]
                val isBlocked = blockedTimes.contains(slot.startTime)

                if (isBlocked) {
                    view.isEnabled = false
                    view.setTextColor(resources.getColor(android.R.color.darker_gray))
                } else {
                    view.isEnabled = true
                    view.setTextColor(resources.getColor(android.R.color.black))
                }
                return view
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Pilih Waktu")
            .setAdapter(adapter) { d, which ->
                val slot = timeSlots[which]
                if (!blockedTimes.contains(slot.startTime)) {
                    // Leader side: we keep it as "HH:mm" directly
                    etTime.setText(slot.startTime)
                }
                d.dismiss()
            }
            .setNegativeButton("Batal", null)
            .create()

        dialog.show()
    }

    /**
     * Dialog to add/edit/delete unit.
     * editIndex == null => add
     * else edit unit at index and present Delete button
     */
    private fun showAddEditUnitDialog(editIndex: Int?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_unit, null)
        val etBrand = dialogView.findViewById<EditText>(R.id.etBrand)
        val etPK = dialogView.findViewById<EditText>(R.id.etPK)
        val spinner = dialogView.findViewById<Spinner>(R.id.spinnerWorkType)

        val workTypes = listOf("Service", "Installation", "Repairment")
        spinner.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, workTypes)

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
                if (workType.isEmpty()) {
                    Toast.makeText(this, "Pilih jenis pekerjaan", Toast.LENGTH_SHORT).show()
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
            if (editIndex == null) {
                btnDelete.visibility = View.GONE
            } else {
                btnDelete.setOnClickListener {
                    if (editIndex in units.indices) {
                        units.removeAt(editIndex)
                        unitsAdapter.notifyItemRemoved(editIndex)
                        // update numbering for the rest
                        unitsAdapter.notifyItemRangeChanged(editIndex, units.size - editIndex)
                    }
                    alert.dismiss()
                }
            }
        }

        alert.show()
    }

    /**
     * Load all technicians into cache for selection.
     */
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
        val scheduleDateStr = scheduleDate

        val items = mutableListOf<TechnicianMultiSelectAdapter.TechItem>()
        for (i in allTechnicianNames.indices) {
            val docFields =
                if (i < allTechnicianDocs.size) allTechnicianDocs[i] else emptyMap<String, Any>()
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
        recycler.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
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

    private fun technicianIsUnavailableForDate(
        docFields: Map<String, Any>,
        targetDateStr: String
    ): Boolean {
        val from = docFields["unavailableFrom"]?.toString() ?: return false
        val to = docFields["unavailableTo"]?.toString()

        val target = try {
            dateFormat.parse(targetDateStr)
        } catch (e: Exception) {
            return false
        }
        val start = try {
            dateFormat.parse(from)
        } catch (e: Exception) {
            return false
        }

        if (to.isNullOrBlank()) {
            return !target.before(start)
        }

        val end = try {
            dateFormat.parse(to)
        } catch (e: Exception) {
            return false
        }
        return !target.before(start) && !target.after(end)
    }

    private fun saveScheduleAsLeader() {
        val time = etTime.text.toString().trim()
        val customer = etCustomer.text.toString().trim()
        val address = etAddress.text.toString().trim()

        if (time.isEmpty()) {
            Toast.makeText(this, "Waktu wajib diisi.", Toast.LENGTH_SHORT).show()
            return
        }
        if (customer.isEmpty()) {
            Toast.makeText(this, "Nama pelanggan wajib diisi.", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedTechIds.isEmpty()) {
            Toast.makeText(this, "Pilih setidaknya 1 teknisi.", Toast.LENGTH_SHORT).show()
            return
        }

        val unitsList =
            units.map { u -> mapOf("brand" to u.brand, "pk" to u.pk, "workType" to u.workType) }

        val scheduleData = hashMapOf<String, Any>(
            "date" to scheduleDate,
            "time" to time,                                   // start time "HH:mm"
            "customerName" to customer,
            "address" to address,
            FirestoreFields.FIELD_TECHNICIANS to selectedTechNames,
            FirestoreFields.FIELD_TECHNICIAN_IDS to selectedTechIds,
            FirestoreFields.FIELD_ASSIGNED_TECHNICIAN_IDS to selectedTechIds,
            "units" to unitsList,
            "createdAt" to Timestamp.now(),
            "createdBy" to "leader",
            // Keep legacy 'status' for compatibility AND add 'workStatus' to match new schedule logic
            "status" to "assigned",
            "workStatus" to "assigned"
        )

        db.collection(FirestoreFields.SCHEDULES)
            .add(scheduleData)
            .addOnSuccessListener {
                Toast.makeText(this, "Jadwal berhasil dibuat.", Toast.LENGTH_SHORT).show()

                // === NOTIFICATIONS: notify each assigned technician about this new schedule ===
                val notifTitle = "Jadwal Baru"
                val notifMessageBase =
                    "Anda dijadwalkan pada $scheduleDate $time untuk pelanggan $customer."

                for (techId in selectedTechIds) {
                    try {
                        createNotification(
                            techId,
                            notifTitle,
                            notifMessageBase
                        )
                    } catch (_: Exception) {
                    }
                }

                startActivity(Intent(this, LeaderDashboard::class.java))
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal membuat jadwal: ${e.message}", Toast.LENGTH_LONG)
                    .show()
            }
    }
}

private fun createNotification(userId: String, title: String, message: String) {
    val db = FirebaseFirestore.getInstance()

    val notif = hashMapOf(
        "userId" to userId,
        "title" to title,
        "message" to message,
        "createdAt" to Timestamp.now(),
        "read" to false
    )

    db.collection("notifications").add(notif)
}
