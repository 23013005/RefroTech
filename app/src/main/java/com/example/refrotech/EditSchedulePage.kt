package com.example.refrotech

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class EditSchedulePage : AppCompatActivity() {

    private lateinit var etTime: EditText
    private lateinit var etTechnician: EditText
    private lateinit var etCustomer: EditText
    private lateinit var etAddress: EditText
    private lateinit var btnSave: FrameLayout
    private lateinit var btnDelete: FrameLayout
    private lateinit var btnBack: FrameLayout

    private lateinit var db: FirebaseFirestore
    private var scheduleId: String? = null
    private var scheduleDate: String? = null
    private var currentTechnicians: MutableList<String> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_schedule_page)

        etTime = findViewById(R.id.etTime)
        etTechnician = findViewById(R.id.etTechnician)
        etCustomer = findViewById(R.id.etCustomer)
        etAddress = findViewById(R.id.etAddress)
        btnSave = findViewById(R.id.btnSave)
        btnDelete = findViewById(R.id.btnDelete)
        btnBack = findViewById(R.id.btnBack)

        db = FirebaseFirestore.getInstance()
        scheduleId = intent.getStringExtra("scheduleId")

        if (scheduleId.isNullOrEmpty()) {
            Toast.makeText(this, "ID jadwal tidak ditemukan.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadScheduleData()

        btnBack.setOnClickListener {
            val intent = Intent(this, leader_dashboard::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        etTime.setOnClickListener { showTimePicker() }
        etTechnician.setOnClickListener { showTechnicianDialog() }
        btnSave.setOnClickListener { updateSchedule() }
        btnDelete.setOnClickListener { confirmDeleteSchedule() }
    }

    private fun showTimePicker() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        TimePickerDialog(this, { _, selectedHour, selectedMinute ->
            etTime.setText(String.format("%02d:%02d", selectedHour, selectedMinute))
        }, hour, minute, true).show()
    }

    private fun loadScheduleData() {
        db.collection("schedules").document(scheduleId!!)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    etTime.setText(doc.getString("time"))
                    etTechnician.setText(doc.getString("technician"))
                    etCustomer.setText(doc.getString("customerName"))
                    etAddress.setText(doc.getString("address"))
                    scheduleDate = doc.getString("date")

                    currentTechnicians = doc.getString("technician")
                        ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toMutableList()
                        ?: mutableListOf()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal memuat data jadwal.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateSchedule() {
        val time = etTime.text.toString().trim()
        val technician = etTechnician.text.toString().trim()
        val customer = etCustomer.text.toString().trim()
        val address = etAddress.text.toString().trim()

        if (time.isEmpty() || technician.isEmpty() || customer.isEmpty() || address.isEmpty()) {
            Toast.makeText(this, "Harap isi semua kolom.", Toast.LENGTH_SHORT).show()
            return
        }

        val updates = hashMapOf<String, Any>(
            "time" to time,
            "technician" to technician,
            "customerName" to customer,
            "address" to address
        )

        db.collection("schedules").document(scheduleId!!)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Jadwal berhasil diperbarui.", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal memperbarui jadwal: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun confirmDeleteSchedule() {
        AlertDialog.Builder(this)
            .setTitle("Hapus Jadwal")
            .setMessage("Apakah Anda yakin ingin menghapus jadwal ini?")
            .setPositiveButton("Ya") { _, _ -> deleteSchedule() }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun deleteSchedule() {
        db.collection("schedules").document(scheduleId!!)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Jadwal berhasil dihapus.", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal menghapus jadwal: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ===== Multi-select Technician Dialog (DATE-BASED availability) =====
    private fun showTechnicianDialog() {
        if (scheduleDate.isNullOrEmpty()) {
            Toast.makeText(this, "Tanggal jadwal tidak ditemukan.", Toast.LENGTH_SHORT).show()
            return
        }

        val technicians = mutableListOf<Map<String, String>>()
        val selectedTechs = mutableSetOf<String>()
        val unavailableTechs = mutableSetOf<String>()

        // Add current technicians to pre-selected list
        selectedTechs.addAll(currentTechnicians)

        // Step 1: Fetch all schedules for the same date (except this one)
        db.collection("schedules")
            .whereEqualTo("date", scheduleDate)
            .get()
            .addOnSuccessListener { scheduleResult ->
                for (doc in scheduleResult) {
                    if (doc.id != scheduleId) { // exclude current schedule
                        val techNames = doc.getString("technician")?.split(",") ?: emptyList()
                        unavailableTechs.addAll(techNames.map { it.trim() })
                    }
                }

                // Step 2: Fetch all technicians
                db.collection("users")
                    .whereEqualTo("role", "technician")
                    .get()
                    .addOnSuccessListener { userResult ->
                        for (doc in userResult) {
                            val name = doc.getString("name") ?: "Tanpa Nama"
                            val isUnavailable = unavailableTechs.contains(name)
                            val status = if (isUnavailable) "Unavailable" else "Available"
                            technicians.add(mapOf("name" to name, "status" to status))
                        }

                        val inflater = LayoutInflater.from(this)
                        val dialogView = inflater.inflate(R.layout.dialog_technician_list, null)
                        val listView = dialogView.findViewById<ListView>(R.id.listTechnicians)
                        val saveBtn = dialogView.findViewById<FrameLayout>(R.id.btnSave)

                        val adapter = object : ArrayAdapter<Map<String, String>>(
                            this,
                            R.layout.item_technician_multiselect,
                            technicians
                        ) {
                            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                                val view = convertView ?: layoutInflater.inflate(
                                    R.layout.item_technician_multiselect,
                                    parent,
                                    false
                                )

                                val checkBox = view.findViewById<CheckBox>(R.id.checkBoxTech)
                                val nameView = view.findViewById<TextView>(R.id.tvTechnicianName)
                                val statusView = view.findViewById<TextView>(R.id.tvTechnicianStatus)

                                val tech = technicians[position]
                                val name = tech["name"] ?: ""
                                val status = tech["status"] ?: ""

                                nameView.text = name
                                statusView.text = status

                                // Allow selecting if available OR already in this schedule
                                val canSelect = status == "Available" || selectedTechs.contains(name)

                                if (canSelect) {
                                    statusView.setTextColor(getColor(android.R.color.holo_green_dark))
                                    checkBox.isEnabled = true
                                    view.alpha = 1f
                                } else {
                                    statusView.setTextColor(getColor(android.R.color.holo_red_dark))
                                    checkBox.isEnabled = false
                                    view.alpha = 0.45f
                                }

                                checkBox.isChecked = selectedTechs.contains(name)

                                view.setOnClickListener {
                                    if (checkBox.isEnabled) {
                                        val newChecked = !checkBox.isChecked
                                        checkBox.isChecked = newChecked
                                        if (newChecked) selectedTechs.add(name)
                                        else selectedTechs.remove(name)
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "$name sudah memiliki jadwal di tanggal ini.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }

                                checkBox.setOnCheckedChangeListener { _, isChecked ->
                                    if (checkBox.isEnabled) {
                                        if (isChecked) selectedTechs.add(name)
                                        else selectedTechs.remove(name)
                                    }
                                }

                                return view
                            }
                        }

                        listView.adapter = adapter

                        val dialog = AlertDialog.Builder(this)
                            .setView(dialogView)
                            .create()

                        saveBtn.setOnClickListener {
                            val selectedNames = selectedTechs.joinToString(", ")
                            etTechnician.setText(selectedNames)
                            dialog.dismiss()
                        }

                        dialog.show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Gagal memuat daftar teknisi.", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal memeriksa jadwal teknisi.", Toast.LENGTH_SHORT).show()
            }
    }
}
