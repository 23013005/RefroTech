package com.example.refrotech

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: FrameLayout
    private lateinit var registerButton: FrameLayout
    private lateinit var empLoginButton: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = FirebaseFirestore.getInstance()

        usernameInput = findViewById(R.id.username_input)
        passwordInput = findViewById(R.id.password_input)
        loginButton = findViewById(R.id.login_button)
        registerButton = findViewById(R.id.register_button)
        empLoginButton = findViewById(R.id.emp_login_page_button)

        // 🔹 Navigate to Register page
        registerButton.setOnClickListener {
            startActivity(Intent(this, Register::class.java))
        }

        // 🔹 Navigate to Employee Login page
        empLoginButton.setOnClickListener {
            startActivity(Intent(this, activity_pegawai_login::class.java))
        }

        // 🔹 Handle Customer Login
        loginButton.setOnClickListener {
            val username = usernameInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Query Firestore for matching username & password
            db.collection("users")
                .whereEqualTo("username", username)
                .whereEqualTo("password", password)
                .get()
                .addOnSuccessListener { result ->
                    if (!result.isEmpty) {
                        val userDoc = result.documents[0]
                        val role = userDoc.getString("role")

                        when (role) {
                            "customer" -> {
                                Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()
                            }
                            "leader", "technician" -> {
                                Toast.makeText(
                                    this,
                                    "Access denied. Please use the Employee Login page.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            else -> {
                                Toast.makeText(
                                    this,
                                    "Invalid role. Contact admin.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    } else {
                        Toast.makeText(this, "Invalid username or password.", Toast.LENGTH_LONG).show()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }
}
