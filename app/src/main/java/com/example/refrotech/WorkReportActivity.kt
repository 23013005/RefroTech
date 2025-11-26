package com.example.refrotech

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class WorkReportActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    private lateinit var tvCustomerName: TextView
    private lateinit var tvPhone: TextView
    private lateinit var tvAddress: TextView
    private lateinit var tvScheduledDate: TextView
    private lateinit var tvScheduledTime: TextView
    private lateinit var tvTechnicians: TextView
    private lateinit var tvTechnicianIds: TextView
    private lateinit var tvUnitsLabel: TextView
    private lateinit var recyclerUnits: RecyclerView
    private lateinit var recyclerDocs: RecyclerView
    private lateinit var noReportContainer: LinearLayout

    private val units = mutableListOf<ACUnit>()
    private lateinit var unitsAdapter: ACUnitAdapter

    private lateinit var docsAdapter: DocumentationPreviewAdapter

    private var origin: String = "schedule" // "schedule" or "request"
    private var docId: String = ""

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val displayDTFormat = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_work_report)

        tvCustomerName = findViewById(R.id.tvReportCustomerName)
        tvPhone = findViewById(R.id.tvReportPhone)
        tvAddress = findViewById(R.id.tvReportAddress)
        tvScheduledDate = findViewById(R.id.tvReportDate)
        tvScheduledTime = findViewById(R.id.tvReportTime)
        tvTechnicians = findViewById(R.id.tvReportTechnicians)
        tvTechnicianIds = findViewById(R.id.tvReportTechnicianIds)
        tvUnitsLabel = findViewById(R.id.tvReportUnitsLabel)
        recyclerUnits = findViewById(R.id.recyclerReportUnits)
        recyclerDocs = findViewById(R.id.recyclerReportDocs)
        noReportContainer = findViewById(R.id.noReportContainer)

        unitsAdapter = ACUnitAdapter(units) { /* read-only */ }
        recyclerUnits.layoutManager = LinearLayoutManager(this)
        recyclerUnits.adapter = unitsAdapter

        docsAdapter = DocumentationPreviewAdapter(mutableListOf(), onDelete = null)
        recyclerDocs.layoutManager = GridLayoutManager(this, 3)
        recyclerDocs.adapter = docsAdapter

        origin = intent.getStringExtra("origin") ?: "schedule"
        docId = intent.getStringExtra("id") ?: ""

        if (docId.isBlank()) {
            showNoReport("Invalid job id")
            return
        }

        loadReport()
    }

    private fun showNoReport(msg: String) {
        noReportContainer.visibility = View.VISIBLE
        // show minimal info in textviews as empty/fallback
        tvCustomerName.text = msg
        tvPhone.text = ""
        tvAddress.text = ""
        tvScheduledDate.text = ""
        tvScheduledTime.text = ""
        tvTechnicians.text = ""
        tvTechnicianIds.text = ""
        units.clear()
        unitsAdapter.notifyDataSetChanged()
        docsAdapter.updateItems(emptyList())
    }

    // Helper: convert possible Firestore value to List<String>
    private fun toStringList(value: Any?): List<String> {
        return when (value) {
            null -> emptyList()
            is String -> listOf(value)
            is List<*> -> value.mapNotNull { it?.toString() }
            is Array<*> -> value.mapNotNull { it?.toString() }
            else -> listOf(value.toString())
        }
    }

    private fun loadReport() {
        val collectionName = if (origin == "request") FirestoreFields.REQUESTS else FirestoreFields.SCHEDULES
        db.collection(collectionName).document(docId)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    showNoReport("No job document found")
                    return@addOnSuccessListener
                }

                // Customer name (you specified: use customerName)
                val customerName = doc.getString("customerName") ?: ""

                // Phone (you specified: use phone)
                val phone = doc.getString("phone") ?: ""

                // Address
                val address = doc.getString("address") ?: ""

                // Date & time stored on the schedule/request
                val date = doc.getString("date") ?: ""
                val time = doc.getString("time") ?: ""

                // Work report timestamp read order:
                // 1) workReport.reportedAt   (map inside doc)
                // 2) completedAt (top-level)
                // else N/A
                var reportedAtTimestamp: Timestamp? = null
                val workReportMap = doc.get("workReport") as? Map<*, *>
                if (workReportMap != null) {
                    val ra = workReportMap["reportedAt"]
                    if (ra is com.google.firebase.Timestamp) reportedAtTimestamp = ra
                }
                if (reportedAtTimestamp == null) {
                    reportedAtTimestamp = doc.getTimestamp("completedAt")
                }

                // Technicians names: prefer 'technicians' list; also handle 'technician' stored as list or string
                val techFromTechnicians = toStringList(doc.get("technicians"))
                val techFromTechnician = toStringList(doc.get("technician"))
                // Merge while preserving order and dedupe
                val techCombined = (techFromTechnicians + techFromTechnician).distinct().toMutableList()

                // Technician IDs: combine technicianIds and assignedTechnicianIds deduped (safe conversion)
                val techIds1 = toStringList(doc.get("technicianIds"))
                val techIds2 = toStringList(doc.get("assignedTechnicianIds"))
                val combinedIds = (techIds1 + techIds2).distinct()

                // Units
                units.clear()
                val unitsField = doc.get("units")
                if (unitsField is List<*>) {
                    for (u in unitsField) {
                        val m = u as? Map<*, *> ?: continue
                        units.add(ACUnit(
                            brand = m["brand"]?.toString() ?: "",
                            pk = m["pk"]?.toString() ?: "",
                            workType = m["workType"]?.toString() ?: ""
                        ))
                    }
                }
                unitsAdapter.notifyDataSetChanged()

                // Documentation (same as other pages)
                db.collection(collectionName).document(docId).collection("documentation")
                    .get()
                    .addOnSuccessListener { snap ->
                        val docs = snap.documents.map { d ->
                            DocItem(
                                id = d.id,
                                base64 = d.getString("base64"),
                                fileName = d.getString("fileName"),
                                localUri = null
                            )
                        }
                        docsAdapter.updateItems(docs)
                    }
                    .addOnFailureListener {
                        docsAdapter.updateItems(emptyList())
                    }

                // Fill views
                noReportContainer.visibility = View.GONE
                tvCustomerName.text = if (customerName.isNotBlank()) customerName else "(No customer name)"
                tvPhone.text = if (phone.isNotBlank()) phone else "(No phone)"
                tvAddress.text = if (address.isNotBlank()) address else ""

                tvScheduledDate.text = if (date.isNotBlank()) date else "(No date)"
                tvScheduledTime.text = if (time.isNotBlank()) time else "(No time)"

                tvTechnicians.text = if (techCombined.isNotEmpty()) techCombined.joinToString(", ") else "(No technicians)"
                tvTechnicianIds.text = if (combinedIds.isNotEmpty()) combinedIds.joinToString(", ") else "(No technician IDs)"

                val createdAtText = when {
                    reportedAtTimestamp != null -> displayDTFormat.format(reportedAtTimestamp.toDate())
                    else -> "(No report timestamp)"
                }

            }
            .addOnFailureListener { e ->
                showNoReport("Failed to load: ${e.message}")
            }
    }
}
