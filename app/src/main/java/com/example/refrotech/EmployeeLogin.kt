package com.example.refrotech

import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class EmployeeLogin : AppCompatActivity() {

    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: FrameLayout
    private lateinit var btnTogglePassword: ImageView
    private lateinit var btnGoCustomerLogin: FrameLayout   // RESTORED

    private val db = FirebaseFirestore.getInstance()
    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_employee_login)

        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        btnTogglePassword = findViewById(R.id.btnTogglePassword)

        // RESTORED BUTTON
        btnGoCustomerLogin = findViewById(R.id.cust_login_page_button)

        // Password eye toggle
        btnTogglePassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                etPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
                btnTogglePassword.setImageResource(R.drawable.ic_eye_open)
            } else {
                etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                btnTogglePassword.setImageResource(R.drawable.ic_eye_closed)
            }
            etPassword.setSelection(etPassword.text?.length ?: 0)
        }

        // Login employee
        btnLogin.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter username and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            db.collection(FirestoreFields.USERS)
                .whereEqualTo("username", username)
                .whereEqualTo("password", password)
                .limit(1)
                .get()
                .addOnSuccessListener { snap ->
                    if (snap.isEmpty) {
                        Toast.makeText(this, "Invalid credentials", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }
                    val doc = snap.documents[0]
                    val role = doc.getString("role") ?: "customer"
                    val uid = doc.id

                    when (role) {
                        "leader" -> {
                            startActivity(Intent(this, LeaderDashboard::class.java).apply {
                                putExtra("userId", uid)
                            })
                            finish()
                        }
                        "technician" -> {
                            startActivity(Intent(this, TechnicianDashboard::class.java).apply {
                                putExtra("userId", uid)
                            })
                            finish()
                        }
                        else -> {
                            startActivity(Intent(this, DashboardCustomer::class.java).apply {
                                putExtra("userId", uid)
                            })
                            finish()
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Login failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        // RESTORED LISTENER (go back to customer login page)
        btnGoCustomerLogin.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
