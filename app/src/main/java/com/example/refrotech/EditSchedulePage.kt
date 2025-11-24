package com.example.refrotech

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

/**
 * EditSchedulePage — parity with AddSchedulePage.
 * Loads and saves units, supports add/edit/delete inside dialog.
 */
class EditSchedulePage : AppCompatActivity() {

    private lateinit var etTime: EditText
    private lateinit var etTechnician: EditText
    private lateinit var etCustomer: EditText
    private lateinit var etAddress: EditText

    private lateinit var recyclerUnits: RecyclerView
    private lateinit var btnAddUnit: FrameLayout
    private lateinit var btnSave: FrameLayout
    private lateinit var btnDelete: FrameLayout

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

        unitsAdapter = ACUnitAdapter(units) { index ->
            showAddEditUnitDialog(index)
        }
        recyclerUnits.layoutManager = LinearLayoutManager(this)
        recyclerUnits.adapter = unitsAdapter

        etTime.setOnClickListener { showTimePicker() }
        etTechnician.setOnClickListener { loadAllTechnicians { showTechnicianDialog() } }

        scheduleId = intent.getStringExtra("scheduleId") ?: ""
        scheduleDate = intent.getStringExtra("date") ?: dateFormat.format(Date())

        loadAllTechnicians()
        if (scheduleId.isNotBlank()) loadSchedule()

        btnAddUnit.setOnClickListener { showAddEditUnitDialog(null) }
        btnSave.setOnClickListener { updateSchedule() }
        btnDelete.setOnClickListener { confirmDeleteSchedule() }
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

        db.collection(FirestoreFields.SCHEDULES).document(scheduleId)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) return@addOnSuccessListener

                etCustomer.setText(doc.getString("customerName") ?: "")
                etAddress.setText(doc.getString("address") ?: "")
                etTime.setText(doc.getString("time") ?: "")
                scheduleDate = doc.getString("date") ?: scheduleDate

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
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal memuat jadwal: ${e.message}", Toast.LENGTH_SHORT).show()
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

        if (selectedTechIds.isNotEmpty()) {
            db.collection(FirestoreFields.USERS)
                .whereIn("__name__", selectedTechIds)
                .get()
                .addOnSuccessListener { snap ->
                    val nowUnavailable = mutableListOf<String>()
                    for (d in snap.documents) {
                        val doc = d.data ?: continue
                        if (technicianIsUnavailableForDate(doc, scheduleDate)) {
                            nowUnavailable.add(d.getString("name") ?: d.id)
                        }
                    }
                    if (nowUnavailable.isNotEmpty()) {
                        Toast.makeText(this, "Teknisi tidak tersedia pada tanggal $scheduleDate: ${nowUnavailable.joinToString(", ")}", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }
                    performSave(customerName, address, time)
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Gagal memeriksa teknisi: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            performSave(customerName, address, time)
        }
    }

    private fun performSave(customerName: String, address: String, time: String) {
        val unitsList = units.map { u -> mapOf("brand" to u.brand, "pk" to u.pk, "workType" to u.workType) }

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

        db.collection(FirestoreFields.SCHEDULES).document(scheduleId)
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
                db.collection(FirestoreFields.SCHEDULES).document(scheduleId)
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
