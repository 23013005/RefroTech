package com.example.refrotech

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

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

    private lateinit var adapter: ACUnitAdapter
    private val acUnits = mutableListOf<ACUnit>()

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard_customer)

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

        // === Setup RecyclerView ===
        adapter = ACUnitAdapter(acUnits)
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
            showAddUnitDialog()
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
            // Pass the userId so CustomerHistory can query only this customer's requests
            val userId = auth.currentUser?.uid
            val intent = Intent(this, CustomerHistory::class.java)
            intent.putExtra("userId", userId)
            startActivity(intent)
        }
    }

    // ===== Date Picker with Restrictions =====
    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        val datePicker = DatePickerDialog(
            this,
            { _, year, month, day ->
                val selectedDate = Calendar.getInstance()
                selectedDate.set(year, month, day)
                val dayOfWeek = selectedDate.get(Calendar.DAY_OF_WEEK)

                if (dayOfWeek == Calendar.SUNDAY) {
                    Toast.makeText(this, "Tidak dapat memilih hari Minggu.", Toast.LENGTH_SHORT).show()
                } else {
                    // Keep original UI format (dd/MM/yyyy) but we convert to ISO when saving
                    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    etDate.setText(sdf.format(selectedDate.time))
                }
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )

        // Minimum selectable date = tomorrow
        val tomorrow = Calendar.getInstance()
        tomorrow.add(Calendar.DAY_OF_YEAR, 1)
        datePicker.datePicker.minDate = tomorrow.timeInMillis
        datePicker.show()
    }

    // ===== Time Picker =====
    private fun showTimePicker() {
        val cal = Calendar.getInstance()
        val timePicker = TimePickerDialog(
            this,
            { _, hour, minute ->
                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                cal.set(Calendar.HOUR_OF_DAY, hour)
                cal.set(Calendar.MINUTE, minute)
                etTime.setText(sdf.format(cal.time))
            },
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            true
        )
        timePicker.show()
    }

    // ===== Dialog for Adding Unit =====
    private fun showAddUnitDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_unit, null)
        val etBrand = dialogView.findViewById<EditText>(R.id.etBrand)
        val etPK = dialogView.findViewById<EditText>(R.id.etPK)
        val spinnerWorkType = dialogView.findViewById<Spinner>(R.id.spinnerWorkType)

        val workTypes = listOf("Service", "Reparement", "Installation")
        val spinnerAdapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, workTypes)
        spinnerWorkType.adapter = spinnerAdapter

        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Tambah Unit AC")
            .setView(dialogView)
            .setPositiveButton("Tambah") { _, _ ->
                val brand = etBrand.text.toString().trim()
                val pk = etPK.text.toString().trim()
                val workType = spinnerWorkType.selectedItem.toString()

                if (brand.isNotEmpty() && pk.isNotEmpty()) {
                    val unit = ACUnit(brand = brand, pk = pk, workType = workType)
                    acUnits.add(unit)
                    adapter.notifyItemInserted(acUnits.size - 1)
                    recyclerACUnits.post {
                        recyclerACUnits.smoothScrollToPosition(acUnits.size - 1)
                    }
                } else {
                    Toast.makeText(this, "Isi semua kolom unit terlebih dahulu", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null)
            .create()

        dialog.show()
    }

    // ===== Save Request to Firestore =====
    private fun saveRequestToFirestore() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show()
            return
        }

        val name = etName.text.toString().trim()
        val address = etAddress.text.toString().trim()
        val dateInputRaw = etDate.text.toString().trim() // could be dd/MM/yyyy or yyyy-MM-dd
        val time = etTime.text.toString().trim()
        val mapLink = etMapLink.text.toString().trim()
        val phone = etPhone.text.toString().trim()

        if (name.isEmpty() || address.isEmpty() || dateInputRaw.isEmpty() || time.isEmpty()) {
            Toast.makeText(this, "Please fill required fields", Toast.LENGTH_SHORT).show()
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

        // convert units (list of ACUnit to Map)
        val unitMaps = acUnits.map { u ->
            mapOf(
                "brand" to u.brand,
                "pk" to u.pk,
                "workType" to u.workType
            )
        }

        val now = Timestamp.now()
        val requestData = hashMapOf(
            "customerId" to userId,
            "name" to name,
            "address" to address,
            "date" to isoDate,
            "time" to time,
            "mapLink" to mapLink,
            "phone" to phone,
            "status" to "pending",
            "units" to unitMaps,
            // Use createdAt & createdAtMillis so other pages/readers can sort and read consistently
            "createdAt" to now,
            "createdAtMillis" to now.toDate().time
        )

        db.collection("requests")
            .add(requestData)
            .addOnSuccessListener { _ ->
                Toast.makeText(this, "Request saved", Toast.LENGTH_SHORT).show()
                // Clear form so the customer can submit another request quickly
                clearForm()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ===== Clear All Fields After Sending =====
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
}
