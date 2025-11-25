package com.example.refrotech

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.*

/**
 * Upload images as individual Firestore documents into:
 *   requests/{requestId}/documentation/{autoId}
 * or
 *   schedules/{scheduleId}/documentation/{autoId}
 *
 * Each image is stored as:
 *   {
 *     fileName: "img_20251123_1234.jpg",
 *     base64: "....",
 *     uploadedBy: "<uid or technicianId>",
 *     createdAt: Timestamp.now()
 *   }
 *
 * Notes:
 * - Firestore document size limit ≈ 1 MiB, so each image must be compressed below ~900 KB.
 * - Max images per job = 3 (enforced here).
 */
object DocumentationUploader {

    private val db = FirebaseFirestore.getInstance()
    private const val MAX_IMAGES_PER_JOB = 3
    private const val MAX_IMAGE_BYTES = 900 * 1024 // 900 KB

    /**
     * @param context: app/activity context
     * @param origin: "request" or "schedule"
     * @param id: requestId or scheduleId
     * @param imageUris: list of content Uris (max 3)
     * @param uploaderId: string id of uploader (technician id)
     * @param callback: (success:Boolean, message:String)
     */
    fun uploadForJob(
        context: Context,
        origin: String,
        id: String,
        imageUris: List<Uri>,
        uploaderId: String,
        callback: (Boolean, String) -> Unit
    ) {
        if (imageUris.isEmpty()) {
            callback(false, "No images selected")
            return
        }
        if (imageUris.size > MAX_IMAGES_PER_JOB) {
            callback(false, "Maximum $MAX_IMAGES_PER_JOB images allowed")
            return
        }

        // Determine parent path
        val parentPath = when (origin) {
            "request" -> "${FirestoreFields.REQUESTS}/$id/documentation"
            "schedule" -> "${FirestoreFields.SCHEDULES}/$id/documentation"
            else -> {
                callback(false, "Invalid origin")
                return
            }
        }

        // Check existing count first
        db.collection(parentPath).get()
            .addOnSuccessListener { snap ->
                val existing = snap.size()
                if (existing + imageUris.size > MAX_IMAGES_PER_JOB) {
                    callback(false, "This job already has $existing image(s). Max $MAX_IMAGES_PER_JOB allowed.")
                    return@addOnSuccessListener
                }

                // process and upload images sequentially (small number)
                uploadSequentially(context, parentPath, imageUris, uploaderId, callback)
            }
            .addOnFailureListener { e ->
                callback(false, "Failed to check existing images: ${e.message}")
            }
    }

    // Internal helper: upload sequentially so we can enforce limits and return aggregated result
    private fun uploadSequentially(
        context: Context,
        parentPath: String,
        imageUris: List<Uri>,
        uploaderId: String,
        callback: (Boolean, String) -> Unit
    ) {
        val collRef = db.collection(parentPath)

        var uploaded = 0
        var failed = false
        var lastError: String? = null

        fun continueNext(i: Int) {
            if (i >= imageUris.size) {
                if (!failed) callback(true, "Uploaded $uploaded image(s)")
                else callback(false, "Uploaded $uploaded images. Last error: $lastError")
                return
            }

            val uri = imageUris[i]
            // run compression & encoding in background
            processUriToBase64(context, uri) { ok, base64OrMsg, fileName ->
                if (!ok) {
                    failed = true
                    lastError = base64OrMsg ?: "Unknown error"
                    continueNext(i + 1)
                    return@processUriToBase64
                }

                // store doc
                val docData = mapOf(
                    "fileName" to (fileName ?: UUID.randomUUID().toString() + ".jpg"),
                    // store WITHOUT linebreaks (NO_WRAP) to avoid Base64 line-wrap issues
                    "base64" to (base64OrMsg ?: ""),
                    "uploadedBy" to uploaderId,
                    "createdAt" to Timestamp.now()
                )

                collRef.add(docData)
                    .addOnSuccessListener {
                        uploaded += 1
                        continueNext(i + 1)
                    }
                    .addOnFailureListener { e ->
                        failed = true
                        lastError = e.message
                        continueNext(i + 1)
                    }
            }
        }

        continueNext(0)
    }

    /**
     * Convert Uri -> compressed JPEG byte array -> Base64 string
     * Calls callback(ok:Boolean, base64OrMessage:String?, fileName:String?)
     *
     * Implementation notes:
     * - Runs on IO dispatcher.
     * - Attempts progressive compression to keep final JPEG under MAX_IMAGE_BYTES.
     */
    private fun processUriToBase64(
        context: Context,
        uri: Uri,
        callback: (Boolean, String?, String?) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val contentResolver = context.contentResolver

                // open stream safely
                val input: InputStream? = try {
                    contentResolver.openInputStream(uri)
                } catch (_: Exception) {
                    null
                }

                if (input == null) {
                    withContext(Dispatchers.Main) {
                        callback(false, "Unable to open image", null)
                    }
                    return@launch
                }

                // decode bitmap from stream (use use{} to ensure close)
                val bytes = input.use { it.readBytes() }
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

                if (bitmap == null) {
                    withContext(Dispatchers.Main) {
                        callback(false, "Invalid image", null)
                    }
                    return@launch
                }

                // compress progressively to meet MAX_IMAGE_BYTES
                var quality = 90
                var finalBytes: ByteArray
                ByteArrayOutputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    finalBytes = out.toByteArray()
                }

                while (finalBytes.size > MAX_IMAGE_BYTES && quality >= 40) {
                    quality -= 10
                    ByteArrayOutputStream().use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                        finalBytes = out.toByteArray()
                    }
                }

                // final check
                if (finalBytes.size > MAX_IMAGE_BYTES) {
                    // still too large
                    withContext(Dispatchers.Main) {
                        callback(false, "Image is too large even after compression", null)
                    }
                    return@launch
                }

                // Use NO_WRAP to store a single-line Base64 representation (safer for storage & transport)
                val base64 = Base64.encodeToString(finalBytes, Base64.NO_WRAP)

                // determine filename if possible
                val fileName = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "img_${System.currentTimeMillis()}.jpg"

                withContext(Dispatchers.Main) {
                    callback(true, base64, fileName)
                }

            } catch (ex: Exception) {
                withContext(Dispatchers.Main) {
                    callback(false, ex.message ?: "Processing error", null)
                }
            }
        }
    }
}
