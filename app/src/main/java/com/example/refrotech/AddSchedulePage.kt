package com.example.refrotech

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
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
    private var requestId: String? = null

    // technician lists for dialog
    private val techNames = mutableListOf<String>()
    private val techIds = mutableListOf<String>()
    private val selectedTechIndices = mutableSetOf<Int>()

    private var leaderId: String = ""

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
        selectedDate = intent.getStringExtra("date") // required for manual creation
        requestId = intent.getStringExtra("requestId") // optional, if leader invoked from request

        // read leaderId from shared prefs (must have been stored at employee login)
        leaderId = getSharedPreferences("user", MODE_PRIVATE)
            .getString("uid", "") ?: ""

        // Prevent creating detached schedule without a date
        if (selectedDate.isNullOrBlank()) {
            // If invoked without date, user must pick date — here we cancel to avoid confusion
            Toast.makeText(this, "Tanggal tidak diberikan. Pilih tanggal di dashboard terlebih dahulu.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        loadAllTechnicians()

        btnBack.setOnClickListener {
            finish()
        }

        etTime.setOnClickListener { showTimePicker() }
        etTechnician.setOnClickListener { showTechnicianDialog() }

        btnSave.setOnClickListener {
            saveSchedule()
        }
    }

    private fun loadAllTechnicians() {
        db.collection("users")
            .whereEqualTo("role", "technician")
            .get()
            .addOnSuccessListener { res ->
                techNames.clear(); techIds.clear()
                for (d in res) {
                    val name = d.getString("name") ?: d.getString("username") ?: "Tanpa Nama"
                    techNames.add(name)
                    techIds.add(d.id)
                }
            }
    }

    private fun showTimePicker() {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        android.app.TimePickerDialog(this, { _, h, m ->
            val fmt = String.format("%02d:%02d", h, m)
            etTime.setText(fmt)
        }, hour, minute, true).show()
    }

    private fun showTechnicianDialog() {
        if (techNames.isEmpty()) {
            Toast.makeText(this, "Daftar teknisi kosong.", Toast.LENGTH_SHORT).show()
            return
        }
        val checked = BooleanArray(techNames.size) { selectedTechIndices.contains(it) }
        val namesArray = techNames.toTypedArray()

        val builder = AlertDialog.Builder(this)
            .setTitle("Pilih teknisi")
            .setMultiChoiceItems(namesArray, checked) { _, which, isChecked ->
                if (isChecked) selectedTechIndices.add(which) else selectedTechIndices.remove(which)
            }
            .setPositiveButton("OK") { dialog, _ ->
                val selectedNames = selectedTechIndices.map { techNames[it] }
                etTechnician.setText(selectedNames.joinToString(", "))
                dialog.dismiss()
            }
            .setNegativeButton("Batal", null)
        builder.show()
    }

    private fun saveSchedule() {
        val time = etTime.text.toString().trim()
        val technicianNames = selectedTechIndices.map { techNames[it] }
        val technicianIds = selectedTechIndices.map { techIds[it] }
        val customer = etCustomer.text.toString().trim()
        val address = etAddress.text.toString().trim()

        if (time.isEmpty() || technicianNames.isEmpty() || customer.isEmpty() || address.isEmpty()) {
            Toast.makeText(this, "Harap isi semua kolom dan pilih teknisi.", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedDate.isNullOrBlank()) {
            Toast.makeText(this, "Tanggal tidak valid.", Toast.LENGTH_SHORT).show()
            return
        }

        // Build document
        val data = hashMapOf<String, Any>(
            "requestId" to (requestId ?: ""),      // empty string when manual
            "origin" to "manual",
            "date" to selectedDate!!,
            "time" to time,
            "customerName" to customer,
            "address" to address,
            "technicians" to technicianNames.joinToString(", "),
            "technicianIds" to technicianIds,
            "status" to "assigned",
            "jobStatus" to "assigned",
            "leaderId" to leaderId,
            "createdAt" to com.google.firebase.Timestamp.now()
        )

        // Save to schedules collection
        db.collection("schedules")
            .add(data)
            .addOnSuccessListener { docRef ->
                // If this schedule originated from a request, update that request linking scheduleId + status
                if (!requestId.isNullOrBlank()) {
                    db.collection("requests").document(requestId!!)
                        .update(mapOf("status" to "assigned", "scheduleId" to docRef.id))
                }
                Toast.makeText(this, "Jadwal berhasil dibuat", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal menyimpan jadwal: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
