package com.example.refrotech

import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

object DocumentationUploader {

    /**
     * Uploads up to 3 images to Firebase Storage and attaches their URLs
     * to Firestore in requests/{requestId}.documentation (array)
     */
    fun uploadImagesForRequest(
        ctx: Context,
        requestId: String,
        uris: List<Uri>,
        contentResolverProvider: () -> android.content.ContentResolver,
        callback: (Boolean, String) -> Unit
    ) {
        if (uris.isEmpty()) {
            callback(false, "Tidak ada gambar dipilih.")
            return
        }

        val storage = FirebaseStorage.getInstance().reference
            .child("documentation")
            .child(requestId)

        val db = FirebaseFirestore.getInstance()
        val uploadedUrls = mutableListOf<String>()
        var counter = 0

        for (uri in uris) {
            val fileName = System.currentTimeMillis().toString() + ".jpg"
            val fileRef = storage.child(fileName)

            val uploadTask = fileRef.putFile(uri)

            uploadTask
                .addOnSuccessListener {
                    fileRef.downloadUrl
                        .addOnSuccessListener { downloadUri ->
                            uploadedUrls.add(downloadUri.toString())

                            counter++
                            if (counter == uris.size) {
                                // All uploads finished
                                attachUrlsToFirestore(
                                    db = db,
                                    requestId = requestId,
                                    urls = uploadedUrls,
                                    callback = callback
                                )
                            }
                        }
                        .addOnFailureListener { e ->
                            callback(false, "Gagal mendapatkan URL: ${e.message}")
                        }
                }
                .addOnFailureListener { e ->
                    callback(false, "Gagal upload gambar: ${e.message}")
                }
        }
    }

    private fun attachUrlsToFirestore(
        db: FirebaseFirestore,
        requestId: String,
        urls: List<String>,
        callback: (Boolean, String) -> Unit
    ) {
        db.collection("requests").document(requestId)
            .update("documentation", FieldValue.arrayUnion(*urls.toTypedArray()))
            .addOnSuccessListener {
                // Also set job status to completed
                db.collection("requests").document(requestId)
                    .update("status", "completed")
                    .addOnSuccessListener {
                        callback(true, "Dokumentasi berhasil diupload.")
                    }
                    .addOnFailureListener { e ->
                        callback(false, "Gagal memperbarui status: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                callback(false, "Gagal menambahkan URL ke database: ${e.message}")
            }
    }
}
