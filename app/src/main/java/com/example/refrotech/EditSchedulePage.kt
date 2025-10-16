package com.example.refrotech

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.SimpleAdapter
import android.widget.TextView
import android.widget.Toast
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

    private lateinit var db: FirebaseFirestore
    private var scheduleId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_schedule_page)

        // Initialize views
        etTime = findViewById(R.id.etTime)
        etTechnician = findViewById(R.id.etTechnician)
        etCustomer = findViewById(R.id.etCustomer)
        etAddress = findViewById(R.id.etAddress)
        btnSave = findViewById(R.id.btnSave)
        btnDelete = findViewById(R.id.btnDelete)

        db = FirebaseFirestore.getInstance()

        scheduleId = intent.getStringExtra("scheduleId")

        if (scheduleId.isNullOrEmpty()) {
            Toast.makeText(this, "ID jadwal tidak ditemukan.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadScheduleData()

        // ===== Open time picker when clicking Waktu field =====
        etTime.setOnClickListener {
            showTimePicker()
        }

        // Technician selection dialog
        etTechnician.setOnClickListener {
            showTechnicianDialog()
        }

        // Save changes
        btnSave.setOnClickListener {
            updateSchedule()
        }

        // Delete schedule
        btnDelete.setOnClickListener {
            confirmDeleteSchedule()
        }
    }

    // ===== Time Picker Dialog =====
    private fun showTimePicker() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        val timePicker = TimePickerDialog(
            this,
            { _, selectedHour, selectedMinute ->
                val formattedTime = String.format("%02d:%02d", selectedHour, selectedMinute)
                etTime.setText(formattedTime)
            },
            hour,
            minute,
            true // true = 24-hour format
        )

        timePicker.setTitle("Pilih Waktu")
        timePicker.show()
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
            .setPositiveButton("Ya") { _, _ ->
                deleteSchedule()
            }
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

    // ===== Technician dialog (color-coded) =====
    private fun showTechnicianDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_technician_list, null)
        val listView = dialogView.findViewById<ListView>(R.id.listTechnicians)

        val builder = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)

        val dialog = builder.create()
        val technicians = ArrayList<HashMap<String, String>>()

        db.collection("technicians").get()
            .addOnSuccessListener { result ->
                for (doc in result) {
                    val name = doc.getString("name") ?: "Tanpa Nama"
                    val status = doc.getString("status") ?: "Unavailable"
                    val map = HashMap<String, String>()
                    map["name"] = name
                    map["status"] = status
                    technicians.add(map)
                }

                val adapter = object : SimpleAdapter(
                    this,
                    technicians,
                    R.layout.item_technician,
                    arrayOf("name", "status"),
                    intArrayOf(R.id.tvTechnicianName, R.id.tvTechnicianStatus)
                ) {
                    override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                        val view = super.getView(position, convertView, parent)
                        val statusView = view.findViewById<TextView>(R.id.tvTechnicianStatus)
                        val status = technicians[position]["status"]
                        if (status.equals("Available", ignoreCase = true)) {
                            statusView.setTextColor(getColor(android.R.color.holo_green_dark))
                        } else {
                            statusView.setTextColor(getColor(android.R.color.holo_red_dark))
                        }
                        return view
                    }
                }

                listView.adapter = adapter

                listView.setOnItemClickListener { _, _, position, _ ->
                    val selectedName = technicians[position]["name"]
                    etTechnician.setText(selectedName)
                    dialog.dismiss()
                }

                dialog.show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal memuat daftar teknisi.", Toast.LENGTH_SHORT).show()
            }
    }
}
