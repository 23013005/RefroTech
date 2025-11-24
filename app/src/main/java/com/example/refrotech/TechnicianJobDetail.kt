package com.example.refrotech

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class TechnicianJobDetail : AppCompatActivity() {

    private val PICK_IMAGES_REQUEST = 101
    private lateinit var recyclerPhotos: RecyclerView
    private lateinit var photoAdapter: DocumentationPreviewAdapter
    private val photoUris = mutableListOf<Uri>()

    private lateinit var tvCustomerName: TextView
    private lateinit var tvCustomerAddress: TextView
    private lateinit var tvScheduledTime: TextView
    private lateinit var btnSelectImages: Button
    private lateinit var btnUpload: Button

    private var requestId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_technician_job_detail)

        requestId = intent.getStringExtra("requestId") ?: ""

        tvCustomerName = findViewById(R.id.tvCustomerName)
        tvCustomerAddress = findViewById(R.id.tvCustomerAddress)
        tvScheduledTime = findViewById(R.id.tvScheduledTime)
        btnSelectImages = findViewById(R.id.btnSelectImages)
        btnUpload = findViewById(R.id.btnUpload)

        recyclerPhotos = findViewById(R.id.recyclerPhotos)
        recyclerPhotos.layoutManager = GridLayoutManager(this, 3)

        // Fixed constructor — now valid
        photoAdapter = DocumentationPreviewAdapter(photoUris, mutableListOf())
        recyclerPhotos.adapter = photoAdapter

        btnSelectImages.setOnClickListener { selectImages() }
        btnUpload.setOnClickListener { uploadImages() }
    }

    private fun selectImages() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        startActivityForResult(intent, PICK_IMAGES_REQUEST)
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)

        if (req == PICK_IMAGES_REQUEST && res == Activity.RESULT_OK) {

            photoUris.clear()

            // MULTIPLE IMAGES
            if (data?.clipData != null) {
                val count = data.clipData!!.itemCount
                for (i in 0 until count) {
                    // FIXED HERE — correct API
                    val uri = data.clipData!!.getItemAt(i).uri
                    photoUris.add(uri)
                }
            }
            // SINGLE IMAGE
            else if (data?.data != null) {
                photoUris.add(data.data!!)
            }

            photoAdapter.notifyDataSetChanged()
        }
    }

    private fun uploadImages() {
        if (photoUris.isEmpty()) {
            Toast.makeText(this, "Pilih gambar dahulu", Toast.LENGTH_SHORT).show()
            return
        }

        DocumentationUploader.uploadImagesAndAttach(this, requestId, photoUris) { success, msg ->
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            if (success) finish()
        }
    }
}
