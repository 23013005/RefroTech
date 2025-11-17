package com.example.refrotech

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.google.firebase.firestore.FirebaseFirestore

class TechnicianJobDetail : AppCompatActivity() {

    private val PICK_IMAGES = 2001
    private var selectedUris = mutableListOf<Uri>()

    private lateinit var requestId: String
    private lateinit var requestData: RequestData
    private val db = FirebaseFirestore.getInstance()

    private lateinit var adapter: DocumentationPreviewAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_technician_job_detail)

        requestId = intent.getStringExtra("requestId") ?: ""

        adapter = DocumentationPreviewAdapter()
        findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerPhotos).apply {
            layoutManager = GridLayoutManager(this@TechnicianJobDetail, 3)
            adapter = this@TechnicianJobDetail.adapter
        }

        loadRequestDetails()

        findViewById<android.view.View>(R.id.btnSelectImages).setOnClickListener {
            pickImages()
        }

        findViewById<android.view.View>(R.id.btnUpload).setOnClickListener {
            uploadDocumentation()
        }

        // Bottom nav
        findViewById<android.view.View>(R.id.navHome).setOnClickListener {
            startActivity(Intent(this, TechnicianDashboard::class.java))
            finish()
        }

        findViewById<android.view.View>(R.id.navHistory).setOnClickListener {
            startActivity(Intent(this, TechnicianHistory::class.java))
            finish()
        }
    }

    private fun loadRequestDetails() {
        db.collection("requests").document(requestId)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Toast.makeText(this, "Request tidak ditemukan", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }

                requestData = RequestData.fromFirestore(doc)

                findViewById<android.widget.TextView>(R.id.tvCustomerName).text = requestData.name
                findViewById<android.widget.TextView>(R.id.tvCustomerAddress).text = requestData.address
                findViewById<android.widget.TextView>(R.id.tvScheduledTime).text =
                    "${requestData.date} • ${requestData.time}"

                adapter.setExistingPhotos(requestData.documentation)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal memuat detail", Toast.LENGTH_SHORT).show()
            }
    }

    private fun pickImages() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        startActivityForResult(Intent.createChooser(intent, "Pilih maksimal 3 gambar"), PICK_IMAGES)
    }

    override fun onActivityResult(reqCode: Int, resCode: Int, data: Intent?) {
        super.onActivityResult(reqCode, resCode, data)

        if (reqCode == PICK_IMAGES && resCode == Activity.RESULT_OK) {
            selectedUris.clear()

            if (data?.clipData != null) {
                val total = minOf(data.clipData!!.itemCount, 3)
                for (i in 0 until total) {
                    selectedUris.add(data.clipData!!.getItemAt(i).uri)
                }
            } else if (data?.data != null) {
                selectedUris.add(data.data!!)
            }

            adapter.setSelectedPhotos(selectedUris)
        }
    }

    private fun uploadDocumentation() {
        if (selectedUris.isEmpty()) {
            Toast.makeText(this, "Pilih gambar terlebih dahulu.", Toast.LENGTH_SHORT).show()
            return
        }

        DocumentationUploader.uploadImagesForRequest(
            ctx = this,
            requestId = requestId,
            uris = selectedUris,
            contentResolverProvider = { contentResolver }
        ) { success, msg ->
            runOnUiThread {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                if (success) loadRequestDetails()
            }
        }
    }
}
