package com.example.refrotech

import android.content.Context
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

object DocumentationUploader {

    /**
     * Uploads multiple images to Firebase Storage and appends URLs to request's `documentation` array.
     * Calls callback(success:Boolean, message:String).
     */
    fun uploadImagesAndAttach(context: Context, requestId: String, uris: List<Uri>, callback: (Boolean, String) -> Unit) {
        if (uris.isEmpty()) {
            callback(false, "No images")
            return
        }

        val storage = FirebaseStorage.getInstance()
        val db = FirebaseFirestore.getInstance()

        val uploadedUrls = mutableListOf<String>()
        var completed = 0
        var failed = false

        for (u in uris) {
            val key = UUID.randomUUID().toString()
            val ref = storage.reference.child("documentation/$requestId/$key.jpg")
            val uploadTask = ref.putFile(u)
            uploadTask.addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { downloadUri ->
                    uploadedUrls.add(downloadUri.toString())
                    completed += 1
                    if (completed == uris.size && !failed) {
                        // attach urls to request + update jobStatus
                        val updates = hashMapOf<String, Any>(
                            FirestoreFields.FIELD_DOCUMENTATION to com.google.firebase.firestore.FieldValue.arrayUnion(*uploadedUrls.toTypedArray()),
                            FirestoreFields.FIELD_JOB_STATUS to "completed"
                        )
                        db.collection(FirestoreFields.REQUESTS).document(requestId)
                            .update(updates as Map<String, Any>)
                            .addOnSuccessListener {
                                callback(true, "Dokumentasi berhasil diupload.")
                            }
                            .addOnFailureListener { e ->
                                callback(false, "Gagal memperbarui dokumentasi: ${e.message}")
                            }
                    }
                }.addOnFailureListener { e ->
                    failed = true
                    callback(false, "Gagal mendapatkan url: ${e.message}")
                }
            }.addOnFailureListener { e ->
                failed = true
                callback(false, "Upload gagal: ${e.message}")
            }
        }
    }
}
