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

class AddSchedulePage : AppCompatActivity() {

    private lateinit var etTime: EditText
    private lateinit var etTechnician: EditText
    private lateinit var etCustomer: EditText
    private lateinit var etAddress: EditText
    private lateinit var btnSave: FrameLayout
    private lateinit var btnBack: FrameLayout

    private lateinit var db: FirebaseFirestore
    private var selectedDate: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_schedule_page)

        etTime = findViewById(R.id.etTime)
        etTechnician = findViewById(R.id.etTechnician)
        etCustomer = findViewById(R.id.etCustomer)
        etAddress = findViewById(R.id.etAddress)
        btnSave = findViewById(R.id.btnSave)
        btnBack = findViewById(R.id.btnBack)

        db = FirebaseFirestore.getInstance()
        selectedDate = intent.getStringExtra("date")

        // ===== BACK BUTTON =====
        btnBack.setOnClickListener {
            val intent = Intent(this, leader_dashboard::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        // ===== TIME PICKER =====
        etTime.setOnClickListener { showTimePicker() }

        // ===== TECHNICIAN PICKER =====
        etTechnician.setOnClickListener { showTechnicianDialog() }

        // ===== SAVE BUTTON =====
        btnSave.setOnClickListener {
            saveSchedule()
        }
    }

    private fun showTimePicker() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        TimePickerDialog(this, { _, selectedHour, selectedMinute ->
            val formattedTime = String.format("%02d:%02d", selectedHour, selectedMinute)
            etTime.setText(formattedTime)
        }, hour, minute, true).show()
    }

    // ===== Multi-select Technician Dialog (DATE-BASED availability) =====
    private fun showTechnicianDialog() {
        if (selectedDate.isNullOrEmpty()) {
            Toast.makeText(this, "Silakan pilih tanggal terlebih dahulu.", Toast.LENGTH_SHORT).show()
            return
        }

        val technicians = mutableListOf<Map<String, String>>()
        val selectedTechs = mutableSetOf<String>()
        val unavailableTechs = mutableSetOf<String>()

        // Step 1: Fetch schedules for the same date
        db.collection("schedules")
            .whereEqualTo("date", selectedDate)
            .get()
            .addOnSuccessListener { scheduleResult ->
                for (doc in scheduleResult) {
                    val techNames = doc.getString("technician")?.split(",") ?: emptyList()
                    unavailableTechs.addAll(techNames.map { it.trim() })
                }

                // Step 2: Fetch all technicians from users
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

                                if (status == "Available") {
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

    private fun saveSchedule() {
        val date = selectedDate ?: ""
        val time = etTime.text.toString().trim()
        val technician = etTechnician.text.toString().trim()
        val customer = etCustomer.text.toString().trim()
        val address = etAddress.text.toString().trim()

        if (date.isEmpty() || time.isEmpty() || technician.isEmpty() || customer.isEmpty()) {
            Toast.makeText(this, "Harap isi semua kolom wajib.", Toast.LENGTH_SHORT).show()
            return
        }

        val schedule = hashMapOf(
            "date" to date,
            "time" to time,
            "technician" to technician,
            "customerName" to customer,
            "address" to address,
            "status" to "pending"
        )

        db.collection("schedules").add(schedule)
            .addOnSuccessListener {
                Toast.makeText(this, "Jadwal berhasil ditambahkan.", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, leader_dashboard::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal menambah jadwal: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
