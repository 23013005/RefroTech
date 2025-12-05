package com.example.refrotech

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class FullScreenImageActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_screen_image)

        val imgView = findViewById<ImageView>(R.id.fullImageView)

        val docId = intent.getStringExtra("docId") ?: ""
        val parentId = intent.getStringExtra("parentId") ?: ""
        val origin = intent.getStringExtra("originCollection") ?: ""

        if (docId.isBlank() || parentId.isBlank() || origin.isBlank()) {
            Log.e("FullScreenImg", "Missing parameters: docId=$docId parentId=$parentId origin=$origin")
            imgView.setImageResource(android.R.color.darker_gray)
            return
        }

        // Correct Firestore nested path:
        // <originCollection>/<parentId>/documentation/<docId>
        db.collection(origin)
            .document(parentId)
            .collection("documentation")
            .document(docId)
            .get()
            .addOnSuccessListener { d ->
                if (!d.exists()) {
                    Log.e("FullScreenImg", "Document not found in Firestore path")
                    imgView.setImageResource(android.R.color.darker_gray)
                    return@addOnSuccessListener
                }

                val base64 = d.getString("base64")

                if (base64.isNullOrBlank()) {
                    Log.e("FullScreenImg", "Base64 empty or null")
                    imgView.setImageResource(android.R.color.darker_gray)
                    return@addOnSuccessListener
                }

                try {
                    val trimmed = base64.replace("\n", "")
                    val bytes = Base64.decode(trimmed, Base64.DEFAULT)
                    val bitmap = decodeBitmapSafely(bytes, maxSize = 2048)

                    if (bitmap != null) {
                        imgView.setImageBitmap(bitmap)
                    } else {
                        Log.e("FullScreenImg", "Bitmap decode returned null")
                        imgView.setImageResource(android.R.color.darker_gray)
                    }

                } catch (e: Exception) {
                    Log.e("FullScreenImg", "Decode exception: ${e.message}")
                    imgView.setImageResource(android.R.color.darker_gray)
                }
            }
            .addOnFailureListener { e ->
                Log.e("FullScreenImg", "Firestore error: ${e.message}")
                imgView.setImageResource(android.R.color.darker_gray)
            }

        imgView.setOnClickListener { finish() }
    }

    private fun decodeBitmapSafely(bytes: ByteArray, maxSize: Int): Bitmap? {
        val opts = BitmapFactory.Options()
        opts.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)

        var scale = 1
        while (opts.outWidth / scale > maxSize || opts.outHeight / scale > maxSize) {
            scale *= 2
        }

        val finalOpts = BitmapFactory.Options().apply {
            inSampleSize = scale
            inPreferredConfig = Bitmap.Config.RGB_565
        }

        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, finalOpts)
    }
}
