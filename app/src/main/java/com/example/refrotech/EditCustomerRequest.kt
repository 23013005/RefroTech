package com.example.refrotech

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

/**
 * EditCustomerRequest
 *
 * - Reuses the customer dashboard layout (activity_dashboard_customer)
 * - Allows editing only when request status is editable (pending / waiting_approval / not_reviewed)
 * - Uses dialog_add_unit.xml for add/edit unit (Servis / Perbaikan / Instalasi)
 * - Persists updates to Firestore (updates updatedAt)
 */
class EditCustomerRequest : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var etAddress: EditText
    private lateinit var etDate: EditText
    private lateinit var etTime: EditText
    private lateinit var etMapLink: EditText
    private lateinit var etPhone: EditText
    private lateinit var rvUnits: RecyclerView
    private lateinit var btnSave: FrameLayout

    // units and adapter
    private val acUnitList = mutableListOf<ACUnit>()
    private lateinit var unitAdapter: ACUnitAdapter

    // firestore
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private var requestId: String = ""
    private var currentStatus: String = ""

    // date formats
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    // editable statuses
    private val editableStatuses =
        setOf("pending", "waiting_approval", "not_reviewed", "notreviewed", "draft")

    // internal time slot model
    private data class TimeSlot(val label: String, val startTime: String)

    private val timeSlots = listOf(
        TimeSlot("08:00 - 09:00", "08:00"),
        TimeSlot("09:00 - 10:00", "09:00"),
        TimeSlot("10:00 - 11:00", "10:00"),
        // 11:00 - 12:00 intentionally skipped
        TimeSlot("12:00 - 13:00", "12:00"),
        TimeSlot("13:00 - 14:00", "13:00"),
        TimeSlot("14:00 - 15:00", "14:00"),
        TimeSlot("15:00 - 16:00", "15:00"),
        TimeSlot("16:00 - 17:00", "16:00")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard_customer) // shared layout

        // find views (IDs must match the dashboard layout)
        etName = findViewById(R.id.etName)
        etAddress = findViewById(R.id.etAddress)
        etDate = findViewById(R.id.etDate)
        etTime = findViewById(R.id.etTime)
        etMapLink = findViewById(R.id.etMapLink)
        etPhone = findViewById(R.id.etPhone)

        rvUnits = findViewById(R.id.recyclerACUnits)
        btnSave = findViewById(R.id.btnPesan)

        // setup units adapter
        unitAdapter = ACUnitAdapter(
            acUnitList,
            onItemClick = { index ->
                showAddEditUnitDialog(editIndex = index)
            }
        )
        rvUnits.layoutManager = LinearLayoutManager(this)
        rvUnits.adapter = unitAdapter

        // read requestId
        requestId = intent.getStringExtra("requestId") ?: ""
        if (requestId.isEmpty()) {
            Toast.makeText(this, "Invalid request", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // load data
        loadExistingData()

        // override button label to "Save Changes"
        val buttonText = btnSave.getChildAt(0) as? TextView
        buttonText?.text = "Save Changes"

        // save listener
        btnSave.setOnClickListener {
            if (!isEditableNow()) {
                Toast.makeText(this, "This request can no longer be edited.", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }
            saveChanges()
        }

        // add unit button from shared layout
        val addUnitButton = findViewById<FrameLayout?>(R.id.btnAddUnit)
        addUnitButton?.setOnClickListener {
            if (!isEditableNow()) {
                Toast.makeText(this, "This request can no longer be edited.", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }
            showAddEditUnitDialog(editIndex = null)
        }

        // date & time pickers — only for editable requests
        etDate.setOnClickListener {
            if (!isEditableNow()) {
                Toast.makeText(this, "This request can no longer be edited.", Toast.LENGTH_SHORT)
                    .show()
            } else {
                showDatePicker()
            }
        }
        etTime.setOnClickListener {
            if (!isEditableNow()) {
                Toast.makeText(this, "This request can no longer be edited.", Toast.LENGTH_SHORT)
                    .show()
            } else {
                showTimePicker()
            }
        }
    }

    private fun isEditableNow(): Boolean {
        return currentStatus.lowercase(Locale.getDefault()) in editableStatuses
    }

    private fun loadExistingData() {
        db.collection(FirestoreFields.REQUESTS)
            .document(requestId)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Toast.makeText(this, "Request not found", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }

                // fetch status first
                val status = (doc.getString("status") ?: "pending")
                currentStatus = status

                if (!isEditableNow()) {
                    Toast.makeText(
                        this,
                        "This request can no longer be edited (status: $status).",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                    return@addOnSuccessListener
                }

                // load basic fields
                val name = doc.getString("customerName") ?: doc.getString("name") ?: ""
                val address = doc.getString("address") ?: ""
                val dateRaw = doc.getString("date") ?: doc.getString("requestedDate") ?: ""
                val timeRaw = doc.getString("time") ?: doc.getString("requestedTime") ?: ""
                val mapLink = doc.getString("mapLink") ?: doc.getString("map") ?: ""
                val phone = doc.getString("phone") ?: doc.getString("phoneNumber") ?: ""

                etName.setText(name)
                etAddress.setText(address)

                // display date as dd/MM/yyyy
                val displayDate = try {
                    if (dateRaw.contains("-")) {
                        val d = isoFormat.parse(dateRaw)
                        if (d != null) displayFormat.format(d) else dateRaw
                    } else {
                        dateRaw
                    }
                } catch (ex: Exception) {
                    dateRaw
                }
                etDate.setText(displayDate)

                // display time as "HH:mm - HH:mm" if matches a slot; otherwise raw
                val displayTime = timeSlots.firstOrNull { it.startTime == timeRaw }?.label ?: timeRaw
                etTime.setText(displayTime)

                etMapLink.setText(mapLink)
                etPhone.setText(phone)

                // units
                acUnitList.clear()
                val unitsField = doc.get("units")
                if (unitsField is List<*>) {
                    for (u in unitsField) {
                        val m = u as? Map<*, *>
                        if (m != null) {
                            val brand = m["brand"]?.toString() ?: ""
                            val pk = m["pk"]?.toString() ?: ""
                            val workType = m["workType"]?.toString() ?: ""
                            acUnitList.add(ACUnit(brand = brand, pk = pk, workType = workType))
                        }
                    }
                }
                unitAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading request: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
                finish()
            }
    }

    // ============================================================
    // ===============  DATE PICKER (RESTRICTED) ==================
    // ============================================================
    /**
     * Same rules as DashboardCustomer:
     * - Only 7 valid days starting from tomorrow
     * - Skip Sundays
     * - Show as dd/MM/yyyy
     */
    private fun showDatePicker() {
        val allowedDates = buildAllowedDates()

        if (allowedDates.isEmpty()) {
            Toast.makeText(this, "Tidak ada tanggal yang tersedia.", Toast.LENGTH_SHORT).show()
            return
        }

        val first = allowedDates.first()
        val last = allowedDates.last()

        val dialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selected = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                if (!isAllowedDate(selected, allowedDates)) {
                    Toast.makeText(
                        this,
                        "Tanggal ini tidak dapat dipilih. Pilih tanggal lain dalam 7 hari ke depan (kecuali Minggu).",
                        Toast.LENGTH_LONG
                    ).show()
                    return@DatePickerDialog
                }

                etDate.setText(displayFormat.format(selected.time))
                // reset time when date changes
                etTime.setText("")
            },
            first.get(Calendar.YEAR),
            first.get(Calendar.MONTH),
            first.get(Calendar.DAY_OF_MONTH)
        )

        dialog.datePicker.minDate = first.timeInMillis
        dialog.datePicker.maxDate = last.timeInMillis

        dialog.show()
    }

    private fun buildAllowedDates(): List<Calendar> {
        val result = mutableListOf<Calendar>()
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, 1) // start from tomorrow

        while (result.size < 7) {
            if (cal.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
                val clone = cal.clone() as Calendar
                clone.set(Calendar.HOUR_OF_DAY, 0)
                clone.set(Calendar.MINUTE, 0)
                clone.set(Calendar.SECOND, 0)
                clone.set(Calendar.MILLISECOND, 0)
                result.add(clone)
            }
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        return result
    }

    private fun isAllowedDate(selected: Calendar, allowedDates: List<Calendar>): Boolean {
        return allowedDates.any { sameDay(it, selected) }
    }

    private fun sameDay(c1: Calendar, c2: Calendar): Boolean {
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
                c1.get(Calendar.MONTH) == c2.get(Calendar.MONTH) &&
                c1.get(Calendar.DAY_OF_MONTH) == c2.get(Calendar.DAY_OF_MONTH)
    }

    // ============================================================
    // ===============  TIME PICKER (SLOT-BASED) ==================
    // ============================================================
    private fun showTimePicker() {
        val dateRaw = etDate.text.toString().trim()
        if (dateRaw.isEmpty()) {
            Toast.makeText(this, "Pilih tanggal terlebih dahulu.", Toast.LENGTH_SHORT).show()
            return
        }

        // convert display date dd/MM/yyyy -> ISO yyyy-MM-dd for querying
        val isoDate = try {
            if (dateRaw.contains("/")) {
                val d = displayFormat.parse(dateRaw)
                if (d != null) isoFormat.format(d) else dateRaw
            } else {
                dateRaw
            }
        } catch (ex: Exception) {
            dateRaw
        }

        fetchBlockedTimesForDate(isoDate) { blockedTimes ->
            showTimeSlotDialog(blockedTimes)
        }
    }

    /**
     * Block times if:
     * - status == "confirmed"  AND date == selectedDate -> field "time"
     * - jobStatus == "assigned" AND newDate == selectedDate -> field "newTime"
     */
    private fun fetchBlockedTimesForDate(selectedIsoDate: String, onResult: (Set<String>) -> Unit) {
        val blocked = mutableSetOf<String>()
        var remaining = 2

        fun done() {
            remaining--
            if (remaining <= 0) {
                onResult(blocked)
            }
        }

        // 1) confirmed requests
        db.collection(FirestoreFields.REQUESTS)
            .whereEqualTo("date", selectedIsoDate)
            .whereEqualTo("status", "confirmed")
            .get()
            .addOnSuccessListener { snap ->
                for (doc in snap.documents) {
                    val t = doc.getString("time")
                    if (!t.isNullOrBlank()) blocked.add(t)
                }
                done()
            }
            .addOnFailureListener { done() }

        // 2) reschedule approved via jobStatus == "assigned"
        db.collection(FirestoreFields.REQUESTS)
            .whereEqualTo("newDate", selectedIsoDate)
            .whereEqualTo("jobStatus", "assigned")
            .get()
            .addOnSuccessListener { snap ->
                for (doc in snap.documents) {
                    val t = doc.getString("newTime")
                    if (!t.isNullOrBlank()) blocked.add(t)
                }
                done()
            }
            .addOnFailureListener { done() }
    }

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
                    etTime.setText(slot.label) // "HH:mm - HH:mm"
                }
                d.dismiss()
            }
            .setNegativeButton("Batal", null)
            .create()

        dialog.show()
    }

    // ============================================================
    // ===== DIALOG FOR ADD / EDIT / DELETE AC UNITS ===============
    // ============================================================
    private fun showAddEditUnitDialog(editIndex: Int?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_unit, null)

        val etBrand = dialogView.findViewById<EditText>(R.id.etBrand)
        val etPK = dialogView.findViewById<EditText>(R.id.etPK)
        val spinner = dialogView.findViewById<Spinner>(R.id.spinnerWorkType)

        val workTypes = listOf("Servis", "Perbaikan", "Instalasi")
        spinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            workTypes
        )

        // pre-fill when editing
        if (editIndex != null && editIndex in acUnitList.indices) {
            val u = acUnitList[editIndex]
            etBrand.setText(u.brand)
            etPK.setText(u.pk)
            val idx = workTypes.indexOf(u.workType)
            spinner.setSelection(if (idx >= 0) idx else 0)
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
                val brand = etBrand.text.toString().trim()
                val pk = etPK.text.toString().trim()
                val workType = spinner.selectedItem.toString()

                if (pk.isEmpty()) {
                    etPK.error = "Jumlah PK wajib diisi"
                    etPK.requestFocus()
                    return@setOnClickListener
                }

                val unit = ACUnit(brand = brand, pk = pk, workType = workType)

                if (editIndex == null) {
                    acUnitList.add(unit)
                    unitAdapter.notifyItemInserted(acUnitList.size - 1)
                } else {
                    acUnitList[editIndex] = unit
                    unitAdapter.notifyItemChanged(editIndex)
                }
                alert.dismiss()
            }

            val btnDelete = alert.getButton(AlertDialog.BUTTON_NEUTRAL)
            if (editIndex == null) {
                btnDelete.visibility = View.GONE
            } else {
                btnDelete.setOnClickListener {
                    if (editIndex in acUnitList.indices) {
                        acUnitList.removeAt(editIndex)
                        unitAdapter.notifyItemRemoved(editIndex)
                        unitAdapter.notifyItemRangeChanged(
                            editIndex,
                            acUnitList.size - editIndex
                        )
                    }
                    alert.dismiss()
                }
            }
        }

        alert.show()
    }

    // ============================================================
    // ================ SAVE REQUEST TO FIRESTORE =================
    // ============================================================

    private fun saveChanges() {
        val name = etName.text.toString().trim()
        val address = etAddress.text.toString().trim()
        val dateRaw = etDate.text.toString().trim()      // dd/MM/yyyy
        val timeDisplay = etTime.text.toString().trim()  // "HH:mm - HH:mm" or "HH:mm"
        val mapLink = etMapLink.text.toString().trim()
        val phone = etPhone.text.toString().trim()

        if (name.isEmpty() || address.isEmpty() || dateRaw.isEmpty() || timeDisplay.isEmpty()) {
            Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (acUnitList.isEmpty()) {
            Toast.makeText(this, "Tambahkan minimal 1 unit AC terlebih dahulu", Toast.LENGTH_SHORT)
                .show()
            return
        }

        // dd/MM/yyyy -> yyyy-MM-dd
        val isoDate = try {
            if (dateRaw.contains("/")) {
                val d = displayFormat.parse(dateRaw)
                if (d != null) isoFormat.format(d) else dateRaw
            } else {
                dateRaw
            }
        } catch (ex: Exception) {
            dateRaw
        }

        // extract start time portion
        val timeForDb = timeDisplay.substringBefore("-").trim()

        val unitMaps = acUnitList.map { unit ->
            mapOf(
                "brand" to unit.brand,
                "pk" to unit.pk,
                "workType" to unit.workType
            )
        }

        val updates = mutableMapOf<String, Any>(
            "customerName" to name,
            "name" to name,
            "address" to address,
            "date" to isoDate,
            "time" to timeForDb,
            "mapLink" to mapLink,
            "phone" to phone,
            "units" to unitMaps,
            "updatedAt" to Timestamp.now()
        )

        db.collection(FirestoreFields.REQUESTS)
            .document(requestId)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Changes saved!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    "Failed to save changes: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
}
