package com.example.refrotech

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

/**
 * AddSchedulePage (FULL)
 *
 * - Keeps all your existing features (units, add unit dialog, saveScheduleAsLeader, etc.)
 * - Replaces technician selection dialog with a centered AlertDialog that contains a RecyclerView
 *   using TechnicianMultiSelectAdapter.
 * - Unavailable technicians are shown with status and disabled (not selectable).
 *
 * IMPORTANT: This file preserves all your existing variable names and behaviors.
 */
class AddSchedulePage : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    private lateinit var etTime: EditText
    private lateinit var etTechnician: EditText
    private lateinit var etCustomer: EditText
    private lateinit var etAddress: EditText
    private lateinit var btnSave: FrameLayout
    private lateinit var btnAddUnit: FrameLayout
    private lateinit var recyclerUnits: RecyclerView

    private val allTechnicianNames = mutableListOf<String>()
    private val allTechnicianIds = mutableListOf<String>()
    private val allTechnicianDocs = mutableListOf<Map<String, Any>>() // store raw doc fields for unavailability checks

    private val selectedTechNames = mutableListOf<String>()
    private val selectedTechIds = mutableListOf<String>()

    private val units = mutableListOf<ACUnit>()
    private lateinit var unitAdapter: ACUnitAdapter

    // Date formatter: expects schedule date in 'yyyy-MM-dd' string form (match your existing data)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_schedule_page)

        etTime = findViewById(R.id.etTime)
        etTechnician = findViewById(R.id.etTechnician)
        etCustomer = findViewById(R.id.etCustomer)
        etAddress = findViewById(R.id.etAddress)
        btnSave = findViewById(R.id.btnSave)
        btnAddUnit = findViewById(R.id.btnAddUnit)
        recyclerUnits = findViewById(R.id.recyclerUnits)

        unitAdapter = ACUnitAdapter(units)
        recyclerUnits.layoutManager = LinearLayoutManager(this)
        recyclerUnits.adapter = unitAdapter

        etTime.setOnClickListener { showTimePicker() }
        etTechnician.setOnClickListener { showTechnicianDialog() }
        btnAddUnit.setOnClickListener { showAddUnitDialog() }
        btnSave.setOnClickListener { saveScheduleAsLeader() }

        // Load technicians once (we will filter by unavailability when showing dialog)
        loadAllTechnicians()
    }

    private fun showTimePicker() {
        val cal = Calendar.getInstance()
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val m = cal.get(Calendar.MINUTE)
        TimePickerDialog(this, { _, hour, minute ->
            etTime.setText(String.format("%02d:%02d", hour, minute))
        }, h, m, true).show()
    }

    /**
     * Fetch all technicians and cache their document fields for unavailability checks
     */
    private fun loadAllTechnicians() {
        db.collection(FirestoreFields.USERS)
            .whereEqualTo("role", "technician")
            .get()
            .addOnSuccessListener { snap ->
                allTechnicianNames.clear()
                allTechnicianIds.clear()
                allTechnicianDocs.clear()
                for (d in snap.documents) {
                    val name = d.getString("name") ?: "Tanpa Nama"
                    allTechnicianNames.add(name)
                    allTechnicianIds.add(d.id)
                    allTechnicianDocs.add(d.data ?: mapOf<String, Any>())
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal memuat daftar teknisi.", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Show centered AlertDialog containing a RecyclerView of technicians.
     * Disabled items represent technicians unavailable on the selected schedule date.
     */
    private fun showTechnicianDialog() {
        // selected schedule date string (expected 'yyyy-MM-dd'); fallback to today if not provided
        val scheduleDateStr = intent.getStringExtra("date") ?: dateFormat.format(Date())

        // Build list of TechItem for adapter (show unavailable but disabled)
        val items = mutableListOf<TechnicianMultiSelectAdapter.TechItem>()
        for (i in allTechnicianNames.indices) {
            val docFields = if (i < allTechnicianDocs.size) allTechnicianDocs[i] else emptyMap<String, Any>()
            val techId = allTechnicianIds.getOrNull(i) ?: continue
            val techName = allTechnicianNames[i]

            val isUnavailable = technicianIsUnavailableForDate(docFields, scheduleDateStr)

            // status text: show unavailable date or "Available"
            val statusText = if (isUnavailable) "Unavailable" else "Available"

            // initial checked state
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

        if (items.isEmpty()) {
            Toast.makeText(this, "Tidak ada teknisi ditemukan.", Toast.LENGTH_SHORT).show()
            return
        }

        // Build RecyclerView programmatically and attach adapter
        val rv = RecyclerView(this)
        rv.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        rv.layoutManager = LinearLayoutManager(this)
        val adapter = TechnicianMultiSelectAdapter(items, onSelectionChanged = null)
        rv.adapter = adapter

        // Build centered AlertDialog
        val dlg = AlertDialog.Builder(this)
            .setTitle("Pilih Teknisi")
            .setView(rv)
            .setPositiveButton("OK") { dialog, _ ->
                // collect selected
                val selIds = adapter.getSelectedIds()
                val selNames = adapter.getSelectedNames()

                selectedTechIds.clear()
                selectedTechNames.clear()
                selectedTechIds.addAll(selIds)
                selectedTechNames.addAll(selNames)

                etTechnician.setText(selectedTechNames.joinToString(", "))

                dialog.dismiss()
            }
            .setNegativeButton("Batal", null)
            .create()

        dlg.show()
    }

    /**
     * Helper: returns true if technician doc has unavailablePeriods that include the targetDateStr
     * Supports both:
     *  - docFields["unavailablePeriods"] as List<Map<String,String>> where start/end are 'yyyy-MM-dd'
     *  - docFields["unavailableFrom"] / docFields["unavailableTo"] as single range (string/date)
     */
    private fun technicianIsUnavailableForDate(docFields: Map<String, Any>, targetDateStr: String): Boolean {
        // try unavailablePeriods first (list)
        val arr = docFields["unavailablePeriods"] as? List<*>
        if (!arr.isNullOrEmpty()) {
            val targetDate = try { dateFormat.parse(targetDateStr) ?: return false } catch (e: Exception) { return false }
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

        // fallback to single fields unavailableFrom / unavailableTo
        val from = docFields["unavailableFrom"]?.toString()
        val to = docFields["unavailableTo"]?.toString() // may be null => until further
        if (from.isNullOrBlank()) return false

        val target = try { dateFormat.parse(targetDateStr) } catch (e: Exception) { return false }
        val start = try { dateFormat.parse(from) } catch (e: Exception) { null }
        val end = if (to.isNullOrBlank()) null else try { dateFormat.parse(to) } catch (e: Exception) { null }

        if (start == null) return false
        // if end is null => until further => target >= start
        if (end == null) return !target.before(start)
        return !target.before(start) && !target.after(end)
    }

    private fun showAddUnitDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_add_unit, null)
        val etBrand = view.findViewById<EditText>(R.id.etBrand)
        val etPK = view.findViewById<EditText>(R.id.etPK)
        val spinnerWorkType = view.findViewById<android.widget.Spinner>(R.id.spinnerWorkType)

        val types = listOf("Service", "Installation", "Cleaning", "Repair")
        spinnerWorkType.adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, types)

        AlertDialog.Builder(this)
            .setTitle("Tambah Unit AC")
            .setView(view)
            .setPositiveButton("Tambah") { dialog, _ ->
                val brand = etBrand.text.toString().trim()
                val pkText = etPK.text.toString().trim()
                val workType = spinnerWorkType.selectedItem?.toString() ?: "Service"

                if (brand.isEmpty() || pkText.isEmpty()) {
                    Toast.makeText(this, "Harap isi merk dan PK.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val unit = ACUnit(brand = brand, pk = pkText, workType = workType)
                units.add(unit)
                unitAdapter.notifyItemInserted(units.size - 1)
                dialog.dismiss()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun saveScheduleAsLeader() {
        val time = etTime.text.toString().trim()
        val customer = etCustomer.text.toString().trim()
        val address = etAddress.text.toString().trim()
        val scheduleDateStr = intent.getStringExtra("date") ?: dateFormat.format(Date())

        if (time.isEmpty() || customer.isEmpty() || address.isEmpty()) {
            Toast.makeText(this, "Harap isi waktu, nama pelanggan, dan alamat.", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedTechIds.isEmpty()) {
            Toast.makeText(this, "Harap pilih minimal 1 teknisi.", Toast.LENGTH_SHORT).show()
            return
        }

        // Optional: verify selected technicians are still available for that date
        db.collection(FirestoreFields.USERS)
            .whereIn("__name__", selectedTechIds)
            .get()
            .addOnSuccessListener { snap ->
                val nowUnavailable = mutableListOf<String>()
                for (d in snap.documents) {
                    val doc = d.data ?: continue
                    if (technicianIsUnavailableForDate(doc, scheduleDateStr)) {
                        nowUnavailable.add(d.getString("name") ?: d.id)
                    }
                }
                if (nowUnavailable.isNotEmpty()) {
                    Toast.makeText(this, "Teknisi tidak tersedia pada tanggal $scheduleDateStr: ${nowUnavailable.joinToString(", ")}", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }
                // proceed to add schedule
                doSaveSchedule(scheduleDateStr, time, customer, address)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal memeriksa ketersediaan teknisi: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun doSaveSchedule(scheduleDateStr: String, time: String, customer: String, address: String) {
        val unitsForFirestore = units.map { unit ->
            mapOf(
                "brand" to unit.brand,
                "pk" to unit.pk,
                "workType" to unit.workType
            )
        }

        val scheduleData = hashMapOf<String, Any>(
            "origin" to "manual",
            "customerName" to customer,
            "address" to address,
            "date" to scheduleDateStr,
            "time" to time,
            FirestoreFields.FIELD_TECHNICIANS to selectedTechNames,
            FirestoreFields.FIELD_TECHNICIAN_IDS to selectedTechIds,
            FirestoreFields.FIELD_ASSIGNED_TECHNICIAN_IDS to selectedTechIds,
            "units" to unitsForFirestore,
            FirestoreFields.FIELD_JOB_STATUS to "assigned",
            "status" to "assigned",
            "requestId" to "",
            FirestoreFields.FIELD_DOCUMENTATION to listOf<String>(),
            "createdAt" to Timestamp.now()
        )

        db.collection(FirestoreFields.SCHEDULES)
            .add(scheduleData)
            .addOnSuccessListener {
                Toast.makeText(this, "Jadwal berhasil dibuat.", Toast.LENGTH_SHORT).show()
                startActivity(android.content.Intent(this, LeaderDashboard::class.java))
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal membuat jadwal: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}
