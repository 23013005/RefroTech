package com.example.refrotech

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import java.io.InputStream

class FullScreenImageActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_screen_image)

        val imgView = findViewById<ImageView>(R.id.fullImageView)

        val base64 = intent.getStringExtra("base64")
        val uriString = intent.getStringExtra("uri")

        try {
            when {
                // FIX: Base64 ALWAYS takes priority
                !base64.isNullOrBlank() -> {
                    val trimmed = base64.replace("\n", "")
                    val bytes = Base64.decode(trimmed, Base64.DEFAULT)
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) imgView.setImageBitmap(bmp)
                    else imgView.setImageResource(android.R.color.darker_gray)
                }

                // Fallback: only use URI when no base64 provided
                !uriString.isNullOrBlank() -> {
                    val uri = android.net.Uri.parse(uriString)
                    val stream: InputStream? = contentResolver.openInputStream(uri)
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp != null) imgView.setImageBitmap(bmp)
                    else imgView.setImageResource(android.R.color.darker_gray)
                }

                else -> imgView.setImageResource(android.R.color.darker_gray)
            }
        } catch (e: Exception) {
            imgView.setImageResource(android.R.color.darker_gray)
        }

        imgView.setOnClickListener {
            finish()
        }
    }
}
