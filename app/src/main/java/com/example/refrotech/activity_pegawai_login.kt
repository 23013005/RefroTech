package com.example.refrotech

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class activity_pegawai_login : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: FrameLayout
    private lateinit var custLoginButton: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.pegawai_login)

        db = FirebaseFirestore.getInstance()

        usernameInput = findViewById(R.id.username_input)
        passwordInput = findViewById(R.id.password_input)
        loginButton = findViewById(R.id.login_button)
        custLoginButton = findViewById(R.id.cust_login_page_button)

        // Switch to customer login page
        custLoginButton.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        // Employee login button
        loginButton.setOnClickListener {
            val username = usernameInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Mohon isi semua kolom.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Employee login (check Firestore)
            db.collection("users")
                .whereEqualTo("username", username)
                .whereEqualTo("password", password)
                .whereEqualTo("role", "employee")
                .get()
                .addOnSuccessListener { result ->
                    if (!result.isEmpty) {
                        Toast.makeText(this, "Login pegawai berhasil.", Toast.LENGTH_SHORT).show()
                        passwordInput.text?.clear()
                    } else {
                        Toast.makeText(
                            this,
                            "Username atau password salah, atau akun bukan pegawai.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }
}
