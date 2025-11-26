package com.example.refrotech

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RateWorkActivity : AppCompatActivity() {

    private lateinit var tvInfo: TextView
    private lateinit var ratingBar: RatingBar
    private lateinit var etComment: EditText
    private lateinit var btnSubmit: FrameLayout

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var requestId: String = ""
    private var requestDate: String = ""
    private var requestTime: String = ""
    private var requestTitle: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rate_work)

        tvInfo = findViewById(R.id.tvRateRequestInfo)
        ratingBar = findViewById(R.id.ratingBar)
        etComment = findViewById(R.id.etRateComment)
        btnSubmit = findViewById(R.id.btnSubmitRating)

        requestId = intent.getStringExtra("requestId") ?: ""
        requestDate = intent.getStringExtra("requestDate") ?: ""
        requestTime = intent.getStringExtra("requestTime") ?: ""
        requestTitle = intent.getStringExtra("requestTitle") ?: ""

        tvInfo.text = "$requestTitle — $requestDate $requestTime"

        btnSubmit.setOnClickListener { submitRating() }
    }

    private fun submitRating() {
        val stars = ratingBar.rating.toInt()
        val comment = etComment.text.toString().trim()

        if (stars < 1) {
            Toast.makeText(this, "Please choose a rating.", Toast.LENGTH_SHORT).show()
            return
        }

        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Not logged in.", Toast.LENGTH_SHORT).show()
            return
        }

        if (requestId.isBlank()) {
            Toast.makeText(this, "Invalid request.", Toast.LENGTH_SHORT).show()
            return
        }

        val update = mapOf(
            "rating" to stars.toLong(),
            "ratingComment" to comment,
            "ratedAt" to Timestamp.now(),
            "ratedBy" to uid
        )

        db.collection(FirestoreFields.REQUESTS)
            .document(requestId)
            .update(update)
            .addOnSuccessListener {
                Toast.makeText(this, "Thanks for your rating!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
