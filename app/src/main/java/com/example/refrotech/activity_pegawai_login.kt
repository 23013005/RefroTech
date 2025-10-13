package com.example.refrotech

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class activity_pegawai_login : AppCompatActivity() {

    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: FrameLayout
    private lateinit var custLoginButton: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.pegawai_login) // employee login xml

        // Views
        usernameInput = findViewById(R.id.username_input)
        passwordInput = findViewById(R.id.password_input)
        loginButton = findViewById(R.id.login_button)
        custLoginButton = findViewById(R.id.cust_login_page_button)

        // Switch back to customer login page
        custLoginButton.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        // Login action (only show a toast on success)
        loginButton.setOnClickListener {
            val username = usernameInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Mohon isi semua kolom.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Dummy username/password check — replace with real backend later
            if (username == "employee" && password == "1234") {
                Toast.makeText(this, "Login pegawai berhasil.", Toast.LENGTH_SHORT).show()
                passwordInput.text?.clear()
            } else {
                Toast.makeText(this, "Login gagal: Username atau password salah.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
