package com.example.refrotech

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

/**
 * EditCustomerRequest
 *
 * - Reuses the customer dashboard layout (activity_dashboard_customer)
 * - Allows editing only when request status is editable (pending / waiting_approval / not_reviewed)
 * - Uses dialog_add_unit.xml for add/edit unit (Service / Installation / Repairment)
 * - Persists updates to Firestore (updates updatedAt)
 */
class EditCustomerRequest : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var etAddress: EditText
    private lateinit var etDate: EditText
    private lateinit var etTime: EditText
    private lateinit var etMapLink: EditText
    private lateinit var etPhone: EditText
    private lateinit var rvUnits: RecyclerView
    private lateinit var btnSave: FrameLayout

    // units and adapter
    private val acUnitList = mutableListOf<ACUnit>()
    private lateinit var unitAdapter: ACUnitAdapter

    // firestore
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private var requestId: String = ""
    private var currentStatus: String = ""

    // date formats
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    // editable statuses
    private val editableStatuses = setOf("pending", "waiting_approval", "not_reviewed", "notreviewed", "draft")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard_customer) // shared layout as requested

        // find views (IDs must match the dashboard layout)
        etName = findViewById(R.id.etName)
        etAddress = findViewById(R.id.etAddress)
        etDate = findViewById(R.id.etDate)
        etTime = findViewById(R.id.etTime)
        etMapLink = findViewById(R.id.etMapLink)
        etPhone = findViewById(R.id.etPhone)

        // IMPORTANT: in your layout the RecyclerView ID is recyclerACUnits per earlier messages
        rvUnits = findViewById(R.id.recyclerACUnits)
        btnSave = findViewById(R.id.btnPesan)

        // setup units adapter:
        // ACUnitAdapter constructor in your project accepts (items, onItemClick?) or (items, onDelete)
        // We'll use the version that accepts an item click callback to support edit on tap, and onDelete for delete.
        unitAdapter = ACUnitAdapter(
            acUnitList,
            onItemClick = { index ->
                // edit unit
                showAddEditUnitDialog(editIndex = index)
            }
        )
        // if your ACUnitAdapter signature differs (older), the above will still work if it accepts null second arg.
        rvUnits.layoutManager = LinearLayoutManager(this)
        rvUnits.adapter = unitAdapter

        // read requestId
        requestId = intent.getStringExtra("requestId") ?: ""
        if (requestId.isEmpty()) {
            Toast.makeText(this, "Invalid request", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // load data
        loadExistingData()

        // override button label to "Save Changes"
        val buttonText = btnSave.getChildAt(0) as? TextView
        buttonText?.text = "Save Changes"

        // save listener
        btnSave.setOnClickListener {
            if (!isEditableNow()) {
                Toast.makeText(this, "This request can no longer be edited.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            saveChanges()
        }

        // support adding units from this page (the dashboard's add unit button id may differ; reuse same button if present)
        val addUnitButton = findViewById<FrameLayout?>(R.id.btnAddUnit)
        addUnitButton?.setOnClickListener {
            if (!isEditableNow()) {
                Toast.makeText(this, "This request can no longer be edited.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showAddEditUnitDialog(editIndex = null)
        }
    }

    private fun isEditableNow(): Boolean {
        return currentStatus.lowercase(Locale.getDefault()) in editableStatuses
    }

    private fun loadExistingData() {
        db.collection(FirestoreFields.REQUESTS)
            .document(requestId)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Toast.makeText(this, "Request not found", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }

                // fetch status first
                val status = (doc.getString("status") ?: "pending")
                currentStatus = status

                if (!isEditableNow()) {
                    // If not editable, inform user and close. Optionally you could open read-only view.
                    Toast.makeText(this, "This request can no longer be edited (status: $status).", Toast.LENGTH_LONG).show()
                    finish()
                    return@addOnSuccessListener
                }

                // load basic fields (support both customerName and name)
                val name = doc.getString("customerName") ?: doc.getString("name") ?: ""
                val address = doc.getString("address") ?: ""
                val dateRaw = doc.getString("date") ?: doc.getString("requestedDate") ?: ""
                val time = doc.getString("time") ?: doc.getString("requestedTime") ?: ""
                val mapLink = doc.getString("mapLink") ?: doc.getString("map") ?: ""
                val phone = doc.getString("phone") ?: doc.getString("phoneNumber") ?: ""

                etName.setText(name)
                etAddress.setText(address)

                // display date as dd/MM/yyyy if possible (doc may be ISO)
                val displayDate = try {
                    if (dateRaw.contains("-")) {
                        val d = isoFormat.parse(dateRaw)
                        if (d != null) displayFormat.format(d) else dateRaw
                    } else {
                        dateRaw
                    }
                } catch (ex: Exception) {
                    dateRaw
                }
                etDate.setText(displayDate)
                etTime.setText(time)
                etMapLink.setText(mapLink)
                etPhone.setText(phone)

                // units: may be stored as list of maps under "units"
                acUnitList.clear()
                val unitsField = doc.get("units")
                if (unitsField is List<*>) {
                    for (u in unitsField) {
                        val m = u as? Map<*, *>
                        if (m != null) {
                            val brand = m["brand"]?.toString() ?: ""
                            val pk = m["pk"]?.toString() ?: ""
                            val workType = m["workType"]?.toString() ?: ""
                            acUnitList.add(ACUnit(brand = brand, pk = pk, workType = workType))
                        }
                    }
                }
                unitAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading request: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    /**
     * Shows dialog to add or edit a unit.
     * If editIndex == null => add; else edit index.
     *
     * This uses your existing dialog_add_unit.xml which has:
     * - etBrand (or etUnitBrand) -> to be defensive we check both ids
     * - etPK (or etUnitPk)
     * - spinnerWorkType (if spinner absent, fallback to EditText)
     */
    private fun showAddEditUnitDialog(editIndex: Int?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_unit, null)

        val etBrand = dialogView.findViewById<EditText>(R.id.etBrand)
        val etPK = dialogView.findViewById<EditText>(R.id.etPK)
        val spinner = dialogView.findViewById<android.widget.Spinner>(R.id.spinnerWorkType)

        val workTypes = listOf("Service", "Installation", "Repairment")
        spinner.adapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            workTypes
        )

        // PRE-FILL WHEN EDITING
        if (editIndex != null) {
            val u = acUnitList[editIndex]
            etBrand.setText(u.brand)
            etPK.setText(u.pk)

            val idx = workTypes.indexOf(u.workType)
            spinner.setSelection(if (idx >= 0) idx else 0)
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
                val brand = etBrand.text.toString().trim()
                val pk = etPK.text.toString().trim()
                val workType = spinner.selectedItem.toString()

                if (pk.isEmpty()) {
                    etPK.error = "Jumlah PK wajib diisi"
                    etPK.requestFocus()
                    return@setOnClickListener
                }

                val unit = ACUnit(brand = brand, pk = pk, workType = workType)

                if (editIndex == null) {
                    acUnitList.add(unit)
                    unitAdapter.notifyItemInserted(acUnitList.size - 1)
                } else {
                    acUnitList[editIndex] = unit
                    unitAdapter.notifyItemChanged(editIndex)
                }
                alert.dismiss()
            }

            val btnDelete = alert.getButton(AlertDialog.BUTTON_NEUTRAL)
            if (editIndex == null) {
                btnDelete.visibility = View.GONE
            } else {
                btnDelete.setOnClickListener {
                    acUnitList.removeAt(editIndex)
                    unitAdapter.notifyItemRemoved(editIndex)
                    unitAdapter.notifyItemRangeChanged(editIndex, acUnitList.size - editIndex)
                    alert.dismiss()
                }
            }
        }

        alert.show()
    }


    private fun saveChanges() {
        // validate
        val name = etName.text.toString().trim()
        val address = etAddress.text.toString().trim()
        val dateRaw = etDate.text.toString().trim()
        val time = etTime.text.toString().trim()
        val mapLink = etMapLink.text.toString().trim()
        val phone = etPhone.text.toString().trim()

        if (name.isEmpty() || address.isEmpty() || dateRaw.isEmpty() || time.isEmpty()) {
            Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show()
            return
        }

        // convert display date dd/MM/yyyy -> ISO yyyy-MM-dd if needed
        val isoDate = try {
            if (dateRaw.contains("/")) {
                val d = displayFormat.parse(dateRaw)
                if (d != null) isoFormat.format(d) else dateRaw
            } else {
                // if user already entered ISO, use as-is
                dateRaw
            }
        } catch (ex: Exception) {
            // fallback: use raw input
            dateRaw
        }

        // convert units
        val unitMaps = acUnitList.map { unit ->
            mapOf(
                "brand" to unit.brand,
                "pk" to unit.pk,
                "workType" to unit.workType
            )
        }

        val updates = mutableMapOf<String, Any>(
            "customerName" to name,
            "name" to name,
            "address" to address,
            "date" to isoDate,
            "time" to time,
            "mapLink" to mapLink,
            "phone" to phone,
            "units" to unitMaps,
            "updatedAt" to Timestamp.now()
        )

        db.collection(FirestoreFields.REQUESTS)
            .document(requestId)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Changes saved!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save changes: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
