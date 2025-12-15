package com.example.refrotech

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import com.example.refrotech.InAppNotificationManager

class DashboardCustomer : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var etAddress: EditText
    private lateinit var etDate: EditText
    private lateinit var etTime: EditText
    private lateinit var etMapLink: EditText
    private lateinit var etPhone: EditText
    private lateinit var recyclerACUnits: RecyclerView
    private lateinit var btnAddUnit: FrameLayout
    private lateinit var btnPesan: FrameLayout
    private lateinit var btnOpenMaps: ImageView

    private lateinit var navHome: LinearLayout
    private lateinit var navHistory: LinearLayout
    private lateinit var navLogout: FrameLayout

    private lateinit var adapter: ACUnitAdapter
    private val acUnits = mutableListOf<ACUnit>()

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // track whether in-app notif listening was started, so we stop it safely
    private var inAppNotifStarted = false

    // date formats
    private val displayDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    // --- internal model for time slots ---
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
        setContentView(R.layout.activity_dashboard_customer)

        // --- Find the in-app notification container safely (nullable) ---
        val notifContainer = findViewById<LinearLayout?>(R.id.inAppNotifContainer)
        if (notifContainer != null) {
            try {
                InAppNotificationManager.registerContainer(notifContainer)
                InAppNotificationManager.startListening(this)
                inAppNotifStarted = true
            } catch (ex: Exception) {
                // Defensive: if anything goes wrong with notification system, log and continue
                Log.w("DashboardCustomer", "Failed to start InAppNotificationManager: ${ex.message}")
                inAppNotifStarted = false
            }
        } else {
            // Log to help debugging if layout id mismatch occurs
            Log.w(
                "DashboardCustomer",
                "inAppNotifContainer not found in layout — notifications disabled for this screen."
            )
        }

        // === Initialize Views ===
        etName = findViewById(R.id.etName)
        etAddress = findViewById(R.id.etAddress)
        etDate = findViewById(R.id.etDate)
        etTime = findViewById(R.id.etTime)
        etMapLink = findViewById(R.id.etMapLink)
        etPhone = findViewById(R.id.etPhone)
        recyclerACUnits = findViewById(R.id.recyclerACUnits)
        btnAddUnit = findViewById(R.id.btnAddUnit)
        btnPesan = findViewById(R.id.btnPesan)
        btnOpenMaps = findViewById(R.id.btnOpenMaps)
        navHome = findViewById(R.id.navHome)
        navHistory = findViewById(R.id.navHistory)
        navLogout = findViewById<FrameLayout>(R.id.navLogout)

        // === Setup RecyclerView (units) ===
        adapter = ACUnitAdapter(acUnits) { index ->
            showAddEditUnitDialog(editIndex = index)
        }
        recyclerACUnits.layoutManager = LinearLayoutManager(this)
        recyclerACUnits.adapter = adapter

        // === Date and Time Picker ===
        etDate.setOnClickListener { showDatePicker() }
        etTime.setOnClickListener { showTimePicker() }

        // === Open Google Maps ===
        btnOpenMaps.setOnClickListener {
            val address = etAddress.text.toString().trim()
            val gmmIntentUri = if (address.isNotEmpty()) {
                Uri.parse("geo:0,0?q=${Uri.encode(address)}")
            } else {
                Uri.parse("geo:0,0")
            }
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            mapIntent.setPackage("com.google.android.apps.maps")
            startActivity(mapIntent)
        }

        // === Add Unit Button ===
        btnAddUnit.setOnClickListener {
            // For new unit, editIndex = null
            showAddEditUnitDialog(editIndex = null)
        }

        // === Submit Request ===
        btnPesan.setOnClickListener {
            saveRequestToFirestore()
        }

        // === Navigation ===
        navHome.setOnClickListener {
            Toast.makeText(this, "Sudah di halaman Beranda", Toast.LENGTH_SHORT).show()
        }

        navHistory.setOnClickListener {
            val intent = Intent(this, CustomerHistory::class.java)
            intent.putExtra("userId", auth.currentUser?.uid)
            startActivity(intent)
        }

        navLogout.setOnClickListener {
            LogoutHelper.logout(this)
        }
    }

    override fun onStop() {
        super.onStop()
        // stop listener when user leaves (only if it was started)
        if (inAppNotifStarted) {
            try {
                InAppNotificationManager.stopListening()
            } catch (ex: Exception) {
                Log.w("DashboardCustomer", "Error stopping InAppNotificationManager: ${ex.message}")
            }
            inAppNotifStarted = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (inAppNotifStarted) {
            try {
                InAppNotificationManager.stopListening()
            } catch (ex: Exception) {
                Log.w("DashboardCustomer", "Error stopping InAppNotificationManager in onDestroy: ${ex.message}")
            }
            inAppNotifStarted = false
        }
    }

    // ============================================================
    // ===============  DATE PICKER (HIGHLY RESTRICTED) ============
    // ============================================================
    /**
     * Customer can choose ONLY:
     * - 7 valid days ahead
     * - Starting from tomorrow
     * - Skipping all Sundays (they don't count in the 7)
     * - Format shown: dd/MM/yyyy
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

                etDate.setText(displayDateFormat.format(selected.time))
                // Reset chosen time when date changes
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

    /**
     * Build list of 7 valid dates starting from tomorrow, skipping Sundays.
     */
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
    /**
     * Customer picks a time slot:
     * - Only from predefined ranges (08:00–17:00, with 11–12 skipped)
     * - Disabled if already taken by ANY job on that date:
     *      • requests with blocking status
     *      • schedules with non-cancelled workStatus
     * - Shows full label in etTime ("08:00 - 09:00")
     * - Saves only startTime ("08:00") to Firestore
     */
    private fun showTimePicker() {
        val dateRaw = etDate.text.toString().trim()
        if (dateRaw.isEmpty()) {
            Toast.makeText(this, "Pilih tanggal terlebih dahulu.", Toast.LENGTH_SHORT).show()
            return
        }

        // convert dd/MM/yyyy -> yyyy-MM-dd for querying
        val isoDate = try {
            if (dateRaw.contains("/")) {
                TimeUtils.toIsoDate(dateRaw)
            } else {
                dateRaw
            }
        } catch (e: Exception) {
            dateRaw
        }

        fetchBlockedTimesForDate(isoDate) { blockedTimes ->
            showTimeSlotDialog(blockedTimes)
        }
    }

    /**
     * GLOBAL time-blocking rules for customers:
     *
     * A slot (date + start time) is BLOCKED when:
     *  1) A schedule exists on that date with non-cancelled workStatus
     *        - collection: "schedules"
     *        - fields: date, time, workStatus
     *  2) A request exists on that date with blocking status:
     *        - status in ["confirmed", "assigned"]
     *        - OR jobStatus in ["confirmed", "assigned", "on-progress", "completed"]
     *        - blocks its "time"
     *  3) A reschedule exists targeting that date:
     *        - newDate == selectedIsoDate
     *        - AND (rescheduleStatus == "accepted"
     *               OR jobStatus in ["assigned", "on-progress", "completed"])
     *        - blocks its "newTime"
     *
     * We intentionally ignore "pending" anything here — pending should never block.
     */
    private fun fetchBlockedTimesForDate(selectedIsoDate: String, onResult: (Set<String>) -> Unit) {
        val blocked = mutableSetOf<String>()
        var remaining = 3

        fun done() {
            remaining--
            if (remaining <= 0) {
                onResult(blocked)
            }
        }

        // 1) SCHEDULES on that date
        db.collection("schedules")
            .whereEqualTo("date", selectedIsoDate)
            .get()
            .addOnSuccessListener { snap ->
                for (doc in snap.documents) {
                    val workStatus = doc.getString("workStatus")?.lowercase(Locale.getDefault())
                    if (workStatus == "cancelled") continue

                    val t = doc.getString("time")
                    if (!t.isNullOrBlank()) {
                        blocked.add(t)
                    }
                }
                done()
            }
            .addOnFailureListener {
                done()
            }

        // 2) REQUESTS using main date/time
        db.collection("requests")
            .whereEqualTo("date", selectedIsoDate)
            .get()
            .addOnSuccessListener { snap ->
                for (doc in snap.documents) {
                    val status = doc.getString("status")?.lowercase(Locale.getDefault())
                    val jobStatus = doc.getString("jobStatus")?.lowercase(Locale.getDefault())

                    val blocking =
                        status in listOf("confirmed", "assigned") ||
                                jobStatus in listOf("confirmed", "assigned", "on-progress", "completed")

                    if (!blocking) continue

                    val t = doc.getString("time")
                    if (!t.isNullOrBlank()) {
                        blocked.add(t)
                    }
                }
                done()
            }
            .addOnFailureListener {
                done()
            }

        // 3) REQUESTS using reschedule newDate/newTime
        db.collection("requests")
            .whereEqualTo("newDate", selectedIsoDate)
            .get()
            .addOnSuccessListener { snap ->
                for (doc in snap.documents) {
                    val rescheduleStatus = doc.getString("rescheduleStatus")?.lowercase(Locale.getDefault())
                    val jobStatus = doc.getString("jobStatus")?.lowercase(Locale.getDefault())

                    val blocking =
                        rescheduleStatus == "accepted" ||
                                jobStatus in listOf("assigned", "on-progress", "completed")

                    if (!blocking) continue

                    val t = doc.getString("newTime")
                    if (!t.isNullOrBlank()) {
                        blocked.add(t)
                    }
                }
                done()
            }
            .addOnFailureListener {
                done()
            }
    }

    /**
     * Shows dialog with time slots.
     * - Gray + disabled for blocked start times
     * - White + enabled for free slots
     */
    private fun showTimeSlotDialog(blockedTimes: Set<String>) {
        val labels = timeSlots.map { it.label }

        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, labels) {
            override fun isEnabled(position: Int): Boolean {
                val slot = timeSlots[position]
                return !blockedTimes.contains(slot.startTime)
            }

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
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

        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Pilih Waktu")
            .setAdapter(adapter) { d, which ->
                val slot = timeSlots[which]
                if (!blockedTimes.contains(slot.startTime)) {
                    etTime.setText(slot.label) // display full range: "08:00 - 09:00"
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

    // Legacy wrapper kept so nothing breaks if it's referenced anywhere else
    private fun showAddUnitDialog() {
        showAddEditUnitDialog(editIndex = null)
    }

    /**
     * Dialog to ADD or EDIT a unit.
     * - editIndex == null → add new unit
     * - editIndex != null → edit existing unit and allow delete
     */
    private fun showAddEditUnitDialog(editIndex: Int?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_unit, null)
        val etBrand = dialogView.findViewById<EditText>(R.id.etBrand)
        val etPK = dialogView.findViewById<EditText>(R.id.etPK)
        val spinnerWorkType = dialogView.findViewById<Spinner>(R.id.spinnerWorkType)
        val etDescription = dialogView.findViewById<EditText>(R.id.etDescription)

        val workTypes = listOf("Servis", "Perbaikan", "Instalasi")
        val spinnerAdapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, workTypes)
        spinnerWorkType.adapter = spinnerAdapter

        // If editing, pre-fill current data
        if (editIndex != null && editIndex in acUnits.indices) {
            val unit = acUnits[editIndex]
            etBrand.setText(unit.brand)
            etPK.setText(unit.pk)
            val idx = workTypes.indexOf(unit.workType)
            spinnerWorkType.setSelection(if (idx >= 0) idx else 0)
            etDescription.setText(unit.description)
        }

        val isEditMode = editIndex != null

        val builder = android.app.AlertDialog.Builder(this)
            .setTitle(if (isEditMode) "Edit Unit AC" else "Tambah Unit AC")
            .setView(dialogView)

        // Right side: SAVE / TAMBAH
        builder.setPositiveButton(if (isEditMode) "Simpan" else "Tambah", null)

        // Middle: BATAL
        builder.setNegativeButton("Batal", null)

        // Left side: HAPUS (only in edit mode)
        if (isEditMode) {
            builder.setNeutralButton("Hapus", null)
        }

        val dialog = builder.create()

        dialog.setOnShowListener {
            val btnSave = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            val btnCancel = dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)
            val btnDelete = if (isEditMode) dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL) else null

            btnSave.setOnClickListener {
                val brand = etBrand.text.toString().trim()
                val pk = etPK.text.toString().trim()
                val workType = spinnerWorkType.selectedItem.toString()
                val description = etDescription.text.toString().trim()


                if (pk.isEmpty()) {
                    etPK.error = "Jumlah PK wajib diisi"
                    etPK.requestFocus()
                    return@setOnClickListener
                }

                if (isEditMode && editIndex != null && editIndex in acUnits.indices) {
                    // Update existing unit
                    acUnits[editIndex] = ACUnit(brand = brand, pk = pk, workType = workType, description = description)
                    adapter.notifyItemChanged(editIndex)
                } else {
                    // Add new unit
                    val unit = ACUnit(brand = brand, pk = pk, workType = workType, description = description)
                    acUnits.add(unit)
                    adapter.notifyItemInserted(acUnits.size - 1)
                    recyclerACUnits.post {
                        recyclerACUnits.smoothScrollToPosition(acUnits.size - 1)
                    }
                }

                dialog.dismiss()
            }

            btnCancel.setOnClickListener {
                dialog.dismiss()
            }

            btnDelete?.setOnClickListener {
                if (isEditMode && editIndex != null && editIndex in acUnits.indices) {
                    acUnits.removeAt(editIndex)
                    adapter.notifyItemRemoved(editIndex)
                    adapter.notifyItemRangeChanged(editIndex, acUnits.size - editIndex)
                }
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    // ============================================================
    // ================ SAVE REQUEST TO FIRESTORE =================
    // ============================================================

    private fun saveRequestToFirestore() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "Login Terlebih Dahulu", Toast.LENGTH_SHORT).show()
            return
        }

        val name = etName.text.toString().trim()
        val address = etAddress.text.toString().trim()
        val dateInputRaw = etDate.text.toString().trim() // dd/MM/yyyy
        val timeDisplay = etTime.text.toString().trim()   // e.g. "08:00 - 09:00"
        val mapLink = etMapLink.text.toString().trim()
        val phone = etPhone.text.toString().trim()

        if (name.isEmpty() || address.isEmpty() || dateInputRaw.isEmpty() || timeDisplay.isEmpty() || phone.isEmpty()) {
            Toast.makeText(this, "Semua data harus diisi terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }

        // REQUIRED: Prevent submitting if no units were added
        if (acUnits.isEmpty()) {
            Toast.makeText(this, "Tambahkan minimal 1 unit AC terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }

        // convert date to ISO yyyy-MM-dd if needed
        val isoDate = try {
            if (dateInputRaw.contains("/")) {
                TimeUtils.toIsoDate(dateInputRaw)
            } else dateInputRaw
        } catch (e: Exception) {
            dateInputRaw
        }

        // extract start time from timeDisplay ("08:00 - 09:00" -> "08:00")
        val timeForDb = timeDisplay.substringBefore("-").trim()

        // convert units (list of ACUnit to Map)
        val unitMaps = acUnits.map { u ->
            mapOf(
                "brand" to u.brand,
                "pk" to u.pk,
                "workType" to u.workType,
                "description" to u.description
            )
        }

        val now = Timestamp.now()
        val requestData = hashMapOf(
            "customerId" to userId,
            "name" to name,
            "address" to address,
            "date" to isoDate,
            "time" to timeForDb,
            "mapLink" to mapLink,
            "phone" to phone,
            "status" to "pending",
            "units" to unitMaps,
            "createdAt" to now,
            "createdAtMillis" to now.toDate().time
        )

        db.collection("requests")
            .add(requestData)
            .addOnSuccessListener { _ ->
                Toast.makeText(this, "Request saved", Toast.LENGTH_SHORT).show()

                // === NOTIFICATION: Notify all leaders ===
                db.collection("users")
                    .whereEqualTo("role", "leader")
                    .get()
                    .addOnSuccessListener { snap ->
                        for (d in snap.documents) {
                            NotificationUtils.createNotification(
                                d.id,
                                "Permintaan Baru",
                                "$name mengirim permintaan baru"
                            )
                        }
                    }

                clearForm()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun clearForm() {
        etName.text.clear()
        etAddress.text.clear()
        etDate.text.clear()
        etTime.text.clear()
        etMapLink.text.clear()
        etPhone.text.clear()
        acUnits.clear()
        adapter.notifyDataSetChanged()
    }

    // OPTIONAL: if you want to save an in-app notification for leaders from client-side:
    private fun createLeaderNotification(customerName: String) {
        try {
            db.collection("users")
                .whereEqualTo("role", "leader")
                .get()
                .addOnSuccessListener { snap ->
                    for (d in snap.documents) {
                        val leaderId = d.id
                        val notif = hashMapOf(
                            "userId" to leaderId,
                            "title" to "Permintaan Baru",
                            "message" to "$customerName mengirim permintaan baru",
                            "createdAt" to Timestamp.now(),
                            "read" to false
                        )
                        db.collection("notifications").add(notif)
                    }
                }
        } catch (ex: Exception) {
            // do not fail user request if notification write fails
            Log.w("DashboardCustomer", "createLeaderNotification failed: ${ex.message}")
        }
    }
}
