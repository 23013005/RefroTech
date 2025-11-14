package com.example.refrotech

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore

class EditCustomerRequest : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var etAddress: EditText
    private lateinit var etDate: EditText
    private lateinit var etTime: EditText
    private lateinit var etMapLink: EditText
    private lateinit var etPhone: EditText
    private lateinit var rvUnits: androidx.recyclerview.widget.RecyclerView
    private lateinit var btnSave: FrameLayout

    private lateinit var unitAdapter: ACUnitAdapter
    private val acUnitList = mutableListOf<ACUnit>()

    private lateinit var db: FirebaseFirestore
    private var requestId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Using the SAME layout as customer request creation
        setContentView(R.layout.activity_dashboard_customer)

        etName = findViewById(R.id.etName)
        etAddress = findViewById(R.id.etAddress)
        etDate = findViewById(R.id.etDate)
        etTime = findViewById(R.id.etTime)
        etMapLink = findViewById(R.id.etMapLink)
        etPhone = findViewById(R.id.etPhone)

        // IMPORTANT FIX: XML ID is rRecyclerUnits, not rvUnits
        rvUnits = findViewById(R.id.recyclerACUnits)
        btnSave = findViewById(R.id.btnPesan)

        rvUnits.layoutManager = LinearLayoutManager(this)
        unitAdapter = ACUnitAdapter(acUnitList)
        rvUnits.adapter = unitAdapter

        db = FirebaseFirestore.getInstance()

        requestId = intent.getStringExtra("requestId") ?: ""
        if (requestId.isEmpty()) {
            Toast.makeText(this, "Invalid request", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadExistingData()
        setupSaveListener()

        // Change “Pesan” to “Save Changes”
        val buttonText = btnSave.getChildAt(0) as? TextView
        buttonText?.text = "Save Changes"
    }

    private fun loadExistingData() {
        db.collection("requests").document(requestId)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Toast.makeText(this, "Request not found", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }

                val req = RequestData.fromFirestore(doc)

                etName.setText(req.name)
                etAddress.setText(req.address)
                etDate.setText(req.date)
                etTime.setText(req.time)
                etMapLink.setText(req.mapLink)
                etPhone.setText(req.phone)

                acUnitList.clear()
                acUnitList.addAll(req.units.map { map ->
                    ACUnit(
                        brand = map["brand"]?.toString() ?: "-",
                        pk = map["pk"]?.toString() ?: "-",
                        workType = map["workType"]?.toString() ?: "-"
                    )
                })
                unitAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupSaveListener() {
        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val address = etAddress.text.toString().trim()
            val dateRaw = etDate.text.toString().trim()
            val time = etTime.text.toString().trim()
            val mapLink = etMapLink.text.toString().trim()
            val phone = etPhone.text.toString().trim()

            if (name.isEmpty() || address.isEmpty() || dateRaw.isEmpty() || time.isEmpty()) {
                Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val isoDate = if (dateRaw.contains("/")) TimeUtils.toIsoDate(dateRaw) else dateRaw

            val unitMaps = acUnitList.map {
                mapOf(
                    "brand" to it.brand,
                    "pk" to it.pk,
                    "workType" to it.workType
                )
            }

            val updates = mapOf(
                "name" to name,
                "address" to address,
                "date" to isoDate,
                "time" to time,
                "mapLink" to mapLink,
                "phone" to phone,
                "units" to unitMaps
            )

            db.collection("requests")
                .document(requestId)
                .update(updates)
                .addOnSuccessListener {
                    Toast.makeText(this, "Changes saved!", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
