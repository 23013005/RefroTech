package com.example.refrotech

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore

class LeaderRescheduleDetailActivity : AppCompatActivity() {

    private lateinit var btnApprove: TextView
    private lateinit var btnReject: TextView

    private lateinit var tvDetailName: TextView
    private lateinit var tvDetailPhone: TextView
    private lateinit var tvDetailAddress: TextView

    private lateinit var tvOldDate: TextView
    private lateinit var tvOldTime: TextView
    private lateinit var tvNewDate: TextView
    private lateinit var tvNewTime: TextView

    private lateinit var rvDetailUnits: androidx.recyclerview.widget.RecyclerView
    private lateinit var btnDetailMap: ImageView

    private val db = FirebaseFirestore.getInstance()
    private var requestId: String = ""

    private var newDate: String = ""
    private var newTime: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_leader_reschedule_detail)

        // UI References
        btnApprove = findViewById(R.id.btnDetailApprove)
        btnReject = findViewById(R.id.btnDetailReject)

        tvDetailName = findViewById(R.id.tvDetailName)
        tvDetailPhone = findViewById(R.id.tvDetailPhone)
        tvDetailAddress = findViewById(R.id.tvDetailAddress)

        tvOldDate = findViewById(R.id.tvOldDate)
        tvOldTime = findViewById(R.id.tvOldTime)
        tvNewDate = findViewById(R.id.tvNewDate)
        tvNewTime = findViewById(R.id.tvNewTime)

        rvDetailUnits = findViewById(R.id.rvDetailUnits)
        rvDetailUnits.layoutManager = LinearLayoutManager(this)

        btnDetailMap = findViewById(R.id.btnDetailMap)

        requestId = intent.getStringExtra("requestId") ?: ""

        if (requestId.isNotBlank()) {
            loadRequestDetails(requestId)
        } else {
            Toast.makeText(this, "Request ID missing", Toast.LENGTH_SHORT).show()
            finish()
        }

        // Approve → Assign Technicians Only
        btnApprove.setOnClickListener {
            showAssignTechnicianDialog()
        }

        // Reject
        btnReject.setOnClickListener {
            showRejectDialog()
        }
    }

    // ====================== LOAD REQUEST ======================
    private fun loadRequestDetails(id: String) {
        db.collection("requests").document(id).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Toast.makeText(this, "Request not found", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }

                val req = RequestData.fromFirestore(doc)

                tvDetailName.text = req.name
                tvDetailPhone.text = req.phone
                tvDetailAddress.text = req.address

                tvOldDate.text = req.oldDate ?: req.date ?: "-"
                tvOldTime.text = req.oldTime ?: req.time ?: "-"

                tvNewDate.text = req.newDate ?: "-"
                tvNewTime.text = req.newTime ?: "-"

                newDate = req.newDate ?: ""
                newTime = req.newTime ?: ""

                btnDetailMap.setOnClickListener {
                    val link = req.mapLink
                    if (!link.isNullOrBlank()) {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                    } else {
                        Toast.makeText(this, "No map link provided", Toast.LENGTH_SHORT).show()
                    }
                }

                val acUnits = req.units.map { m ->

                    val brand = (m as? Map<*, *>)?.get("brand")?.toString() ?: "-"
                    val pk = (m as? Map<*, *>)?.get("pk")?.toString() ?: "-"
                    val workType = (m as? Map<*, *>)?.get("workType")?.toString() ?: "-"

                    ACUnit(
                        brand = brand,
                        pk = pk,
                        workType = workType
                    )
                }


                rvDetailUnits.adapter = SimpleUnitsAdapter(acUnits)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load request: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ====================== ASSIGN TECHNICIAN DIALOG ======================
    private fun showAssignTechnicianDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_assign_technician, null)
        val listView = dialogView.findViewById<ListView>(R.id.listTechnicians)
        val btnConfirm = dialogView.findViewById<TextView>(R.id.btnConfirm)
        val btnCancel = dialogView.findViewById<TextView>(R.id.btnCancel)

        val selectedTechs = mutableSetOf<String>()

        // Load technicians
        db.collection("users")
            .whereEqualTo("role", "technician")
            .get()
            .addOnSuccessListener { techDocs ->

                val technicians = techDocs.documents.map { it.getString("name") ?: "Unknown" }

                val adapter = object : ArrayAdapter<String>(
                    this,
                    R.layout.item_dialog_technician,
                    technicians
                ) {
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val view = convertView ?: layoutInflater.inflate(
                            R.layout.item_dialog_technician,
                            parent,
                            false
                        )

                        val checkBox = view.findViewById<CheckBox>(R.id.checkBoxTech)
                        val tvName = view.findViewById<TextView>(R.id.tvName)
                        val tvStatus = view.findViewById<TextView>(R.id.tvStatus)

                        val name = technicians[position]

                        tvName.text = name
                        tvStatus.text = "Available"
                        tvStatus.setTextColor(getColor(android.R.color.holo_green_dark))

                        checkBox.isChecked = selectedTechs.contains(name)

                        view.setOnClickListener {
                            checkBox.isChecked = !checkBox.isChecked
                        }

                        checkBox.setOnCheckedChangeListener { _, isChecked ->
                            if (isChecked) selectedTechs.add(name)
                            else selectedTechs.remove(name)
                        }

                        return view
                    }
                }

                listView.adapter = adapter
            }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnConfirm.setOnClickListener {
            if (selectedTechs.isEmpty()) {
                Toast.makeText(this, "Pilih minimal 1 teknisi", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            saveAssignedSchedule(selectedTechs.toList())
            dialog.dismiss()
        }

        dialog.show()
    }

    // ====================== SAVE ASSIGNMENT ======================
    private fun saveAssignedSchedule(techList: List<String>) {
        val updates = mapOf(
            "status" to "assigned",
            "jobStatus" to "assigned",
            "technician" to techList.joinToString(", "),
            "date" to newDate,
            "time" to newTime
        )

        db.collection("requests")
            .document(requestId)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Reschedule disetujui & teknisi ditetapkan", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal menyimpan: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ====================== REJECT DIALOG ======================
    private fun showRejectDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_reject_reason, null)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.radioRejectReasons)
        val edtOther = dialogView.findViewById<EditText>(R.id.edtOtherReason)

        edtOther.visibility = View.GONE

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbOther) edtOther.visibility = View.VISIBLE
            else edtOther.visibility = View.GONE
        }

        AlertDialog.Builder(this)
            .setTitle("Pilih Alasan Penolakan")
            .setView(dialogView)
            .setPositiveButton("Submit") { _, _ ->
                val selectedId = radioGroup.checkedRadioButtonId
                var reason = when (selectedId) {
                    R.id.rbOther -> edtOther.text.toString().trim()
                    else -> dialogView.findViewById<RadioButton>(selectedId).text.toString()
                }

                if (reason.isBlank()) {
                    Toast.makeText(this, "Alasan tidak boleh kosong", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                rejectRequest(reason)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun rejectRequest(reason: String) {
        db.collection("requests")
            .document(requestId)
            .update(
                mapOf(
                    "status" to "rejected",
                    "rejectReason" to reason
                )
            )
            .addOnSuccessListener {
                Toast.makeText(this, "Reschedule ditolak", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
