package com.example.refrotech

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class EmployeeLogin : AppCompatActivity() {

    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: FrameLayout
    private lateinit var btnTogglePassword: ImageView
    private lateinit var switchToCustomer: FrameLayout

    private val db = FirebaseFirestore.getInstance()
    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_employee_login)

        // BIND VIEWS
        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        btnTogglePassword = findViewById(R.id.btnTogglePassword)
        switchToCustomer = findViewById(R.id.cust_login_page_button)

        // PASSWORD VISIBILITY TOGGLE
        btnTogglePassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible

            if (isPasswordVisible) {
                etPassword.transformationMethod =
                    android.text.method.HideReturnsTransformationMethod.getInstance()
                btnTogglePassword.setImageResource(R.drawable.ic_eye_open)
            } else {
                etPassword.transformationMethod =
                    android.text.method.PasswordTransformationMethod.getInstance()
                btnTogglePassword.setImageResource(R.drawable.ic_eye_closed)
            }

            etPassword.setSelection(etPassword.text.length)
        }

        // LOGIN BUTTON
        btnLogin.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Isi semua input!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            loginUser(username, password)
        }

        // SWITCH TO CUSTOMER LOGIN
        switchToCustomer.setOnClickListener {
            finish()
        }
    }

    // ===================== LOGIN FUNCTION ==========================
    private fun loginUser(username: String, password: String) {
        db.collection("users")
            .whereEqualTo("username", username)
            .whereEqualTo("password", password)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    Toast.makeText(this, "Username atau password salah!", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val user = result.documents[0]
                val role = user.getString("role") ?: ""
                val name = user.getString("name") ?: "Unknown"
                val userId = user.id   // FIRESTORE DOCUMENT ID

                // SAVE USER DATA LOCALLY FOR GLOBAL ACCESS
                val prefs = getSharedPreferences("user", MODE_PRIVATE)
                prefs.edit()
                    .putString("uid", userId)
                    .putString("role", role)
                    .putString("name", name)
                    .apply()

                when (role) {
                    "leader" -> {
                        startActivity(Intent(this, leader_dashboard::class.java))
                        finish()
                    }
                    "technician" -> {
                        startActivity(Intent(this, TechnicianDashboard::class.java))
                        finish()
                    }
                    else -> Toast.makeText(this, "Role tidak valid!", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
