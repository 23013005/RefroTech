package com.example.refrotech

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import android.util.Log
import android.net.Uri


class TechnicianJobDetail : AppCompatActivity() {

    private val PICK_IMAGES_REQUEST = 101
    private val MAX_IMAGES_PER_JOB = 3

    private lateinit var recyclerPhotos: RecyclerView
    private lateinit var photoAdapter: DocumentationPreviewAdapter

    // canonical sources of truth
    private val existingDocs = mutableListOf<DocItem>()            // loaded from Firestore
    private val tempSelectedPreviews = mutableListOf<DocItem>()   // preview items for selected-but-not-uploaded images
    private val photoUris = mutableListOf<Uri>()                  // actual URIs that will be uploaded

    private lateinit var tvCustomerName: TextView
    private lateinit var tvCustomerAddress: TextView
    private lateinit var tvScheduledTime: TextView
    private lateinit var btnSelectImages: FrameLayout
    private lateinit var btnUpload: FrameLayout
    private lateinit var spinnerStatus: Spinner

    private var origin: String = "request"
    private var id: String = ""
    private var technicianId: String = ""

    private val db = FirebaseFirestore.getInstance()

    // reflect current job status (used to decide if delete allowed for technician)
    private var currentWorkStatus: String = "pending"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_technician_job_detail)

        origin = intent.getStringExtra("origin") ?: "request"
        id = intent.getStringExtra("id") ?: ""
        technicianId = intent.getStringExtra("userId") ?: ""

        tvCustomerName = findViewById(R.id.tvCustomerName)
        tvCustomerAddress = findViewById(R.id.tvCustomerAddress)
        tvScheduledTime = findViewById(R.id.tvScheduledTime)
        btnSelectImages = findViewById(R.id.btnSelectImages)
        btnUpload = findViewById(R.id.btnUpload)
        spinnerStatus = findViewById(R.id.spinnerStatus)

        recyclerPhotos = findViewById(R.id.recyclerPhotos)
        recyclerPhotos.layoutManager = GridLayoutManager(this, 3)

        // Adapter uses a snapshot list for display only. We give it a new merged list every time.
        photoAdapter = DocumentationPreviewAdapter(
            items = mutableListOf(),
            onDelete = { docItem ->
                handleDeleteDoc(docItem)
            }
        )
        recyclerPhotos.adapter = photoAdapter

        btnSelectImages.setOnClickListener { selectImages() }
        btnUpload.setOnClickListener { uploadImages() }

        setupStatusSpinner()

        // initial load
        loadJobDetails()
        loadExistingDocumentation()
    }

    private fun jobDocumentRef() = when (origin) {
        "request" -> db.collection(FirestoreFields.REQUESTS).document(id)
        "schedule" -> db.collection(FirestoreFields.SCHEDULES).document(id)
        else -> null
    }

    private fun documentationCollectionRef() = when (origin) {
        "request" -> db.collection(FirestoreFields.REQUESTS).document(id).collection("documentation")
        "schedule" -> db.collection(FirestoreFields.SCHEDULES).document(id).collection("documentation")
        else -> null
    }

    private fun loadJobDetails() {
        val jobDoc = jobDocumentRef() ?: return
        jobDoc.get().addOnSuccessListener { doc ->
            if (doc != null && doc.exists()) {
                val status = doc.getString("workStatus")
                    ?: doc.getString("jobStatus")
                    ?: doc.getString("status")
                currentWorkStatus = status?.lowercase() ?: "pending"

                tvCustomerName.text = doc.getString("customerName") ?: doc.getString("name") ?: "Nama Pelanggan"
                tvCustomerAddress.text = doc.getString("address") ?: "Alamat"
                val date = doc.getString("date") ?: ""
                val time = doc.getString("time") ?: ""
                tvScheduledTime.text = "$date • $time"

                setSpinnerSelectionFromStatus(currentWorkStatus)
            }
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Failed to load job details: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Load all documents under the documentation subcollection and update the UI.
     * This function REPLACES existingDocs with the fresh Firestore state and then rebuilds
     * a display list = existingDocs + tempSelectedPreviews (previews remain until upload).
     */
    private fun loadExistingDocumentation() {
        val collectionRef = documentationCollectionRef() ?: return

        Log.d("JOBDETAIL", "Loading docs from [$origin] / id=$id")
        collectionRef.get().addOnSuccessListener { snap ->
            Log.d("JOBDETAIL", "Docs fetched: ${snap.size()}")
            val docs = snap.documents.map { d ->
                DocItem(
                    id = d.id,
                    base64 = d.getString("base64"),
                    fileName = d.getString("fileName"),
                    localUri = null
                )
            }

            existingDocs.clear()
            existingDocs.addAll(docs)

            // If previews exist, show them after existing docs.
            // Build a fresh list (avoid passing mutable lists directly).
            val merged = if (tempSelectedPreviews.isNotEmpty()) {
                val result = ArrayList<DocItem>(existingDocs.size + tempSelectedPreviews.size)
                result.addAll(existingDocs)
                // ensure previews are unique by ID
                val previewsUnique = tempSelectedPreviews.distinctBy { it.id }
                result.addAll(previewsUnique)
                result.toList()
            } else {
                existingDocs.toList()
            }

            photoAdapter.updateItems(merged)

        }.addOnFailureListener { e ->
            Toast.makeText(this, "Failed to load documentation: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("JOBDETAIL", "Failed to load docs: ${e.message}")
        }
    }

    /**
     * When selecting images we must account for both uploaded and previewed images.
     * remaining = MAX - (existingDocs.size + tempSelectedPreviews.size)
     */
    private fun selectImages() {
        val remaining = MAX_IMAGES_PER_JOB - (existingDocs.size + tempSelectedPreviews.size)
        if (remaining <= 0) {
            Toast.makeText(this, "Maximum $MAX_IMAGES_PER_JOB images already used", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        startActivityForResult(Intent.createChooser(intent, "Select pictures (max $remaining)"), PICK_IMAGES_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_IMAGES_REQUEST || resultCode != Activity.RESULT_OK) return

        // note: do not clear existingDocs here (those are authoritative from Firestore)
        val clip = data?.clipData
        val remaining = MAX_IMAGES_PER_JOB - (existingDocs.size + tempSelectedPreviews.size)

        // add only new URIs and respect remaining
        if (clip != null) {
            for (i in 0 until clip.itemCount) {
                if (photoUris.size >= remaining) break
                val uri = clip.getItemAt(i).uri
                if (!photoUris.contains(uri)) photoUris.add(uri)
            }
        } else {
            data?.data?.let { uri ->
                if (photoUris.size < remaining && !photoUris.contains(uri)) photoUris.add(uri)
            }
        }

        if (photoUris.isEmpty()) {
            Toast.makeText(this, "No images selected", Toast.LENGTH_SHORT).show()
            return
        }

        // rebuild tempSelectedPreviews deterministically (avoid duplicates)
        tempSelectedPreviews.clear()
        tempSelectedPreviews.addAll(photoUris.map { uri ->
            DocItem(
                id = "temp_${uri.hashCode()}",
                base64 = null,
                fileName = uri.toString(),
                localUri = uri
            )
        })

        // update adapter with a new merged list (copied)
        val merged = ArrayList<DocItem>(existingDocs.size + tempSelectedPreviews.size)
        merged.addAll(existingDocs)
        merged.addAll(tempSelectedPreviews)
        photoAdapter.updateItems(merged)

        Toast.makeText(this, "Selected ${photoUris.size} image(s). Tap Upload to save.", Toast.LENGTH_SHORT).show()
    }

    private fun uploadImages() {
        if (photoUris.isEmpty()) {
            Toast.makeText(this, "Select images first", Toast.LENGTH_SHORT).show()
            return
        }

        // The uploader will also re-check the count in Firestore, but check locally first:
        val remaining = MAX_IMAGES_PER_JOB - existingDocs.size
        if (photoUris.size > remaining) {
            Toast.makeText(this, "Only $remaining images can be uploaded", Toast.LENGTH_SHORT).show()
            return
        }

        DocumentationUploader.uploadForJob(this, origin, id, photoUris, technicianId) { success, msg ->
            runOnUiThread {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                if (success) {
                    // after successful upload: clear previews & URIs, then refresh from Firestore
                    photoUris.clear()
                    tempSelectedPreviews.clear()
                    loadExistingDocumentation()
                }
            }
        }
    }

    /**
     * Delete either a preview (not uploaded) or an uploaded doc (Firestore).
     * - Preview deletion updates tempSelectedPreviews + photoUris
     * - Uploaded deletion calls Firestore.delete() then reloads existingDocs.
     */
    private fun handleDeleteDoc(docItem: DocItem) {
        val allowDeleteForTechnician = currentWorkStatus != "completed"
        if (!allowDeleteForTechnician) {
            Toast.makeText(this, "Cannot delete after job is completed", Toast.LENGTH_SHORT).show()
            return
        }

        // PREVIEW (not uploaded)
        if (docItem.localUri != null) {
            // remove preview and corresponding uri
            tempSelectedPreviews.removeAll { it.id == docItem.id }
            photoUris.removeAll { it == docItem.localUri }
            // update visible list
            val merged = ArrayList<DocItem>(existingDocs.size + tempSelectedPreviews.size)
            merged.addAll(existingDocs)
            merged.addAll(tempSelectedPreviews)
            photoAdapter.updateItems(merged)
            return
        }

        // UPLOADED doc: remove from Firestore by doc id
        val collectionRef = documentationCollectionRef() ?: return
        collectionRef.document(docItem.id).delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Deleted image", Toast.LENGTH_SHORT).show()
                // reload authoritative Firestore state (this will rebuild existingDocs)
                loadExistingDocumentation()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ---------------------
    // Status spinner helpers
    // ---------------------
    private fun setupStatusSpinner() {
        val statuses = listOf("Confirmed", "On-Progress", "Completed")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, statuses)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerStatus.adapter = adapter

        spinnerStatus.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            var initialized = false
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, idPos: Long) {
                if (!initialized) {
                    initialized = true
                    return
                }
                val selected = statuses[position].lowercase()
                if (selected == currentWorkStatus) return

                val allowed = when (currentWorkStatus) {
                    "confirmed" -> listOf("on-progress", "completed")
                    "on-progress" -> listOf("completed")
                    "pending" -> listOf("confirmed", "on-progress", "completed")
                    else -> listOf("confirmed", "on-progress", "completed")
                }

                if (selected !in allowed) {
                    setSpinnerSelectionFromStatus(currentWorkStatus)
                    Toast.makeText(this@TechnicianJobDetail, "Cannot change status to $selected from $currentWorkStatus", Toast.LENGTH_SHORT).show()
                    return
                }

                val docRef = jobDocumentRef() ?: return
                val fieldToUpdate = if (origin == "schedule") "workStatus" else "jobStatus"
                val updates = mapOf(fieldToUpdate to selected)

                docRef.update(updates)
                    .addOnSuccessListener {
                        currentWorkStatus = selected
                        Toast.makeText(this@TechnicianJobDetail, "Status updated", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this@TechnicianJobDetail, "Failed to update status: ${e.message}", Toast.LENGTH_SHORT).show()
                        setSpinnerSelectionFromStatus(currentWorkStatus)
                    }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun setSpinnerSelectionFromStatus(status: String) {
        val normalized = status.lowercase()
        val index = when (normalized) {
            "confirmed" -> 0
            "on-progress", "on progress", "onprogress" -> 1
            "completed" -> 2
            else -> 0
        }
        spinnerStatus.setSelection(index, false)
    }
}
