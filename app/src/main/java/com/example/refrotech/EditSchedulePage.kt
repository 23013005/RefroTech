package com.example.refrotech

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

/**
 * EditSchedulePage (FULL)
 *
 * - Keeps all your existing features (units, delete, update)
 * - Technician selection uses RecyclerView dialog (shows unavailable techs but disables selection)
 * - Adds "Add Unit" dialog & button so Edit page matches Add page functionality
 *
 * NOTE: This file intentionally preserves almost the entire original logic you provided.
 * Only additions: btnAddUnit, showAddUnitDialog(), RecyclerView unit wiring if missing.
 */
class EditSchedulePage : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    private lateinit var etTime: EditText
    private lateinit var etTechnician: EditText
    private lateinit var etCustomer: EditText
    private lateinit var etAddress: EditText
    private lateinit var btnSave: FrameLayout
    private lateinit var btnDelete: FrameLayout

    // Add Unit support
    private lateinit var btnAddUnit: FrameLayout
    private lateinit var recyclerUnits: RecyclerView
    private val units = mutableListOf<ACUnit>()
    private lateinit var unitAdapter: ACUnitAdapter

    private val allTechnicianNames = mutableListOf<String>()
    private val allTechnicianIds = mutableListOf<String>()
    private val allTechnicianDocs = mutableListOf<Map<String, Any>>() // cached docs

    private val selectedTechNames = mutableListOf<String>()
    private val selectedTechIds = mutableListOf<String>()

    private var scheduleId: String = ""
    private var scheduleDateStr: String = ""
    private var scheduleDocExists = false

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_schedule_page)

        etTime = findViewById(R.id.etTime)
        etTechnician = findViewById(R.id.etTechnician)
        etCustomer = findViewById(R.id.etCustomer)
        etAddress = findViewById(R.id.etAddress)
        btnSave = findViewById(R.id.btnSave)
        btnDelete = findViewById(R.id.btnDelete)

        // unit views & adapter
        recyclerUnits = findViewById(R.id.recyclerUnits)
        unitAdapter = ACUnitAdapter(units)
        recyclerUnits.layoutManager = LinearLayoutManager(this)
        recyclerUnits.adapter = unitAdapter

        // add unit button (matching AddSchedulePage)
        btnAddUnit = findViewById(R.id.btnAddUnit)

        scheduleId = intent.getStringExtra("scheduleId") ?: ""

        etTime.setOnClickListener { showTimePicker() }
        etTechnician.setOnClickListener { showTechnicianDialog() }
        btnSave.setOnClickListener { updateSchedule() }
        btnDelete.setOnClickListener { confirmDeleteSchedule() }
        btnAddUnit.setOnClickListener { showAddUnitDialog() }

        loadAllTechnicians()
        loadSchedule()
    }

    private fun showTimePicker() {
        val cal = Calendar.getInstance()
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val m = cal.get(Calendar.MINUTE)
        TimePickerDialog(this, { _, hour, minute ->
            etTime.setText(String.format("%02d:%02d", hour, minute))
        }, h, m, true).show()
    }

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
                // keep silent on load failure here but you can show a toast if needed
            }
    }

    private fun loadSchedule() {
        if (scheduleId.isBlank()) {
            Toast.makeText(this, "Schedule ID missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        db.collection(FirestoreFields.SCHEDULES).document(scheduleId)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Toast.makeText(this, "Jadwal tidak ditemukan", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }
                scheduleDocExists = true

                scheduleDateStr = doc.getString("date") ?: dateFormat.format(Date())
                etTime.setText(doc.getString("time") ?: "")
                etCustomer.setText(doc.getString("customerName") ?: "")
                etAddress.setText(doc.getString("address") ?: "")

                // load assigned technicians (if any)
                val techNames = doc.get("technicians") as? List<*> ?: emptyList<Any>()
                val techIds = doc.get("technicianIds") as? List<*> ?: emptyList<Any>()
                selectedTechNames.clear()
                selectedTechIds.clear()
                techNames.forEach { selectedTechNames.add(it.toString()) }
                techIds.forEach { selectedTechIds.add(it.toString()) }

                // load units
                units.clear()
                val unitsList = doc.get("units") as? List<*>
                unitsList?.forEach { item ->
                    (item as? Map<*, *>)?.let { m ->
                        val brand = (m["brand"] ?: "").toString()
                        val pk = (m["pk"] ?: "").toString()
                        val workType = (m["workType"] ?: "").toString()
                        units.add(ACUnit(brand = brand, pk = pk, workType = workType))
                    }
                }
                unitAdapter.notifyDataSetChanged()
                etTechnician.setText(selectedTechNames.joinToString(", "))
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal memuat jadwal.", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * New centered AlertDialog technician selector (RecyclerView).
     * Shows unavailable techs but disables selection for them.
     */
    private fun showTechnicianDialog() {
        if (allTechnicianNames.isEmpty()) {
            loadAllTechnicians()
        }

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

        if (items.isEmpty()) {
            Toast.makeText(this, "Tidak ada teknisi tersedia.", Toast.LENGTH_SHORT).show()
            return
        }

        val rv = RecyclerView(this)
        rv.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        rv.layoutManager = LinearLayoutManager(this)
        val adapter = TechnicianMultiSelectAdapter(items, onSelectionChanged = null)
        rv.adapter = adapter

        val dlg = AlertDialog.Builder(this)
            .setTitle("Pilih Teknisi")
            .setView(rv)
            .setPositiveButton("OK") { dialog, _ ->
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

    private fun technicianIsUnavailableForDate(docFields: Map<String, Any>, targetDateStr: String): Boolean {
        // check array periods first
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

        // fallback single-range fields
        val from = docFields["unavailableFrom"]?.toString()
        val to = docFields["unavailableTo"]?.toString()
        if (from.isNullOrBlank()) return false

        val target = try { dateFormat.parse(targetDateStr) } catch (e: Exception) { return false }
        val start = try { dateFormat.parse(from) } catch (e: Exception) { null }
        val end = if (to.isNullOrBlank()) null else try { dateFormat.parse(to) } catch (e: Exception) { null }

        if (start == null) return false
        if (end == null) return !target.before(start) // until further notice -> any date >= start is unavailable
        return !target.before(start) && !target.after(end)
    }

    /**
     * Add Unit dialog (re-uses dialog_add_unit.xml you provided)
     * IDs inside: etBrand, etPK, spinnerWorkType
     */
    private fun showAddUnitDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_unit, null)
        val etBrand = view.findViewById<EditText>(R.id.etBrand)
        val etPK = view.findViewById<EditText>(R.id.etPK)
        val spinnerWorkType = view.findViewById<Spinner>(R.id.spinnerWorkType)

        val types = listOf("Service", "Installation", "Cleaning", "Repair")
        spinnerWorkType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, types)

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

    private fun updateSchedule() {
        if (!scheduleDocExists) {
            Toast.makeText(this, "Cannot update — schedule not loaded.", Toast.LENGTH_SHORT).show()
            return
        }

        val time = etTime.text.toString().trim()
        val customer = etCustomer.text.toString().trim()
        val address = etAddress.text.toString().trim()

        if (time.isEmpty() || customer.isEmpty() || address.isEmpty()) {
            Toast.makeText(this, "Harap isi semua field.", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedTechIds.isEmpty()) {
            Toast.makeText(this, "Harap pilih teknisi.", Toast.LENGTH_SHORT).show()
            return
        }

        // confirm no selected tech is currently marked unavailable for this schedule date
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

                val unitsForFirestore = units.map { u ->
                    mapOf("brand" to u.brand, "pk" to u.pk, "workType" to u.workType)
                }

                val updates = hashMapOf<String, Any>(
                    "time" to time,
                    "customerName" to customer,
                    "address" to address,
                    FirestoreFields.FIELD_TECHNICIANS to selectedTechNames,
                    FirestoreFields.FIELD_TECHNICIAN_IDS to selectedTechIds,
                    FirestoreFields.FIELD_ASSIGNED_TECHNICIAN_IDS to selectedTechIds,
                    "units" to unitsForFirestore
                )

                db.collection(FirestoreFields.SCHEDULES).document(scheduleId)
                    .update(updates as Map<String, Any>)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Jadwal diperbarui.", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Gagal memperbarui: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal memeriksa ketersediaan teknisi: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun confirmDeleteSchedule() {
        AlertDialog.Builder(this)
            .setTitle("Hapus Jadwal")
            .setMessage("Apakah Anda yakin ingin menghapus jadwal ini?")
            .setPositiveButton("Ya") { _, _ ->
                db.collection(FirestoreFields.SCHEDULES).document(scheduleId)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Jadwal dihapus.", Toast.LENGTH_SHORT).show()
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
