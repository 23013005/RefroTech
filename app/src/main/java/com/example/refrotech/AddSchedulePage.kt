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

class AddSchedulePage : AppCompatActivity() {

    private lateinit var etTime: EditText
    private lateinit var etTechnician: EditText
    private lateinit var etCustomer: EditText
    private lateinit var etAddress: EditText
    private lateinit var btnSave: FrameLayout

    private lateinit var db: FirebaseFirestore
    private var selectedDate: String? = null // passed from Leader Dashboard

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_schedule_page)

        // Initialize views
        etTime = findViewById(R.id.etTime)
        etTechnician = findViewById(R.id.etTechnician)
        etCustomer = findViewById(R.id.etCustomer)
        etAddress = findViewById(R.id.etAddress)
        btnSave = findViewById(R.id.btnSave)

        db = FirebaseFirestore.getInstance()

        // Date passed from Leader Dashboard
        selectedDate = intent.getStringExtra("date")

        // ===== TIME PICKER =====
        etTime.setOnClickListener {
            showTimePicker()
        }

        // ===== TECHNICIAN DIALOG =====
        etTechnician.setOnClickListener {
            showTechnicianDialog()
        }

        // ===== SAVE SCHEDULE =====
        btnSave.setOnClickListener {
            val date = selectedDate ?: ""
            val time = etTime.text.toString().trim()
            val technician = etTechnician.text.toString().trim()
            val customer = etCustomer.text.toString().trim()
            val address = etAddress.text.toString().trim()

            if (date.isEmpty() || time.isEmpty() || technician.isEmpty() || customer.isEmpty()) {
                Toast.makeText(this, "Harap isi semua kolom wajib.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val schedule = hashMapOf(
                "date" to date,
                "time" to time,
                "technician" to technician,
                "customerName" to customer,
                "address" to address,
                "status" to "pending"
            )

            db.collection("schedules")
                .add(schedule)
                .addOnSuccessListener {
                    Toast.makeText(this, "Jadwal berhasil ditambahkan.", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Gagal menambah jadwal: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // ===== TIME PICKER FUNCTION =====
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
            true // 24-hour format
        )

        timePicker.setTitle("Pilih Waktu")
        timePicker.show()
    }

    // ===== TECHNICIAN PICKER DIALOG =====
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
