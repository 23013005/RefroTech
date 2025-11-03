package com.example.refrotech

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

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
        navHome = findViewById(R.id.navHome)
        navHistory = findViewById(R.id.navHistory)

        // === Setup RecyclerView ===
        adapter = ACUnitAdapter(acUnits)
        recyclerACUnits.layoutManager = LinearLayoutManager(this)
        recyclerACUnits.adapter = adapter

        // === Add New Unit Button ===
        btnAddUnit.setOnClickListener {
            showAddUnitDialog()
        }

        // === Submit Request to Firestore ===
        btnPesan.setOnClickListener {
            saveRequestToFirestore()
        }

        // === Navigation Bar ===
        navHome.setOnClickListener {
            Toast.makeText(this, "Already on Home", Toast.LENGTH_SHORT).show()
        }

        navHistory.setOnClickListener {
            Toast.makeText(this, "History page not ready yet", Toast.LENGTH_SHORT).show()
        }
    }

    // ===== Dialog for Adding Unit =====
    private fun showAddUnitDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_unit, null)
        val etBrand = dialogView.findViewById<EditText>(R.id.etBrand)
        val etPK = dialogView.findViewById<EditText>(R.id.etPK)
        val spinnerWorkType = dialogView.findViewById<Spinner>(R.id.spinnerWorkType)

        // Dropdown values
        val workTypes = listOf("Service", "Reparement", "Installation")
        val spinnerAdapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, workTypes)
        spinnerWorkType.adapter = spinnerAdapter

        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Add AC Unit")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
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
                    Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
    }

    // ===== Save Request to Firestore =====
    private fun saveRequestToFirestore() {
        val name = etName.text.toString().trim()
        val address = etAddress.text.toString().trim()
        val date = etDate.text.toString().trim()
        val time = etTime.text.toString().trim()
        val mapLink = etMapLink.text.toString().trim()
        val phone = etPhone.text.toString().trim()

        if (name.isEmpty() || address.isEmpty() || date.isEmpty() ||
            time.isEmpty() || phone.isEmpty() || acUnits.isEmpty()
        ) {
            Toast.makeText(
                this,
                "Please complete all fields and add at least one unit",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val userId = auth.currentUser?.uid ?: "UnknownUser"

        val unitsForFirestore = acUnits.map { unit ->
            mapOf(
                "brand" to unit.brand,
                "pk" to unit.pk,
                "workType" to unit.workType
            )
        }

        val requestData = hashMapOf(
            "customerId" to userId,
            "name" to name,
            "address" to address,
            "date" to date,
            "time" to time,
            "mapLink" to mapLink,
            "phone" to phone,
            "units" to unitsForFirestore,
            "status" to "Pending",
            "timestamp" to FieldValue.serverTimestamp()
        )

        db.collection("requests")
            .add(requestData)
            .addOnSuccessListener {
                Toast.makeText(this, "Request sent successfully!", Toast.LENGTH_SHORT).show()
                clearForm()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
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
}
